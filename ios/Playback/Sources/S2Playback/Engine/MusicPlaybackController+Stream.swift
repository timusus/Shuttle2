import AVFoundation
import os

// MARK: - The stream: restarting, reading and scheduling (engine queue)

extension MusicPlaybackController {
    /// A seek that didn't happen may have interrupted the next track, already being read behind an
    /// unseekable current one. Sought to exactly where it was read to, it carries on seamlessly. One
    /// that refuses the seek (``TrackSourceError/unseekable``) can't carry on, and isn't the current
    /// track the owner could re-open at a position: it failed.
    func resumeReadingNext() {
        guard let slot = reading, slot !== current, slot.opened, !slot.failed, !slot.awaitingReopen else { return }
        let streamStart = timelineLock.withLock { timeline.segments.last { $0.slot == slot.id }?.streamStart }
        guard let streamStart else { return }
        do {
            try seek(slot, toFrame: inputIndex - streamStart)
        } catch {
            reportFailure(slot, error)
        }
        // `inputIndex` already counts the interrupted read's frames: they're the stream's next.
        if let heldChunk {
            self.heldChunk = nil
            schedule(heldChunk)
        }
        fill()
    }

    func interruptActiveRead() {
        activeSourceLock.withLock { activeSource }?.interrupt()
    }

    /// A source still opening isn't exposed as active: its open must not be interrupted (a seek of
    /// the current track would fail it), and a load or stop releases it anyway.
    func setReading(_ slot: Slot?) {
        reading = slot
        let source = slot?.preparing == nil ? slot?.source : nil
        activeSourceLock.withLock { activeSource = source }
    }

    /// Drop everything scheduled and start the stream again at `frame` of the current track.
    /// `retryingStart`: an engine that won't start is tried again (``retryStart(attempt:)``) rather than
    /// left paused at once.
    func restart(atFrame frame: Int64, retryingStart: Bool = false) {
        guard let current else { return }
        engineLog.notice("restart at \(frame) frames, playWhenReady \(self.playWhenReady)")
        generation += 1
        heldChunk = nil
        heldForResume = []
        player.stop()
        // What the time-pitch unit already pulled belongs to the old stream.
        if timePitchInGraph { timePitch.reset() }
        buffersInFlight = 0
        starved = false
        // An underrun carries on until the restarted stream's first buffer; one that's no longer played ends.
        if !playWhenReady { endUnderrun("restarted") }
        drained = false
        pausingAtEnd = false
        pausedAtEnd = false
        inputIndex = 0
        outputIndex = 0
        if let pendingLimiter {
            processor.setLimiter(pendingLimiter)
            self.pendingLimiter = nil
        }
        processor.reset()
        // A next that had started to be read is re-opened from its start when it is reached again.
        // One opened (or opening) and not read yet is still at its start, and is kept.
        if let old = next, old.opened, !old.atStart {
            release(old)
            next = makeSlot(old.track)
        }
        timelineLock.withLock { timeline = Timeline() }
        setReading(current)
        var startFrame = frame
        // S2: a source still at its first frame is not sought to frame 0. The decoder's start trims
        // the encoder delay (an MP4's edit list, Opus pre-skip); FFmpeg's seek to the start of an
        // AAC-in-MP4 track does not, and ~2,100 frames of priming would open every load.
        if current.opened, !current.failed, current.source.isSeekable, !(frame == 0 && current.atStart) {
            do {
                try seek(current, toFrame: frame)
            } catch TrackSourceError.unseekable {
                // Refused (an estimated length the stream can't serve): unseekable from now on, below.
                current.awaitingReopen = true
            } catch {
                reportFailure(current, error)
                startFrame = 0
            }
        }
        if current.opened, !current.failed, !current.source.isSeekable, !(frame == 0 && current.atStart) {
            // A progressive transcode: the owner is told so it can re-open the stream at the frame.
            // After a load it plays from its start meanwhile. Otherwise (a speed or output change, a
            // rebuild) it was read a schedule-ahead past the frame, and played on it would jump that
            // far (#614): it reads nothing more (``readChunk()``) until the owner re-opens it.
            if current.atStart { startFrame = 0 } else { current.awaitingReopen = true }
            if current.awaitingReopen {
                awaitReopen(current, ms: ms(frames: frame))
            } else {
                reportSeekUnsupported(current, ms: ms(frames: frame))
            }
        }
        appendSegment(for: current, mediaStart: startFrame)
        if startTiming?.positionedAt == nil { startTiming?.positionedAt = StartupTiming.now() }
        if !playWhenReady {
            pendingActivation = nil
            fill()
            readyPausedAt = StartupTiming.now()
            setState(.paused)
        } else if !startPlaying() {
            if retryingStart { retryStart() } else { stayPaused() }
        }
        emitPosition()
    }

