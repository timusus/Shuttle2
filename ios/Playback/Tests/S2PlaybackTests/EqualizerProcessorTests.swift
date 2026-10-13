import Foundation
import PlaybackRender
import XCTest
@testable import S2Playback

final class EqualizerProcessorTests: XCTestCase {

    /// A +6 dB peak at 1 kHz is +6 dB at 1 kHz at either rate: the filters are designed for the rate
    /// configured. Narrow enough that a 48 kHz design run at 44.1 kHz misses its centre by 2 dB.
    func testTheBandCentreIsBoostedByItsGainAtEachRate() throws {
        for rate in [44_100.0, 48_000.0] {
            let processor = try configured(EqualizerProcessor(), rate: rate, channelCount: 2)
            let designedAt = Rates()
            processor.setEqualizer(enabled: true) { rate in
                designedAt.append(rate)
                return EqualizerDesign(preampDb: 0, coefficients: peaking(frequency: 1_000, gainDb: 6, q: 8, sampleRate: rate))
            }
            let input = sine(frequency: 1_000, rate: rate, frames: Int(rate), channelCount: 2)
            let output = stream(input, through: processor)

            XCTAssertEqual(designedAt.values, [rate])
            // The second half: the filter has settled.
            let settled = (input.count / 2)...
            let gainDb = 20 * log10(rms(output[settled]) / rms(input[settled]))
            XCTAssertEqual(gainDb, 6, accuracy: 0.1, "at \(rate) Hz")
        }
    }

    func testANewRateIsDesignedForWhenThePipelineConfiguresIt() throws {
        let processor = try configured(EqualizerProcessor(), rate: 44_100, channelCount: 2)
        let designedAt = Rates()
        processor.setEqualizer(enabled: true) { rate in
            designedAt.append(rate)
            return EqualizerDesign(preampDb: 0, coefficients: peaking(frequency: 1_000, gainDb: 6, q: 8, sampleRate: rate))
        }
        _ = try processor.configure(PCMFormat(sampleRate: 96_000, channelCount: 2))
        processor.flush(item)

        XCTAssertEqual(designedAt.values, [44_100, 96_000])
    }

    func testOffOrFlatPassesTheAudioThroughBitForBit() throws {
        let input = noise(frames: 4_000, channelCount: 2, seed: 1)
        let flat = [Double](repeating: 0, count: 10).enumerated().map { $0.offset % 5 == 0 ? 1.0 : 0.0 }
        let boosted = peaking(frequency: 1_000, gainDb: 6, q: 1, sampleRate: 48_000)

        let untouched = try configured(EqualizerProcessor(), rate: 48_000, channelCount: 2)
        XCTAssertEqual(stream(input, through: untouched), input)

        let off = try configured(EqualizerProcessor(), rate: 48_000, channelCount: 2)
        off.setEqualizer(enabled: false) { _ in EqualizerDesign(preampDb: -6, coefficients: boosted) }
        XCTAssertEqual(stream(input, through: off), input)

        let onButFlat = try configured(EqualizerProcessor(), rate: 48_000, channelCount: 2)
        onButFlat.setEqualizer(enabled: true) { _ in EqualizerDesign(preampDb: 0, coefficients: flat) }
        XCTAssertEqual(stream(input, through: onButFlat), input)
    }

    /// A settings change mid-stream lands at a chunk boundary without a step: the filters keep their
    /// state and the gain ramps, so the signal never moves further between two samples than a sine
    /// at this level does on its own.
    func testAChangeMidStreamDoesNotClick() throws {
        for channelCount in [1, 2, 6] {
            let processor = try configured(EqualizerProcessor(), rate: 48_000, channelCount: channelCount)
            processor.setEqualizer(enabled: true) { rate in
                EqualizerDesign(preampDb: 0, coefficients: peaking(frequency: 1_000, gainDb: 6, q: 1, sampleRate: rate))
            }
            let input = sine(frequency: 200, rate: 48_000, frames: 48_000, channelCount: channelCount, amplitude: 0.4)
            var output: [Float] = []
            let chunk = 512 * channelCount
            for (index, start) in stride(from: 0, to: input.count, by: chunk).enumerated() {
                if index == 20 {
                    processor.setEqualizer(enabled: true) { rate in
                        EqualizerDesign(preampDb: -12, coefficients: peaking(frequency: 150, gainDb: 12, q: 1, sampleRate: rate))
                    }
                }
                processor.queueInput(Array(input[start..<min(start + chunk, input.count)]))
                output += processor.getOutput().samples
            }

            XCTAssertEqual(output.count, input.count)
            // A 200 Hz sine at up to 0.8 peak moves at most 2π·200/48000·0.8 ≈ 0.021 per sample.
            for c in 0..<channelCount {
                let deltas = (1..<output.count / channelCount).map { abs(output[$0 * channelCount + c] - output[($0 - 1) * channelCount + c]) }
                XCTAssertLessThan(deltas.max()!, 0.03, "channel \(c) of \(channelCount)")
            }
        }
    }
}

