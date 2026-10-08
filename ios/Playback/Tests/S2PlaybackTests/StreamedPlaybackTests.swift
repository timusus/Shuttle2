import AVFoundation
import PlaybackDecode
import PlaybackStreaming
import PlaybackStreamingTestSupport
import S2PlaybackTestSupport
import XCTest
@testable import S2Playback

/// A server's stream played through shuttle-playback's growing-file source (#958): rendered offline from a loopback
/// server and compared sample for sample with the same file decoded locally.
final class StreamedPlaybackTests: XCTestCase {

    private let rate = 48_000.0
    /// A test WAV's header; 4 bytes a frame follow it.
    private let headerBytes = 44
    private var files: [URL] = []
    private var servers: [PlaybackStreamingTestSupport.LoopbackMediaServer] = []

    override func tearDown() {
        servers.forEach { $0.stop() }
        files.forEach { try? FileManager.default.removeItem(at: $0) }
        super.tearDown()
    }

    private func makeController() throws -> (MusicPlaybackController, CallbackLog) {
        let log = CallbackLog()
        let controller = try MusicPlaybackController(
            outputSampleRate: rate,
            renderingMode: .offline(maximumFrameCount: 4096),
            scheduleAheadSeconds: 0.5,
            callbackQueue: log.queue
        )
        log.attach(to: controller)
        return (controller, log)
    }

    /// A 48 kHz stereo WAV of `seconds`.
    private func sine(seconds: Double) throws -> URL {
        let file = try TestSignal.writeSineWAV(sampleRate: 48_000, seconds: seconds, frequency: 440, amplitude: 0.4)
        files.append(file)
        return file
    }

    /// `file` served over loopback, with its bytes and its PCM decoded locally.
    private func served(_ file: URL) throws -> (PlaybackStreamingTestSupport.LoopbackMediaServer, Data, [Float]) {
        let body = try Data(contentsOf: file)
        let server = try PlaybackStreamingTestSupport.LoopbackMediaServer(body: body, mimeType: "audio/mpeg")
        servers.append(server)
        return (server, body, try decodeAll(FFmpegTrackSource(url: file)))
    }

    private func decodeAll(_ source: FFmpegTrackSource) throws -> [Float] {
        _ = try source.open(sampleRate: rate, channelCount: 2)
        var out: [Float] = []
        var buffer = [Float](repeating: 0, count: 4096 * 2)
        while true {
            let frames = try buffer.withUnsafeMutableBufferPointer { try source.read(into: $0.baseAddress!, maxFrames: 4096) }
            if frames == 0 { return out }
            out.append(contentsOf: buffer[0..<(frames * 2)])
        }
    }

    private func streamed(_ uid: String, _ url: URL, store: GrowingFileStore = .temporary()) -> PlaybackTrack {
        PlaybackTrack(uid: uid, gainDb: 0) { FFmpegTrackSource(url: url, store: store) }
    }

    private func assertRendered(_ out: (left: [Float], right: [Float]), _ expected: ArraySlice<Float>,
                                file: StaticString = #filePath, line: UInt = #line) {
        let interleaved = zip(out.left, out.right).flatMap { [$0, $1] }
        let wanted = Array(expected)
        XCTAssertEqual(interleaved.count, wanted.count, "frame count", file: file, line: line)
        if let i = zip(interleaved, wanted).enumerated().first(where: { $0.element.0 != $0.element.1 })?.offset {
            XCTFail("sample \(i): got \(interleaved[i]), expected \(wanted[i])", file: file, line: line)
        }
    }

    private func waitUntil(_ condition: () -> Bool, file: StaticString = #filePath, line: UInt = #line) {
        let deadline = Date().addingTimeInterval(5)
        while !condition() && Date() < deadline { Thread.sleep(forTimeInterval: 0.01) }
        XCTAssertTrue(condition(), "timed out", file: file, line: line)
    }

    func testAStreamPlaysThroughAndIsKeptForTheNextPlay() throws {
        let (server, _, pcm) = try served(sine(seconds: 1))
        let store = GrowingFileStore.temporary()
        let (controller, log) = try makeController()
        controller.load(current: streamed("A", server.url, store: store), next: nil, playWhenReady: true)
        controller.syncForTesting()

        let out = try OfflineRenderer(controller: controller, slice: 512).render(frames: 48_000)

        assertRendered(out, pcm[...])
        XCTAssertEqual(log.failures, [])
        waitUntil { store.completedFile(for: StreamCacheKey.stableURL(for: server.url)) != nil }
    }

    /// A seek into what has already downloaded is read from the file, with no request.
    func testASeekWithinTheDownloadIsFrameExact() throws {
        let (server, body, pcm) = try served(sine(seconds: 2))
        let (controller, log) = try makeController()
        controller.load(current: streamed("A", server.url), next: nil, playWhenReady: true)
        controller.syncForTesting()
        let renderer = OfflineRenderer(controller: controller, slice: 512)
        _ = try renderer.render(frames: 9_600)
        waitUntil { server.servedBytes == Int64(body.count) }

        controller.seek(toMs: 1_000)
        controller.syncForTesting()
        let out = try renderer.render(frames: 9_600)

        assertRendered(out, pcm[(48_000 * 2)..<(57_600 * 2)])
        XCTAssertEqual(server.requestedRanges, [0])
        XCTAssertEqual(log.failures, [])
        XCTAssertEqual(log.seeksUnsupported, [])
    }

