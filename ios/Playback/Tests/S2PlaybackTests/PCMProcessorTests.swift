import XCTest
@testable import S2Playback

final class PCMProcessorTests: XCTestCase {

    /// The limiter's delay is hidden: what goes in comes out, frame for frame, once drained.
    func testLimiterLatencyIsCompensated() {
        let processor = PCMProcessor(sampleRate: 48_000, channelCount: 2)
        XCTAssertGreaterThan(processor.latencyFrames, 0)
        var input = TestSignal.noise(frames: 1_000, seed: 20)
        let expected = input
        var output: [Float] = []
        input.withUnsafeMutableBufferPointer { buffer in
            processor.process(buffer.baseAddress!, frameCount: 600, gain: 1, into: &output)
            processor.process(buffer.baseAddress! + 1_200, frameCount: 400, gain: 1, into: &output)
        }
        processor.drain(into: &output)
        XCTAssertEqual(output, expected)
    }

    /// A stream shorter than the lookahead still comes out whole.
    func testShortStreamDrainsExactly() {
        let processor = PCMProcessor(sampleRate: 48_000, channelCount: 2)
        var input = TestSignal.noise(frames: 10, seed: 21)
        let expected = input
        var output: [Float] = []
        input.withUnsafeMutableBufferPointer { processor.process($0.baseAddress!, frameCount: 10, gain: 1, into: &output) }
        processor.drain(into: &output)
        XCTAssertEqual(output, expected)
    }

    func testFlatBandsArePassThrough() {
        let processor = PCMProcessor(sampleRate: 48_000, channelCount: 2)
        processor.setLimiter(LimiterSettings(enabled: false))
        processor.setEqualizer(EqualizerSettings(enabled: true, preampDb: 0, coefficients: [1, 0, 0, 0, 0, 1, 0, 0, 0, 0]))
        var input = TestSignal.noise(frames: 500, seed: 22)
        let expected = input
        var output: [Float] = []
        input.withUnsafeMutableBufferPointer { processor.process($0.baseAddress!, frameCount: 500, gain: 1, into: &output) }
        XCTAssertEqual(output, expected)
    }
}

private enum TestSignal {
    /// Deterministic stereo noise in ±`amplitude`, never exactly zero, so a sample out of place or a frame inserted is
    /// visible.
    static func noise(frames: Int, seed: UInt64, amplitude: Float = 0.5) -> [Float] {
        var state = seed &* 6364136223846793005 &+ 1442695040888963407
        var out = [Float](repeating: 0, count: frames * 2)
        for i in 0..<out.count {
            state = state &* 6364136223846793005 &+ 1442695040888963407
            let unit = Float(Double(state >> 11) / Double(1 << 53))
            var value = (unit * 2 - 1) * amplitude
            if value == 0 { value = amplitude / 3 }
            out[i] = value
        }
        return out
    }
}
