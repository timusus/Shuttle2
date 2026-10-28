import AVFoundation
import Foundation
import os
import PlaybackDecode
@_spi(Testing) import PlaybackPlayer
import PlaybackRender
import PlaybackStreaming
import S2Playback
import Shared

/// The Kotlin `IosAudioPlayer` over AudioPlaybackKit's `Player`, which owns the play order, the gapless join, repeat,
/// pause at the end of an item and the audio session, as Media3 does on Android. Kotlin keeps the queue and its policy
/// (skipping failed items, restart-vs-previous) and hands the whole order over on every edit; each item opens its stream
/// through Kotlin's `IosItemResolver` when the player decodes it.
///
/// **Threading.** Kotlin calls on the main thread and every call runs on the main actor there; the player's events and
/// state changes reach the listener on the main actor. Positions are seconds here and ms in Kotlin. Items are reported
/// under their queue uid.
final class KitAudioPlayer: NSObject, IosAudioPlayer {
    struct QueueKey: Hashable, Sendable {
        let uid: Int64
        let songId: Int64
    }

    let player: Player<QueueKey>
    private let replayGain: ReplayGainProcessor
    private let equalizer = EqualizerProcessor()
    /// Held strongly: Kotlin's listener is an object only the player refers to.
    private var listener: IosAudioPlayerListener?
    private var sampleRateListener: ((KotlinInt?) -> Void)?
    private var lastReport: (uid: Int64, state: IosAudioPlayerState)?
    private var positionObserver: TimeObserverToken?
    private var lastLoggedStatus: String?
    private var routeObserver: NSObjectProtocol?
    private let log = Logger(subsystem: "com.simplecityapps.shuttle2", category: "playback")
    /// When the last play start was asked for, until it is heard; feeds the time-to-first-audio log.
    private var startRequested: UInt64?
    /// Whether the last load, skip or seek is still to be heard; a resume of audio already heard isn't timed.
    private var awaitingFirstAudio = false
    private struct Times {
        var positionMs: Int64 = -1
        var durationMs: Int64 = -1
    }

    private let times = OSAllocatedUnfairLock(initialState: Times())

    @MainActor
    init(player: Player<QueueKey>, replayGain: ReplayGainProcessor = ReplayGainProcessor()) {
        self.player = player
        self.replayGain = replayGain
        super.init()
        // Always active; the kit queues up to 30 s ahead, so a live parameter change calls `processorsChanged()` to be
        // heard now. ReplayGain levels each item before the EQ's preamp and filters, as on Android; the limiter catches
        // the overshoot of both.
        player.setProcessors([replayGain, equalizer, LimiterProcessor()])
        observeState()
        observeSampleRate()
        observeTimes()
        observeRoute()
        Task { @MainActor [weak self, events = player.events] in
            for await event in events {
                self?.handle(event)
            }
        }
        // Ticks for the progress bar; Kotlin's elapsed time between them is its own.
        positionObserver = player.addPeriodicObserver(interval: 0.5) { [weak self] in
            self?.tick()
        }
    }

    deinit {
        if let routeObserver { NotificationCenter.default.removeObserver(routeObserver) }
    }

    /// The app's player: each item's source reads the stream `resolve` hands it, opened on the decode thread, and plays
    /// at the ReplayGain of the stream's tags. A transcode seeks and recovers by resolving the stream again at the position.
    @MainActor
    static func live(resolve: @escaping ResolvingByteReader.Resolve) -> KitAudioPlayer {
        let replayGain = ReplayGainProcessor()
        let player = Player<QueueKey>(makeSource: { key in
            TimeOffsetItemSource { startMs in
                ResolvingByteReader(uid: key.uid, startMs: startMs) { uid, startMs, completion in
                    resolve(uid, startMs) { stream, error in
                        if let stream {
                            replayGain.setTags(track: stream.replayGainTrackDb?.floatValue,
                                               album: stream.replayGainAlbumDb?.floatValue, for: key)
                        }
                        completion(stream, error)
                    }
                }
            }
        })
        return KitAudioPlayer(player: player, replayGain: replayGain)
    }

    // MARK: - Player to Kotlin

    @MainActor
    private func observeState() {
        withObservationTracking {
            _ = (player.state, player.isPlaying, player.playWhenReady, player.currentIndex, player.error != nil)
        } onChange: { [weak self] in
            Task { @MainActor in
                self?.logStatus()
                self?.reportState()
                self?.observeState()
            }
        }
    }

