import AVFoundation
import XCTest
@testable import S2Playback
import S2PlaybackTestSupport

/// The `engine: ttfa` record: one per start, from the load or play to the node's first render (#687).
final class StartupTimingTests: XCTestCase {

    private let rate = 48_000.0

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

    private func servedTone() throws -> (LoopbackMediaServer, URL) {
        let file = try TestSignal.writeSineWAV(sampleRate: 48_000, seconds: 1, frequency: 440, amplitude: 0.4)
        defer { try? FileManager.default.removeItem(at: file) }
        let server = try LoopbackMediaServer(body: Data(contentsOf: file), mimeType: "audio/wav")
        return (server, server.url)
    }

    func testAStreamedLoadThatPlaysIsTimedToItsFirstRender() throws {
        let (server, url) = try servedTone()
        defer { server.stop() }
        let (controller, log) = try makeController()
        controller.load(current: PlaybackTrack(uid: "A", url: url), next: nil, playWhenReady: true)
        controller.syncForTesting()
        XCTAssertNil(controller.lastStartTimingForTesting, "logged before anything rendered")

        _ = try OfflineRenderer(controller: controller, slice: 512).render(frames: 1024, log: log)

        let timing = try XCTUnwrap(controller.lastStartTimingForTesting)
        XCTAssertEqual(timing.source, .streamed)
        XCTAssertEqual(timing.start, .fresh)
        XCTAssertEqual(timing.open, .opened)
        XCTAssertNil(timing.playAfterReadyMs)
        XCTAssertEqual(timing.firstResponse?.status, 206)
        XCTAssertEqual(timing.probe?.codec, "pcm_s16le")
        XCTAssertEqual(timing.transactions, 1)
        let stages = [timing.preOpenMs, timing.firstResponseMs, timing.probeMs, timing.firstBufferMs, timing.playMs, timing.renderMs]
        XCTAssertTrue(stages.allSatisfy { ($0 ?? -1) >= 0 }, "a stage missing or negative: \(stages)")
        XCTAssertGreaterThanOrEqual(try XCTUnwrap(timing.totalMs), try XCTUnwrap(timing.playMs))
        XCTAssertTrue(timing.logLine.hasPrefix("engine: ttfa total="))
    }

    /// The owner's output activation runs alongside the open; the record has when it returned and
    /// how long the engine start waited on it.
    func testALoadThatPlaysRecordsItsOutputActivation() throws {
        let (server, url) = try servedTone()
        defer { server.stop() }
        let (controller, log) = try makeController()
        controller.activateOutput = { true }
        controller.load(current: PlaybackTrack(uid: "A", url: url), next: nil, playWhenReady: true)
        controller.syncForTesting()

        _ = try OfflineRenderer(controller: controller, slice: 512).render(frames: 1024, log: log)

        let timing = try XCTUnwrap(controller.lastStartTimingForTesting)
        XCTAssertGreaterThanOrEqual(try XCTUnwrap(timing.sessionMs), 0)
        XCTAssertGreaterThanOrEqual(try XCTUnwrap(timing.sessionWaitMs), 0)
        XCTAssertTrue(timing.logLine.contains("session-wait="))
    }

    /// A load made paused and played once it's ready: one start, timed from the load.
    func testAPlayCloseBehindAPausedLoadIsTheLoadsStart() throws {
        let (server, url) = try servedTone()
        defer { server.stop() }
        let (controller, log) = try makeController()
        controller.load(current: PlaybackTrack(uid: "A", url: url), next: nil, playWhenReady: false)
        controller.play()
        controller.syncForTesting()

        _ = try OfflineRenderer(controller: controller, slice: 512).render(frames: 1024, log: log)

        let timing = try XCTUnwrap(controller.lastStartTimingForTesting)
        XCTAssertEqual(timing.open, .opened)
        XCTAssertNotNil(timing.playAfterReadyMs)
        XCTAssertNotNil(timing.firstResponseMs, "the load's open is in the record")
    }

