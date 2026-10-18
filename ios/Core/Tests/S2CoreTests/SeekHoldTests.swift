import Foundation
import Testing
@testable import S2Core

/// The seek target stays on screen until the player's reported position catches up with it.
struct SeekHoldTests {
    private let start = Date(timeIntervalSinceReferenceDate: 0)

    @Test func passesReportedPositionsThroughWithoutASeek() {
        var hold = SeekHold()
        #expect(hold.displayed(reportedMs: 1_000, now: start) == 1_000)
    }

    @Test func holdsTheTargetUntilTheReportCatchesUp() {
        var hold = SeekHold()
        hold.begin(60_000, now: start)
        #expect(hold.displayed(reportedMs: 10_000, now: start.addingTimeInterval(0.3)) == 60_000)
        #expect(hold.displayed(reportedMs: 59_000, now: start.addingTimeInterval(0.6)) == 59_000)
        #expect(hold.targetMs == nil)
        #expect(hold.displayed(reportedMs: 10_000, now: start.addingTimeInterval(0.9)) == 10_000)
    }

    @Test func releasesTheHoldAfterTheTimeout() {
        var hold = SeekHold()
        hold.begin(60_000, now: start)
        #expect(hold.displayed(reportedMs: 10_000, now: start.addingTimeInterval(SeekHold.timeout)) == 10_000)
        #expect(hold.targetMs == nil)
    }
}
