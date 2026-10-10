import Foundation

/// When a node that ran dry while playing gets its audio again: going on with the first buffer turns a slow link into
/// a cycle of a moment's audio and a pause, so it waits for a few seconds of it, capped so a link just under real time
/// isn't silent for long.
enum UnderrunResumeRule {
    static let resumeSeconds: Double = 2
    static let capSeconds: Double = 5

    /// `bufferedSeconds` decoded and held back, the first of it `heldSeconds` ago; `ended` when the queue's end is
    /// read, so no more is coming.
    static func resumes(bufferedSeconds: Double, heldSeconds: Double, ended: Bool) -> Bool {
        ended || bufferedSeconds >= resumeSeconds || (bufferedSeconds > 0 && heldSeconds >= capSeconds)
    }
}
