import AVFoundation
import os

// MARK: - Start timing (engine queue)

extension MusicPlaybackController {
    func timingSource(_ slot: Slot) -> StartupTiming.Source {
        (slot.source as? FFmpegTrackSource)?.isStreamed == true ? .streamed : .file
    }

    /// Times a new start, answering the pending play request if it's this start's: one request, one start.
    func beginStartTiming(_ timing: StartupTiming, of slot: Slot) {
        endStartSignpost("superseded")
        startTimingSerial += 1
        startTiming = timing
        startTiming?.origin = (slot.source as? FFmpegTrackSource)?.streamOrigin
        if let request = pendingPlayRequest {
            startTiming?.attach(request)
            pendingPlayRequest = nil
        }
        startSignpost = startSignposter.beginInterval("ttfa", id: startSignposter.makeSignpostID())
    }

    /// A start that won't reach the ear (paused, refused, torn down) has no line.
    func dropStartTiming() {
        endStartSignpost("dropped")
        startTimingSerial += 1
        startTiming = nil
    }

    private func endStartSignpost(_ outcome: StaticString) {
        guard let state = startSignpost else { return }
        startSignpost = nil
        startSignposter.endInterval("ttfa", state, "\(outcome.description, privacy: .public)")
    }

    /// A play of a paused track. Close behind its load being ready (a load made paused, then played
    /// as it completes) it's still the load's start, timed from the load; otherwise a start of its
    /// own, of a track already open.
    func timePlay(requestedAt: TimeInterval) {
        if let ready = readyPausedAt, startTiming?.nodePlayedAt == nil, startTiming != nil,
           requestedAt - ready < Self.playAfterReadyWindow {
            startTiming?.playAfterReadyMs = Int((max(requestedAt - ready, 0) * 1000).rounded())
            return
        }
        guard let current else { return }
        let ms = position?.ms ?? 0
        beginStartTiming(
            StartupTiming(
                source: timingSource(current),
                start: ms > 0 ? .resume(seconds: Double(ms) / 1000) : .fresh,
                open: .preopened,
                playRequestedAt: requestedAt
            ),
            of: current
        )
    }

    /// How soon after a paused load's ready a play still counts as that load's start.
    private static let playAfterReadyWindow: TimeInterval = 0.5
    /// How long the render watch polls the node's clock before giving up.
    private static let renderWatchSeconds: TimeInterval = 2

    func noteFirstBuffer() {
        guard startTiming != nil, startTiming?.firstBufferScheduledAt == nil else { return }
        startTiming?.firstBufferScheduledAt = StartupTiming.now()
        startSignposter.emitEvent("first buffer")
        if let stats = (current?.source as? FFmpegTrackSource)?.openStats { startTiming?.apply(stats) }
    }

    func playNode() {
        player.play()
        guard startTiming != nil, startTiming?.nodePlayedAt == nil else { return }
        let now = StartupTiming.now()
        startTiming?.nodePlayedAt = now
        startSignposter.emitEvent("node played")
        watchFirstRender(serial: startTimingSerial, deadline: now + Self.renderWatchSeconds)
    }

    /// Polls the node's clock until it moves, so `render` is the output's own latency and not the
    /// position tick's. Offline rendering is noticed by ``updateTimeline(concludingEnd:)``.
    private func watchFirstRender(serial: Int, deadline: TimeInterval) {
        guard case .realtime = renderingMode else { return }
        engineQueue.asyncAfter(deadline: .now() + .milliseconds(5)) { [weak self] in
            guard let self, serial == startTimingSerial, startTiming != nil else { return }
            noteFirstRender()
            guard startTiming != nil else { return }
            if StartupTiming.now() > deadline {
                startTiming?.renderTimedOut = true
                logStartTiming()
            } else {
                watchFirstRender(serial: serial, deadline: deadline)
            }
        }
    }

    func noteFirstRender() {
        guard startTiming?.nodePlayedAt != nil, let sampleTime = nodeSampleTime(), sampleTime > 0 else { return }
        startTiming?.firstRenderedAt = StartupTiming.now()
        logStartTiming()
    }

    private func logStartTiming() {
        guard let timing = startTiming else { return }
        startTiming = nil
        lastStartTiming = timing
        endStartSignpost(timing.renderTimedOut ? "render timeout" : "rendered")
        let line = timing.logLine
        if timing.isSlow {
            engineLog.error("\(line, privacy: .public)")
        } else {
            engineLog.info("\(line, privacy: .public)")
        }
    }
}
