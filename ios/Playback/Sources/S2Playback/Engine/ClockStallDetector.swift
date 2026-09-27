// Copied from Shuttle Podcasts (podcasts@9ee6e0954) mobile/ios/Playback/Sources/Playback/ClockStallDetector.swift — see ios/Playback/README.md.
import Foundation

/// **The decision core of render-clock stall recovery.**
///
/// The position the app shows is read off the player node's render clock twice a second, and a
/// read that fails is silently skipped — the tick has always been `guard let position else
/// return`. On the device (owner's iPhone over CarPlay, phone locked, 2026-09-18) that read failed
/// for most of an episode while the audio kept playing: every progress surface, and the ten-second
/// position saver behind them, sat mid-episode until the app was foregrounded and something
/// re-anchored the clock. Nothing logged it, because nothing counted it.
///
/// This type is the counting, pure and clock-injected so it can be pinned without an
/// `AVAudioEngine` (which the simulator on this machine cannot start). The controller feeds it one
/// ``Sample`` per tick while it believes it is playing; it says when the reads have been failing
/// long enough to be a stall rather than a startup gap, and when a recovery is due. It does not
/// know what a recovery IS — that is ``AVAudioEnginePlaybackController``'s choice per ``Cause``.
///
/// The rule: ``stalledTicksBeforeRecovery`` consecutive failed reads open a stall episode, which is
/// logged ONCE and recovered at most once per ``recoveryIntervalMs``. Any successful read closes
/// the episode. There is no budget: a clock that keeps failing keeps being recovered, ten seconds
/// apart, each attempt logged, because unlike a network stall there is nothing else that will fix
/// it.
struct ClockStallDetector {

    /// Consecutive failed ticks before a stall is declared. Ticks are 500 ms apart, so ~2 s: long
    /// enough that a node which has just been pressed play and not yet rendered is not a stall.
    static let stalledTicksBeforeRecovery = 4

    /// Minimum gap between recovery attempts.
    static let recoveryIntervalMs: Int64 = 10_000

    /// Why a tick's read failed. Named so the log line says which of the chain's links broke.
    enum Cause: String, Equatable {
        /// `lastRenderTime` was nil: the engine is not rendering, whatever `isRunning` says.
        case nilRenderTime = "nil-render-time"
        /// `playerTime(forNodeTime:)` was nil: the node has no clock for the engine's time.
        case nilPlayerTime = "nil-player-time"
        /// The clock read but no checkpoint maps it: the node's sample time is behind the first
        /// checkpoint's output frame, which is what a node clock that restarted from zero looks
        /// like, or there are no checkpoints at all.
        case noAnchor = "no-anchor"
        /// The clock read the same sample time as the last tick: the engine is not rendering.
        case notAdvancing = "not-advancing"
    }

    /// What one tick read.
    enum Sample: Equatable {
        /// A media position was produced from a clock at `sampleTime`.
        case position(sampleTime: Int64)
        /// The read failed at the named link, with the clock's sample time if it got that far.
        case failed(Cause, sampleTime: Int64?)
    }

    /// What the controller should do after a tick.
    struct Decision: Equatable {
        /// The threshold was crossed on this tick: log the stall, once.
        let declare: Bool
        /// A recovery is due now. Log the attempt; the next is at least the interval away.
        let recover: Bool
        let cause: Cause

        static let healthy = Decision(declare: false, recover: false, cause: .notAdvancing)
    }

    /// Consecutive failed ticks. Visible so the clock heartbeat can print it.
    private(set) var stalledTicks = 0
    /// The last failed tick's cause, while a run of failures is open.
    private(set) var cause: Cause?
    private var lastSampleTime: Int64?
    private var lastRecoveryClockMs: Int64?

    var isStalled: Bool { stalledTicks >= Self.stalledTicksBeforeRecovery }

    /// One tick while the controller believes it is playing.
    mutating func observe(_ sample: Sample, nowMs: Int64) -> Decision {
        let failure: Cause
        switch sample {
        case .position(let sampleTime):
            if let last = lastSampleTime, sampleTime == last {
                failure = .notAdvancing
            } else {
                lastSampleTime = sampleTime
                reset()
                return .healthy
            }
        case .failed(let cause, let sampleTime):
            // A clock that reads but stands still is the stronger fact, whichever link the mapping
            // then failed at: the recovery for a frozen clock restarts the engine, the one for a
            // broken mapping only rebases it. Equality, not `<=`: a clock that went BACKWARDS is
            // a node clock that restarted, which is a mapping fault and the rebase's whole case.
            if let sampleTime, let last = lastSampleTime, sampleTime == last {
                failure = .notAdvancing
            } else {
                failure = cause
                if let sampleTime { lastSampleTime = sampleTime }
            }
        }

        stalledTicks += 1
        cause = failure
        guard isStalled else { return .healthy }
        let declare = stalledTicks == Self.stalledTicksBeforeRecovery
        let recover = lastRecoveryClockMs.map { nowMs - $0 >= Self.recoveryIntervalMs } ?? true
        if recover { lastRecoveryClockMs = nowMs }
        return Decision(declare: declare, recover: recover, cause: failure)
    }

    /// A tick that did not count — the node is paused, the player is not playing — closes any open
    /// run. The recovery clock is kept: a recovery that pauses and restarts the node must not
    /// earn an immediate second one.
    mutating func reset() {
        stalledTicks = 0
        cause = nil
    }

    /// The clock has been rebuilt (a seek, a re-anchor): sample times start over, so the last one
    /// read is no longer a floor for "advancing".
    mutating func clockRebuilt() {
        reset()
        lastSampleTime = nil
    }
}
