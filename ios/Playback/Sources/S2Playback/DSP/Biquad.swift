// Adapted from Shuttle Podcasts (podcasts@9ee6e0954) mobile/ios/Playback/Sources/Playback/DSP/Biquad.swift — see ios/Playback/README.md.
// S2 changes: the factories are gone (the shared Kotlin EQ designs every band, #604); `adoptState(of:)`
// lets the EQ swap a band's coefficients without a click.
import Foundation

/// **Second-order IIR section, Direct Form II Transposed.**
///
/// Runs coefficients the shared Kotlin EQ designed (`EqualizerCascade`, normalised to `a0 = 1`),
/// with the state update and `Double` arithmetic on `Float` samples of Android's `BandProcessor`:
/// a `Float` accumulator in a feedback path audibly drifts at low cutoffs, which is why Android
/// widened it and why this does too.
///
/// Kept as its own type because it is pure, so it can be pinned by a test on a machine with no
/// audio output at all.
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

    /// Carry `other`'s delay line over, so new coefficients continue the signal instead of
    /// restarting it from silence.
    mutating func adoptState(of other: Biquad) {
        z1 = other.z1
        z2 = other.z2
    }
}
