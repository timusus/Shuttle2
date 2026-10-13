import Foundation
import PlaybackRender

/// **The last stage: nothing leaves above -0.1 dBFS.** Catches what the EQ's boost and preamp push
/// over full scale, where Android's EQ clamps.
///
/// Its lookahead delays the audio, which the stage hides: the first `latencyFrames` outputs after a
/// flush are the delay line's silence and are held back, and an end of stream pushes the last real
/// frames out, so output frame n is input frame n and no frame is added.
public final class LimiterProcessor: BufferingAudioProcessor, @unchecked Sendable {
    // Transport queue only.
    private var staged: PCMFormat?
    private var format: PCMFormat?
    private var limiter: LookaheadLimiter?
    /// `latencyFrames` frames, to push the delay line out at the end of a stream; that overwrites it,
    /// so each flush allocates it again.
    private var silence: [Float] = []
    /// Delay-line frames still to hold back.
    private var prime = 0
    private var output: [Float] = []
    private var endQueued = false

    public init() {}

    public func configure(_ input: PCMFormat) throws -> PCMFormat {
        staged = input
        return input
    }

    public var isActive: Bool { true }

    public func queueInput(_ samples: [Float]) {
        guard var limiter, !samples.isEmpty else {
            append(samples)
            return
        }
        // Taken out so the struct's buffers stay uniquely referenced and process in place.
        self.limiter = nil
        // The one copy per chunk: the pipeline hands samples over by value.
        var samples = samples
        let channelCount = limiter.channelCount
        samples.withUnsafeMutableBufferPointer { buffer in
            for f in 0..<buffer.count / channelCount { limiter.process(buffer.baseAddress! + f * channelCount) }
        }
        self.limiter = limiter
        let held = min(prime, samples.count / channelCount)
        prime -= held
        samples.removeFirst(held * channelCount)
        append(samples)
    }

    public func getOutput() -> ProcessorOutput {
        defer { output = [] }
        return ProcessorOutput(samples: output)
    }

    public func queueEndOfStream() {
        guard !endQueued else { return }
        endQueued = true
        guard var limiter else { return }
        self.limiter = nil
        let channelCount = limiter.channelCount
        // Only frames that went in are owed: a stream shorter than the delay owes less.
        let owed = limiter.latencyFrames - prime
        silence.withUnsafeMutableBufferPointer { buffer in
            for f in 0..<limiter.latencyFrames { limiter.process(buffer.baseAddress! + f * channelCount) }
        }
        self.limiter = limiter
        output.append(contentsOf: silence[((limiter.latencyFrames - owed) * channelCount)...])
    }

    public var isEnded: Bool { endQueued && output.isEmpty }

    public func flush(_: StreamMetadata) {
        apply()
    }

    public func reset() {
        staged = nil
        apply()
    }

    private func apply() {
        if staged != format || limiter == nil {
            format = staged
            limiter = format.map {
                LookaheadLimiter(channelCount: $0.channelCount, ceilingDb: -0.1, attackMs: 5, releaseMs: 100, sampleRate: $0.sampleRate)
            }
        } else {
            limiter?.reset()
        }
        silence = [Float](repeating: 0, count: (limiter?.latencyFrames ?? 0) * (limiter?.channelCount ?? 0))
        prime = limiter?.latencyFrames ?? 0
        output = []
        endQueued = false
    }

    private func append(_ samples: [Float]) {
        if output.isEmpty {
            output = samples
        } else {
            output.append(contentsOf: samples)
        }
    }
}
