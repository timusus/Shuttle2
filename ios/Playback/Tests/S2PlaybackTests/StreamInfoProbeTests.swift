import XCTest
import PlaybackDecode
@testable import S2Playback
import S2PlaybackTestSupport

/// #822: FLAC, ALAC and WAV skip `avformat_find_stream_info` (the header already describes them);
/// everything else still probes. The skip path must decode exactly what the probe path does.
final class StreamInfoProbeTests: XCTestCase {

    private func fixtureURL(_ name: String, _ ext: String) throws -> URL {
        try XCTUnwrap(LoopbackMediaServer.fixtureURL(name, withExtension: ext))
    }

    private func open(_ url: URL, forcesProbe: Bool) throws -> (FFmpegStreamDecoder, StreamAudioFormat) {
        let decoder = FFmpegStreamDecoder(reader: try FileByteReader(url: url), forcesProbe: forcesProbe)
        return (decoder, try decoder.open())
    }

    private func firstSamples(_ decoder: FFmpegStreamDecoder, channels: Int, frames: Int = 8192) -> [Float] {
        var buffer = [Float](repeating: 0, count: frames * channels)
        let n = buffer.withUnsafeMutableBufferPointer { decoder.read(into: $0.baseAddress!, maxFrames: frames) }
        return Array(buffer[0..<(n * channels)])
    }

    private func assertSkipMatchesProbe(_ name: String, _ ext: String, file: StaticString = #filePath, line: UInt = #line) throws {
        let url = try fixtureURL(name, ext)
        let (skipping, skipFormat) = try open(url, forcesProbe: false)
        let (probing, probeFormat) = try open(url, forcesProbe: true)
        XCTAssertTrue(skipping.skippedProbe, "\(name).\(ext) should skip the probe", file: file, line: line)
        XCTAssertFalse(probing.skippedProbe, file: file, line: line)
        XCTAssertEqual(skipFormat.sampleRate, probeFormat.sampleRate, file: file, line: line)
        XCTAssertEqual(skipFormat.channelCount, probeFormat.channelCount, file: file, line: line)
        XCTAssertEqual(skipFormat.codec, probeFormat.codec, file: file, line: line)
        XCTAssertEqual(skipFormat.container, probeFormat.container, file: file, line: line)
        let skipDuration = try XCTUnwrap(skipFormat.duration, file: file, line: line)
        XCTAssertGreaterThan(skipDuration, 0, file: file, line: line)
        XCTAssertEqual(skipDuration, try XCTUnwrap(probeFormat.duration, file: file, line: line), accuracy: 0.001,
                       file: file, line: line)
        let channels = skipFormat.channelCount
        let skipped = firstSamples(skipping, channels: channels)
        XCTAssertFalse(skipped.isEmpty, file: file, line: line)
        XCTAssertEqual(skipped, firstSamples(probing, channels: channels), file: file, line: line)
    }

    func testFLACSkipsProbeAndMatches() throws { try assertSkipMatchesProbe("tone-44k", "flac") }

    func testALACSkipsProbeAndMatches() throws { try assertSkipMatchesProbe("tone-44k-alac", "m4a") }

    func testWAVSkipsProbeAndMatches() throws { try assertSkipMatchesProbe("tone-48k-s24", "wav") }

    func testMP3StillProbes() throws {
        let (decoder, _) = try open(try fixtureURL("tone", "mp3"), forcesProbe: false)
        XCTAssertFalse(decoder.skippedProbe)
    }

    func testAACInMP4StillProbes() throws {
        let (decoder, _) = try open(try fixtureURL("chirp-44k-aac", "m4a"), forcesProbe: false)
        XCTAssertFalse(decoder.skippedProbe)
    }

    func testOpusStillProbes() throws {
        let (decoder, _) = try open(try fixtureURL("tone-48k", "opus"), forcesProbe: false)
        XCTAssertFalse(decoder.skippedProbe)
    }
}
