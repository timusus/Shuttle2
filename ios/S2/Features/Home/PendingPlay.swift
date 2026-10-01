import Foundation
import Observation
import Shared

/// The Home item whose play is under way: from the tap until the player plays the item's context, the action reports a
/// result (a failure), or `timeout` passes. Its play button shows a spinner meanwhile, rather than sitting still while
/// the item's songs are read and its stream opens.
@MainActor
@Observable
final class PendingPlay {
    /// The pending item's `HomeItem.key`; nil when nothing is pending.
    private(set) var key: String?
    @ObservationIgnored private var context: PlayContext?
    /// The queue content version a rebuilding play started at; nil for a resume.
    @ObservationIgnored private var rebuiltAfter: Int64?
    @ObservationIgnored private var timeoutTask: Task<Void, Never>?
    @ObservationIgnored let timeout: Duration

    init(timeout: Duration = .seconds(15)) {
        self.timeout = timeout
    }

    /// A play of `item` was dispatched. `resumes` is a plain resume (carry on from where its queue was left); anything
    /// else (Play, Play from Start, Shuffle) rebuilds the queue. A resume of the context that's already playing carries
    /// on as it is, so there's nothing to wait for; the rest wait for the player.
    ///
    /// `playing` is whether the player is playing or buffering, `current` the queue's play context and `version` its
    /// content version, as they stand now.
    func start(_ item: HomeItem, resumes: Bool, playing: Bool, current: PlayContext, version: Int64) {
        timeoutTask?.cancel()
        guard !(resumes && playing && Self.same(current, item.playContext)) else {
            settle()
            return
        }
        key = item.key
        context = item.playContext
        // A rebuild is only done once the queue's content has changed: before that, the old queue may be the same
        // context and still playing.
        rebuiltAfter = resumes ? nil : version
        timeoutTask = Task { [weak self, timeout] in
            try? await Task.sleep(for: timeout)
            guard !Task.isCancelled else { return }
            self?.settle()
        }
    }

    /// The player's state or its queue changed (call it for either, with both as they now stand): playing or buffering
    /// the pending item's context settles it, whichever of the two changed last.
    func playerChanged(playing: Bool, current: PlayContext, version: Int64) {
        guard playing, let context, Self.same(current, context) else { return }
        if let rebuiltAfter, version == rebuiltAfter { return }
        settle()
    }

    /// Whether `result` of an action is a play that didn't happen. Anything else (a notice that something was queued,
    /// a navigation) says nothing about the pending play.
    static func isFailure(_ result: any MediaActionResult) -> Bool {
        guard let message = (result as? MediaActionResultMessage)?.message else { return false }
        return message is MediaActionMessagePlaybackFailed || message is MediaActionMessageNoSongs
    }

    /// Ends the wait: playback started, or the action failed.
    func settle() {
        timeoutTask?.cancel()
        timeoutTask = nil
        key = nil
        context = nil
        rebuiltAfter = nil
    }

    private static func same(_ lhs: PlayContext, _ rhs: PlayContext) -> Bool {
        (lhs as AnyObject).isEqual(rhs as AnyObject)
    }
}
