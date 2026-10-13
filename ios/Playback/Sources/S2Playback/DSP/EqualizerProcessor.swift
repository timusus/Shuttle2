import Foundation
import os
import PlaybackRender

/// The equalizer designed for one sample rate: a gain in dB applied after the filters (the headroom
/// attenuation and the user's preamp together), and five coefficients per band (b0, b1, b2, a1, a2,
/// normalised to a0 = 1), in band order.
public struct EqualizerDesign: Sendable {
    public var preampDb: Float
    public var coefficients: [Double]

    public init(preampDb: Float, coefficients: [Double]) {
        self.preampDb = preampDb
        self.coefficients = coefficients
    }
}

/// **The EQ stage: one biquad per band and channel, then the gain.**
///
/// Ported from Android's `EqualizerAudioProcessor`. Swift never designs a filter: `design` (the shared
/// Kotlin `EqualizerCascade`) is called for each rate the pipeline configures, so 44.1, 48 and 96 kHz
/// run the same response. Always active, so a toggle never needs `setProcessors`; off or flat, a
/// chunk passes through untouched.
///
/// A settings change is designed on the caller's thread for the format playing and taken at the next
/// chunk boundary with a try-lock, so the transport's queue never waits, allocates filters or designs
/// per chunk. The new filters carry the old ones' state and the gain ramps across the first chunk, so
/// a change doesn't click.
public final class EqualizerProcessor: BufferingAudioProcessor, @unchecked Sendable {
    public typealias Design = @Sendable (_ sampleRate: Double) -> EqualizerDesign

    /// One settings change at one format. A class, so the transport queue mutates the filters in place
    /// with no copy-on-write.
    private final class Prepared {
        let generation: Int
        let format: PCMFormat
        let enabled: Bool
        /// `[band * channelCount + channel]`; empty when every band is flat.
        var filters: [Biquad]
        let gain: Float

        init(generation: Int, format: PCMFormat, enabled: Bool, design: Design) {
            self.generation = generation
            self.format = format
            self.enabled = enabled
            guard enabled else {
                filters = []
                gain = 1
                return
            }
            let designed = design(format.sampleRate)
            let c = designed.coefficients
            let bandCount = c.count / 5
            // A flat band designs to exactly the identity, so flat needs no tolerance.
            let flat = (0..<bandCount).allSatisfy { band in
                c[band * 5] == 1 && c[band * 5 + 1] == 0 && c[band * 5 + 2] == 0 && c[band * 5 + 3] == 0 && c[band * 5 + 4] == 0
            }
            filters = flat ? [] : (0..<bandCount * format.channelCount).map { index in
                let base = index / format.channelCount * 5
                return Biquad(b0: c[base], b1: c[base + 1], b2: c[base + 2], a1: c[base + 3], a2: c[base + 4])
            }
            gain = designed.preampDb == 0 ? 1 : powf(10, designed.preampDb / 20)
        }

        var isBypassed: Bool { !enabled || (filters.isEmpty && gain == 1) }

        /// Same band count: carry each delay line so the signal continues instead of restarting.
        func adoptState(of other: Prepared) {
            guard other.filters.count == filters.count else { return }
            for index in filters.indices { filters[index].adoptState(of: other.filters[index]) }
        }

        func resetFilters() {
            for index in filters.indices { filters[index].reset() }
        }
    }

    /// Shared with the caller's thread.
    private struct Shared {
        var enabled = false
        var design: Design = { _ in EqualizerDesign(preampDb: 0, coefficients: []) }
        var generation = 0
        /// The format playing, so a change is designed for it.
        var format: PCMFormat?
        /// The latest change designed for `format`, or the one the transport queue swapped out, left for
        /// the caller's thread to free.
        var handover: Prepared?
    }

    private let shared = OSAllocatedUnfairLock(initialState: Shared())

    // Transport queue only.
    private var staged: PCMFormat?
    private var format: PCMFormat?
    private var current: Prepared?
    private var output: [Float] = []
    private var endQueued = false
    private var endedSinceFlush = false
    /// The gain the last chunk ended on.
    private var lastGain: Float = 1

