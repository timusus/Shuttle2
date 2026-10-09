import XCTest
@testable import S2Playback

final class UnderrunResumeRuleTests: XCTestCase {

    func testAFirstSmallBufferDoesNotResume() {
        XCTAssertFalse(UnderrunResumeRule.resumes(bufferedSeconds: 0.085, waitedSeconds: 0.5, ended: false))
        XCTAssertFalse(UnderrunResumeRule.resumes(bufferedSeconds: 1.9, waitedSeconds: 4.9, ended: false))
    }

    func testTwoSecondsBufferedResumes() {
        XCTAssertTrue(UnderrunResumeRule.resumes(bufferedSeconds: 2, waitedSeconds: 0.5, ended: false))
    }

    func testTheCapResumesWithWhateverIsBuffered() {
        XCTAssertTrue(UnderrunResumeRule.resumes(bufferedSeconds: 0.085, waitedSeconds: 5, ended: false))
    }

    func testTheCapWithNothingBufferedHasNothingToResume() {
        XCTAssertFalse(UnderrunResumeRule.resumes(bufferedSeconds: 0, waitedSeconds: 30, ended: false))
    }

    func testTheQueuesEndResumes() {
        XCTAssertTrue(UnderrunResumeRule.resumes(bufferedSeconds: 0.085, waitedSeconds: 0.5, ended: true))
    }
}
