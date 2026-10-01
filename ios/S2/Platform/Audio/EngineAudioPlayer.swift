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
/// `play()` does nothing and a load loads paused, so the engine reports paused, as when the engine can't start.
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
        engine = newEngine
        attach(newEngine)
        equalizer?.apply(to: newEngine)
    }

    private func attach(_ engine: AudioEngine) {
        engine.setEventHandler { [weak self] event in self?.forward(event) }
    }

    private func forward(_ event: EngineEvent) {
        switch event {
        case let .state(state, trackId):
            listener?.onStateChanged(trackId: trackId ?? "", state: IosAudioPlayerState(state))
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
        guard let track = engineTrack(current) else {
            engine.stop()
            return
        }
        let plays = playWhenReady && onWillPlay()
        engine.load(current: track, next: next.flatMap(engineTrack), startMs: startMs, playWhenReady: plays)
    }

    func setNext(next: IosAudioTrack?) {
        engine.setNext(next.flatMap(engineTrack))
    }

    func play() {
        guard onWillPlay() else { return }
        engine.play()
    }

    func pause() {
        engine.pause()
        onPaused()
    }

    func seek(positionMs: Int64) {
        engine.seek(toMs: positionMs)
    }

    func stop() {
        currentId = nil
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