    /// The status lines the AirPlay device check reads: one per change, with the position it happened at.
    @MainActor
    private func logStatus() {
        let status = if player.error != nil {
            "failed"
        } else {
            switch player.state {
            case .idle: "idle"
            case .buffering: "buffering"
            case .ready: player.isPlaying ? "playing" : "paused"
            case .ended: "ended"
            }
        }
        guard status != lastLoggedStatus else { return }
        lastLoggedStatus = status
        log.info("status \(status, privacy: .public) at \(self.player.position, format: .fixed(precision: 2), privacy: .public) s")
    }

    private func observeRoute() {
        routeObserver = NotificationCenter.default.addObserver(
            forName: AVAudioSession.routeChangeNotification, object: nil, queue: .main
        ) { [weak self] note in
            let reason = (note.userInfo?[AVAudioSessionRouteChangeReasonKey] as? UInt) ?? 0
            let session = AVAudioSession.sharedInstance()
            let ports = session.currentRoute.outputs.map(\.portType.rawValue).joined(separator: ",")
            self?.log.info("route change reason \(reason, privacy: .public), output \(ports, privacy: .public), latency \(session.outputLatency, format: .fixed(precision: 3), privacy: .public) s")
        }
    }

    @MainActor
    private func observeSampleRate() {
        let rate = withObservationTracking {
            player.audioFormat.map { Int32($0.sampleRate) }
        } onChange: { [weak self] in
            Task { @MainActor in self?.observeSampleRate() }
        }
        sampleRateListener?(rate.map { KotlinInt(int: $0) })
    }

    @MainActor
    private func reportState() {
        guard let uid = player.currentItem?.uid, player.error == nil else { return }
        let state: IosAudioPlayerState = switch player.state {
        case .idle: .idle
        // A paused load or seek buffers until its first audio; as on Android it reads as paused, not loading.
        case .buffering: player.playWhenReady ? .loading : .paused
        case .ready: player.isPlaying ? .playing : .paused
        case .ended: .ended
        }
        if let lastReport, lastReport.uid == uid, lastReport.state == state { return }
        lastReport = (uid, state)
        listener?.onStateChanged(trackId: "\(uid)", state: state)
    }

    @MainActor
    private func requestStart(_ playWhenReady: Bool) {
        awaitingFirstAudio = true
        startRequested = playWhenReady ? DispatchTime.now().uptimeNanoseconds : nil
    }

    @MainActor
    private func firstAudioRendered(_ item: QueueKey?) {
        awaitingFirstAudio = false
        if let item { log.info("first audio rendered, song \(item.songId, privacy: .public)") }
        guard let start = startRequested, let item else { return }
        startRequested = nil
        let ms = (DispatchTime.now().uptimeNanoseconds - start) / 1_000_000
        log.notice("time to first audio: song \(item.songId, privacy: .public), \(ms, privacy: .public) ms")
    }

    @MainActor
    private func handle(_ event: PlayerEvent<QueueKey>) {
        switch event {
        case let .itemTransition(_, to, reason):
            // A seek's transition is Kotlin's own move; only the player's moves are news to it.
            guard reason != .seek, let item = to?.item else { return }
            lastReport = nil
            listener?.onTransition(trackId: "\(item.uid)")
            // Kotlin drops a report for an item before it has moved onto it, so the arrived item's state follows here.
            reportState()
        case let .itemFailed(ref, error):
            lastReport = nil
            listener?.onFailed(trackId: "\(ref.item.uid)", message: "\(error.underlying)")
        case let .seekLanded(ref, position), let .seekAdjusted(ref, _, position):
            // An adjusted or refused seek moves Kotlin's optimistic progress back to where the player is.
            listener?.onPosition(trackId: "\(ref.item.uid)", positionMs: Int64(position * 1000))
        case .recoveredPaused:
            reportState()
        case let .playWhenReadyChanged(playing, reason):
            if !playing, reason == .endOfItem, let uid = player.currentItem?.uid {
                listener?.onPausedAtEnd(trackId: "\(uid)")
            }
        case let .firstAudioRendered(ref, _):
            firstAudioRendered(ref.item)
        case let .bufferingStarted(ref, position):
            log.info("underrun: buffering started, song \(ref.item.songId, privacy: .public) at \(position, format: .fixed(precision: 2), privacy: .public) s")
        case .bufferingEnded:
            log.info("underrun: buffering ended at \(self.player.position, format: .fixed(precision: 2), privacy: .public) s")
        }
    }

    @MainActor
    private func tick() {
        guard player.isPlaying, let uid = player.currentItem?.uid else { return }
        listener?.onPosition(trackId: "\(uid)", positionMs: Int64(player.position * 1000))
    }

    // MARK: - IosAudioPlayer

    func setListener(listener: IosAudioPlayerListener?) {
        self.listener = listener
    }

