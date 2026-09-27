// Copied from Shuttle Podcasts (podcasts@9ee6e0954) mobile/ios/Playback/Tests/PlaybackTests/PlayerStallRecoveryTests.swift — see ios/Playback/README.md.
import Foundation
import Testing

@testable import S2Playback
import S2PlaybackTestSupport

/// ``PlayerStallRecovery`` — the one bounded nudge the controller runs when
/// `AVPlayerItemPlaybackStalled` never resolves on its own (owner's iPhone 16, 2026-09-13: a seek
/// just inside the spine loader's throttled frontier stalled for good — no error, no rate event, a
/// healthy fetch, and nothing that ever asked AVFoundation to try again).
///
/// The rule is pinned pure, clock-injected, because the alternative is stalling a real `AVPlayer`
/// on cue, which no harness here can do deliberately.
struct PlayerStallRecoveryTests {

    private static let stalledAt: Int64 = 1_224_510
    private static let stallClock: Int64 = 1_000_000

    private func stalledMachine() -> PlayerStallRecovery {
        var machine = PlayerStallRecovery()
        machine.stallObserved(positionMs: Self.stalledAt, nowMs: Self.stallClock)
        return machine
    }

    private func due(
        _ machine: inout PlayerStallRecovery,
        at nowMs: Int64,
        positionMs: Int64 = PlayerStallRecoveryTests.stalledAt
    ) -> PlayerStallRecovery.Action? {
        machine.actionDue(positionMs: positionMs, nowMs: nowMs)
    }

    // MARK: - The one step

    @Test("no stall, no action")
    func quietPlayerIsLeftAlone() {
        var machine = PlayerStallRecovery()
        #expect(due(&machine, at: Self.stallClock + 60_000, positionMs: 500) == nil)
        #expect(!machine.episodeIsOpen)
    }

    @Test("a stall inside the grace is given time to resolve itself")
    func stallWithinGraceDoesNothing() {
        var machine = stalledMachine()
        #expect(due(&machine, at: Self.stallClock + 1_000) == nil)
        #expect(due(&machine, at: Self.stallClock + 2_999) == nil)
    }

    @Test("a stall with no progress gets one re-seek after the grace")
    func stalledPlayerIsReseekedOnce() {
        var machine = stalledMachine()
        let action = due(&machine, at: Self.stallClock + 3_000)
        #expect(action == .reseek(positionMs: Self.stalledAt))
        // The budget is spent: no second re-seek, however long the freeze lasts.
        #expect(due(&machine, at: Self.stallClock + 4_500) == nil)
        #expect(due(&machine, at: Self.stallClock + 8_000) == nil)
        #expect(due(&machine, at: Self.stallClock + 60_000) == nil)
        #expect(due(&machine, at: Self.stallClock + 600_000) == nil)
        #expect(machine.episodeIsOpen, "the episode stays open, still observing")
    }

    // MARK: - Progress resets everything

    @Test("progress before the grace closes the episode")
    func progressBeforeGraceClosesTheEpisode() {
        var machine = stalledMachine()
        machine.noteProgress(positionMs: Self.stalledAt + 800)
        #expect(!machine.episodeIsOpen)
        #expect(due(&machine, at: Self.stallClock + 10_000, positionMs: Self.stalledAt + 800) == nil)
    }

    @Test("the re-seek resolving the stall closes the episode")
    func recoveryAfterReseekResetsEverything() {
        var machine = stalledMachine()
        _ = due(&machine, at: Self.stallClock + 3_000)
        machine.noteProgress(positionMs: Self.stalledAt + 1_200)
        #expect(!machine.episodeIsOpen)
        #expect(due(&machine, at: Self.stallClock + 8_000, positionMs: Self.stalledAt + 1_200) == nil)
    }

    @Test("progress seen by actionDue itself also closes the episode")
    func progressObservedDuringEvaluationClosesTheEpisode() {
        var machine = stalledMachine()
        #expect(
            due(&machine, at: Self.stallClock + 4_000, positionMs: Self.stalledAt + 600) == nil,
            "a playhead that moved on needs no saving"
        )
        #expect(!machine.episodeIsOpen)
    }

    @Test("jitter smaller than the epsilon is not progress")
    func jitterIsNotProgress() {
        var machine = stalledMachine()
        machine.noteProgress(positionMs: Self.stalledAt + 200)
        #expect(machine.episodeIsOpen)
        #expect(
            due(&machine, at: Self.stallClock + 3_500, positionMs: Self.stalledAt - 200)
                == .reseek(positionMs: Self.stalledAt - 200)
        )
    }

    @Test("a stall after real progress is a fresh episode with a fresh budget")
    func newStallAfterProgressGetsAFreshBudget() {
        var machine = stalledMachine()
        _ = due(&machine, at: Self.stallClock + 3_000)
        machine.noteProgress(positionMs: Self.stalledAt + 5_000)
        let secondStallAt: Int64 = 1_300_000
        machine.stallObserved(positionMs: secondStallAt, nowMs: Self.stallClock + 60_000)
        #expect(due(&machine, at: Self.stallClock + 62_000, positionMs: secondStallAt) == nil)
        #expect(
            due(&machine, at: Self.stallClock + 63_000, positionMs: secondStallAt)
                == .reseek(positionMs: secondStallAt)
        )
    }

    // MARK: - Anchoring and clearing

    @Test("repeated stall notifications do not restart the clock")
    func repeatedStallsKeepTheFirstClock() {
        var machine = stalledMachine()
        machine.stallObserved(positionMs: Self.stalledAt, nowMs: Self.stallClock + 2_000)
        // Still the ORIGINAL episode: the grace ran from the first notification, so the re-seek is
        // already due, not another three seconds away.
        #expect(
            due(&machine, at: Self.stallClock + 3_500)
                == .reseek(positionMs: Self.stalledAt)
        )
    }

    @Test("clear disarms everything")
    func clearDisarms() {
        var machine = stalledMachine()
        _ = due(&machine, at: Self.stallClock + 3_000)
        machine.clear()
        #expect(!machine.episodeIsOpen)
        #expect(due(&machine, at: Self.stallClock + 60_000) == nil)
    }
}