    func testPlayingATrackPausedForAWhileIsAStartOfItsOwn() throws {
        let (controller, log) = try makeController()
        let samples = TestSignal.noise(frames: 48_000, seed: 3)
        controller.load(
            current: PlaybackTrack(uid: "A") { InMemoryTrackSource(samples: samples) }, next: nil, playWhenReady: false
        )
        controller.syncForTesting()
        Thread.sleep(forTimeInterval: 0.6)
        controller.play()
        controller.syncForTesting()

        _ = try OfflineRenderer(controller: controller, slice: 512).render(frames: 1024, log: log)

        let timing = try XCTUnwrap(controller.lastStartTimingForTesting)
        XCTAssertEqual(timing.source, .file)
        XCTAssertEqual(timing.open, .preopened)
        XCTAssertNil(timing.playAfterReadyMs)
        XCTAssertLessThan(try XCTUnwrap(timing.totalMs), 500)
    }

    func testAPauseBeforeTheFirstRenderLogsNothing() throws {
        let (controller, log) = try makeController()
        let samples = TestSignal.noise(frames: 48_000, seed: 4)
        controller.load(
            current: PlaybackTrack(uid: "A") { InMemoryTrackSource(samples: samples) }, next: nil, playWhenReady: true
        )
        controller.pause()
        controller.syncForTesting()
        _ = try OfflineRenderer(controller: controller, slice: 512).render(frames: 1024, log: log)
        XCTAssertNil(controller.lastStartTimingForTesting)
    }

    /// A pre-opened track's open was paid before the play: its stamps would read as negative stages.
    func testAPreopenedStartPrintsItsOpenStagesAsDashes() {
        var timing = StartupTiming(source: .streamed, start: .fresh, open: .preopened, playRequestedAt: 10)
        timing.apply(
            StartupTiming.OpenStats(
                startedAt: 2, finishedAt: 3, probe: StartupTiming.Probe(codec: "mp3", container: "mp3", bytes: 100),
                requestIssuedAt: 2.1, firstResponseAt: 2.5,
                firstResponse: StartupTiming.FirstResponse(status: 206, redirects: 0, hosts: []),
                transactions: 1
            )
        )
        timing.positionedAt = 10.001
        timing.firstBufferScheduledAt = 10.003
        timing.nodePlayedAt = 10.004
        timing.firstRenderedAt = 10.020

        XCTAssertEqual(timing.totalMs, 20)
        XCTAssertNil(timing.preOpenMs)
        XCTAssertNil(timing.firstResponseMs)
        XCTAssertNil(timing.probeMs)
        XCTAssertEqual(timing.probe?.codec, "mp3")
        let line = timing.logLine
        XCTAssertTrue(line.contains("open=preopened"), line)
        XCTAssertTrue(line.contains("first-response=- "), line)
        XCTAssertTrue(line.contains("render=16ms"), line)
    }
}

/// ``StartupTiming/Origin``: provider and transcode read off a stream URL for the `ttfa` line (#822).
final class StartupTimingOriginTests: XCTestCase {
    private func origin(_ url: String) -> StartupTiming.Origin { StartupTiming.Origin(url: URL(string: url)!) }

    func testPlexTranscodeAndDirectPlay() {
        XCTAssertEqual(
            origin("https://plex.local/music/:/transcode/universal/start.m3u8?session=u"),
            .init(provider: .plex, transcode: .yes)
        )
        XCTAssertEqual(
            origin("https://plex.local/library/parts/1/2/file.flac?X-Plex-Token=t"),
            .init(provider: .plex, transcode: .no)
        )
    }

    func testJellyfinAndEmbyUniversalAreServerDecided() {
        XCTAssertEqual(origin("https://jf.example.com/Audio/a/universal?ApiKey=t"), .init(provider: .jellyfin, transcode: .server))
        XCTAssertEqual(origin("http://emby.local/emby/Audio/4/universal?api_key=t"), .init(provider: .emby, transcode: .server))
        XCTAssertEqual(origin("http://emby.local/emby/Audio/4/stream?static=true&api_key=t"), .init(provider: .emby, transcode: .no))
        XCTAssertEqual(origin("https://jf.example.com/Audio/a/stream?static=true&ApiKey=t"), .init(provider: .jellyfin, transcode: .no))
    }
}
