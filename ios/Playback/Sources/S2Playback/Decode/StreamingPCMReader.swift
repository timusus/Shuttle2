// Copied from Shuttle Podcasts (podcasts@9ee6e0954) mobile/ios/Playback/Sources/Playback/StreamingPCMReader.swift — see ios/Playback/README.md.
import Foundation
import OSLog

/// Same category as the engine controller's and ``HTTPRangeByteSource``'s, so one
/// `log stream --predicate 'category == "audio-engine"'` shows the fetch and the decode of a stall
/// together.
private let engineLog = Logger(subsystem: "com.simplecityapps.shuttle2", category: "audio-engine")

/// The decoded shape of a piece of media, learned once before playback starts.
struct AudioAssetFormat: Equatable {
    let sampleRate: Double
    let channelCount: Int
    /// Total media duration in seconds, or 0 when the container will not say.
    let duration: TimeInterval
}

/// **Pulls interleaved Float32 PCM out of a local file or an HTTP stream, through FFmpeg.**
///
/// The replacement for `AudioAssetPCMReader`, and the reason it is a replacement rather than a
/// sibling is in the plan (`mobile/ios/docs/plans/2026-09-09-streaming-audio-pipeline.md` §6 Phase
/// 1, and §8's verdict on `AVAssetReader`): `AVAssetReader` needs a finished, seekable asset, so on
/// an `https://` URL it yields no frame and no error. Every streamed episode on the engine path was
/// therefore silent. FFmpeg over a seekable byte source has no such restriction, and it is the same
/// decoder the ad-skip scanner already uses, so the player and the scanner cannot drift.
///
/// The shape is exactly what ``AVAudioEnginePlaybackController`` already drove: learn the format
/// once, ``start(at:)`` to (re)position, pull chunks until nil, ``cancel()``. Two things are new and
/// both matter upstream:
///
///  - ``landedPosition`` — a seek lands on a frame boundary, not on the requested second (plan
///    §5.1). The controller anchors its media clock to THAT, or the scrubber disagrees with the
///    audio and every ad-skip seek is computed against a time nobody played.
///  - ``ReaderError/unprobeable(_:)`` — FFmpeg refusing to probe (HLS, a 404 body, garbage) is a
///    distinguishable failure, because plan §1's `AVPlayer` fallback keys off it.
///
/// Everything but ``cancel()`` blocks, which is why the caller runs it on its own queue.
final class StreamingPCMReader: ReadAheadControl {

    /// Why ``nextChunk()`` stopped handing out samples. Same cases the controller already switches
    /// on, so its end-of-episode handling is unchanged.
    enum EndReason: String {
        case running, eof, failure, cancelled, notReading
        /// ``interrupt()`` brought a blocked read back so a seek could be applied. **Not the end of
        /// anything**: the next ``start(at:seekGeneration:)`` resumes on the same decoder, and a
        /// caller that treated it as end of stream would end the episode on every stalled seek.
        case interrupted
    }

    enum ReaderError: Error, CustomStringConvertible {
        /// FFmpeg could not identify the container or find an audio stream in it. **The fallback
        /// signal**: an HLS playlist, a 404's HTML body and a truncated file all land here, and the
        /// answer to all three is the `AVPlayer` path, not an error the listener sees.
        case unprobeable(String)
        /// ``start(at:)`` or ``nextChunk()`` before ``loadFormat()``.
        case notOpen

        var description: String {
            switch self {
            case let .unprobeable(why): return "cannot decode this stream: \(why)"
            case .notOpen: return "streaming reader used before its format was loaded"
            }
        }
    }

    private let url: URL
    private let logHost: String
    private let authHeaderCount: Int
    private let byteReader: StreamByteReader
    /// Nil for a `file://` URL. Held so the read-ahead window can be told the duration, and so the
    /// spine's appetite and the smoke's byte counters have something to ask.
    private let httpSource: HTTPRangeByteSource?
    private let decoder: FFmpegStreamDecoder
    /// Every blocking call is serialised here, so `open` on one queue and `start` on another cannot
    /// race the decoder, which is explicitly not thread-safe.
    private let work = DispatchQueue(label: "com.simplecityapps.shuttle2.streaming-pcm-reader")

