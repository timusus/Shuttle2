import Foundation
import Shared

/// The Kotlin `IosAudioPlayer` over the S2Playback engine (phase 6, docs/architecture/ios-port/phase-6-playback.md):
/// each method is one engine call, and each engine report goes to the Kotlin listener. The queue, what comes next
/// and what to do about a failed track are the Kotlin `IosPlayerController`'s.
///
/// **Threading.** Kotlin calls on the main thread; the engine's API is thread-safe, so every call goes straight
/// through. The engine reports on the main queue, in order, and each report reaches the listener synchronously there.
/// A failure found here (a URL that doesn't parse) is posted to the main queue the same way, never reported inside
/// the Kotlin call that caused it.
///
/// **Audio session.** `onWillPlay` runs before anything plays (`play()`, or a load that plays) and `onPaused` on
/// every pause; `PlaybackSystemCoordinator` points them at `AudioSessionController.activate()` and
/// `playbackPaused()`. A session that won't activate (a call, another app holding the hardware) cancels the play:
/// `play()` does nothing and a load loads paused. Both report paused for that track, on the calling thread, so Kotlin
/// can drop the intent. A refused load's report is not the track becoming ready — the engine's own loading and paused
/// reports, afterwards and in order, are. A refused play of a track that's already ready has no engine transition, so
/// the paused report is the only one. A play while the engine is already playing is no change, so it asks nothing of
/// the session and is never refused.
///
/// **Which play a report answers.** The engine stamps each state report with the commands it had taken (loads, plays,
/// pauses, stops), and they're counted here as they're sent: a report made before the engine took the last of them is
/// forwarded as superseded. Kotlin doesn't take a pause's or a load's paused, queued before a play reached the engine,
/// for that play's refusal (#708); any other paused while it intends to play is a refusal, or the engine pausing itself
/// (#716). Nor is a playing report from before a pause playing now.
final class EngineAudioPlayer: NSObject, IosAudioPlayer {
    /// Called on the main thread before playback starts; false cancels it.
    var onWillPlay: () -> Bool = { true }
    /// Called on the main thread on every pause.
    var onPaused: () -> Void = {}

    private(set) var engine: AudioEngine
    /// Held strongly: Kotlin's listener is an object only the player refers to.
    private var listener: IosAudioPlayerListener?
    /// The track the engine is playing as far as Kotlin knows: the last loaded, or transitioned to. The engine's
    /// position only counts for it, as a load or a transition reaches the engine queue after the call returns.
    private var currentId: String?
    /// The engine's last report for `currentId` was playing, and nothing asked it to pause, load or stop since.
    private var isPlaying = false
    /// Commands sent to the engine that it counts in its state reports (loads, plays, pauses, stops).
    private var commandsSent = 0
    /// The equalizer Kotlin last set, handed to a replacement engine too.
    private var equalizer: EngineEqualizer?

    init(engine: AudioEngine) {
        self.engine = engine
        super.init()
        attach(engine)
    }

    /// Swaps in a rebuilt engine after a media-services reset. The old one is silenced and stopped, and the new one
    /// gets the equalizer; the caller reloads the current item into it.
    func replaceEngine(_ newEngine: AudioEngine) {
        engine.setEventHandler(nil)
        engine.stop()
        currentId = nil
        isPlaying = false
        commandsSent = 0
        engine = newEngine
        attach(newEngine)
        equalizer?.apply(to: newEngine)
    }

    private func attach(_ engine: AudioEngine) {
        engine.setEventHandler { [weak self] event in self?.forward(event) }
    }

    private func forward(_ event: EngineEvent) {
        switch event {
        case let .state(state, trackId, commands):
            let superseded = commands < commandsSent
            isPlaying = state == .playing && trackId == currentId && !superseded
            listener?.onStateChanged(trackId: trackId ?? "", state: IosAudioPlayerState(state), superseded: superseded)
        case let .transition(trackId):
            currentId = trackId
            listener?.onTransition(trackId: trackId)
        case let .failed(trackId, message):
            listener?.onFailed(trackId: trackId, message: message)
        case let .position(trackId, ms):
            listener?.onPosition(trackId: trackId, positionMs: ms)
        case let .seekUnsupported(trackId, ms):
            listener?.onSeekUnsupported(trackId: trackId, positionMs: ms)
        }
    }

