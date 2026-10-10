import Foundation
import os

// MARK: - State and reports (engine queue)

extension MusicPlaybackController {
    func frames(ms: Int64) -> Int64 { ms * Int64(outputSampleRate) / 1000 }

    func ms(frames: Int64) -> Int64 { frames * 1000 / Int64(outputSampleRate) }

    func setState(_ newState: State) {
        guard newState != state else { return }
        engineLog.notice(
            "state \(self.state.rawValue, privacy: .public) -> \(newState.rawValue, privacy: .public) uid \(self.current?.track.uid ?? "-", privacy: .public)"
        )
        state = newState
        if newState == .playing { lastHealthLog = nil }
        if newState == .paused || newState == .idle || newState == .ended {
            endUnderrun(newState.rawValue)
            pauseEngine()
        }
        reportState(newState)
    }

    /// The current track's state, on the callback queue. Playing is loading while an underrun lasts: a play, a
    /// restart's start, a command answered then doesn't end the buffering the listener hears (#897).
    func reportState(_ newState: State) {
        let newState = newState == .playing && underrun != nil ? .loading : newState
        let uid = current?.track.uid
        let commands = commandsTaken
        commandsReported = commands
        let callback = callbackLock.withLock { callbacks.state }
        if let callback { callbackQueue.async { callback(newState, uid, commands) } }
    }

    /// A command that changed nothing `setState` reports (a play while playing, a pause while paused, a play
    /// refused while paused) still has its answer: the state it left, at its count. Otherwise the owner would
    /// only have reports from before it, which it takes as superseded, and never hear where the engine is. The
    /// end of a track isn't repeated: it's an event, said once, and nothing but a load moves the engine on.
    func answerCommand() {
        guard commandsReported < commandsTaken, state != .ended else { return }
        reportState(state)
    }

    func reportFailure(_ slot: Slot, _ error: Error) {
        slot.failed = true
        // The description can carry a URL and its query: only the domain and code are public.
        let nsError = error as NSError
        engineLog.error(
            "track \(slot.track.uid, privacy: .public) failed: \(nsError.domain, privacy: .public) \(nsError.code) \(String(describing: error), privacy: .private)"
        )
        let uid = slot.track.uid
        let callback = callbackLock.withLock { callbacks.failed }
        if let callback { callbackQueue.async { callback(uid, error) } }
    }

    func reportSeekUnsupported(_ slot: Slot, ms: Int64) {
        engineLog.info("track \(slot.track.uid, privacy: .public) can't seek; reporting \(ms) ms")
        let uid = slot.track.uid
        let callback = callbackLock.withLock { callbacks.seekUnsupported }
        if let callback { callbackQueue.async { callback(uid, ms) } }
    }

    func reportTransition(to slot: Slot, gapless: Bool) {
        let uid = slot.track.uid
        engineLog.notice("transition to uid \(uid, privacy: .public), \(gapless ? "gapless" : "restarted", privacy: .public)")
        let callback = callbackLock.withLock { callbacks.transition }
        if let callback { callbackQueue.async { callback(uid) } }
    }

    func emitPosition() {
        guard let position else { return }
        let callback = callbackLock.withLock { callbacks.position }
        if let callback { callbackQueue.async { callback(position.uid, position.ms) } }
    }
}