    private let stateLock = NSLock()
    private var formatValue: AudioAssetFormat?
    private var probeValue: StartupTiming.Probe?
    private var probeStartedAtValue: TimeInterval?
    private var probeFinishedAtValue: TimeInterval?
    private var landedPositionValue: TimeInterval = 0
    private var hasStarted = false
    private var didLogEnd = false

    /// - Parameters:
    ///   - authHeaders: resolved through `FeedRequestAuthorizing` by the caller — a private feed's
    ///     audio needs its `Authorization` header and the blocking read cannot await a lookup.
    ///     Values never reach a log; host and header count only.
    ///   - tee: where the raw enclosure bytes go for the ad-skip spine. Nil for a download and
    ///     whenever the spine is off.
    ///   - readAhead: how far past the decoder the fetch may run. Defaulted to the production
    ///     profile; a test drives it down to a few kilobytes so a seek is actually outside the
    ///     window and the fetch bound is observable at all.
    init(
        url: URL,
        authHeaders: [String: String] = [:],
        tee: AudioByteTee? = nil,
        readAhead: ReadAheadPolicy = .default,
        session: URLSession = HTTPRangeByteSource.sharedSession,
        runStore: CachedRunStore? = .shared,
        resolvedURLs: ResolvedURLCache? = .shared
    ) throws {
        self.url = url
        self.logHost = url.host ?? "?"
        self.authHeaderCount = authHeaders.count
        if url.isFileURL {
            // A downloaded episode is a complete, stable file: no range transactions, no tee.
            self.httpSource = nil
            self.byteReader = try FileByteReader(url: url)
        } else {
            let source = HTTPRangeByteSource(
                url: url,
                authHeaders: authHeaders,
                readAhead: readAhead,
                session: session,
                tee: tee,
                runStore: runStore,
                resolvedURLs: resolvedURLs
            )
            self.httpSource = source
            self.byteReader = source
        }
        self.tee = url.isFileURL ? nil : tee
        self.decoder = FFmpegStreamDecoder(reader: byteReader)
        // Before the first transaction opens (`loadFormat()`), so appetite can be wired from the
        // tee's first byte.
        self.tee?.byteSourceDidStart(readAhead: self)
    }

    /// The tee the byte source writes to, kept so a seek can be announced to it before the
    /// decoder opens the transaction that seek causes. The byte source holds it strongly.
    private let tee: AudioByteTee?

    // MARK: - Diagnostics

    private(set) var endReasonValue: EndReason = .running

    var endReason: EndReason {
        stateLock.lock(); defer { stateLock.unlock() }
        return endReasonValue
    }

    var mediaFramesRead: Int64 { decoder.mediaFramesRead }

    /// Where ``start(at:)`` actually landed, in media seconds. See the type's note.
    var landedPosition: TimeInterval {
        stateLock.lock(); defer { stateLock.unlock() }
        return landedPositionValue
    }

    var format: AudioAssetFormat? {
        stateLock.lock(); defer { stateLock.unlock() }
        return formatValue
    }

    /// What the probe found and what it read, for the start timing (#193). Nil until
    /// ``loadFormat()`` has returned.
    var probe: StartupTiming.Probe? {
        stateLock.lock(); defer { stateLock.unlock() }
        return probeValue
    }

    /// When `open()` began, on ``StartupTiming/now()``'s clock. Stamped on the reader's queue, so
    /// the probe is measured from the decoder's first chance at a byte and not from the tap: on a
    /// start served from the run on disk there is no first response to measure it from, and the
    /// tap-to-open path (session, node stop, tee, task hop, reader init) is not the probe.
    var probeStartedAt: TimeInterval? {
        stateLock.lock(); defer { stateLock.unlock() }
        return probeStartedAtValue
    }

    /// When `open()` returned, on ``StartupTiming/now()``'s clock. Stamped on the reader's queue,
    /// before the continuation hop back to the caller, so the probe is not charged for that hop.
    var probeFinishedAt: TimeInterval? {
        stateLock.lock(); defer { stateLock.unlock() }
        return probeFinishedAtValue
    }

