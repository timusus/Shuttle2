import Foundation

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
}

public extension TrackPCMSource {
    var isSeekable: Bool { true }
}

/// A track decoded by FFmpeg from a file or an HTTP(S) URL.
///
/// File URLs are read through ``FileByteReader``; anything else through ``HTTPRangeByteSource``
/// (range requests, read-ahead and the on-disk run cache Podcasts built for streaming).
public final class FFmpegTrackSource: TrackPCMSource {
    private let url: URL
    private let headers: [String: String]
    private let lock = NSLock()
    private var reader: StreamByteReader?
    private var decoder: FFmpegStreamDecoder?
    private var outputRate: Double = 0
    private var outputChannels = 2
    private var cancelled = false

    public init(url: URL, headers: [String: String] = [:]) {
        self.url = url
        self.headers = headers
    }

    public func open(sampleRate: Double, channelCount: Int) throws -> Int64? {
        let reader: StreamByteReader
        do {
            reader = url.isFileURL
                ? try FileByteReader(url: url)
                : HTTPRangeByteSource(url: url, authHeaders: headers)
        } catch {
            throw TrackSourceError.failed("open \(url.lastPathComponent): \(error)")
        }
        let decoder = FFmpegStreamDecoder(reader: reader)
        lock.lock()
        let wasCancelled = cancelled
        self.reader = reader
        self.decoder = decoder
        lock.unlock()
        if wasCancelled { decoder.cancel() }
        do {
            let format = try decoder.open()
            try decoder.setOutputFormat(sampleRate: sampleRate, channelCount: channelCount)
            outputRate = sampleRate
            outputChannels = channelCount
            return format.duration > 0 ? Int64((format.duration * sampleRate).rounded()) : nil
        } catch StreamDecoderError.cancelled {
            throw TrackSourceError.cancelled
        } catch StreamDecoderError.interrupted {
            throw TrackSourceError.interrupted
        } catch {
            throw TrackSourceError.failed("decode \(url.lastPathComponent): \(error)")
        }
    }

    /// Exact: the decoder lands at or before the target (the first decoded frame's timestamp), and
    /// the frames between the landing and the target are read and dropped here.
    public func seek(toFrame frame: Int64) throws {
        guard let decoder else { throw TrackSourceError.failed("seek before open") }
        let landed: TimeInterval
        do {
            landed = try decoder.seek(toSeconds: Double(frame) / outputRate)
        } catch StreamDecoderError.interrupted {
            throw TrackSourceError.interrupted
        } catch StreamDecoderError.cancelled {
            throw TrackSourceError.cancelled
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

    /// A file, or an HTTP stream whose length the host gave (`Content-Range` or `Content-Length`).
    /// A transcode streamed as it is made has neither.
    public var isSeekable: Bool {
        lock.lock()
        defer { lock.unlock() }
        return reader?.totalLength != nil
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
