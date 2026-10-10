import Foundation

// MARK: - Opening and pre-opening (engine queue)

extension MusicPlaybackController {
    /// Opens `slot`'s source, or waits for the open already under way; false (after reporting it)
    /// if it could not be.
    @discardableResult
    func openIfNeeded(_ slot: Slot) -> Bool {
        if let preparing = slot.preparing {
            preparing.wait()
            settlePrepared(slot)
        }
        guard !slot.opened, !slot.failed else { return slot.opened }
        do {
            let started = DispatchTime.now().uptimeNanoseconds
            slot.durationFrames = try slot.source.open(sampleRate: outputSampleRate, channelCount: outputChannelCount)
            slot.opened = true
            preopenLead.record(openSeconds: Self.seconds(since: started))
            return true
        } catch {
            reportFailure(slot, error)
            return false
        }
    }

    /// Open the next track off the engine queue once the current one is within the pre-open lead
    /// of its end (read, not heard: the stream reaches the join a schedule-ahead before the ear).
    /// When neither the container nor the track's expected duration says where the end is, once
    /// `steadyFrames` of it have been read since the stream last started.
    func prepareNextIfDue() {
        guard let current, let next, !next.opened, !next.failed, next.preparing == nil else { return }
        if reading === current, current.opened, !current.failed,
           let segment = timelineLock.withLock({ timeline.segments.last { $0.slot == current.id } }) {
            let streamed = inputIndex - segment.streamStart
            if let duration = current.durationFrames ?? current.track.expectedDurationMs.map({ frames(ms: $0) }) {
                let lead = Int64(preopenLead.seconds * outputSampleRate)
                guard duration - (segment.mediaStart + streamed) <= lead else { return }
            } else {
                guard streamed >= steadyFrames else { return }
            }
        }
        prepare(next)
    }

    private static func seconds(since uptimeNanoseconds: UInt64) -> Double {
        Double(DispatchTime.now().uptimeNanoseconds - uptimeNanoseconds) / 1e9
    }

    /// Start opening `slot` on the prepare queue, and decoding its first chunk, so its first read
    /// doesn't wait on the network either.
    func prepare(_ slot: Slot) {
        guard !slot.opened, !slot.failed, slot.preparing == nil else { return }
        let group = DispatchGroup()
        group.enter()
        opening.enter()
        slot.preparing = group
        let source = slot.source
        let sampleRate = outputSampleRate
        let channels = outputChannelCount
        let chunkFrames = Self.chunkFrames
        prepareQueue.async { [weak self] in
            let started = DispatchTime.now().uptimeNanoseconds
            slot.prepared = Result {
                let duration = try source.open(sampleRate: sampleRate, channelCount: channels)
                var primed = [Float](repeating: 0, count: chunkFrames * channels)
                var primeError: Error?
                do {
                    let got = try primed.withUnsafeMutableBufferPointer {
                        try source.read(into: $0.baseAddress!, maxFrames: chunkFrames)
                    }
                    primed.removeSubrange((got * channels)...)
                } catch {
                    primed = []
                    primeError = error
                }
                return (duration: duration, primed: primed, primeError: primeError, seconds: Self.seconds(since: started))
            }
            group.leave()
            if let self {
                engineQueue.async { self.finishPrepare(slot) }
                opening.leave()
            }
        }
    }

    /// `slot`'s open is done: apply it, and carry the stream on into it if it was waiting there.
    private func finishPrepare(_ slot: Slot) {
        // Settled already by a wait for it, or released.
        guard slot.preparing != nil else { return }
        settlePrepared(slot)
        guard reading === slot else { return }
        setReading(slot)
        if slot.opened { appendSegment(for: slot, mediaStart: 0) }
        fill()
    }

    private func settlePrepared(_ slot: Slot) {
        guard slot.preparing != nil else { return }
        slot.preparing = nil
        switch slot.prepared {
        case let .success(result):
            slot.durationFrames = result.duration
            slot.primed = result.primed
            slot.primeError = result.primeError
            slot.opened = true
            preopenLead.record(openSeconds: result.seconds)
        case let .failure(error):
            reportFailure(slot, error)
        case nil:
            break
        }
        slot.prepared = nil
    }

    /// Let go of a slot that is no longer current or next. One still opening is cancelled and
    /// marked failed, silently, so a stream waiting on it reads it as ended.
    func release(_ slot: Slot) {
        slot.source.cancel()
        if slot.preparing != nil {
            slot.preparing = nil
            slot.failed = true
        }
    }

    func seek(_ slot: Slot, toFrame frame: Int64) throws {
        slot.primed = []
        slot.primeError = nil
        slot.atStart = false
        try slot.source.seek(toFrame: frame)
    }
}