    public init() {}

    /// Takes effect from the next chunk processed. `design` runs here for the format playing, and on
    /// the transport's queue whenever the pipeline configures a new one.
    public func setEqualizer(enabled: Bool, design: @escaping Design) {
        let (generation, format) = shared.withLock { state in
            state.enabled = enabled
            state.design = design
            state.generation += 1
            return (state.generation, state.format)
        }
        guard let format else { return }
        let prepared = Prepared(generation: generation, format: format, enabled: enabled, design: design)
        let retired = shared.withLock { state -> Prepared? in
            // A newer change, or a flush to another format, got there first and wins.
            guard state.generation == generation, state.format == format else { return nil }
            defer { state.handover = prepared }
            return state.handover
        }
        withExtendedLifetime(retired) {}
    }

    public func configure(_ input: PCMFormat) throws -> PCMFormat {
        staged = input
        return input
    }

    public var isActive: Bool { true }

    public func queueInput(_ samples: [Float]) {
        takeHandover()
        guard let current, !current.isBypassed, !samples.isEmpty else {
            lastGain = 1
            append(samples)
            return
        }
        // The one copy per chunk: the pipeline hands samples over by value.
        var samples = samples
        let channelCount = current.format.channelCount
        let frameCount = samples.count / channelCount
        let bandCount = current.filters.count / channelCount
        let from = lastGain
        let to = current.gain
        lastGain = to
        current.filters.withUnsafeMutableBufferPointer { filters in
            samples.withUnsafeMutableBufferPointer { buffer in
                for f in 0..<frameCount {
                    let gain = from == to ? to : from + (to - from) * Float(f + 1) / Float(frameCount)
                    for c in 0..<channelCount {
                        var x = buffer[f * channelCount + c]
                        for band in 0..<bandCount { x = filters[band * channelCount + c].process(x) }
                        buffer[f * channelCount + c] = x * gain
                    }
                }
            }
        }
        append(samples)
    }

    public func getOutput() -> ProcessorOutput {
        defer { output = [] }
        return ProcessorOutput(samples: output)
    }

    public func queueEndOfStream() {
        endQueued = true
        endedSinceFlush = true
    }

    public var isEnded: Bool { endQueued && output.isEmpty }

    /// As on Android, the filters keep their history only when the audio carries straight on in the
    /// same format (a drained join); after a seek that history would be a click.
    public func flush(_: StreamMetadata) {
        apply()
    }

    public func reset() {
        staged = nil
        apply()
        lastGain = 1
        shared.withLock { $0.format = nil }
    }

    private func apply() {
        output = []
        endQueued = false
        let carriesOn = endedSinceFlush && staged == format
        endedSinceFlush = false
        let formatChanged = staged != format
        format = staged
        guard let format else {
            current = nil
            return
        }
        let (enabled, design, generation) = shared.withLock { state in
            state.format = format
            state.handover = nil
            return (state.enabled, state.design, state.generation)
        }
        if !formatChanged, let current, current.generation == generation {
            if !carriesOn { current.resetFilters() }
            return
        }
        let prepared = Prepared(generation: generation, format: format, enabled: enabled, design: design)
        if carriesOn, let current { prepared.adoptState(of: current) }
        lastGain = prepared.isBypassed ? 1 : prepared.gain
        current = prepared
    }

    /// Swaps in a change the caller designed, leaving the old one for the caller to free. Never waits:
    /// with the lock held, the change lands a chunk later.
    private func takeHandover() {
        guard let current else { return }
        shared.withLockIfAvailable { state in
            guard let next = state.handover, next.generation > current.generation, next.format == current.format else { return }
            next.adoptState(of: current)
            state.handover = current
            self.current = next
        }
    }

    private func append(_ samples: [Float]) {
        if output.isEmpty {
            output = samples
        } else {
            output.append(contentsOf: samples)
        }
    }
}
