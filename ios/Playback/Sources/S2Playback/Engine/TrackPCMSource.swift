import Foundation
import PlaybackDecode
import PlaybackStreaming

/// Why a ``TrackPCMSource`` could not deliver audio.
public enum TrackSourceError: Error, Equatable {
    /// The source could not be opened or decoded. The controller reports it through `onFailed`.
    case failed(String)
    /// ``TrackPCMSource/cancel()`` ended the read.
    case cancelled
    /// ``TrackPCMSource/interrupt()`` ended the read so a seek could be applied; not terminal.
    case interrupted
    /// ``TrackPCMSource/seek(toFrame:)`` needed bytes the stream couldn't serve (a transcode whose
    /// length was estimated). Terminal: every read after it throws it too, and the source reports
    /// itself unseekable. The controller reports it through `onSeekUnsupported`, as it does a
    /// source that was never seekable, and reads nothing more until the owner re-opens the track.
    case unseekable
}

/// One track's audio as interleaved float32 in the controller's output format.
///
/// The seam between ``MusicPlaybackController`` and the decoder: the controller only ever asks for
/// the next frames of the current track, then of the next one. ``FFmpegTrackSource`` is the real
/// implementation; tests use an in-memory one, which is what lets the gapless claims be checked
/// sample for sample.
///
/// Called on the controller's engine queue only, except ``cancel()``, ``interrupt()`` and
/// ``isSeekable``, which are safe from any thread.
public protocol TrackPCMSource: AnyObject {
    /// Open the track and convert everything read afterwards to `sampleRate` Hz, `channelCount`
    /// channels. Blocking. Returns the duration in OUTPUT frames, nil when the container does not
    /// know it.
    func open(sampleRate: Double, channelCount: Int) throws -> Int64?

    /// Position the next ``read(into:maxFrames:)`` at exactly `frame` (output frames from the start).
    func seek(toFrame frame: Int64) throws

    /// Whether ``seek(toFrame:)`` can work, asked once the source is open. False for a stream of
    /// unknown length (a server's progressive transcode): no byte maps to a time, and its host
    /// answers every range from the start. The controller never seeks one; it reports
    /// `onSeekUnsupported` and the owner re-opens the stream at the position. Nor does it ever
    /// interrupt one, since only a seek undoes an interrupt. Safe from any thread.
    var isSeekable: Bool { get }

    /// Read up to `maxFrames` interleaved frames into `buffer`. Returns the frames written; 0 is
    /// the end of the track. Throws on failure, cancel or interrupt.
    func read(into buffer: UnsafeMutablePointer<Float>, maxFrames: Int) throws -> Int

    func cancel()
    func interrupt()

    /// `handler` is called on the reading thread each time a read has waited a while for its stream, so the
    /// engine can tell a stall from inside its own read. A source that never waits ignores it.
    func onReadWaiting(_ handler: @escaping () -> Void)
}

public extension TrackPCMSource {
    var isSeekable: Bool { true }

    func onReadWaiting(_ handler: @escaping () -> Void) {}
}

/// A track decoded by shuttle-playback's `FFmpegStreamDecoder` from a file or an HTTP(S) URL.
///
/// File URLs, and streams ``StreamStore`` kept whole, are read through `FileByteReader`; anything
/// else downloads through shuttle-playback's `GrowingFileByteSource` (``StreamedTrackReader``),
/// kept under the stream's token-free ``StreamCacheKey``. The decoder owns demux, decode, the
/// conversion to the controller's format and sample-accurate seeking; this picks the byte source
/// and maps the decoder's errors to ``TrackSourceError``.
public final class FFmpegTrackSource: TrackPCMSource {
    private let url: URL
    /// The reader, and the kept download it reads when it reads one.
    private let makeReader: (_ onWait: @escaping () -> Void) throws -> (StreamByteReader, URL?)
    private let lock = NSLock()
    private var reader: StreamByteReader?
    private var decoder: FFmpegStreamDecoder?
    private var outputRate: Double = 0
    private var outputChannels = 2
    private var cancelled = false
    /// The decoder refused a seek because the reader couldn't serve the offset: from then on the
    /// track reports itself unseekable.
    private var seekRefused = false
    private var openStartedAt: TimeInterval?
    private var openFinishedAt: TimeInterval?
    private var probe: StartupTiming.Probe?
    private var readWaiting: (() -> Void)?
    private var durationSeconds: Double?

