import XCTest
@testable import S2Playback

final class UnderrunResumeRuleTests: XCTestCase {

    func testAFirstSmallBufferDoesNotResume() {
        XCTAssertFalse(UnderrunResumeRule.resumes(bufferedSeconds: 0.085, heldSeconds: 0.5, ended: false))
        XCTAssertFalse(UnderrunResumeRule.resumes(bufferedSeconds: 1.9, heldSeconds: 4.9, ended: false))
    }

    func testTwoSecondsBufferedResumes() {
        XCTAssertTrue(UnderrunResumeRule.resumes(bufferedSeconds: 2, heldSeconds: 0.5, ended: false))
    }

    func testTheCapResumesWithWhateverIsBuffered() {
        XCTAssertTrue(UnderrunResumeRule.resumes(bufferedSeconds: 0.085, heldSeconds: 5, ended: false))
    }

    func testTheCapWithNothingBufferedHasNothingToResume() {
        XCTAssertFalse(UnderrunResumeRule.resumes(bufferedSeconds: 0, heldSeconds: 30, ended: false))
    }

    func testTheQueuesEndResumes() {
        XCTAssertTrue(UnderrunResumeRule.resumes(bufferedSeconds: 0.085, heldSeconds: 0.5, ended: true))
    }
}