    // MARK: - IosAudioPlayer

    func setListener(listener: IosAudioPlayerListener?) {
        self.listener = listener
    }

    func load(current: IosAudioTrack, next: IosAudioTrack?, startMs: Int64, playWhenReady: Bool) {
        currentId = current.id
        isPlaying = false
        commandsSent += 1
        guard let track = engineTrack(current) else {
            engine.stop()
            return
        }
        let plays = playWhenReady && onWillPlay()
        engine.load(current: track, next: next.flatMap(engineTrack), startMs: startMs, playWhenReady: plays)
        if playWhenReady && !plays { reportPaused(current.id) }
    }

    func setNext(next: IosAudioTrack?) {
        engine.setNext(next.flatMap(engineTrack))
    }

    func play() {
        guard !isPlaying else { return }
        guard onWillPlay() else {
            if let currentId { reportPaused(currentId) }
            return
        }
        commandsSent += 1
        engine.play()
    }

    /// The session refused playback of `trackId`. Synchronous, on the call's thread (the main thread): Kotlin is still
    /// inside the load or play and can tell this from the engine's later ready-paused.
    private func reportPaused(_ trackId: String) {
        listener?.onStateChanged(trackId: trackId, state: .paused, superseded: false)
    }

    func pause() {
        isPlaying = false
        commandsSent += 1
        engine.pause()
        onPaused()
    }

    func seek(positionMs: Int64) {
        engine.seek(toMs: positionMs)
    }

    func stop() {
        currentId = nil
        isPlaying = false
        commandsSent += 1
        engine.stop()
    }

    func setSpeed(speed: Float) {
        engine.setSpeed(speed)
    }

    func positionMs() -> Int64 {
        guard let position = engine.position, position.uid == currentId else { return -1 }
        return position.ms
    }

    func durationMs() -> Int64 {
        guard let currentId, engine.position?.uid == currentId else { return -1 }
        return engine.durationMs ?? -1
    }

    func setEqualizer(enabled: Bool, preampDb: Float, coefficients: KotlinDoubleArray) {
        let settings = EngineEqualizer(
            enabled: enabled,
            preampDb: preampDb,
            coefficients: (0..<coefficients.size).map { coefficients.get(index: $0) }
        )
        equalizer = settings
        settings.apply(to: engine)
    }

    func engineSampleRate() -> Int32 {
        Int32(engine.outputSampleRate)
    }

    // MARK: -

    /// The engine's form of `track`, or nil (its failure posted) if its URL doesn't parse.
    private func engineTrack(_ track: IosAudioTrack) -> EngineTrack? {
        guard let url = URL(string: track.url) else {
            let id = track.id
            let message = "Not a URL: \(track.url)"
            DispatchQueue.main.async { [weak self] in self?.listener?.onFailed(trackId: id, message: message) }
            return nil
        }
        return EngineTrack(
            id: track.id,
            url: url,
            headers: track.headers,
            gainDb: track.gainDb,
            expectedDurationMs: track.expectedDurationMs > 0 ? track.expectedDurationMs : nil
        )
    }
}

/// What Kotlin's `IosEqualizer` designed, as the engine takes it.
private struct EngineEqualizer {
    let enabled: Bool
    let preampDb: Float
    let coefficients: [Double]

    func apply(to engine: AudioEngine) {
        engine.setEqualizer(enabled: enabled, preampDb: preampDb, coefficients: coefficients)
    }
}

private extension IosAudioPlayerState {
    init(_ state: EngineState) {
        switch state {
        case .idle: self = .idle
        case .loading: self = .loading
        case .playing: self = .playing
        case .paused: self = .paused
        case .ended: self = .ended
        }
    }
}
