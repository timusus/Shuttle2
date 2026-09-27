// Adapted from Shuttle Podcasts (podcasts@9ee6e0954) mobile/ios/Playback/Sources/Playback/DSP/LookaheadLimiter.swift — see ios/Playback/README.md.
// S2 changes: stereo-linked (one gain for every channel of a frame, so the image does not shift
// when one side peaks), frame-interleaved I/O, and the window covers the frame being emitted.
import Foundation

/// **The last stage: nothing leaves above the ceiling.**
///
/// Ported from Android's `LookaheadLimiter`. Output is delayed by the lookahead window, so a peak
/// is seen before it is emitted and the gain is already down when it arrives — which is what makes
/// this a limiter rather than a clipper. ``latencyFrames`` is that delay; the caller compensates
/// for it (``PCMProcessor`` drops the first `latencyFrames` outputs and flushes as many at the end)
/// so the stream it schedules stays frame-aligned with the decoded one.
///
/// The sliding-window maximum is a monotonic deque over a fixed ring, amortised O(1) per frame
/// with no allocation on the audio path, the same structure Android uses.
///
/// Below the ceiling the gain is exactly 1.0 and the output is the delayed input bit for bit, which
/// is what lets the gapless tests compare samples for equality with the limiter in the chain.
struct LookaheadLimiter {

    let channelCount: Int
    /// Frames between a frame going in and the same frame coming out.
    let latencyFrames: Int

    private let ceiling: Double
    private let releaseCoeff: Double

    /// `latencyFrames` interleaved frames.
    private var delayBuffer: [Float]
    private var writePos: Int = 0
    private var gainEnvelope: Double = 1.0

    /// Monotonic deque of `(frameIndex, peak)` in decreasing peak order; the head is the window
    /// maximum. The window is the `latencyFrames + 1` frames from the one being emitted to the one
    /// just read.
    private var peakIndices: [Int]
    private var peakValues: [Double]
    private var head: Int = 0
    private var count: Int = 0

    private var frameIndex: Int = 0

    init(channelCount: Int, ceilingDb: Float, attackMs: Float, releaseMs: Float, sampleRate: Double) {
        self.channelCount = max(channelCount, 1)
        ceiling = pow(10.0, Double(ceilingDb) / 20.0)
        // At least one frame of delay: a zero-length window empties the deque and the front read
        // becomes a crash on the audio path (Android hit exactly this on a malformed format).
        latencyFrames = max(Int(Double(attackMs) / 1000.0 * sampleRate), 1)
        releaseCoeff = exp(-1.0 / (Double(releaseMs) / 1000.0 * sampleRate))
        delayBuffer = [Float](repeating: 0, count: latencyFrames * self.channelCount)
        peakIndices = [Int](repeating: 0, count: latencyFrames + 2)
        peakValues = [Double](repeating: 0, count: latencyFrames + 2)
    }

    /// Replace the frame at `frame` (interleaved, `channelCount` floats) with the frame that went
    /// in `latencyFrames` calls ago, limited.
    mutating func process(_ frame: UnsafeMutablePointer<Float>) {
        var peak = 0.0
        for c in 0..<channelCount { peak = max(peak, Double(abs(frame[c]))) }
        let capacity = peakValues.count

        // Anything at or below the incoming peak can never be the window maximum again.
        while count > 0, peakValues[(head + count - 1) % capacity] <= peak { count -= 1 }
        let tail = (head + count) % capacity
        peakIndices[tail] = frameIndex
        peakValues[tail] = peak
        count += 1

        let windowStart = frameIndex - latencyFrames
        while count > 0, peakIndices[head] < windowStart {
            head = (head + 1) % capacity
            count -= 1
        }

        let maxPeak = peakValues[head]
        let targetGain = maxPeak > ceiling ? ceiling / maxPeak : 1.0

        gainEnvelope = targetGain < gainEnvelope
            // Attack: 40% of the remaining distance per frame, so the window is more than enough
            // to converge before the peak emerges.
            ? targetGain + (gainEnvelope - targetGain) * 0.6
            : targetGain + releaseCoeff * (gainEnvelope - targetGain)
        let gain = gainEnvelope

        let base = writePos * channelCount
        for c in 0..<channelCount {
            let delayed = delayBuffer[base + c]
            delayBuffer[base + c] = frame[c]
            frame[c] = gain == 1.0 ? delayed : Float(Double(delayed) * gain)
        }
        writePos = (writePos + 1) % latencyFrames
        frameIndex += 1
    }

    mutating func reset() {
        for i in delayBuffer.indices { delayBuffer[i] = 0 }
        head = 0
        count = 0
        writePos = 0
        frameIndex = 0
        gainEnvelope = 1.0
    }
}