    /// A seek past what has arrived on a link slower than the audio asks for the range it needs.
    func testASeekPastTheFrontierRequestsItsRange() throws {
        let (server, _, pcm) = try served(sine(seconds: 2))
        server.bytesPerSecond = 96_000
        let (controller, log) = try makeController()
        controller.load(current: streamed("A", server.url), next: nil, playWhenReady: true)
        controller.syncForTesting()
        let renderer = OfflineRenderer(controller: controller, slice: 512)
        _ = try renderer.render(frames: 9_600)

        controller.seek(toMs: 1_500)
        controller.syncForTesting()
        let out = try renderer.render(frames: 9_600)

        assertRendered(out, pcm[(72_000 * 2)..<(81_600 * 2)])
        // The source starts a little short of the seek's offset, never at the frontier it left.
        XCTAssertEqual(server.requestedRanges.count, 2, "\(server.requestedRanges)")
        XCTAssertGreaterThan(server.requestedRanges.last ?? 0, Int64(headerBytes + 48_000 * 4))
        XCTAssertLessThanOrEqual(server.requestedRanges.last ?? .max, Int64(headerBytes + 72_000 * 4))
        XCTAssertEqual(log.failures, [])
    }

    /// A transcode made as it streams: no length, and a range is answered with the whole body from the start. A seek
    /// is reported as one it can't make, never as a failure, and playback carries on (#606).
    func testASeekOnARangeIgnoringTranscodeIsUnsupportedNotAFailure() throws {
        let (server, _, pcm) = try served(XCTUnwrap(S2PlaybackTestSupport.LoopbackMediaServer.fixtureURL("tone", withExtension: "mp3")))
        server.respondsWholeBodyIgnoringRange = true
        server.omitsContentLength = true
        // Still arriving, as a transcode is while it is made: at its end the length is known.
        server.stallsAfterBodyBytes = 64 * 1024
        let (controller, log) = try makeController()
        controller.load(current: streamed("A", server.url), next: nil, playWhenReady: true)
        controller.syncForTesting()
        let renderer = OfflineRenderer(controller: controller, slice: 512)
        _ = try renderer.render(frames: 9_600)

        controller.seek(toMs: 800)
        controller.syncForTesting()
        let out = try renderer.render(frames: 9_600)

        assertRendered(out, pcm[(9_600 * 2)..<(19_200 * 2)])
        XCTAssertEqual(log.seeksUnsupported, ["A 800"])
        XCTAssertEqual(log.failures, [])
    }

    /// A transcode's estimated length can promise bytes the server never sends: a seek past the length is refused as
    /// unseekable, which the engine reports as a seek it can't make (#950).
    func testASeekPastTheStreamsLengthIsRefusedAsUnseekable() throws {
        let (server, body, _) = try served(sine(seconds: 1))
        let reader = StreamedTrackReader(GrowingFileByteSource(url: server.url, authHeaders: [:], store: .temporary())) {}
        defer { reader.cancel() }
        var byte: UInt8 = 0
        XCTAssertEqual(try reader.read(into: &byte, maxLength: 1), 1)
        XCTAssertEqual(reader.totalLength, Int64(body.count))

        XCTAssertThrowsError(try reader.seek(to: Int64(body.count) + 1)) {
            XCTAssertEqual($0 as? StreamByteReaderError, .unseekable)
        }
        XCTAssertNoThrow(try reader.seek(to: Int64(body.count)))
    }

    /// A read waiting on a slow server says so every second, so the engine sees the stall from inside it (#897).
    func testAReadThatWaitsSaysSo() throws {
        let (server, _, _) = try served(sine(seconds: 1))
        server.delayForOffsetZero = 1.5
        let waits = NSLock()
        var waited = 0
        let reader = StreamedTrackReader(GrowingFileByteSource(url: server.url, authHeaders: [:], store: .temporary())) {
            waits.withLock { waited += 1 }
        }
        defer { reader.cancel() }
        var byte: UInt8 = 0

        XCTAssertEqual(try reader.read(into: &byte, maxLength: 1), 1)

        XCTAssertEqual(waits.withLock { waited }, 1)
    }

    /// Servers put a per-play session id or a token in the stream's URL: a new one is the same stream, played from
    /// the kept file without a request.
    func testANewTokenPlaysTheKeptFile() throws {
        let (server, _, pcm) = try served(sine(seconds: 1))
        let store = GrowingFileStore.temporary()
        func withToken(_ token: String) -> URL {
            var components = URLComponents(url: server.url, resolvingAgainstBaseURL: false)!
            components.queryItems = [URLQueryItem(name: "api_key", value: token), URLQueryItem(name: "PlaySessionId", value: token)]
            return components.url!
        }
        let first = FFmpegTrackSource(url: withToken("one"), store: store)
        XCTAssertEqual(try decodeAll(first), pcm)
        first.cancel()
        waitUntil { store.completedFile(for: StreamCacheKey.stableURL(for: withToken("one"))) != nil }
        let requests = server.requestHeads.count

        let second = FFmpegTrackSource(url: withToken("two"), store: store)
        defer { second.cancel() }
        XCTAssertEqual(try decodeAll(second), pcm)

        XCTAssertEqual(server.requestHeads.count, requests, "the second play made a request")
    }
}
