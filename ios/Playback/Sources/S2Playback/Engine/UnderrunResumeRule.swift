import Foundation

/// When a node that ran dry while playing gets its audio again (#950). Going on with the first buffer that arrives
/// plays a slow link as a cycle of a moment's audio and a pause; waiting for a few seconds of it plays the same link
/// as fewer, longer pauses. Ported from Shuttle Podcasts' `BufferingResumeRule` (podcasts#389), which waits for
/// about 3 s decoded and 5 s of bytes ahead. This engine sees decoded frames only, the byte source's read-ahead
/// being its own, so it waits for decoded audio, with a cap so a link just under real time isn't silent for long.
/// A start or a seek never waits on it: their node isn't playing yet, and they go on as soon as they can.
enum UnderrunResumeRule {
    static let resumeSeconds: Double = 2
    static let capSeconds: Double = 5

    /// `bufferedSeconds` decoded and held back since the underrun began `waitedSeconds` ago; `ended` when the queue's
    /// end is read, so no more is coming.
    static func resumes(bufferedSeconds: Double, waitedSeconds: Double, ended: Bool) -> Bool {
        ended || bufferedSeconds >= resumeSeconds || (bufferedSeconds > 0 && waitedSeconds >= capSeconds)
    }
}
