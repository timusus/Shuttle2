// Copied from Shuttle Podcasts (podcasts@9ee6e0954) mobile/ios/Playback/Sources/Playback/DSP/Biquad.swift — see ios/Playback/README.md.
import Foundation

/// **Second-order IIR section, Direct Form II Transposed.**
///
/// A straight port of Android's `BiquadFilter`
/// (`playback/.../audio/dsp/BiquadFilter.kt`), coefficients and all. The Android file is the
/// spec: same Audio EQ Cookbook formulas, same normalisation to `a0 = 1`, same state update, and
/// the same `Double` arithmetic on `Float` samples — a `Float` accumulator in a feedback path
/// audibly drifts at low cutoffs, which is why Android widened it and why this does too.
///
/// Kept as its own type for the same reason ``SilenceGate`` is: it is pure, so it can be pinned by
/// a test on a machine with no audio output at all.
struct Biquad {

    private let b0: Double
    private let b1: Double
    private let b2: Double
    private let a1: Double
    private let a2: Double

    // Direct Form II Transposed delay line.
    private var z1: Double = 0
    private var z2: Double = 0

    init(b0: Double, b1: Double, b2: Double, a1: Double, a2: Double) {
        self.b0 = b0
        self.b1 = b1
        self.b2 = b2
        self.a1 = a1
        self.a2 = a2
    }

    /// ```
    /// y  = b0*x + z1
    /// z1 = b1*x - a1*y + z2
    /// z2 = b2*x - a2*y
    /// ```
    mutating func process(_ input: Float) -> Float {
        let x = Double(input)
        let y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return Float(y)
    }

    /// Zero the delay line. Called on seek and between episodes: filter memory from before a
    /// discontinuity is not audio, it is a click.
    mutating func reset() {
        z1 = 0
        z2 = 0
    }

    // MARK: - Cookbook factories

    /// High-pass. `q` of 0.707 is Butterworth / maximally flat, which is what the chain uses.
    static func highPass(frequency: Double, sampleRate: Double, q: Double) -> Biquad {
        let w0 = 2.0 * Double.pi * frequency / sampleRate
        let alpha = sin(w0) / (2.0 * q)
        let cosW0 = cos(w0)

        let b0 = (1.0 + cosW0) / 2.0
        let b1 = -(1.0 + cosW0)
        let b2 = (1.0 + cosW0) / 2.0
        let a0 = 1.0 + alpha
        let a1 = -2.0 * cosW0
        let a2 = 1.0 - alpha

        return Biquad(b0: b0 / a0, b1: b1 / a0, b2: b2 / a0, a1: a1 / a0, a2: a2 / a0)
    }

    /// Peaking EQ. Positive `gainDb` boosts a band centred on `frequency`.
    static func peaking(frequency: Double, sampleRate: Double, gainDb: Double, q: Double) -> Biquad {
        let A = pow(10.0, gainDb / 40.0)
        let w0 = 2.0 * Double.pi * frequency / sampleRate
        let alpha = sin(w0) / (2.0 * q)
        let cosW0 = cos(w0)

        let b0 = 1.0 + alpha * A
        let b1 = -2.0 * cosW0
        let b2 = 1.0 - alpha * A
        let a0 = 1.0 + alpha / A
        let a1 = -2.0 * cosW0
        let a2 = 1.0 - alpha / A

        return Biquad(b0: b0 / a0, b1: b1 / a0, b2: b2 / a0, a1: a1 / a0, a2: a2 / a0)
    }
}