    /// Carry on from the current track into `slot`, the next: at once if it's open, else once its
    /// open (started now, if it wasn't already) is done; ``readChunk()`` waits for it. A next that
    /// can't be opened gets no segment, so it is never transitioned into: the current track ends,
    /// and the owner, told of the failure, decides what follows it.
    func beginReading(_ slot: Slot) {
        prepare(slot)
        setReading(slot)
        if slot.opened { appendSegment(for: slot, mediaStart: 0) }
    }

    /// Make `slot` (the next) the current track, report the transition, and start the stream at
    /// `frame` of it.
    func promote(_ slot: Slot, restartingAt frame: Int64) {
        current.map(release)
        current = slot
        next = nil
        reportTransition(to: slot, gapless: false)
        openIfNeeded(slot)
        restart(atFrame: frame)
    }

    /// `slot`, the current track, reads nothing more until the owner re-opens it at `ms` (``onSeekUnsupported``).
    /// No re-open (a load) within ``reopenTimeout`` fails it, so it can't stay silent for good.
    func awaitReopen(_ slot: Slot, ms: Int64) {
        slot.awaitingReopen = true
        reportSeekUnsupported(slot, ms: ms)
        engineQueue.asyncAfter(deadline: .now() + reopenTimeout) { [weak self, weak slot] in
            guard let self, let slot, slot === current, slot.awaitingReopen, !slot.failed else { return }
            reportFailure(slot, TrackSourceError.failed("no re-open within \(Self.reopenTimeoutSeconds) s"))
            fill()
        }
    }

    /// A transcode whose download gave up may be re-opened, unless it is itself the re-open of one that gave up and
    /// hasn't been heard for ``giveUpRetryWindowSeconds``: a flaky link fails rather than looping.
    private func mayReopenAfterGiveUp(_ slot: Slot) -> Bool {
        slot.id != reopenedAfterGiveUp || currentMediaFrame() >= frames(ms: Self.giveUpRetryWindowSeconds * 1000)
    }

    func appendSegment(for slot: Slot, mediaStart: Int64) {
        let segment = Segment(
            streamStart: inputIndex, uid: slot.track.uid, mediaStart: mediaStart,
            durationFrames: slot.durationFrames, slot: slot.id
        )
        timelineLock.withLock { timeline.segments.append(segment) }
    }

    func teardown() {
        generation += 1
        heldChunk = nil
        heldForResume = []
        dropStartTiming()
        player.stop()
        stopTicker()
        current.map(release)
        next.map(release)
        current = nil
        next = nil
        setReading(nil)
        drained = false
        pausingAtEnd = false
        pausedAtEnd = false
        buffersInFlight = 0
        endUnderrun("stopped")
        timelineLock.withLock { timeline = Timeline() }
    }

    /// Decode and schedule until `aheadFrames` are queued ahead of the playhead or the queue's end is
    /// scheduled.
    func fill(aheadFrames: Int64? = nil) {
        let aheadFrames = aheadFrames ?? scheduleAheadFrames
        guard current != nil else { return }
        resumeIfDue()
        prepareNextIfDue()
        if let pendingEqualizer {
            processor.setEqualizer(pendingEqualizer)
            self.pendingEqualizer = nil
        }
        while !drained, outputIndex - playedStreamIndex() < aheadFrames {
            guard let buffer = readChunk() else { return }
            if buffer.frameLength > 0 { schedule(buffer) }
        }
    }

