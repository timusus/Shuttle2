import Foundation
import PlaybackDecode

/// Why a ``TrackPCMSource`` could not deliver audio.
public enum TrackSourceError: Error, Equatable {
    /// The source could not be opened or decoded. The controller reports it through `onFailed`.
    case failed(String)
    /// ``TrackPCMSource/cancel()`` ended the read.
    case cancelled
    /// ``TrackPCMSource/interrupt()`` ended the read so a seek could be applied; not terminal.
    case interrupted
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
/// File URLs are read through `FileByteReader`; anything else through ``HTTPRangeByteSource``
/// (range requests, read-ahead and the on-disk run cache Podcasts built for streaming). The decoder
/// owns demux, decode, the conversion to the controller's format and sample-accurate seeking; this
/// picks the byte source and maps the decoder's errors to ``TrackSourceError``.
public final class FFmpegTrackSource: TrackPCMSource {
    private let url: URL
    private let makeReader: () throws -> StreamByteReader
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

    public init(url: URL, headers: [String: String] = [:]) {
        self.url = url
        makeReader = {
            if url.isFileURL { return try FileByteReader(url: url) }
            return HTTPRangeByteSource(url: url, authHeaders: headers)
        }
    }

    /// Tests: reads `url`'s bytes through `makeReader`'s reader, not one picked by its scheme.
    init(url: URL, makeReader: @escaping () throws -> StreamByteReader) {
        self.url = url
        self.makeReader = makeReader
    }

    public func open(sampleRate: Double, channelCount: Int) throws -> Int64? {
        let reader: StreamByteReader
        do {
            reader = try makeReader()
        } catch {
            throw TrackSourceError.failed("open \(url.lastPathComponent): \(error)")
        }
        let decoder = FFmpegStreamDecoder(reader: reader)
        lock.lock()
        if let readWaiting { (reader as? HTTPRangeByteSource)?.onReadWaiting = readWaiting }
        let wasCancelled = cancelled
        self.reader = reader
        self.decoder = decoder
        openStartedAt = StartupTiming.now()
        lock.unlock()
        if wasCancelled { decoder.cancel() }
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
            throw TrackSourceError.failed("decode \(url.lastPathComponent): \(error)")
        }
    }

    /// Exact: the decoder lands on the requested sample. The one exception is a VBR MP3 sought far
    /// from a frame of known time, which lands on an estimate from its Xing table or bitrate; one
    /// that lands early is read forward to the target here.
    ///
    /// A reader that refuses the offset (a forward-only stream) ends the decode, and the track
    /// reports itself unseekable from then on (``isSeekable``).
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
            throw TrackSourceError.failed("seek \(url.lastPathComponent): the stream can't seek")
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
        guard let http = reader as? HTTPRangeByteSource, let duration, let total = http.totalLength, total > 0
        else { return nil }
        return Double(http.bufferedAheadBytes) * duration / Double(total)
    }

    /// What the open learned and cost, for the start timing (#687); nil until it began. The
    /// byte source's figures are read as of now.
    var openStats: StartupTiming.OpenStats? {
        let (reader, startedAt, finishedAt, probe) = lock.withLock { (reader, openStartedAt, openFinishedAt, probe) }
        guard let startedAt else { return nil }
        var stats = StartupTiming.OpenStats(startedAt: startedAt, finishedAt: finishedAt, probe: probe)
        if let http = reader as? HTTPRangeByteSource {
            stats.requestIssuedAt = http.requestIssuedAt
            stats.firstResponseAt = http.firstResponseAt
            stats.firstResponse = http.firstResponse
            stats.transactions = http.transactionCount
            stats.tail = http.tailStatus
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
        case .failure: throw TrackSourceError.failed("decode \(url.lastPathComponent) failed mid-stream")
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
