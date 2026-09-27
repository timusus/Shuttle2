// Copied from Shuttle Podcasts (podcasts@9ee6e0954) mobile/ios/Playback/Tests/PlaybackTests/ClockStallDetectorTests.swift — see ios/Playback/README.md.
import XCTest
@testable import S2Playback

/// **The clock-stall rule, pinned without an engine.**
///
/// The device stall (CarPlay, phone locked, 2026-09-18) was a tick whose clock read failed for most
/// of an episode with nothing counting it. What is pinned here is the counting: four failed reads
/// running declare a stall ONCE and recover ONCE, a second recovery waits the interval, any
/// successful read closes the run, and a healthy clock never trips it. The controller's own
/// recovery needs a rendering engine, which the simulator on this machine cannot start; see
/// `AVAudioEngineClockStallTests` for the half of the wiring that can be driven here.
final class ClockStallDetectorTests: XCTestCase {

    private let threshold = ClockStallDetector.stalledTicksBeforeRecovery
    private let interval = ClockStallDetector.recoveryIntervalMs

    private func healthyTicks(_ detector: inout ClockStallDetector, count: Int, from sampleTime: Int64 = 0) -> Int64 {
        var time = sampleTime
        for _ in 0..<count {
            time += 22_050
            XCTAssertEqual(detector.observe(.position(sampleTime: time), nowMs: 0), .healthy)
        }
        return time
    }

    func testHealthyClockNeverDeclaresOrRecovers() {
        var detector = ClockStallDetector()
        _ = healthyTicks(&detector, count: 200)
        XCTAssertEqual(detector.stalledTicks, 0)
        XCTAssertFalse(detector.isStalled)
    }

    func testNilClockDeclaresOnceAndRecoversOnceAtTheThreshold() {
        var detector = ClockStallDetector()
        _ = healthyTicks(&detector, count: 3)

        var declared = 0
        var recovered = 0
        for tick in 1...(threshold + 6) {
            let decision = detector.observe(.failed(.nilRenderTime, sampleTime: nil), nowMs: Int64(tick) * 500)
            if decision.declare { declared += 1 }
            if decision.recover { recovered += 1 }
            XCTAssertEqual(decision.declare, tick == threshold, "declared on tick \(tick)")
            XCTAssertEqual(decision.recover, tick == threshold, "recovered on tick \(tick)")
            XCTAssertEqual(decision.cause, tick >= threshold ? .nilRenderTime : .notAdvancing)
        }
        XCTAssertEqual(declared, 1)
        XCTAssertEqual(recovered, 1)
        XCTAssertEqual(detector.stalledTicks, threshold + 6)
        XCTAssertEqual(detector.cause, .nilRenderTime)
    }

    func testASuccessfulReadClosesTheRunAndTheNextStallDeclaresAgain() {
        var detector = ClockStallDetector()
        for tick in 1...threshold {
            _ = detector.observe(.failed(.nilPlayerTime, sampleTime: nil), nowMs: Int64(tick) * 500)
        }
        XCTAssertTrue(detector.isStalled)

        XCTAssertEqual(detector.observe(.position(sampleTime: 1_000), nowMs: 3_000), .healthy)
        XCTAssertEqual(detector.stalledTicks, 0)
        XCTAssertNil(detector.cause)

        // Well past the interval: the next run declares and recovers again.
        var declared = 0
        var recovered = 0
        for tick in 1...threshold {
            let decision = detector.observe(.failed(.nilPlayerTime, sampleTime: nil), nowMs: interval + Int64(tick) * 500)
            if decision.declare { declared += 1 }
            if decision.recover { recovered += 1 }
        }
        XCTAssertEqual(declared, 1)
        XCTAssertEqual(recovered, 1)
    }

    func testNonAdvancingClockIsAStallEvenThoughEveryReadSucceeded() {
        var detector = ClockStallDetector()
        let frozen = healthyTicks(&detector, count: 2)
        var decisions: [ClockStallDetector.Decision] = []
        for tick in 1...threshold {
            decisions.append(detector.observe(.position(sampleTime: frozen), nowMs: Int64(tick) * 500))
        }
        XCTAssertEqual(decisions.dropLast().map(\.recover), Array(repeating: false, count: threshold - 1))
        XCTAssertEqual(decisions.last, ClockStallDetector.Decision(declare: true, recover: true, cause: .notAdvancing))
    }

    func testAFrozenClockBehindABrokenMappingIsReportedAsNotAdvancing() {
        var detector = ClockStallDetector()
        let frozen = healthyTicks(&detector, count: 2)
        var last = ClockStallDetector.Decision.healthy
        for tick in 1...threshold {
            last = detector.observe(.failed(.noAnchor, sampleTime: frozen), nowMs: Int64(tick) * 500)
        }
        XCTAssertEqual(last.cause, .notAdvancing, "a mapping fault on a clock that is not moving hid the frozen clock")
    }

    func testAClockThatRestartedUnderTheCheckpointsIsANoAnchorStall() {
        var detector = ClockStallDetector()
        _ = healthyTicks(&detector, count: 20)
        var last = ClockStallDetector.Decision.healthy
        // The node's sample time restarted from zero and keeps advancing; nothing maps it.
        for tick in 1...threshold {
            last = detector.observe(.failed(.noAnchor, sampleTime: Int64(tick) * 22_050), nowMs: Int64(tick) * 500)
        }
        XCTAssertEqual(last, ClockStallDetector.Decision(declare: true, recover: true, cause: .noAnchor))
    }

    func testRecoveryIsRateLimitedToTheIntervalWhileTheStallPersists() {
        var detector = ClockStallDetector()
        var recoveries: [Int64] = []
        var now: Int64 = 0
        // Forty seconds of a clock that never comes back.
        for _ in 0..<80 {
            now += 500
            if detector.observe(.failed(.nilRenderTime, sampleTime: nil), nowMs: now).recover {
                recoveries.append(now)
            }
        }
        XCTAssertEqual(recoveries, [2_000, 12_000, 22_000, 32_000])
    }

    func testResetKeepsTheRecoveryClock() {
        var detector = ClockStallDetector()
        for tick in 1...threshold {
            _ = detector.observe(.failed(.nilRenderTime, sampleTime: nil), nowMs: Int64(tick) * 500)
        }
        // The recovery paused the node; the ticks in between do not count.
        detector.reset()
        XCTAssertEqual(detector.stalledTicks, 0)
        var recovered = 0
        for tick in 1...threshold {
            if detector.observe(.failed(.nilRenderTime, sampleTime: nil), nowMs: 2_000 + Int64(tick) * 500).recover {
                recovered += 1
            }
        }
        XCTAssertEqual(recovered, 0, "a recovery that had just run earned a second one inside the interval")
    }

    func testClockRebuiltForgetsTheSampleTimeFloor() {
        var detector = ClockStallDetector()
        let time = healthyTicks(&detector, count: 5)
        detector.clockRebuilt()
        // The rebuilt clock happens to read the same number: not a frozen clock.
        XCTAssertEqual(detector.observe(.position(sampleTime: time), nowMs: 0), .healthy)
    }
}
