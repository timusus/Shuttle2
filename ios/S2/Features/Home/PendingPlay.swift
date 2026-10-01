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
    @ObservationIgnored private var timeoutTask: Task<Void, Never>?
    @ObservationIgnored let timeout: Duration

    init(timeout: Duration = .seconds(15)) {
        self.timeout = timeout
    }

    /// A play of `item` was dispatched while the player was (or wasn't) `playing` the `current` context. One that's
    /// already playing the item's context carries on as it is, so there's nothing to wait for.
    func start(_ item: HomeItem, playing: Bool, current: PlayContext) {
        timeoutTask?.cancel()
        guard !(playing && Self.same(current, item.playContext)) else {
            settle()
            return
        }
        key = item.key
        context = item.playContext
        timeoutTask = Task { [weak self, timeout] in
            try? await Task.sleep(for: timeout)
            guard !Task.isCancelled else { return }
            self?.settle()
        }
    }

    /// The player changed: playing the pending item's context settles it.
    func playerChanged(playing: Bool, current: PlayContext) {
        guard playing, let context, Self.same(current, context) else { return }
        settle()
    }

    /// Ends the wait: playback started, or the action failed.
    func settle() {
        timeoutTask?.cancel()
        timeoutTask = nil
        key = nil
        context = nil
    }

    private static func same(_ lhs: PlayContext, _ rhs: PlayContext) -> Bool {
        (lhs as AnyObject).isEqual(rhs as AnyObject)
    }
}
