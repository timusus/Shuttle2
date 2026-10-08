import XCTest
import PlaybackDecode
@testable import S2Playback
import S2PlaybackTestSupport

/// The formats S2 adds over Podcasts decode through shuttle-playback's FFmpeg build,
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

    /// A server's progressive transcode has no length and answers no range: the source says it
    /// can't seek, and still plays. The same body served with a length can (#606).
    func testAStreamWithoutALengthIsNotSeekable() throws {
        let body = try Data(contentsOf: try fixture("tone", "mp3"))
        for withoutLength in [true, false] {
            let server = try LoopbackMediaServer(body: body, mimeType: "audio/mpeg")
            defer { server.stop() }
            server.streamsWithoutLength = withoutLength
            // Still arriving, as a transcode is while it is made: at its end the length is known.
            server.stallsAfterBodyBytes = 64 * 1024
            let source = FFmpegTrackSource(url: server.url)
            defer { source.cancel() }
            _ = try source.open(sampleRate: 48_000, channelCount: 2)
            XCTAssertEqual(source.isSeekable, !withoutLength, "withoutLength \(withoutLength)")
            var buffer = [Float](repeating: 0, count: 4096 * 2)
            let frames = try buffer.withUnsafeMutableBufferPointer { try source.read(into: $0.baseAddress!, maxFrames: 4096) }
            XCTAssertGreaterThan(frames, 0)
        }
    }

    /// A forward-only stream (a transcode streamed as it is made) whose length turns up while it
    /// plays: the track looks seekable then, but the reader refuses a seek behind what it has
    /// served. The decoder's `.unseekable` makes the track report itself unseekable, and reads end
    /// instead of crashing.
    func testAForwardOnlyReaderMakesTheTrackUnseekable() throws {
        let url = try fixture("tone", "mp3")
        let reader = ForwardOnlyByteReader(try Data(contentsOf: url))
        let source = FFmpegTrackSource(url: url) { reader }
        _ = try source.open(sampleRate: 44_100, channelCount: 2)
        XCTAssertFalse(source.isSeekable, "no length yet")
        var buffer = [Float](repeating: 0, count: 4096 * 2)
        // Far enough that the start has left libavformat's read buffer, so the seek reaches the reader.
        while reader.position < 100_000 {
            let frames = try buffer.withUnsafeMutableBufferPointer { try source.read(into: $0.baseAddress!, maxFrames: 4096) }
            XCTAssertGreaterThan(frames, 0)
        }
        reader.knowsLength = true
        XCTAssertTrue(source.isSeekable)
        XCTAssertThrowsError(try source.seek(toFrame: 22_050))
        XCTAssertFalse(source.isSeekable)
        do {
            let frames = try buffer.withUnsafeMutableBufferPointer { try source.read(into: $0.baseAddress!, maxFrames: 4096) }
            XCTAssertEqual(frames, 0)
        } catch {
            XCTAssertNotEqual(error as? TrackSourceError, .cancelled)
        }
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

/// Refuses any seek behind its read position; reports its length only once `knowsLength`.
private final class ForwardOnlyByteReader: StreamByteReader {
    private let data: Data
    private(set) var position: Int64 = 0
    var knowsLength = false

    init(_ data: Data) { self.data = data }

    var totalLength: Int64? { knowsLength ? Int64(data.count) : nil }

    func read(into buffer: UnsafeMutableRawPointer, maxLength: Int) throws -> Int {
        let take = min(Int(Int64(data.count) - position), maxLength)
        if take <= 0 { return 0 }
        data.withUnsafeBytes { memcpy(buffer, $0.baseAddress!.advanced(by: Int(position)), take) }
        position += Int64(take)
        return take
    }

    func seek(to offset: Int64) throws {
        guard offset >= position else { throw StreamByteReaderError.unseekable }
        position = offset
    }

    func cancel() {}
    func interrupt() {}
    func clearInterrupt() {}
}
