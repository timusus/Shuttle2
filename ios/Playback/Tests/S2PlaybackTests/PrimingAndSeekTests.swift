import Accelerate
import XCTest
@testable import S2Playback
import S2PlaybackTestSupport

/// Where each decoded frame came from, for the lossy codecs whose encoders add frames: AAC in MP4,
/// MP3 and Opus. Each fixture is 2 s of a linear chirp, 300 Hz rising at 1,350 Hz/s:
///
///     x(n) = 0.5 sin(2π (300 t + 675 t²)),  t = n / rate
///
/// Its frequency, and so its phase, is different at every sample, so cross-correlating a window of
/// decoded audio with the formula finds the source frame it starts at, to the sample, whatever the
/// codec did to the waveform. That proves:
///
/// - **priming** (encoder delay) is trimmed at the start, and again after a seek back to 0:00;
/// - **end padding** is trimmed: exactly 2 s of frames, no more;
/// - **seeks** land on exactly the frame asked for.
///
/// Made with (`chirp` is the formula above as an `aevalsrc` at the rate given):
///
///     ffmpeg -f lavfi -i "$(chirp 44100)" -c:a aac -b:a 128k chirp-44k-aac.m4a
///     ffmpeg -f lavfi -i "$(chirp 44100)" -c:a libmp3lame -b:a 128k chirp-44k.mp3
///     ffmpeg -f lavfi -i "$(chirp 48000)" -c:a libopus -b:a 64k chirp-48k.opus
final class PrimingAndSeekTests: XCTestCase {

    private struct Fixture {
        let name: String
        let ext: String
        let rate: Double
        var frames: Int { Int(2 * rate) }
    }

    private let aac = Fixture(name: "chirp-44k-aac", ext: "m4a", rate: 44_100)
    private let mp3 = Fixture(name: "chirp-44k", ext: "mp3", rate: 44_100)
    private let opus = Fixture(name: "chirp-48k", ext: "opus", rate: 48_000)

    // MARK: Priming

    func testAACStartTrimsPriming() throws { try assertStartsAtFrameZero(aac) }
    func testMP3StartTrimsPriming() throws { try assertStartsAtFrameZero(mp3) }
    func testOpusStartTrimsPreSkip() throws { try assertStartsAtFrameZero(opus) }

    // MARK: End padding

    func testAACInMP4TrimsEndPadding() throws { try assertFrameCount(aac) }
    func testMP3TrimsEndPadding() throws { try assertFrameCount(mp3) }
    func testOpusTrimsEndPadding() throws { try assertFrameCount(opus) }

    // MARK: Seek

    func testAACSeekIsSampleExact() throws { try assertSeeksExactly(aac) }
    /// Not exact: FFmpeg's MP3 seek (the Xing TOC, or scaling by the bitrate for CBR) stamps the
    /// frame it syncs to with the time asked for, so the landing is off by up to one MP3 frame
    /// (1,152 frames, 26 ms). A seek to the start is exact. Exact MP3 seeking needs an index of
    /// frame offsets, built by walking the file.
    func testMP3SeekIsWithinOneFrame() throws { try assertSeeksExactly(mp3, tolerance: 1152) }
    func testOpusSeekIsSampleExact() throws { try assertSeeksExactly(opus) }

    func testAACSeekBackToStartTrimsPriming() throws { try assertSeekBackToStart(aac) }
    func testMP3SeekBackToStartTrimsPriming() throws { try assertSeekBackToStart(mp3) }
    func testOpusSeekBackToStartTrimsPreSkip() throws { try assertSeekBackToStart(opus) }

    // MARK: -

    private func assertStartsAtFrameZero(_ fixture: Fixture, file: StaticString = #filePath, line: UInt = #line) throws {
        let source = try open(fixture)
        let pcm = try read(source, frames: 8192)
        // The first few hundred frames of a lossy decode are the codec settling; frame 1,024 on is
        // the signal.
        XCTAssertEqual(try sourceFrame(of: pcm, at: 1024, fixture), 1024, "first frame is source frame 0",
                       file: file, line: line)
    }

