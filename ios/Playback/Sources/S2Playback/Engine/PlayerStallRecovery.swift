// Copied from Shuttle Podcasts (podcasts@9ee6e0954) mobile/ios/Playback/Sources/Playback/PlayerStallRecovery.swift — see ios/Playback/README.md.
import Foundation

/// **The decision core of player-stall recovery.**
///
/// `AVPlayerItemPlaybackStalled` is benign on its own — AVFoundation usually recovers — but on the
/// device (owner's iPhone 16, 2026-09-13) a seek that landed just inside the spine loader's
/// throttled frontier left the item stalled for good: no error, no rate event, the loader healthy,
/// and nothing anywhere that ever asked AVFoundation to try again. ``AVPlayerPlaybackController``
/// needs a bounded nudge for exactly that shape, and this type is its whole decision rule, pure
/// and clock-injected so it can be pinned without an `AVPlayer` (which the simulator on this
/// machine cannot reliably stall on cue in the first place).
///
/// One stall EPISODE runs from a stall observed while playback is wanted until the playhead makes
/// real progress again. Within an episode there is exactly one step: after ``reseekGraceMs`` with
/// no progress, one re-seek to the current time. A raw player seek makes AVFoundation cancel its
/// current loading requests and issue fresh ones, which is the only lever the app has on a request
/// AVFoundation stopped re-issuing by itself. That is the whole rule (client skip semantics, rule
/// 8): detect, one re-seek, log. An item-rebuild step was built on top of this for a failure
/// nobody reproduced and was removed; the loader's `spine_loader_read_unanswered` event is what
/// names the cause of the next field stall instead.
///
/// Progress at any point closes the episode: a player that moved does not need saving, and the next
/// stall starts a fresh episode with a fresh budget. There is no second step and no loop: an item
/// that survives a re-seek without moving a fraction of a second is left to AVFoundation and to the
/// listener's manual controls.
struct PlayerStallRecovery {

    /// What counts as progress: the playhead moving at least this far from where it stalled.
    /// Anything less is jitter around a frozen position.
    static let progressEpsilonMs: Int64 = 500

    /// How long a stall is given to resolve itself before the re-seek.
    static let reseekGraceMs: Int64 = 3_000

    /// One per episode. The bound IS the loop guard.
    static let maxReseeks = 1

    /// The action the controller should perform now, if any.
    enum Action: Equatable {
        /// Seek the raw player to the current time to force fresh loading requests, then play.
        case reseek(positionMs: Int64)
    }

    /// Where the playhead sat when the episode opened, in stream ms. Nil while no episode is open.
    private(set) var stalledAtMs: Int64?
    /// Wall clock when the episode opened, in ms. Nil while no episode is open.
    private(set) var stalledClockMs: Int64?
    private var reseeksIssued = 0

    var episodeIsOpen: Bool { stalledAtMs != nil }

    /// A stall was observed while playback was wanted. Repeated notifications for the same frozen
    /// playhead do not re-anchor the episode — the first clock is the one the grace runs from.
    mutating func stallObserved(positionMs: Int64, nowMs: Int64) {
        guard stalledAtMs == nil else { return }
        stalledAtMs = positionMs
        stalledClockMs = nowMs
        reseeksIssued = 0
    }

    /// The playhead was seen at `positionMs`. Real progress closes the episode and returns the
    /// budget; a stall that never re-observed itself does not get to re-seek twice.
    mutating func noteProgress(positionMs: Int64) {
        guard let stalledAt = stalledAtMs else { return }
        if abs(positionMs - stalledAt) >= Self.progressEpsilonMs {
            clear()
        }
    }

    /// The action due at `nowMs` given the playhead is still at `positionMs`, or nil when the
    /// episode has resolved, has not reached the grace, or has spent its budget.
    mutating func actionDue(positionMs: Int64, nowMs: Int64) -> Action? {
        guard let stalledAt = stalledAtMs, let stalledClock = stalledClockMs else { return nil }
        // The playhead moving on is the recovery this exists to produce; observed here as well as
        // through `noteProgress` so the controller cannot forget to call it and re-seek a player
        // that has been fine for minutes.
        if abs(positionMs - stalledAt) >= Self.progressEpsilonMs {
            clear()
            return nil
        }
        let elapsedMs = nowMs - stalledClock
        if elapsedMs >= Self.reseekGraceMs, reseeksIssued < Self.maxReseeks {
            reseeksIssued += 1
            return .reseek(positionMs: positionMs)
        }
        return nil
    }

    /// No episode, no budget spent. Called on pause, on a new load, on anything that means a
    /// stalled item is no longer the thing the listener is waiting on.
    mutating func clear() {
        stalledAtMs = nil
        stalledClockMs = nil
        reseeksIssued = 0
    }
}
