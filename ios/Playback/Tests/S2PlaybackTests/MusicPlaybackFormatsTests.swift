import AVFoundation
import XCTest
import PlaybackDecode
@testable import S2Playback
import S2PlaybackTestSupport

/// Every fixture format end to end, the way the app plays a file: `PlaybackTrack(uid:url:)` into
/// `MusicPlaybackController`, FFmpeg decoding and swresample converting to the engine's 48 kHz
/// stereo, rendered offline. The render is what a separate FFmpeg source decodes from the same
/// file, from its first frame (encoder delay trimmed) to its last, then silence, and the track ends
/// without a failure. Covers each demuxer and decoder
/// the build enables that a music library holds (AudioPlaybackKit's scripts/build-ffmpeg.sh).
final class MusicPlaybackFormatsTests: XCTestCase {

    private let rate = 48_000.0

    func testMP3() throws { try assertPlaysThrough("tone", "mp3", codec: "mp3", seconds: 20) }
    func testAACInMP4() throws { try assertPlaysThrough("tone_moov_last", "m4a", codec: "aac", seconds: 20) }
    func testALACInMP4() throws { try assertPlaysThrough("tone-44k-alac", "m4a", codec: "alac", seconds: 0.5) }
    func testFLAC() throws { try assertPlaysThrough("tone-44k", "flac", codec: "flac", seconds: 0.5) }
    func testOpusInOgg() throws { try assertPlaysThrough("tone-48k", "opus", codec: "opus", seconds: 1) }
    func testVorbisInOgg() throws { try assertPlaysThrough("tone-44k", "ogg", codec: "vorbis", seconds: 0.5) }
    func testWAV24Bit() throws { try assertPlaysThrough("tone-48k-s24", "wav", codec: "pcm_s24le", seconds: 0.25) }
    func testAIFF() throws { try assertPlaysThrough("tone-44k", "aiff", codec: "pcm_s16be", seconds: 0.25) }

    private func assertPlaysThrough(_ name: String, _ ext: String, codec: String, seconds: Double,
                                    file: StaticString = #filePath, line: UInt = #line) throws {
        let url = try XCTUnwrap(LoopbackMediaServer.fixtureURL(name, withExtension: ext), file: file, line: line)

        let probe = FFmpegStreamDecoder(reader: try FileByteReader(url: url))
        XCTAssertEqual(try probe.open().codec, codec, "demuxed and decoded by the expected codec", file: file, line: line)

        // What FFmpeg alone makes of the file at the engine's format.
        let reference = FFmpegTrackSource(url: url)
        _ = try reference.open(sampleRate: rate, channelCount: 2)
        var expected: [Float] = []
        var buffer = [Float](repeating: 0, count: 4096 * 2)
        while true {
            let frames = try buffer.withUnsafeMutableBufferPointer { try reference.read(into: $0.baseAddress!, maxFrames: 4096) }
            if frames == 0 { break }
            expected.append(contentsOf: buffer[0..<(frames * 2)])
        }
        let frames = expected.count / 2
        // Encoder delay and padding are the decoder's to trim; allow one lossy packet either way.
        XCTAssertEqual(Double(frames), seconds * rate, accuracy: 2048, "duration", file: file, line: line)
        let rms = sqrt(expected.reduce(0) { $0 + $1 * $1 } / Float(max(expected.count, 1)))
        XCTAssertGreaterThan(rms, 0.05, "decoded audio, not silence", file: file, line: line)

        let gainDb: Float = -6
        let log = CallbackLog()
        let controller = try MusicPlaybackController(
            outputSampleRate: rate,
            renderingMode: .offline(maximumFrameCount: 4096),
            scheduleAheadSeconds: 0.5,
            callbackQueue: log.queue
        )
        log.attach(to: controller)
        controller.load(current: PlaybackTrack(uid: name, url: url, gainDb: gainDb), next: nil, playWhenReady: true)
        controller.syncForTesting()
        let out = try OfflineRenderer(controller: controller, slice: 4096).render(frames: frames + 4096, log: log)
        controller.syncForTesting()

        // At -6 dB nothing reaches the limiter's ceiling (tone.mp3 and tone_moov_last.m4a peak at
        // 0 dBFS), so the chain is the ReplayGain multiply and nothing else.
        let scale = powf(10, gainDb / 20)
        let left = expected.channel(0).map { $0 * scale }
        let right = expected.channel(1).map { $0 * scale }
        if let mismatch = (0..<frames).first(where: { abs(out.left[$0] - left[$0]) > 1e-6 || abs(out.right[$0] - right[$0]) > 1e-6 }) {
            XCTFail("frame \(mismatch) of \(frames): got (\(out.left[mismatch]), \(out.right[mismatch])), "
                + "direct decode (\(left[mismatch]), \(right[mismatch]))", file: file, line: line)
        }
        XCTAssertTrue(out.left[frames...].allSatisfy { $0 == 0 }, "silence after the track", file: file, line: line)
        XCTAssertEqual(log.failures, [], file: file, line: line)
        XCTAssertEqual(log.states.last, .ended, file: file, line: line)
    }
}
