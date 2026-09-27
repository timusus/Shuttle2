import Foundation
import S2Playback

/// The real engine behind `EngineAudioPlayer`: each `AudioEngine` call is the controller's own, with
/// the app's track and event types mapped to S2Playback's.
extension MusicPlaybackController: AudioEngine {
    func setEventHandler(_ handler: ((EngineEvent) -> Void)?) {
        guard let handler else {
            onStateChanged = nil
            onTransition = nil
            onFailed = nil
            onPosition = nil
            onSeekUnsupported = nil
            return
        }
        onStateChanged = { state, uid in handler(.state(EngineState(state), trackId: uid)) }
        onTransition = { uid in handler(.transition(trackId: uid)) }
        onFailed = { uid, error in handler(.failed(trackId: uid, message: String(describing: error))) }
        onPosition = { uid, ms in handler(.position(trackId: uid, ms: ms)) }
        onSeekUnsupported = { uid, ms in handler(.seekUnsupported(trackId: uid, ms: ms)) }
    }

    func load(current: EngineTrack, next: EngineTrack?, startMs: Int64, playWhenReady: Bool) {
        load(
            current: PlaybackTrack(current),
            next: next.map(PlaybackTrack.init),
            startMs: startMs,
            playWhenReady: playWhenReady
        )
    }

    func setNext(_ track: EngineTrack?) {
        let playbackTrack: PlaybackTrack? = track.map(PlaybackTrack.init)
        setNext(playbackTrack)
    }
}

private extension PlaybackTrack {
    init(_ track: EngineTrack) {
        self.init(uid: track.id, url: track.url, headers: track.headers, gainDb: track.gainDb)
    }
}

private extension EngineState {
    init(_ state: MusicPlaybackController.State) {
        switch state {
        case .idle: self = .idle
        case .loading: self = .loading
        case .playing: self = .playing
        case .paused: self = .paused
        case .ended: self = .ended
        }
    }
}