    private func assertFrameCount(_ fixture: Fixture, file: StaticString = #filePath, line: UInt = #line) throws {
        let source = try open(fixture)
        let pcm = try read(source, frames: .max)
        XCTAssertEqual(pcm.count / 2, fixture.frames, "every source frame, and nothing after", file: file, line: line)
        XCTAssertEqual(try sourceFrame(of: pcm, at: fixture.frames - 4096, fixture), fixture.frames - 4096,
                       "the tail is the source's tail", file: file, line: line)
    }

    private func assertSeeksExactly(_ fixture: Fixture, tolerance: Int = 0,
                                    file: StaticString = #filePath, line: UInt = #line) throws {
        let source = try open(fixture)
        _ = try read(source, frames: 4096)
        // Forward, back, and across packet boundaries of every codec here (1,024, 1,152 and 960).
        for target in [61_234, 4_410, 30_001, 1_152, 77_777] {
            try source.seek(toFrame: Int64(target))
            let pcm = try read(source, frames: 4096)
            let landed = try sourceFrame(of: pcm, at: 0, near: target, fixture)
            XCTAssertLessThanOrEqual(abs(landed - target), tolerance, "seek to \(target) landed at \(landed)",
                                     file: file, line: line)
        }
    }

    private func assertSeekBackToStart(_ fixture: Fixture, file: StaticString = #filePath, line: UInt = #line) throws {
        let source = try open(fixture)
        _ = try read(source, frames: 20_000)
        try source.seek(toFrame: 0)
        let pcm = try read(source, frames: .max)
        XCTAssertEqual(try sourceFrame(of: pcm, at: 1024, fixture), 1024, "after a seek to 0, frame 0 is source frame 0",
                       file: file, line: line)
        XCTAssertEqual(pcm.count / 2, fixture.frames, "every frame again, and no priming", file: file, line: line)
    }

    private func open(_ fixture: Fixture) throws -> FFmpegTrackSource {
        let url = try XCTUnwrap(LoopbackMediaServer.fixtureURL(fixture.name, withExtension: fixture.ext))
        let source = FFmpegTrackSource(url: url)
        _ = try source.open(sampleRate: fixture.rate, channelCount: 2)
        return source
    }

    /// Up to `frames` frames, interleaved stereo.
    private func read(_ source: TrackPCMSource, frames: Int) throws -> [Float] {
        var out: [Float] = []
        var buffer = [Float](repeating: 0, count: 4096 * 2)
        while out.count / 2 < frames {
            let want = min(4096, frames - out.count / 2)
            let got = try buffer.withUnsafeMutableBufferPointer { try source.read(into: $0.baseAddress!, maxFrames: want) }
            if got == 0 { break }
            out.append(contentsOf: buffer[0..<(got * 2)])
        }
        return out
    }

    /// The source frame that decoded frame `index` (of `pcm`, the left channel) is, to the nearest
    /// sample, searched for within 3,000 frames of `near`: the lag at which a 2,048-frame window
    /// best matches the chirp, refined to a fraction of a sample by the parabola through the peak
    /// and its neighbours, then rounded.
    private func sourceFrame(of pcm: [Float], at index: Int, near: Int? = nil, _ fixture: Fixture) throws -> Int {
        let search = 3000
        let window = 2048
        let left = pcm.channel(0)
        guard index + window <= left.count else {
            XCTFail("only \(left.count) frames decoded; wanted \(index + window)")
            return -1
        }
        let decoded = Array(left[index..<(index + window)])
        let center = near ?? index
        let lowest = max(0, center - search)
        let highest = center + search
        let reference = (lowest..<(highest + window)).map { n -> Float in
            let t = Double(n) / fixture.rate
            return Float(0.5 * sin(2 * .pi * (300 * t + 675 * t * t)))
        }
        let scores = reference.withUnsafeBufferPointer { ref in
            (0...(highest - lowest)).map { lag -> Float in
                var dot: Float = 0
                vDSP_dotpr(decoded, 1, ref.baseAddress! + lag, 1, &dot, vDSP_Length(window))
                return dot
            }
        }
        let peak = scores.indices.max { scores[$0] < scores[$1] }!
        var offset = 0.0
        if peak > 0, peak < scores.count - 1 {
            let (a, b, c) = (Double(scores[peak - 1]), Double(scores[peak]), Double(scores[peak + 1]))
            let denominator = a - 2 * b + c
            if denominator != 0 { offset = 0.5 * (a - c) / denominator }
        }
        return lowest + peak + Int(offset.rounded())
    }
}
