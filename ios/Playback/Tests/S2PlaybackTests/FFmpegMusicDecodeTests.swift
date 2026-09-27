import XCTest
@testable import S2Playback
import S2PlaybackTestSupport

/// The formats S2 adds over Podcasts decode through the FFmpeg build (`ios/scripts/build-ffmpeg.sh`),
/// and the decoder converts them to the engine's output format. Fixtures were made with the
/// ffmpeg CLI; see ios/Playback/README.md.
final class FFmpegMusicDecodeTests: XCTestCase {

    private func fixture(_ name: String, _ ext: String) throws -> URL {
        try XCTUnwrap(LoopbackMediaServer.fixtureURL(name, withExtension: ext))
    }

    /// Every frame of `source`, interleaved stereo.
    private func decodeAll(_ source: TrackPCMSource) throws -> [Float] {
        var out: [Float] = []
        var buffer = [Float](repeating: 0, count: 4096 * 2)
        while true {
            let frames = try buffer.withUnsafeMutableBufferPointer { try source.read(into: $0.baseAddress!, maxFrames: 4096) }
            if frames == 0 { return out }
            out.append(contentsOf: buffer[0..<(frames * 2)])
        }
    }

    private func rms(_ samples: [Float]) -> Float {
        sqrt(samples.reduce(0) { $0 + $1 * $1 } / Float(max(samples.count, 1)))
    }

    func testDecodesFLAC() throws {
        let decoder = FFmpegStreamDecoder(reader: try FileByteReader(url: try fixture("tone-44k", "flac")))
        let format = try decoder.open()
        XCTAssertEqual(format.codec, "flac")
        XCTAssertEqual(format.sampleRate, 44_100)
        XCTAssertEqual(format.channelCount, 2)

        let source = FFmpegTrackSource(url: try fixture("tone-44k", "flac"))
        let duration = try source.open(sampleRate: 44_100, channelCount: 2)
        XCTAssertEqual(duration, 22_050)
        let pcm = try decodeAll(source)
        XCTAssertEqual(pcm.count / 2, 22_050, "lossless: every frame, no padding")
        XCTAssertEqual(pcm.map(abs).max() ?? 0, 0.5, accuracy: 0.01)
    }

    func testDecodesOpus() throws {
        let decoder = FFmpegStreamDecoder(reader: try FileByteReader(url: try fixture("tone-48k", "opus")))
        let format = try decoder.open()
        XCTAssertEqual(format.codec, "opus")
        XCTAssertEqual(format.sampleRate, 48_000)

        let source = FFmpegTrackSource(url: try fixture("tone-48k", "opus"))
        _ = try source.open(sampleRate: 48_000, channelCount: 2)
        let pcm = try decodeAll(source)
        // Pre-skip and end trimming applied: one second, give or take a packet.
        XCTAssertEqual(Double(pcm.count / 2), 48_000, accuracy: 960)
        // A 0.5-amplitude sine: RMS 0.354.
        XCTAssertEqual(rms(pcm), 0.354, accuracy: 0.03)
    }

    func testResamplesOpusTo44100() throws {
        let source = FFmpegTrackSource(url: try fixture("tone-48k", "opus"))
        _ = try source.open(sampleRate: 44_100, channelCount: 2)
        let native = FFmpegTrackSource(url: try fixture("tone-48k", "opus"))
        _ = try native.open(sampleRate: 48_000, channelCount: 2)
        let resampled = try decodeAll(source).count / 2
        let original = try decodeAll(native).count / 2
        XCTAssertEqual(Double(resampled), Double(original) * 44_100 / 48_000, accuracy: 2)
    }

    func testSeekIsFrameExact() throws {
        let url = try fixture("tone-44k", "flac")
        let whole = FFmpegTrackSource(url: url)
        _ = try whole.open(sampleRate: 44_100, channelCount: 2)
        let all = try decodeAll(whole)

        let seeking = FFmpegTrackSource(url: url)
        _ = try seeking.open(sampleRate: 44_100, channelCount: 2)
        try seeking.seek(toFrame: 10_000)
        var buffer = [Float](repeating: 0, count: 2_000 * 2)
        var got = 0
        while got < 2_000 {
            let n = try buffer.withUnsafeMutableBufferPointer {
                try seeking.read(into: $0.baseAddress! + got * 2, maxFrames: 2_000 - got)
            }
            XCTAssertGreaterThan(n, 0)
            if n == 0 { break }
            got += n
        }
        XCTAssertEqual(buffer, Array(all[(10_000 * 2)..<(12_000 * 2)]))
    }

    func testMonoIsSpreadToBothSidesAtFullLevel() throws {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("s2-mono-\(UUID().uuidString).wav")
        defer { try? FileManager.default.removeItem(at: url) }
        // One channel of 16-bit 0.25 DC.
        let frames = 4_800
        var wav = Data()
        func append<T: FixedWidthInteger>(_ v: T) { withUnsafeBytes(of: v.littleEndian) { wav.append(contentsOf: $0) } }
        wav.append(contentsOf: Array("RIFF".utf8)); append(UInt32(36 + frames * 2))
        wav.append(contentsOf: Array("WAVE".utf8))
        wav.append(contentsOf: Array("fmt ".utf8)); append(UInt32(16)); append(UInt16(1)); append(UInt16(1))
        append(UInt32(48_000)); append(UInt32(96_000)); append(UInt16(2)); append(UInt16(16))
        wav.append(contentsOf: Array("data".utf8)); append(UInt32(frames * 2))
        for _ in 0..<frames { append(Int16(8192)) }
        try wav.write(to: url)

        let source = FFmpegTrackSource(url: url)
        _ = try source.open(sampleRate: 48_000, channelCount: 2)
        let pcm = try decodeAll(source)
        XCTAssertEqual(pcm.count / 2, frames)
        XCTAssertEqual(pcm[100], 0.25, accuracy: 1e-4)
        XCTAssertEqual(pcm[101], 0.25, accuracy: 1e-4)
    }
}
