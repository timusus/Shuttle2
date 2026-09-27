// Copied from Shuttle Podcasts (podcasts@9ee6e0954) mobile/ios/Playback/Sources/Playback/DSP/LookaheadLimiter.swift — see ios/Playback/README.md.
import Foundation

/// **The last stage: nothing leaves above the ceiling.**
///
/// Ported from Android's `LookaheadLimiter`. Output is delayed by the lookahead window, so a peak
/// is seen before it is emitted and the gain is already down when it arrives — which is what makes
/// this a limiter rather than a clipper.
///
/// The sliding-window maximum is a monotonic deque, amortised O(1) per sample, the same structure
/// Android uses. Scanning the window per sample was measurably too slow on the render thread there,
/// and this runs on the decode queue with a comparable budget.
///
/// The deque is a fixed ring buffer rather than an `Array`: `Array.removeFirst()` shifts every
/// remaining element, which turns the front-trim into O(n) and hands back a per-sample cost that
/// grows with the window. `ArrayDeque` gives Android O(1) there; the ring gives it here, without
/// allocating on the audio path.
struct LookaheadLimiter {

    private let ceiling: Double
    private let lookaheadSamples: Int
    private let releaseCoeff: Double

    private var delayBuffer: [Float]
    private var writePos: Int = 0
    private var gainEnvelope: Double = 1.0

    /// Monotonic deque of `(sampleIndex, peak)` in decreasing peak order; the head is the window
    /// maximum. Stored as two parallel arrays over a ring of fixed capacity — the window holds at
    /// most `lookaheadSamples` entries, plus the one appended before the front-trim runs.
    private var peakIndices: [Int]
    private var peakValues: [Double]
    private var head: Int = 0
    private var count: Int = 0

    private var sampleIndex: Int = 0

    init(ceilingDb: Float, attackMs: Float, releaseMs: Float, sampleRate: Double) {
        ceiling = pow(10.0, Double(ceilingDb) / 20.0)
        // At least one sample of delay: a zero-length window empties the deque and the front read
        // becomes a crash on the audio path (Android hit exactly this on a malformed format).
        lookaheadSamples = max(Int(Double(attackMs) / 1000.0 * sampleRate), 1)
        releaseCoeff = exp(-1.0 / (Double(releaseMs) / 1000.0 * sampleRate))
        delayBuffer = [Float](repeating: 0, count: lookaheadSamples)
        peakIndices = [Int](repeating: 0, count: lookaheadSamples + 1)
        peakValues = [Double](repeating: 0, count: lookaheadSamples + 1)
    }

    mutating func process(_ input: Float) -> Float {
        let peak = Double(abs(input))
        let capacity = peakValues.count

        // Anything at or below the incoming peak can never be the window maximum again.
        while count > 0, peakValues[(head + count - 1) % capacity] <= peak { count -= 1 }
        let tail = (head + count) % capacity
        peakIndices[tail] = sampleIndex
        peakValues[tail] = peak
        count += 1

        let windowStart = sampleIndex - lookaheadSamples + 1
        while count > 0, peakIndices[head] < windowStart {
            head = (head + 1) % capacity
            count -= 1
        }

        let maxPeak = peakValues[head]
        let targetGain = maxPeak > ceiling ? ceiling / maxPeak : 1.0

        gainEnvelope = targetGain < gainEnvelope
            // Attack: 40% of the remaining distance per sample, so the window is more than enough
            // to converge before the peak emerges.
            ? targetGain + (gainEnvelope - targetGain) * 0.6
            : targetGain + releaseCoeff * (gainEnvelope - targetGain)

        let delayed = delayBuffer[writePos]
        delayBuffer[writePos] = input
        writePos = (writePos + 1) % lookaheadSamples
        sampleIndex += 1

        return Float(Double(delayed) * gainEnvelope)
    }

    /// End of stream: push the delay line out so the last `lookaheadSamples` are not swallowed.
    mutating func flush() -> [Float] {
        (0..<lookaheadSamples).map { _ in process(0) }
    }

    mutating func reset() {
        for i in delayBuffer.indices { delayBuffer[i] = 0 }
        head = 0
        count = 0
        writePos = 0
        sampleIndex = 0
        gainEnvelope = 1.0
    }
}
