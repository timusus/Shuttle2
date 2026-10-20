import Foundation
import os
import PlaybackRender

/// **The last stage: nothing leaves above -0.1 dBFS.** Catches what the EQ's boost and preamp push
/// over full scale, where Android's EQ clamps.
///
/// Its lookahead delays the audio, which the stage hides: the first `latencyFrames` outputs after a
/// flush are the delay line's silence and are held back, and an end of stream pushes the last real
/// frames out, so output frame n is input frame n and no frame is added.
public final class LimiterProcessor: BufferingAudioProcessor, Sendable {
    /// Everything is touched from the transport queue; the lock makes that provable rather than assumed.
    private struct State {
        var staged: PCMFormat?
        var format: PCMFormat?
        var limiter: LookaheadLimiter?
        /// `latencyFrames` frames, to push the delay line out at the end of a stream; that overwrites it,
        /// so each flush allocates it again.
        var silence: [Float] = []
        /// Delay-line frames still to hold back.
        var prime = 0
        var output: [Float] = []
        var endQueued = false

        mutating func queueInput(_ samples: [Float]) {
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

        mutating func getOutput() -> ProcessorOutput {
            defer { output = [] }
            return ProcessorOutput(samples: output)
        }

        mutating func queueEndOfStream() {
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

        mutating func apply() {
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

        private mutating func append(_ samples: [Float]) {
            if output.isEmpty {
                output = samples
            } else {
                output.append(contentsOf: samples)
            }
        }
    }

    private let state = OSAllocatedUnfairLock(initialState: State())

    public init() {}

    public func configure(_ input: PCMFormat) throws -> PCMFormat {
        state.withLock { $0.staged = input }
        return input
    }

    public var isActive: Bool { true }

    public func queueInput(_ samples: [Float]) {
        state.withLock { $0.queueInput(samples) }
    }

    public func getOutput() -> ProcessorOutput {
        state.withLock { $0.getOutput() }
    }

    public func queueEndOfStream() {
        state.withLock { $0.queueEndOfStream() }
    }

    public var isEnded: Bool { state.withLock { $0.endQueued && $0.output.isEmpty } }

    public func flush(_: StreamMetadata) {
        state.withLock { $0.apply() }
    }

    public func reset() {
        state.withLock {
            $0.staged = nil
            $0.apply()
        }
    }
}