    func setItems(order: [IosQueueEntry], currentIndex: Int32, startMs: Int64, playWhenReady: Bool) {
        MainActor.assumeIsolated {
            lastReport = nil
            requestStart(playWhenReady)
            player.setItems(order.map(Self.key), startIndex: Int(currentIndex), startPosition: Double(startMs) / 1000)
            player.playWhenReady = playWhenReady
        }
    }

    func replaceItems(order: [IosQueueEntry], currentIndex: Int32) {
        MainActor.assumeIsolated { player.replaceItems(order.map(Self.key), currentIndex: Int(currentIndex)) }
    }

    func skipTo(index: Int32, startMs: Int64, playWhenReady: Bool) {
        MainActor.assumeIsolated {
            lastReport = nil
            requestStart(playWhenReady)
            let failed = player.error != nil
            let moves = player.discontinuityCount
            // A move to the current item is a seek in it, which it may refuse.
            player.seek(to: Double(startMs) / 1000, itemAt: Int(index))
            if player.discontinuityCount == moves { awaitingFirstAudio = false }
            prepareAfterFailure(failed, playWhenReady: playWhenReady, position: Double(startMs) / 1000)
        }
    }

    func skipToNext(playWhenReady: Bool) {
        MainActor.assumeIsolated {
            lastReport = nil
            requestStart(playWhenReady)
            let failed = player.error != nil
            player.skipToNext()
            prepareAfterFailure(failed, playWhenReady: playWhenReady, position: 0)
        }
    }

    /// A failed or stopped player only moves its index on a seek or skip, and a play prepares the item there; a
    /// paused move off a failure is prepared by handing the order over again, as the Player has no `prepare()`.
    @MainActor
    private func prepareAfterFailure(_ failed: Bool, playWhenReady: Bool, position: TimeInterval) {
        if failed, !playWhenReady, let index = player.currentIndex {
            player.setItems(player.items, startIndex: index, startPosition: position)
        }
        player.playWhenReady = playWhenReady
    }

    func setRepeatMode(mode: Shared.RepeatMode) {
        MainActor.assumeIsolated {
            player.repeatMode = switch mode {
            case .off: .off
            case .all: .all
            case .one: .one
            }
        }
    }

    // Answered with a report: the controller takes the intent from it, so a refused play must still say paused. A
    // failed player reports nothing; Kotlin has already dealt with its item.
    func play() {
        MainActor.assumeIsolated {
            lastReport = nil
            if awaitingFirstAudio, !player.isPlaying { startRequested = DispatchTime.now().uptimeNanoseconds }
            player.play()
            reportState()
        }
    }

    func pause() {
        MainActor.assumeIsolated {
            lastReport = nil
            startRequested = nil
            player.pause()
            reportState()
        }
    }

    func seek(positionMs: Int64) {
        MainActor.assumeIsolated {
            let moves = player.discontinuityCount
            player.seek(to: Double(positionMs) / 1000)
            // A refused seek leaves the audio already heard playing.
            if player.discontinuityCount != moves { awaitingFirstAudio = true }
        }
    }

    func stop() {
        MainActor.assumeIsolated {
            lastReport = nil
            requestStart(false)
            player.stop()
        }
    }

    func setSpeed(speed: Float) {
        MainActor.assumeIsolated { player.rate = Double(speed) }
    }

    func setPauseAtEnd(enabled: Bool) {
        MainActor.assumeIsolated { player.pauseAtEndOfItem = enabled }
    }

    // Off the main thread these read the last position and duration the player published there.
    func positionMs() -> Int64 {
        guard Thread.isMainThread else { return times.withLock(\.positionMs) }
        return MainActor.assumeIsolated { currentTimes().positionMs }
    }

    func durationMs() -> Int64 {
        guard Thread.isMainThread else { return times.withLock(\.durationMs) }
        return MainActor.assumeIsolated { currentTimes().durationMs }
    }

    @MainActor
    private func currentTimes() -> Times {
        Times(positionMs: player.currentIndex == nil ? -1 : Int64(player.position * 1000),
              durationMs: player.duration.map { Int64($0 * 1000) } ?? -1)
    }

    @MainActor
    private func observeTimes() {
        let current = withObservationTracking {
            currentTimes()
        } onChange: { [weak self] in
            Task { @MainActor in self?.observeTimes() }
        }
        times.withLock { $0 = current }
    }

    func setEqualizer(design: IosEqualizerDesign) {
        equalizer.setEqualizer(enabled: design.enabled) { sampleRate in
            let filters = design.filtersAt(sampleRateHz: Int32(sampleRate))
            let coefficients = filters.coefficients
            return EqualizerDesign(preampDb: filters.preampDb,
                                   coefficients: (0..<coefficients.size).map { coefficients.get(index: $0) })
        }
        MainActor.assumeIsolated { player.processorsChanged() }
    }

