import Foundation
import Observation
import Shared

/// The Home item whose play is under way. Its play button shows a spinner meanwhile, rather than sitting still while
/// the item's songs are read and its stream opens.
///
/// The wait follows the tapped action itself, not the player's queue: it starts at the tap (`start`, which hands back a
/// ticket for that action) and ends when the action's own result comes back (`finished`) as a failure, or, once the
/// action has succeeded, at the first playing or buffering state from then on (at once if the player is already
/// playing then: the action has called play by the time it returns). So a play of what's already queued, a resume that
/// starts over, and another action's result all settle the right wait. `timeout` is the fallback if nothing does.
@MainActor
@Observable
final class PendingPlay {
    /// The pending item's `HomeItem.key`; nil when nothing is pending.
    private(set) var key: String?
    /// The pending action's ticket: a result for any other ticket is a superseded action's.
    @ObservationIgnored private var ticket = 0
    /// The pending action has succeeded, so the player playing now means it's playing the item.
    @ObservationIgnored private var succeeded = false
    @ObservationIgnored private var timeoutTask: Task<Void, Never>?
    @ObservationIgnored let timeout: Duration

    init(timeout: Duration = .seconds(15)) {
        self.timeout = timeout
    }

    /// A play of `item` is about to be dispatched; pass the returned ticket to `finished` with its result.
    @discardableResult
    func start(_ item: HomeItem) -> Int {
        settle()
        ticket += 1
        key = item.key
        timeoutTask = Task { [weak self, timeout] in
            try? await Task.sleep(for: timeout)
            guard !Task.isCancelled else { return }
            self?.settle()
        }
        return ticket
    }

    /// The action `start` handed out `ticket` for came back with `result`; `playing` is whether the player is playing
    /// or buffering now.
    func finished(_ ticket: Int, result: any MediaActionResult, playing: Bool) {
        guard ticket == self.ticket, key != nil else { return }
        if Self.isFailure(result) || playing {
            settle()
        } else {
            succeeded = true
        }
    }

    /// The player's state changed: playing or buffering after the pending action succeeded settles it.
    func playbackChanged(playing: Bool) {
        if playing && succeeded { settle() }
    }

    /// Whether `result` of an action is a play that didn't happen. Anything else (a notice that something was queued,
    /// a navigation) says nothing about the pending play.
    static func isFailure(_ result: any MediaActionResult) -> Bool {
        guard let message = (result as? MediaActionResultMessage)?.message else { return false }
        return message is MediaActionMessagePlaybackFailed || message is MediaActionMessageNoSongs
    }

    /// Ends the wait.
    func settle() {
        timeoutTask?.cancel()
        timeoutTask = nil
        key = nil
        succeeded = false
    }
}