final class LimiterProcessorTests: XCTestCase {

    func testNothingLeavesAboveTheCeiling() throws {
        let processor = try configured(LimiterProcessor(), rate: 48_000, channelCount: 2)
        let output = stream(noise(frames: 20_000, channelCount: 2, seed: 2, amplitude: 2), through: processor)

        // -0.1 dBFS is 0.9885531, give or take a Float's last bit.
        XCTAssertLessThanOrEqual(output.map(abs).max()!, 0.988554)
    }

    /// Below the ceiling the limiter's delay is hidden: what goes in comes out, frame for frame.
    func testBelowTheCeilingTheAudioComesOutUnchanged() throws {
        for frames in [10, 20_000] {
            let processor = try configured(LimiterProcessor(), rate: 48_000, channelCount: 2)
            let input = noise(frames: frames, channelCount: 2, seed: 3)

            XCTAssertEqual(stream(input, through: processor), input, "\(frames) frames")
            XCTAssertTrue(processor.isEnded)
        }
    }
}

private let item = StreamMetadata(item: 1, mediaStartFrame: 0)

private func configured<P: BufferingAudioProcessor>(_ processor: P, rate: Double, channelCount: Int) throws -> P {
    _ = try processor.configure(PCMFormat(sampleRate: rate, channelCount: channelCount))
    processor.flush(item)
    return processor
}

/// Feeds `input` in 1000-sample chunks, then ends the stream.
private func stream(_ input: [Float], through processor: BufferingAudioProcessor) -> [Float] {
    var output: [Float] = []
    for start in stride(from: 0, to: input.count, by: 1_000) {
        processor.queueInput(Array(input[start..<min(start + 1_000, input.count)]))
        output += processor.getOutput().samples
    }
    processor.queueEndOfStream()
    output += processor.getOutput().samples
    return output
}

private final class Rates: @unchecked Sendable {
    private let lock = NSLock()
    private var rates: [Double] = []
    var values: [Double] { lock.withLock { rates } }
    func append(_ rate: Double) { lock.withLock { rates.append(rate) } }
}

/// The RBJ cookbook peaking filter, normalised to a0 = 1.
private func peaking(frequency: Double, gainDb: Double, q: Double, sampleRate: Double) -> [Double] {
    let a = pow(10, gainDb / 40)
    let w0 = 2 * Double.pi * frequency / sampleRate
    let alpha = sin(w0) / (2 * q)
    let a0 = 1 + alpha / a
    return [(1 + alpha * a) / a0, -2 * cos(w0) / a0, (1 - alpha * a) / a0, -2 * cos(w0) / a0, (1 - alpha / a) / a0]
}

private func sine(frequency: Double, rate: Double, frames: Int, channelCount: Int, amplitude: Float = 0.25) -> [Float] {
    (0..<frames * channelCount).map { amplitude * Float(sin(2 * Double.pi * frequency * Double($0 / channelCount) / rate)) }
}

private func rms(_ samples: ArraySlice<Float>) -> Float {
    sqrt(samples.reduce(0) { $0 + $1 * $1 } / Float(samples.count))
}

/// Deterministic noise in ±`amplitude`, never exactly zero, so a sample out of place is visible.
private func noise(frames: Int, channelCount: Int, seed: UInt64, amplitude: Float = 0.5) -> [Float] {
    var state = seed &* 6364136223846793005 &+ 1442695040888963407
    return (0..<frames * channelCount).map { _ in
        state = state &* 6364136223846793005 &+ 1442695040888963407
        let value = (Float(Double(state >> 11) / Double(1 << 53)) * 2 - 1) * amplitude
        return value == 0 ? amplitude / 3 : value
    }
}
