import PlaybackRender
import XCTest
@testable import S2Playback

final class ReplayGainProcessorTests: XCTestCase {

    func testEachItemPlaysAtItsOwnGain() throws {
        let processor = try configured(ReplayGainProcessor())
        processor.setMode(.track, preampDb: 0)
        processor.setTags(track: -6, album: nil, for: 1)
        processor.setTags(track: 6, album: nil, for: 2)

        processor.flush(StreamMetadata(item: 1, mediaStartFrame: 0))
        XCTAssertEqual(processed([0.5, -0.5], through: processor), [0.25059, -0.25059], accuracy: 0.0001)

        processor.flush(StreamMetadata(item: 2, mediaStartFrame: 0))
        XCTAssertEqual(processed([0.25, -0.25], through: processor), [0.49882, -0.49882], accuracy: 0.0001)
    }

    /// A mode or preamp change applies to the item playing, as on Android, ramping there over 5 ms (240 frames at
    /// 48 kHz) rather than jumping.
    func testAModeOrPreampChangeMidItemAppliesToTheItemPlaying() throws {
        let processor = try configured(ReplayGainProcessor())
        processor.setMode(.track, preampDb: 0)
        processor.setTags(track: -6, album: 0, for: 1)
        processor.flush(StreamMetadata(item: 1, mediaStartFrame: 0))
        XCTAssertEqual(processed([0.5], through: processor), [0.25059], accuracy: 0.0001)

        processor.setMode(.track, preampDb: -6)
        let ramp = processed([Float](repeating: 0.5, count: 480), through: processor)
        XCTAssertEqual(ramp[0], 0.25059, accuracy: 0.001)
        XCTAssertEqual(zip(ramp, ramp.dropFirst()).filter { $1 > $0 }.count, 0)
        XCTAssertEqual(Array(ramp[240...]), [Float](repeating: 0.12559, count: 240), accuracy: 0.0001)

        processor.setMode(.album, preampDb: -6)
        let album = processed([Float](repeating: 0.5, count: 480), through: processor)
        XCTAssertEqual(Array(album[240...]), [Float](repeating: 0.25059, count: 240), accuracy: 0.0001)
    }

    /// Android's `replayGainDb`: the preamp applies with the mode off, and a missing tag falls back to the other one.
    func testTheGainFollowsAndroidsRule() throws {
        let processor = try configured(ReplayGainProcessor())
        processor.setTags(track: 6, album: nil, for: 1)

        processor.setMode(.off, preampDb: -6)
        processor.flush(StreamMetadata(item: 1, mediaStartFrame: 0))
        XCTAssertEqual(processed([0.5], through: processor), [0.25059], accuracy: 0.0001)

        processor.setMode(.album, preampDb: 0)
        processor.flush(StreamMetadata(item: 1, mediaStartFrame: 0))
        XCTAssertEqual(processed([0.25], through: processor), [0.49882], accuracy: 0.0001)
    }

    func testOffOrUnknownPassesTheAudioThroughBitForBit() throws {
        let input: [Float] = [0.123456789, -0.987654321, 0.5, 1e-7]
        let processor = try configured(ReplayGainProcessor())
        processor.setTags(track: -6, album: -6, for: 1)

        processor.flush(StreamMetadata(item: 1, mediaStartFrame: 0))
        XCTAssertEqual(processed(input, through: processor), input)

        processor.setMode(.track, preampDb: 0)
        processor.flush(StreamMetadata(item: 2, mediaStartFrame: 0))
        XCTAssertEqual(processed(input, through: processor), input)
    }

    /// ReplayGain runs before the EQ, so the EQ's preamp stacks on it and the EQ's filters see the levelled signal.
    func testTheEqualizersPreampStacksOnTheItemsGain() throws {
        let replayGain = try configured(ReplayGainProcessor())
        replayGain.setMode(.track, preampDb: 0)
        replayGain.setTags(track: -6, album: nil, for: 1)
        let equalizer = EqualizerProcessor()
        _ = try equalizer.configure(PCMFormat(sampleRate: 48_000, channelCount: 1))
        equalizer.setEqualizer(enabled: true) { _ in EqualizerDesign(preampDb: -6, coefficients: [1, 0, 0, 0, 0]) }
        let item = StreamMetadata(item: 1, mediaStartFrame: 0)
        replayGain.flush(item)
        equalizer.flush(item)

        equalizer.queueInput(processed([0.8], through: replayGain))

        XCTAssertEqual(equalizer.getOutput().samples, [0.20095], accuracy: 0.0001)
    }
}

private func configured(_ processor: ReplayGainProcessor) throws -> ReplayGainProcessor {
    _ = try processor.configure(PCMFormat(sampleRate: 48_000, channelCount: 1))
    return processor
}

private func processed(_ input: [Float], through processor: AudioProcessor) -> [Float] {
    var samples = input
    samples.withUnsafeMutableBufferPointer { processor.process($0) }
    return samples
}

private func XCTAssertEqual(_ actual: [Float], _ expected: [Float], accuracy: Float, file: StaticString = #filePath, line: UInt = #line) {
    XCTAssertEqual(actual.count, expected.count, file: file, line: line)
    for (a, e) in zip(actual, expected) {
        XCTAssertEqual(a, e, accuracy: accuracy, file: file, line: line)
    }
}