    func setOutputSampleRateListener(listener: ((KotlinInt?) -> Void)?) {
        MainActor.assumeIsolated {
            sampleRateListener = listener
            listener?(player.audioFormat.map { KotlinInt(int: Int32($0.sampleRate)) })
        }
    }

    func setReplayGain(mode: ReplayGainMode, preampDb: Float) {
        let kitMode: ReplayGainProcessor.Mode = switch mode {
        case .track: .track
        case .album: .album
        default: .off
        }
        replayGain.setMode(kitMode, preampDb: preampDb)
        MainActor.assumeIsolated { player.processorsChanged() }
    }

    private static func key(_ entry: IosQueueEntry) -> QueueKey {
        QueueKey(uid: entry.uid, songId: entry.songId)
    }
}

/// A queue item's bytes, found on first use: it blocks the decode thread on Kotlin's resolver (on the main thread),
/// then reads the file or download it named. A stream that can't be resolved (a paywall refusal, a server error)
/// throws, so the player reports the item failed.
final class ResolvingByteReader: TimeOffsetByteReader, @unchecked Sendable {
    /// Hands `completion` the item's stream started `startMs` in, or why it has none; called on the main thread.
    typealias Resolve = @MainActor (_ uid: Int64, _ startMs: Int64, _ completion: @escaping (IosStream?, String?) -> Void) -> Void

    private let uid: Int64
    private let startMs: Int64
    private let resolve: Resolve
    /// Guards the state below; signalled when the resolve answers, or on a cancel or interrupt.
    private let condition = NSCondition()
    private var inner: Result<StreamByteReader, Error>?
    private var requested = false
    private var cancelled = false
    private var interrupted = false
    private var answer: (stream: IosStream?, error: String?)?

    init(uid: Int64, startMs: Int64, resolve: @escaping Resolve) {
        self.uid = uid
        self.startMs = startMs
        self.resolve = resolve
    }

    var transcodeDurationMs: Int64? {
        condition.withLock {
            guard let stream = answer?.stream, stream.transcoded else { return nil }
            return stream.durationMs > 0 ? startMs + stream.durationMs : -1
        }
    }

    var totalLength: Int64? { try? opened().totalLength }

    var position: Int64 { (try? opened().position) ?? 0 }

    func read(into buffer: UnsafeMutableRawPointer, maxLength: Int) throws -> Int {
        try opened().read(into: buffer, maxLength: maxLength)
    }

    func seek(to offset: Int64) throws {
        try opened().seek(to: offset)
    }

    func cancel() {
        settle { cancelled = true }?.cancel()
    }

    // Also ends a wait on the resolve, which the player's seek or skip would otherwise sit behind.
    func interrupt() {
        settle { interrupted = true }?.interrupt()
    }

    func clearInterrupt() {
        settle { interrupted = false }?.clearInterrupt()
    }

    /// Applies `change` and wakes a waiting open; returns the opened reader to forward to, if there is one.
    private func settle(_ change: () -> Void) -> StreamByteReader? {
        condition.withLock {
            change()
            condition.broadcast()
            return try? inner?.get()
        }
    }

    private func opened() throws -> StreamByteReader {
        try condition.withLock {
            if let inner { return try inner.get() }
            if !requested {
                requested = true
                DispatchQueue.main.async {
                    MainActor.assumeIsolated {
                        self.resolve(self.uid, self.startMs) { stream, error in
                            self.condition.withLock {
                                self.answer = (stream, error)
                                self.condition.broadcast()
                            }
                        }
                    }
                }
            }
            while answer == nil, !cancelled, !interrupted {
                condition.wait()
            }
            if cancelled { throw StreamByteReaderError.cancelled }
            if interrupted { throw StreamByteReaderError.interrupted }
            guard let answer else { preconditionFailure("the wait ends only on an answer, a cancel or an interrupt") }
            let result = Result { try Self.reader(answer.stream, error: answer.error) }
            inner = result
            return try result.get()
        }
    }

    private static func reader(_ stream: IosStream?, error: String?) throws -> StreamByteReader {
        guard let stream, let url = URL(string: stream.url) else {
            throw StreamByteReaderError.failed(StreamFailure(.internal, detail: error ?? "no stream"))
        }
        return try StreamStore.reader(url: url, headers: stream.headers, readAhead: readAhead(stream))
    }

    /// How far `stream` downloads ahead on an expensive network: a minute at the library's rate (-1 is unknown).
    static func readAhead(_ stream: IosStream) -> GrowingFileReadAhead {
        StreamReadAhead.readAhead(bitrateKbps: Int(stream.bitrateKbps), sizeBytes: stream.sizeBytes, durationMs: stream.durationMs)
    }
}