    /// `readAhead` caps how far a stream downloads ahead of the decoder on an expensive network path; nil
    /// downloads it whole.
    public convenience init(url: URL, headers: [String: String] = [:], readAhead: GrowingFileReadAhead? = nil) {
        self.init(url: url, headers: headers, readAhead: readAhead, store: StreamStore.shared)
    }

    /// Tests: streams into `store` rather than the app's.
    init(url: URL, headers: [String: String] = [:], readAhead: GrowingFileReadAhead? = nil, store: GrowingFileStore) {
        self.url = url
        makeReader = { onWait in
            if url.isFileURL { return (try FileByteReader(url: url), nil) }
            let key = StreamCacheKey.stableURL(for: url)
            if let kept = store.completedFile(for: key) { return (try FileByteReader(url: kept), kept) }
            let source = GrowingFileByteSource(
                url: url,
                authHeaders: headers,
                cacheKey: key,
                connectionPolicy: ServerConnections.policy?.growingFilePolicy(for: url),
                readAhead: readAhead,
                store: store
            )
            return (StreamedTrackReader(source, onWait: onWait), nil)
        }
    }

    /// Tests: reads `url`'s bytes through `makeReader`'s reader, not one picked by its scheme.
    init(url: URL, makeReader: @escaping () throws -> StreamByteReader) {
        self.url = url
        self.makeReader = { _ in (try makeReader(), nil) }
    }

    public func open(sampleRate: Double, channelCount: Int) throws -> Int64? {
        let reader: StreamByteReader
        let keptFile: URL?
        do {
            (reader, keptFile) = try makeReader { [weak self] in self?.lock.withLock { self?.readWaiting }?() }
        } catch {
            throw TrackSourceError.failed("open \(url.lastPathComponent): \(error)")
        }
        let stream = (reader as? StreamedTrackReader)?.source
        let decoder = FFmpegStreamDecoder(reader: reader)
        lock.lock()
        let wasCancelled = cancelled
        self.reader = reader
        self.decoder = decoder
        openStartedAt = StartupTiming.now()
        lock.unlock()
        if wasCancelled { decoder.cancel() }
        stream?.isProbing = true
        defer { stream?.isProbing = false }
        do {
            let format = try decoder.open()
            lock.withLock {
                openFinishedAt = StartupTiming.now()
                probe = StartupTiming.Probe(codec: format.codec, container: format.container, bytes: decoder.bytesConsumed)
            }
            try decoder.setOutputFormat(sampleRate: sampleRate, channelCount: channelCount)
            outputRate = sampleRate
            outputChannels = channelCount
            guard let duration = format.duration else { return nil }
            lock.withLock { durationSeconds = duration }
            return Int64((duration * sampleRate).rounded())
        } catch StreamDecoderError.cancelled {
            throw TrackSourceError.cancelled
        } catch StreamDecoderError.interrupted {
            throw TrackSourceError.interrupted
        } catch {
            // Bytes the decoder couldn't open aren't kept for the next play.
            stream?.cancelDiscardingCache()
            keptFile.map { try? FileManager.default.removeItem(at: $0) }
            throw TrackSourceError.failed("decode \(url.lastPathComponent): \(error)")
        }
    }

