import AVFoundation

// MARK: - Testing

extension MusicPlaybackController {
    /// Offline mode: render the next `frameCount` frames of the mixer's output.
    func renderOffline(frameCount: AVAudioFrameCount) throws -> AVAudioPCMBuffer {
        guard let buffer = AVAudioPCMBuffer(pcmFormat: engine.manualRenderingFormat, frameCapacity: frameCount) else {
            throw TrackSourceError.failed("render buffer")
        }
        let status = try engine.renderOffline(frameCount, to: buffer)
        guard status == .success else { throw TrackSourceError.failed("render status \(status.rawValue)") }
        return buffer
    }

    /// What the position ticker does, synchronously: top the node's queue up and follow the
    /// playhead (transitions, the end, position). Then, `awaitingOpens`, waits for the next track's
    /// open if one is in flight, as if it were instant; and for the callbacks it all caused.
    func pumpForTesting(awaitingOpens: Bool = true) {
        engineQueue.sync {
            fill()
            updateTimeline()
        }
        if awaitingOpens { awaitOpensForTesting() }
        callbackQueue.sync {}
    }

    /// Runs `body` on the engine queue: whatever it asks of the controller waits until it returns.
    func onEngineQueueForTesting(_ body: () -> Void) {
        engineQueue.sync(execute: body)
    }

    /// The last start's `ttfa` record, once its line was logged.
    var lastStartTimingForTesting: StartupTiming? { engineQueue.sync { lastStartTiming } }

    /// Wait for everything already asked of the controller, a next track's open included.
    func syncForTesting() {
        engineQueue.sync {}
        awaitOpensForTesting()
        callbackQueue.sync {}
    }

    /// Whether a track's open (or its first chunk's decode) is still running on the prepare queue.
    var hasOpenInFlightForTesting: Bool { opening.wait(timeout: .now()) == .timedOut }

    /// An open queues its result on the engine queue before it leaves `opening`.
    private func awaitOpensForTesting() {
        opening.wait()
        engineQueue.sync {}
    }
}