    /// Read up to one chunk from `reading`, crossing into `next` where the current track ends.
    /// nil when nothing could be read (an interrupted read, the next track still opening, or
    /// nothing left).
    private func readChunk() -> AVAudioPCMBuffer? {
        var output: [Float] = []
        output.reserveCapacity(scratch.count)
        var filled = 0
        let channels = outputChannelCount
        var interrupted = false
        scratch.withUnsafeMutableBufferPointer { raw in
            let base = raw.baseAddress!
            while filled < Self.chunkFrames, let slot = reading {
                // Still opening: what's read so far goes out, and its open's end resumes the fill.
                // The current track's last frames, still inside the limiter, go out too: they'd
                // otherwise be heard after the wait.
                // A next that dropped while read ahead reads nothing more: what was read of it plays, and it's
                // re-opened once it's current (``updateTimeline(concludingEnd:)``).
                if slot.preparing != nil || (slot.awaitingReopen && slot !== current) {
                    processor.drain(into: &output)
                    return
                }
                if slot.awaitingReopen, !slot.failed {
                    interrupted = true
                    return
                }
                var got = 0
                if slot.awaitingReopen {
                    // Its re-open never came (``awaitReopen(_:ms:)``): it ends here.
                } else if !slot.primed.isEmpty {
                    slot.atStart = false
                    got = min(slot.primed.count / channels, Self.chunkFrames - filled)
                    slot.primed.withUnsafeBufferPointer {
                        (base + filled * channels).update(from: $0.baseAddress!, count: got * channels)
                    }
                    slot.primed.removeFirst(got * channels)
                } else if let error = slot.primeError {
                    slot.atStart = false
                    slot.primeError = nil
                    reportFailure(slot, error)
                } else if slot.opened {
                    slot.atStart = false
                    do {
                        got = try slot.source.read(into: base + filled * channels, maxFrames: Self.chunkFrames - filled)
                    } catch TrackSourceError.interrupted {
                        interrupted = true
                        return
                    } catch TrackSourceError.unseekable where slot === current {
                        // It refused a seek, or its stream dropped and its host would only start it
                        // again from the top: neither ended nor failed, it waits, as an interrupted read
                        // does, for the owner to re-open it where it's heard.
                        interrupted = true
                        if !slot.awaitingReopen { awaitReopen(slot, ms: ms(frames: currentMediaFrame())) }
                        return
                    } catch TrackSourceError.unseekable {
                        slot.awaitingReopen = true
                        continue
                    } catch TrackSourceError.failed where !slot.source.isSeekable && mayReopenAfterGiveUp(slot) {
                        // A transcode whose download gave up after its retries: re-opened where it's heard, as a
                        // drop is. A next is re-opened once it's current (``updateTimeline(concludingEnd:)``).
                        engineLog.notice("track \(slot.track.uid, privacy: .public) gave up mid-stream; asking for a re-open")
                        slot.gaveUp = true
                        slot.awaitingReopen = true
                        guard slot === current else { continue }
                        interrupted = true
                        awaitReopen(slot, ms: ms(frames: currentMediaFrame()))
                        return
                    } catch {
                        reportFailure(slot, error)
                    }
                }
                if got > 0 {
                    processor.process(base + filled * channels, frameCount: got,
                                      gain: PCMProcessor.linear(db: slot.track.gainDb), into: &output)
                    filled += got
                    inputIndex += Int64(got)
                    continue
                }
                // The slot ended (or failed, or never opened): on into the next, or the queue's end.
                if slot === current, let next, !(pauseAtEnd && !slot.failed) {
                    beginReading(next)
                } else {
                    setReading(nil)
                    processor.drain(into: &output)
                    drained = true
                    pausingAtEnd = slot === current && next != nil
                }
            }
        }
        if interrupted {
            // What was read before the interrupt waits for the seek behind it: a restart drops it as the old
            // position's, a next resumed in place schedules it. Scheduled now, it would end an underrun the
            // seek's own read is still waiting out.
            heldChunk = output.isEmpty ? nil : buffer(interleaved: output)
            return nil
        }
        if output.isEmpty {
            guard drained else { return nil }
            // Nothing more will reach the node: a dry node is the queue's end, not a stall.
            starved = false
            endUnderrun("drained")
            return AVAudioPCMBuffer(pcmFormat: format, frameCapacity: 1)
        }
        return buffer(interleaved: output)
    }

    private func buffer(interleaved output: [Float]) -> AVAudioPCMBuffer? {
        let channels = outputChannelCount
        let frameCount = output.count / channels
        guard let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: AVAudioFrameCount(frameCount)),
              let channelData = buffer.floatChannelData
        else { return nil }
        buffer.frameLength = AVAudioFrameCount(frameCount)
        output.withUnsafeBufferPointer { interleaved in
            for c in 0..<channels {
                let destination = channelData[c]
                for f in 0..<frameCount { destination[f] = interleaved[f * channels + c] }
            }
        }
        return buffer
    }

    private func schedule(_ buffer: AVAudioPCMBuffer) {
        if underrun != nil, timelineLock.withLock({ timeline.held == nil }) {
            if heldForResume.isEmpty { heldSince = clock() }
            heldForResume.append(buffer)
            resumeIfDue()
            return
        }
        if underrun != nil {
            endUnderrun("recovered")
            // The one place an underrun's end says playing: a buffer is at the node. A start still to come says it.
            if current != nil, state == .playing { reportState(.playing) }
        }
        enqueue(buffer)
    }

    func enqueue(_ buffer: AVAudioPCMBuffer) {
        if starved {
            // The node ran dry and played silence the stream does not contain; this buffer starts
            // wherever the node's clock is now.
            starved = false
            if let now = nodeSampleTime() {
                let anchor = Anchor(stream: outputIndex, player: now)
                timelineLock.withLock { timeline.anchors.append(anchor) }
            }
        }
        noteFirstBuffer()
        let generation = self.generation
        buffersInFlight += 1
        player.scheduleBuffer(buffer, at: nil, options: [], completionCallbackType: .dataConsumed) { [weak self] _ in
            self?.engineQueue.async {
                guard let self, self.generation == generation else { return }
                self.buffersInFlight -= 1
                // A stopped engine's node plays nothing: it hands its buffers back unplayed. A route change
                // stops the engine before it's reported, so those completions can reach this queue ahead of
                // the rebuild; they're not an underrun (#944). Only this queue starts the engine, and a
                // restart's `generation` drops anything handed back before it.
                if self.buffersInFlight == 0, !self.drained, self.state == .playing, self.engine.isRunning {
                    self.beginUnderrun()
                }
                self.fill()
            }
        }
        outputIndex += Int64(buffer.frameLength)
        let end = outputIndex
        timelineLock.withLock { timeline.scheduledEnd = end }
    }
}