    /// Exact: the decoder lands on the requested sample. The one exception is a VBR MP3 sought far
    /// from a frame of known time, which lands on an estimate from its Xing table or bitrate; one
    /// that lands early is read forward to the target here.
    ///
    /// A reader that refuses the offset (a forward-only stream) ends the decode with
    /// ``TrackSourceError/unseekable``, and the track reports itself unseekable from then on
    /// (``isSeekable``).
    public func seek(toFrame frame: Int64) throws {
        guard let decoder else { throw TrackSourceError.failed("seek before open") }
        let landed: TimeInterval
        do {
            landed = try decoder.seek(toSeconds: Double(frame) / outputRate)
        } catch StreamDecoderError.interrupted {
            throw TrackSourceError.interrupted
        } catch StreamDecoderError.cancelled {
            throw TrackSourceError.cancelled
        } catch StreamDecoderError.unseekable {
            lock.withLock { seekRefused = true }
            throw TrackSourceError.unseekable
        } catch {
            throw TrackSourceError.failed("seek \(url.lastPathComponent): \(error)")
        }
        var skip = frame - Int64((landed * outputRate).rounded())
        guard skip > 0 else { return }
        let scratchFrames = 4096
        var scratch = [Float](repeating: 0, count: scratchFrames * outputChannels)
        while skip > 0 {
            let want = Int(min(skip, Int64(scratchFrames)))
            let got = try scratch.withUnsafeMutableBufferPointer { try read(into: $0.baseAddress!, maxFrames: want) }
            if got == 0 { return }
            skip -= Int64(got)
        }
    }

    /// Whether it plays over HTTP, for the start timing.
    var isStreamed: Bool { !url.isFileURL }

    /// Which server it streams from and whether that's a transcode: nil for a file.
    var streamOrigin: StartupTiming.Origin? { isStreamed ? StartupTiming.Origin(url: url) : nil }

    /// Seconds of the track fetched ahead of the decoder and not yet read, at its average bitrate: nil for a file, or
    /// a stream whose length or duration isn't known. For the buffer health line (#897).
    var networkBufferedSeconds: Double? {
        let (reader, duration) = lock.withLock { (reader, durationSeconds) }
        guard let stream = (reader as? StreamedTrackReader)?.source, let duration else { return nil }
        let file = stream.snapshot
        guard let total = file.totalLength, total > 0 else { return nil }
        let ahead = max(0, file.frontier - max(stream.position, file.base))
        return Double(ahead) * duration / Double(total)
    }

    /// What the open learned and cost, for the start timing (#687); nil until it began. The
    /// byte source's figures are read as of now.
    var openStats: StartupTiming.OpenStats? {
        let (reader, startedAt, finishedAt, probe) = lock.withLock { (reader, openStartedAt, openFinishedAt, probe) }
        guard let startedAt else { return nil }
        var stats = StartupTiming.OpenStats(startedAt: startedAt, finishedAt: finishedAt, probe: probe)
        if let stream = (reader as? StreamedTrackReader)?.source {
            let startup = stream.startup
            stats.requestIssuedAt = startup.requestIssuedAt
            stats.firstResponseAt = startup.firstResponseAt
            stats.firstResponse = startup.status.map {
                StartupTiming.FirstResponse(status: $0, redirects: startup.hosts.count, hosts: startup.hosts,
                                            remembered: startup.remembered)
            }
            stats.transactions = stream.snapshot.transactionGeneration
        }
        return stats
    }

    /// A file, or an HTTP stream whose length the host gave (`Content-Range` or `Content-Length`),
    /// that hasn't refused a seek. A transcode streamed as it is made has no length.
    public var isSeekable: Bool {
        lock.lock()
        defer { lock.unlock() }
        return !seekRefused && reader?.totalLength != nil
    }

    public func read(into buffer: UnsafeMutablePointer<Float>, maxFrames: Int) throws -> Int {
        guard let decoder else { throw TrackSourceError.failed("read before open") }
        let frames = decoder.read(into: buffer, maxFrames: maxFrames)
        if frames > 0 { return frames }
        switch decoder.endReason {
        case .eof, .running: return 0
        case .cancelled: throw TrackSourceError.cancelled
        case .interrupted: throw TrackSourceError.interrupted
        case .failure:
            if lock.withLock({ seekRefused }) { throw TrackSourceError.unseekable }
            throw TrackSourceError.failed("decode \(url.lastPathComponent) failed mid-stream")
        }
    }

    public func onReadWaiting(_ handler: @escaping () -> Void) {
        lock.withLock { readWaiting = handler }
    }

    public func cancel() {
        lock.lock()
        cancelled = true
        let decoder = self.decoder
        let reader = self.reader
        lock.unlock()
        decoder?.cancel()
        reader?.cancel()
    }

    public func interrupt() {
        lock.lock()
        let decoder = self.decoder
        lock.unlock()
        decoder?.interrupt()
    }
}
