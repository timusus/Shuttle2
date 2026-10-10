import AVFoundation
import os

extension MusicPlaybackController {
    struct Segment {
        let streamStart: Int64
        let uid: String
        let mediaStart: Int64
        let durationFrames: Int64?
        let slot: Int
    }

    struct Anchor {
        let stream: Int64
        let player: Int64
    }

    struct Timeline {
        var segments: [Segment] = []
        var anchors: [Anchor] = [Anchor(stream: 0, player: 0)]
        /// Frames scheduled so far; the playhead never passes it.
        var scheduledEnd: Int64 = 0
        /// Non-nil while the node is not rendering (stopped, paused, not yet started).
        var held: Int64? = 0
        /// The last stream index known to be heard: where the node was released from `held`, then
        /// each tick's reading. The answer while the node's clock can't be read: before it first
        /// renders after a release, and once a route change has stopped the engine under it (#714).
        var heard: Int64 = 0

        /// The stream index being heard, given the node's clock (`nodeTime`, nil when it has no
        /// valid render time).
        func playedStreamIndex(nodeTime: Int64?) -> Int64 {
            if let held { return held }
            guard let now = nodeTime else { return heard }
            var stream: Int64 = 0
            for (i, anchor) in anchors.enumerated() where anchor.player <= now {
                stream = anchor.stream + (now - anchor.player)
                // A starved node's clock ran on; the stream did not.
                if i + 1 < anchors.count { stream = min(stream, anchors[i + 1].stream) }
            }
            return max(0, min(stream, scheduledEnd))
        }
    }

    /// Follow the playhead: promote the next track once it is being heard, notice the end.
    func updateTimeline(concludingEnd: Bool = true) {
        guard current != nil else { return }
        noteFirstRender()
        let stream = playedStreamIndex()
        // Recorded on the engine queue only, where a restart can't replace the timeline meanwhile.
        let segment = timelineLock.withLock {
            timeline.heard = stream
            return Self.segment(at: stream, in: timeline)
        }
        if let segment, let next, segment.slot == next.id {
            let old = current
            current = next
            self.next = nil
            if reading === old { setReading(next) }
            old.map(release)
            timelineLock.withLock {
                timeline.segments.removeAll { $0.streamStart < segment.streamStart }
            }
            reportTransition(to: next, gapless: true)
            // It dropped while read ahead: the owner re-opens it where it's heard, about its start.
            if next.awaitingReopen { reportSeekUnsupported(next, ms: ms(frames: currentMediaFrame())) }
        }
        if concludingEnd, drained, pausingAtEnd, !pausedAtEnd, state == .playing, stream >= outputIndex, let current {
            playWhenReady = false
            pendingActivation = nil
            player.pause()
            timelineLock.withLock { timeline.held = outputIndex }
            stopTicker()
            pausedAtEnd = true
            let uid = current.track.uid
            engineLog.notice("paused at the end of uid \(uid, privacy: .public)")
            let callback = callbackLock.withLock { callbacks.pausedAtEnd }
            if let callback { callbackQueue.async { callback(uid) } }
            setState(.paused)
            emitPosition()
        } else if concludingEnd, drained, !pausingAtEnd, state == .playing, stream >= outputIndex {
            player.stop()
            timelineLock.withLock { timeline.held = outputIndex }
            stopTicker()
            setState(.ended)
        }
        if state == .playing { emitPosition() }
    }

    func currentMediaFrame() -> Int64 {
        let stream = playedStreamIndex()
        return timelineLock.withLock {
            guard let segment = Self.segment(at: stream, in: timeline) else { return 0 }
            return segment.mediaStart + stream - segment.streamStart
        }
    }

    // MARK: - Timeline (any thread)

    func nodeSampleTime() -> Int64? {
        guard let nodeTime = player.lastRenderTime, nodeTime.isSampleTimeValid,
              let playerTime = player.playerTime(forNodeTime: nodeTime)
        else { return nil }
        return playerTime.sampleTime
    }

    /// The stream index being heard.
    func playedStreamIndex() -> Int64 {
        let snapshot = timelineLock.withLock { timeline }
        return snapshot.playedStreamIndex(nodeTime: nodeSampleTime())
    }

    static func segment(at stream: Int64, in timeline: Timeline) -> Segment? {
        timeline.segments.last { $0.streamStart <= stream } ?? timeline.segments.first
    }
}
