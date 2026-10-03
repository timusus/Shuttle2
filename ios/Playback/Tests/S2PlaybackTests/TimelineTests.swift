import XCTest
@testable import S2Playback

/// Which stream index the controller takes as heard, from its timeline and the node's clock.
final class TimelineTests: XCTestCase {

    private typealias Timeline = MusicPlaybackController.Timeline

    private func playing(heard: Int64, scheduledEnd: Int64 = 96_000) -> Timeline {
        var timeline = Timeline()
        timeline.held = nil
        timeline.heard = heard
        timeline.scheduledEnd = scheduledEnd
        return timeline
    }

    func testAHeldPositionWins() {
        var timeline = playing(heard: 1_000)
        timeline.held = 5_000
        XCTAssertEqual(timeline.playedStreamIndex(nodeTime: 20_000), 5_000)
        XCTAssertEqual(timeline.playedStreamIndex(nodeTime: nil), 5_000)
    }

    func testTheNodesClockIsTheAnswerWhileItRenders() {
        XCTAssertEqual(playing(heard: 1_000).playedStreamIndex(nodeTime: 20_000), 20_000)
    }

    /// Just after a load, a seek or a resume, before the node first renders: where it was released.
    func testBeforeTheNodeRendersItIsWhereItWasReleased() {
        var timeline = Timeline()
        timeline.held = 7_000
        timeline.heard = timeline.held ?? 0
        timeline.held = nil
        XCTAssertEqual(timeline.playedStreamIndex(nodeTime: nil), 7_000)
    }

    /// #714: a route change stops the engine and the node's clock with it; the last reading stands,
    /// not the start of the stream.
    func testAStoppedNodeIsWhereItWasLastHeard() {
        XCTAssertEqual(playing(heard: 30_000).playedStreamIndex(nodeTime: nil), 30_000)
    }

    func testTheClockNeverPassesWhatIsScheduled() {
        XCTAssertEqual(playing(heard: 0, scheduledEnd: 10_000).playedStreamIndex(nodeTime: 20_000), 10_000)
    }

    /// A starved node's clock runs on through silence the stream doesn't contain.
    func testAStarvedNodeHoldsAtTheNextAnchor() {
        var timeline = playing(heard: 0)
        timeline.anchors.append(MusicPlaybackController.Anchor(stream: 10_000, player: 15_000))
        XCTAssertEqual(timeline.playedStreamIndex(nodeTime: 12_000), 10_000)
        XCTAssertEqual(timeline.playedStreamIndex(nodeTime: 16_000), 11_000)
    }
}