    /// Bytes the decoder has consumed from the source: the playhead in bytes.
    var bytesConsumed: Int64 { decoder.bytesConsumed }

    // MARK: - ReadAheadControl

    var decoderBytesConsumed: Int64 { decoder.bytesConsumed }
    func openAppetite() { httpSource?.openAppetite() }
    func closeAppetite() { httpSource?.closeAppetite() }
    /// Bytes `URLSession` has handed over across every transaction. Nil for a file.
    var bytesFetched: Int64? { httpSource?.bytesFetched }
    /// Response bodies opened. Nil for a file.
    var transactionCount: Int? { httpSource?.transactionCount }
    /// How the mp3 footer look was answered. Nil for a file.
    var tailStatus: StartupTiming.Tail? { httpSource?.tailStatus }
    /// Fetched but not yet decoded, in bytes — half of the engine controller's buffered-duration
    /// estimate. Zero for a file, which is never short of bytes.
    var bufferedAheadBytes: Int64 { httpSource?.bufferedAheadBytes ?? 0 }

    /// The byte source, for the player's own stall recovery (``HTTPRangeByteSource/reopen()``).
    /// Nil for a download.
    var byteSource: HTTPRangeByteSource? { httpSource }

    // MARK: - Loading

    /// Probe the container and learn its audio format. Must be awaited before ``start(at:)``.
    ///
    /// The blocking probe runs on this reader's own queue: `open()` reads through the byte source,
    /// which on a cellular link is a whole round trip, and the caller's `Task` may well be on the
    /// main actor.
    func loadFormat() async throws -> AudioAssetFormat {
        try await withCheckedThrowingContinuation { continuation in
            work.async { [self] in
                do {
                    let probeStartedAt = StartupTiming.now()
                    stateLock.lock()
                    probeStartedAtValue = probeStartedAt
                    stateLock.unlock()
                    let opened = try decoder.open()
                    let probeFinishedAt = StartupTiming.now()
                    guard opened.sampleRate > 0, opened.channelCount > 0 else {
                        throw ReaderError.unprobeable("no usable audio stream")
                    }
                    let resolved = AudioAssetFormat(
                        sampleRate: opened.sampleRate,
                        channelCount: opened.channelCount,
                        duration: opened.duration.isFinite && opened.duration > 0 ? opened.duration : 0
                    )
                    stateLock.lock()
                    formatValue = resolved
                    probeValue = StartupTiming.Probe(
                        codec: opened.codec,
                        container: opened.container,
                        bytes: decoder.bytesConsumed
                    )
                    probeFinishedAtValue = probeFinishedAt
                    stateLock.unlock()
                    // The read-ahead window is expressed in SECONDS of audio; without a duration it
                    // falls back to a fixed byte count, which on a 30 kbps talk feed is minutes and
                    // on a 320 kbps music feed is seconds.
                    if resolved.duration > 0 { httpSource?.durationHint = resolved.duration }
                    engineLog.info(
                        """
                        engine: reader format host=\(logHost, privacy: .public) \
                        auth_headers=\(authHeaderCount, privacy: .public) \
                        codec=\(opened.codec, privacy: .public) \
                        container=\(opened.container, privacy: .public) \
                        sample_rate=\(Int(resolved.sampleRate), privacy: .public) \
                        channels=\(resolved.channelCount, privacy: .public) \
                        duration_s=\(String(format: "%.1f", resolved.duration), privacy: .public)
                        """
                    )
                    continuation.resume(returning: resolved)
                } catch let error as ReaderError {
                    continuation.resume(throwing: error)
                } catch StreamDecoderError.cancelled {
                    continuation.resume(throwing: StreamDecoderError.cancelled)
                } catch {
                    // Everything else FFmpeg can answer with means the same thing to the caller:
                    // these bytes are not something this decoder can play.
                    continuation.resume(throwing: ReaderError.unprobeable(String(describing: error)))
                }
            }
        }
    }

