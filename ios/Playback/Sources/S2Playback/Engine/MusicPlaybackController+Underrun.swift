import Foundation
import os

// MARK: - Underruns (engine queue)

extension MusicPlaybackController {
    /// An underrun on a playing node ends once ``UnderrunResumeRule`` says enough is held, scheduling it. Asked as each
    /// buffer is held, at every fill (the ticker's too, so held audio waiting on a slow next's open still goes on at the
    /// cap) and while a read waits.
    func resumeIfDue() {
        guard underrun != nil else { return }
        let frames = heldForResume.reduce(0) { $0 + Int($1.frameLength) }
        guard UnderrunResumeRule.resumes(
            bufferedSeconds: Double(frames) / outputSampleRate,
            heldSeconds: clock() - heldSince,
            ended: drained
        ) else { return }
        endUnderrun("recovered")
        if current != nil, state == .playing { reportState(.playing) }
    }

    /// The node played its last buffer with the stream not over: from here it renders silence the
    /// stream doesn't contain, until a buffer is scheduled. The one place `starved` is set, so every
    /// underrun heard while playing is said once as it starts and once as it ends (#896); one found
    /// while paused is nobody's silence, and only marks the anchor.
    func beginUnderrun() {
        starved = true
        guard underrun == nil, state == .playing else { return }
        let atMs = ms(frames: currentMediaFrame())
        underrun = (StartupTiming.now(), atMs)
        engineLog.warning("underrun: starved at \(atMs) ms uid \(self.current?.track.uid ?? "-", privacy: .public)")
        reportState(.loading)
    }

    /// A read of the stream has waited a while for its bytes. Called on the reading thread: on the engine queue
    /// that's inside `fill`, which a stalled stream blocks, so the node's own completions can't say it ran dry
    /// until the bytes are back. Once the node has played everything scheduled, the underrun starts here (#897).
    /// A node still held (a restart's first fill, before it plays) isn't rendering anything: a seek waiting on its
    /// stream isn't an underrun.
    func readWaited() {
        guard DispatchQueue.getSpecific(key: Self.engineQueueKey) == true else { return }
        // Held audio waiting on a read that stalls again goes on at the rule's cap, without that read.
        if underrun != nil { return resumeIfDue() }
        guard state == .playing, !drained, !starved else { return }
        let (scheduledEnd, held) = timelineLock.withLock { (timeline.scheduledEnd, timeline.held) }
        guard held == nil, playedStreamIndex() >= scheduledEnd else { return }
        beginUnderrun()
    }

    /// The underrun is over: `how` is "recovered" when a buffer reached the node, else what dropped
    /// it (a pause, a stop, the queue's end). Schedules what was held for the resume. Logs how long the listener
    /// heard silence; reports nothing, which is the caller's to say.
    func endUnderrun(_ how: String) {
        guard let underrun else { return }
        self.underrun = nil
        let held = heldForResume
        heldForResume = []
        held.forEach(enqueue)
        let starvedMs = Int(((StartupTiming.now() - underrun.since) * 1000).rounded())
        engineLog.notice("underrun: \(how, privacy: .public) after \(starvedMs) ms, starved at \(underrun.ms) ms")
    }
}
