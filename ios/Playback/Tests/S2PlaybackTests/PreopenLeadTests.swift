import XCTest
@testable import S2Playback

/// How far ahead of the join the next track is opened (#620): the configured window, widened to
/// twice the slowest recent open, capped.
final class PreopenLeadTests: XCTestCase {

    func testWithNoOpensSeenItIsTheMinimum() {
        XCTAssertEqual(PreopenLead(minimumSeconds: 10).seconds, 10)
    }

    func testOpensFasterThanHalfTheMinimumLeaveIt() {
        var lead = PreopenLead(minimumSeconds: 10)
        lead.record(openSeconds: 0.2)
        lead.record(openSeconds: 4.9)
        XCTAssertEqual(lead.seconds, 10)
    }

    func testASlowOpenWidensItToTwiceThatOpen() {
        var lead = PreopenLead(minimumSeconds: 10)
        lead.record(openSeconds: 8)
        lead.record(openSeconds: 0.5)
        XCTAssertEqual(lead.seconds, 16)
    }

    func testASlowOpenAgesOutOfTheWindow() {
        var lead = PreopenLead(minimumSeconds: 10, window: 3)
        lead.record(openSeconds: 8)
        for _ in 0..<3 { lead.record(openSeconds: 0.5) }
        XCTAssertEqual(lead.seconds, 10)
    }

    func testItIsCapped() {
        var lead = PreopenLead(minimumSeconds: 10, maximumSeconds: 60)
        lead.record(openSeconds: 45)
        XCTAssertEqual(lead.seconds, 60)
    }

    func testTheCapNeverUndercutsTheMinimum() {
        XCTAssertEqual(PreopenLead(minimumSeconds: 90, maximumSeconds: 60).seconds, 90)
    }
}