    /// Begin (or resume) decoding at `position` seconds, and record where it landed.
    ///
    /// - Parameter seekGeneration: the player's seek generation, carried to the byte source so the
    ///   transaction this opens can be matched to the seek that asked for it — see
    ///   ``AudioByteTee/playerWillSeek(toMs:generation:)``.
    ///
    /// Safe to call repeatedly: after the first call each one is a seek on the LIVE decoder, which
    /// is the whole difference from the `AVAssetReader` this replaces — no reader is rebuilt, so the
    /// container is not re-probed and the connection is reused when the target is inside the window.
    func start(at position: TimeInterval, seekGeneration: Int = 0) throws {
        guard let format = format else { throw ReaderError.notOpen }
        let target = max(position, 0)
        // Before the decoder is told anything: the byte source opens its transaction from inside
        // the seek below, so the tag has to be in place before that call, not after it.
        httpSource?.seekGeneration = seekGeneration

        stateLock.lock()
        let needsSeek = hasStarted || target > 0
        hasStarted = true
        stateLock.unlock()

        var landed = target
        if needsSeek {
            // Same reason, same order: the tee learns the target and the generation before the
            // transaction that will carry them can exist.
            tee?.playerWillSeek(toMs: Int64(target * 1000), generation: seekGeneration)
            landed = try decoder.seek(toSeconds: target)
        }

        stateLock.lock()
        landedPositionValue = landed
        endReasonValue = .running
        didLogEnd = false
        stateLock.unlock()

        engineLog.info(
            """
            engine: reader start host=\(self.logHost, privacy: .public) \
            auth_headers=\(self.authHeaderCount, privacy: .public) \
            sample_rate=\(Int(format.sampleRate), privacy: .public) \
            channels=\(format.channelCount, privacy: .public) \
            duration_s=\(String(format: "%.1f", format.duration), privacy: .public) \
            from_s=\(String(format: "%.1f", target), privacy: .public) \
            landed_s=\(String(format: "%.1f", landed), privacy: .public)
            """
        )
    }

    /// The next block of interleaved Float32 samples, or nil at end of stream.
    ///
    /// nil is not by itself "the episode finished" — ``endReason`` says which of EOF, a cancel and a
    /// transport failure it was, and the controller distinguishes them.
    func nextChunk() -> [Float]? {
        guard let chunk = decoder.nextChunk() else {
            logEnd()
            return nil
        }
        return chunk
    }

    /// Bring a blocked ``nextChunk()`` back so a seek can be applied. See
    /// ``FFmpegStreamDecoder/interrupt()``: the reader stays usable and the next
    /// ``start(at:seekGeneration:)`` resumes it. Safe from any thread.
    func interrupt() {
        stateLock.lock()
        if endReasonValue == .running { endReasonValue = .interrupted }
        stateLock.unlock()
        decoder.interrupt()
    }

    func cancel() {
        stateLock.lock()
        if endReasonValue == .running { endReasonValue = .cancelled }
        stateLock.unlock()
        // The decoder first: it cancels the reader too, and a reader cancelled from under a decode
        // in flight is the case its own `cancel()` is written for.
        decoder.cancel()
        byteReader.cancel()
    }

    /// Record and log why the stream ended. Only reached on the nil path, and at most once.
    private func logEnd() {
        let resolved: EndReason
        switch decoder.endReason {
        case .running: resolved = .notReading
        case .eof: resolved = .eof
        case .failure: resolved = .failure
        case .cancelled: resolved = .cancelled
        case .interrupted: resolved = .interrupted
        }

        stateLock.lock()
        endReasonValue = resolved
        // An interruption is a pause in the conversation, not the end of one, so it neither logs an
        // end nor latches: the seek that follows starts reading again on the same decoder.
        let alreadyLogged = didLogEnd
        if resolved != .interrupted { didLogEnd = true }
        stateLock.unlock()
        guard !alreadyLogged, resolved != .interrupted else { return }

        engineLog.info(
            """
            engine: reader end reason=\(resolved.rawValue, privacy: .public) \
            media_frames=\(self.decoder.mediaFramesRead, privacy: .public) \
            bytes_consumed=\(self.decoder.bytesConsumed, privacy: .public) \
            host=\(self.logHost, privacy: .public)
            """
        )
    }
}
