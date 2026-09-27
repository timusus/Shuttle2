import Foundation

/// The equaliser the Kotlin side computes: `enabled`, a preamp, and one biquad per band.
///
/// The coefficients are normalised (a0 = 1) and computed by the shared Kotlin EQ for the
/// controller's output rate (`MusicPlaybackController.outputSampleRate`), five per band in the
/// order b0, b1, b2, a1, a2 — the same numbers Android's `EqualizerAudioProcessor` runs. Swift
/// never designs a filter; it only runs them.
public struct EqualizerSettings: Equatable {
    public var enabled: Bool
    public var preampDb: Float
    /// `5 × bandCount` values: b0, b1, b2, a1, a2 for each band in turn.
    public var coefficients: [Double]

    public init(enabled: Bool, preampDb: Float = 0, coefficients: [Double] = []) {
        self.enabled = enabled
        self.preampDb = preampDb
        self.coefficients = coefficients
    }

    public static let off = EqualizerSettings(enabled: false)
}

/// The limiter at the end of the chain, which catches what ReplayGain's boost and the EQ's peaks
/// push over full scale.
public struct LimiterSettings: Equatable {
    public var enabled: Bool
    public var ceilingDb: Float
    public var attackMs: Float
    public var releaseMs: Float

    public init(enabled: Bool = true, ceilingDb: Float = -0.1, attackMs: Float = 5, releaseMs: Float = 100) {
        self.enabled = enabled
        self.ceilingDb = ceilingDb
        self.attackMs = attackMs
        self.releaseMs = releaseMs
    }
}

/// **ReplayGain gain → EQ preamp → EQ bands → limiter, on interleaved float32.**
///
/// Runs on the controller's engine queue as each buffer is scheduled, so the chain sees one
/// continuous stream across track boundaries: filter memory and the limiter's envelope carry from
/// the end of one track into the start of the next, as they would on a real signal, and are reset
/// only on a seek or a new load (a discontinuity, where old memory would be a click).
///
/// **Frame alignment.** The limiter delays its output by its lookahead. The processor hides that:
/// ``process(_:frameCount:gain:into:)`` drops the first `latency` output frames after a reset and
/// ``drain(into:)`` hands back the `latency` frames still inside at the end of the queue, so output
/// frame n is always input frame n and the controller's timeline needs no correction.
///
/// Not thread-safe; settings changes are handed over through ``MusicPlaybackController``.
final class PCMProcessor {
    let channelCount: Int
    private let sampleRate: Double

    private var equalizer = EqualizerSettings.off
    private var preampGain: Float = 1
    /// `[band][channel]`.
    private var filters: [[Biquad]] = []

    private var limiter: LookaheadLimiter?
    /// Output frames still to drop to cancel the limiter's delay.
    private var dropFrames = 0

    init(sampleRate: Double, channelCount: Int) {
        self.sampleRate = sampleRate
        self.channelCount = channelCount
        applyLimiter(LimiterSettings())
    }

    /// Frames the chain holds back; what ``drain(into:)`` returns.
    var latencyFrames: Int { limiter?.latencyFrames ?? 0 }

    /// Takes effect from the next processed frame. Filter state is kept when only the gains of an
    /// unchanged band layout move, so dragging a slider does not click.
    func setEqualizer(_ settings: EqualizerSettings) {
        let bandCount = settings.coefficients.count / 5
        let layoutChanged = bandCount != filters.count || settings.enabled != equalizer.enabled
        equalizer = settings
        preampGain = settings.enabled ? Self.linear(db: settings.preampDb) : 1
        guard settings.enabled else {
            filters = []
            return
        }
        filters = (0..<bandCount).map { band in
            let c = Array(settings.coefficients[(band * 5)..<(band * 5 + 5)])
            return (0..<channelCount).map { channel in
                var filter = Biquad(b0: c[0], b1: c[1], b2: c[2], a1: c[3], a2: c[4])
                if !layoutChanged { filter.adoptState(of: filters[band][channel]) }
                return filter
            }
        }
    }

    /// Rebuilds the limiter; the stream restarts its alignment, so call it at a reset point.
    func setLimiter(_ settings: LimiterSettings) {
        applyLimiter(settings)
        dropFrames = latencyFrames
    }

    private func applyLimiter(_ settings: LimiterSettings) {
        limiter = settings.enabled
            ? LookaheadLimiter(channelCount: channelCount, ceilingDb: settings.ceilingDb,
                               attackMs: settings.attackMs, releaseMs: settings.releaseMs,
                               sampleRate: sampleRate)
            : nil
        dropFrames = latencyFrames
    }

    /// Clear every filter and the limiter: the next frame starts a new stream.
    func reset() {
        for band in filters.indices {
            for channel in filters[band].indices { filters[band][channel].reset() }
        }
        limiter?.reset()
        dropFrames = latencyFrames
    }

    /// Process `frameCount` interleaved frames in place and append what comes out of the chain to
    /// `output`: `frameCount` frames, fewer while the limiter's delay is being dropped.
    func process(_ frames: UnsafeMutablePointer<Float>, frameCount: Int, gain: Float, into output: inout [Float]) {
        let gain = gain * preampGain
        let samples = frameCount * channelCount
        if gain != 1 {
            for i in 0..<samples { frames[i] *= gain }
        }
        if !filters.isEmpty {
            for f in 0..<frameCount {
                let base = f * channelCount
                for c in 0..<channelCount {
                    var x = frames[base + c]
                    for band in filters.indices { x = filters[band][c].process(x) }
                    frames[base + c] = x
                }
            }
        }
        var start = 0
        if limiter != nil {
            for f in 0..<frameCount { limiter!.process(frames + f * channelCount) }
            start = min(dropFrames, frameCount)
            dropFrames -= start
        }
        output.append(contentsOf: UnsafeBufferPointer(start: frames + start * channelCount,
                                                      count: (frameCount - start) * channelCount))
    }

    /// The end of the queue: push the frames still inside the limiter out, then start a new stream
    /// (anything processed after this is a fresh start, dropped for alignment again).
    func drain(into output: inout [Float]) {
        guard limiter != nil else { return }
        // Only what was not dropped is still owed: a stream shorter than the latency owes less.
        let owed = latencyFrames - dropFrames
        if owed > 0 {
            var silence = [Float](repeating: 0, count: latencyFrames * channelCount)
            silence.withUnsafeMutableBufferPointer { buffer in
                for f in 0..<latencyFrames { limiter!.process(buffer.baseAddress! + f * channelCount) }
            }
            let skip = latencyFrames - owed
            output.append(contentsOf: silence[(skip * channelCount)...])
        }
        limiter?.reset()
        dropFrames = latencyFrames
    }

    static func linear(db: Float) -> Float {
        db == 0 ? 1 : powf(10, db / 20)
    }
}
