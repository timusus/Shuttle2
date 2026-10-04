import AVFoundation
import XCTest
@testable import S2Playback

/// The engine's claims, checked sample for sample in offline manual rendering: nothing plays, the
/// test pulls the mixer's output and compares it with the PCM the sources handed over.
final class MusicPlaybackControllerTests: XCTestCase {

    private let rate = 48_000.0

    private func makeController(scheduleAhead: Double = 0.5) throws -> (MusicPlaybackController, CallbackLog) {
        let log = CallbackLog()
        let controller = try MusicPlaybackController(
            outputSampleRate: rate,
            renderingMode: .offline(maximumFrameCount: 4096),
            scheduleAheadSeconds: scheduleAhead,
            callbackQueue: log.queue
        )
        log.attach(to: controller)
        return (controller, log)
    }

    private func track(_ uid: String, _ samples: [Float], gainDb: Float = 0) -> PlaybackTrack {
        PlaybackTrack(uid: uid, gainDb: gainDb) { InMemoryTrackSource(samples: samples) }
    }

    /// Asserts `left`/`right` equal interleaved `expected` exactly, reporting the first mismatch.
    private func assertEqual(_ left: ArraySlice<Float>, _ right: ArraySlice<Float>, _ expected: [Float],
                             file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertEqual(left.count, expected.count / 2, "frame count", file: file, line: line)
        let l = expected.channel(0)
        let r = expected.channel(1)
        for i in 0..<min(left.count, l.count) {
            let a = left[left.startIndex + i], b = right[right.startIndex + i]
            if a != l[i] || b != r[i] {
                XCTFail("frame \(i): got (\(a), \(b)), expected (\(l[i]), \(r[i]))", file: file, line: line)
                return
            }
        }
    }

    // MARK: - Gapless

    func testTwoTracksRenderBackToBackWithNothingInserted() throws {
        let (controller, log) = try makeController()
        let a = TestSignal.noise(frames: 12_000, seed: 1)
        let b = TestSignal.noise(frames: 14_400, seed: 2)
        controller.load(current: track("A", a), next: track("B", b), playWhenReady: true)
        controller.syncForTesting()

        let total = 12_000 + 14_400
        let out = try OfflineRenderer(controller: controller, slice: 512).render(frames: total + 4096, log: log)

        // The first rendered frame is A's first frame, and every frame after it is the next one
        // of A then B: no silence at the start, none at the join, none dropped.
        assertEqual(out.left[0..<total], out.right[0..<total], a + b)
        XCTAssertTrue(out.left[total...].allSatisfy { $0 == 0 }, "silence after the queue")

        controller.syncForTesting()
        XCTAssertEqual(log.transitions, ["B"])
        let seenAt = try XCTUnwrap(log.transitionFrames.first)
        XCTAssertGreaterThanOrEqual(seenAt, 12_000, "transition reported before B was heard")
        XCTAssertLessThanOrEqual(seenAt, 12_000 + 1024, "transition reported late")
        XCTAssertEqual(log.states.last, .ended)
    }

    /// 44.1 kHz then 48 kHz, both FFmpeg-decoded into a 48 kHz engine: the render is exactly what
    /// the two decoders produced, one after the other.
    func testFormatChangeIsGapless() throws {
        let urlA = try TestSignal.writeSineWAV(sampleRate: 44_100, seconds: 0.3, frequency: 440, amplitude: 0.4)
        let urlB = try TestSignal.writeSineWAV(sampleRate: 48_000, seconds: 0.3, frequency: 660, amplitude: 0.4)
        defer {
            try? FileManager.default.removeItem(at: urlA)
            try? FileManager.default.removeItem(at: urlB)
        }
        let sourceA = RecordingTrackSource(FFmpegTrackSource(url: urlA))
        let sourceB = RecordingTrackSource(FFmpegTrackSource(url: urlB))
        let (controller, log) = try makeController()
        controller.load(
            current: PlaybackTrack(uid: "A") { sourceA },
            next: PlaybackTrack(uid: "B") { sourceB },
            playWhenReady: true
        )
        controller.syncForTesting()
        let out = try OfflineRenderer(controller: controller, slice: 1024).render(frames: 30_000, log: log)
        controller.syncForTesting()

        let framesA = sourceA.recorded.count / 2
        let framesB = sourceB.recorded.count / 2
        // 0.3 s at 44.1 kHz is 13 230 frames; resampled to 48 kHz, 14 400.
        XCTAssertEqual(Double(framesA), 14_400, accuracy: 2)
        XCTAssertEqual(framesB, 14_400)
        let total = framesA + framesB
        assertEqual(out.left[0..<total], out.right[0..<total], sourceA.recorded + sourceB.recorded)
        XCTAssertTrue(out.left[total...].allSatisfy { $0 == 0 })
        XCTAssertEqual(log.transitions, ["B"])
        XCTAssertEqual(log.failures, [])
    }

    // MARK: - Seek

    func testSeekWithinTrackResumesAtTheExactFrame() throws {
        let (controller, log) = try makeController()
        let a = TestSignal.noise(frames: 96_000, seed: 3)
        controller.load(current: track("A", a), next: nil, playWhenReady: true)
        controller.syncForTesting()
        let renderer = OfflineRenderer(controller: controller, slice: 512)
        _ = try renderer.render(frames: 9_600)

        controller.seek(toMs: 1_000)
        controller.syncForTesting()
        let out = try renderer.render(frames: 9_600)

        let from = 48_000
        assertEqual(out.left[0..<9_600], out.right[0..<9_600], Array(a[(from * 2)..<((from + 9_600) * 2)]))
        let position = try XCTUnwrap(controller.position)
        XCTAssertEqual(position.uid, "A")
        // lastRenderTime is the start of the last render slice.
        XCTAssertEqual(Double(position.ms), 1_200, accuracy: 15)
        XCTAssertEqual(log.failures, [])
    }

    func testSeekAfterCrossingIntoNextRebuildsFromTheCurrentTrack() throws {
        let (controller, _) = try makeController(scheduleAhead: 1.0)
        let a = TestSignal.noise(frames: 24_000, seed: 4)
        let b = TestSignal.noise(frames: 24_000, seed: 5)
        controller.load(current: track("A", a), next: track("B", b), playWhenReady: true)
        controller.syncForTesting()
        // A second ahead is scheduled, so B has already started to be read.
        controller.seek(toMs: 250)
        controller.syncForTesting()
        let out = try OfflineRenderer(controller: controller, slice: 512).render(frames: 36_000)
        assertEqual(out.left[0..<36_000], out.right[0..<36_000], Array(a[(12_000 * 2)...]) + b)
    }

    // MARK: - Unseekable (a progressive transcode, #606)

    private func transcode(_ uid: String, _ samples: [Float]) -> PlaybackTrack {
        PlaybackTrack(uid: uid, gainDb: 0) { InMemoryTrackSource(samples: samples, seekable: false) }
    }

    func testSeekOnAnUnseekableTrackPlaysOnAndIsReported() throws {
        let (controller, log) = try makeController()
        let a = TestSignal.noise(frames: 48_000, seed: 6)
        controller.load(current: transcode("A", a), next: nil, playWhenReady: true)
        controller.syncForTesting()
        let renderer = OfflineRenderer(controller: controller, slice: 512)
        _ = try renderer.render(frames: 9_600)

        controller.seek(toMs: 800)
        controller.syncForTesting()
        let out = try renderer.render(frames: 9_600)

        // Nothing dropped, nothing sought: the stream carries on from where it was heard.
        assertEqual(out.left[0..<9_600], out.right[0..<9_600], Array(a[(9_600 * 2)..<(19_200 * 2)]))
        XCTAssertEqual(log.seeksUnsupported, ["A 800"])
        XCTAssertEqual(log.failures, [])
        XCTAssertEqual(controller.position?.uid, "A")
    }

    func testLoadAtAPositionOnAnUnseekableTrackStartsAtItsStartAndIsReported() throws {
        let (controller, log) = try makeController()
        let a = TestSignal.noise(frames: 24_000, seed: 7)
        controller.load(current: transcode("A", a), next: nil, startMs: 250, playWhenReady: true)
        controller.syncForTesting()
        let out = try OfflineRenderer(controller: controller, slice: 512).render(frames: 9_600)

        assertEqual(out.left[0..<9_600], out.right[0..<9_600], Array(a[0..<(9_600 * 2)]))
        XCTAssertEqual(log.seeksUnsupported, ["A 250"])
        XCTAssertEqual(log.failures, [])
        XCTAssertEqual(Double(try XCTUnwrap(controller.position).ms), 190, accuracy: 15)
    }

    /// The seek that didn't happen doesn't disturb the next track already being read behind the
    /// unseekable one: the join stays gapless.
    func testSeekOnAnUnseekableTrackKeepsTheNextGapless() throws {
        let (controller, log) = try makeController(scheduleAhead: 1.0)
        let a = TestSignal.noise(frames: 24_000, seed: 8)
        let b = TestSignal.noise(frames: 24_000, seed: 9)
        controller.load(current: transcode("A", a), next: track("B", b), playWhenReady: true)
        controller.syncForTesting()
        controller.seek(toMs: 250)
        controller.syncForTesting()
        let out = try OfflineRenderer(controller: controller, slice: 512).render(frames: 48_000)

        assertEqual(out.left[0..<48_000], out.right[0..<48_000], a + b)
        XCTAssertEqual(log.seeksUnsupported, ["A 250"])
        XCTAssertEqual(log.failures, [])
    }

    func testReplacingAnAlreadyScheduledNextPlaysTheNewOne() throws {
        let (controller, log) = try makeController(scheduleAhead: 1.0)
        let a = TestSignal.noise(frames: 24_000, seed: 6)
        let b = TestSignal.noise(frames: 24_000, seed: 7)
        let c = TestSignal.noise(frames: 12_000, seed: 8)
        controller.load(current: track("A", a), next: track("B", b), playWhenReady: true)
        controller.syncForTesting()
        let renderer = OfflineRenderer(controller: controller, slice: 512)
        let head = try renderer.render(frames: 4_096)
        controller.setNext(track("C", c))
        controller.syncForTesting()
        let rest = try renderer.render(frames: 24_000 + 12_000, log: log)
        XCTAssertEqual(head.left, Array(a.channel(0)[0..<4_096]))
        // The rebuild restarts from the playhead the node last reported, which is up to one render
        // slice behind what was rendered: a few frames of A may repeat, none are skipped.
        let resumed = try XCTUnwrap((3_000..<4_200).first { a[$0 * 2] == rest.left[0] })
        XCTAssertLessThanOrEqual(resumed, 4_096)
        let expected = Array(a[(resumed * 2)...]) + c
        let count = expected.count / 2
        assertEqual(rest.left[0..<count], rest.right[0..<count], expected)
        controller.syncForTesting()
        XCTAssertEqual(log.transitions, ["C"])
    }

    /// The queue's end (current with no next) is already scheduled when the next arrives: the new
    /// next carries on from the last frame, still with nothing in between.
    func testNextSetAfterTheEndWasScheduledStaysGapless() throws {
        let (controller, log) = try makeController(scheduleAhead: 0.5)
        let a = TestSignal.noise(frames: 12_000, seed: 13)
        let b = TestSignal.noise(frames: 6_000, seed: 14)
        controller.load(current: track("A", a), next: nil, playWhenReady: true)
        controller.syncForTesting()
        let renderer = OfflineRenderer(controller: controller, slice: 512)
        let head = try renderer.render(frames: 2_048)
        controller.setNext(track("B", b))
        controller.syncForTesting()
        let rest = try renderer.render(frames: 18_000 - 2_048 + 1_024, log: log)
        let left = head.left + rest.left
        let right = head.right + rest.right
        assertEqual(left[0..<18_000], right[0..<18_000], a + b)
        XCTAssertTrue(left[18_000...].allSatisfy { $0 == 0 })
        controller.syncForTesting()
        XCTAssertEqual(log.transitions, ["B"])
        XCTAssertEqual(log.states.last, .ended)
    }

    // MARK: - Failures

    private func failing(_ uid: String, atRead: Bool = false) -> PlaybackTrack {
        PlaybackTrack(uid: uid) { FailingTrackSource(atRead: atRead) }
    }

    /// The owner loads with no next and hands the next over straight after, as the shared
    /// `IosPlayerController` does. A current that failed to open has drained the queue by then; the
    /// engine carries on into the new next, as it would have into a next given with the load.
    func testNextSetAfterTheCurrentFailedToOpenPlays() throws {
        let (controller, log) = try makeController()
        let b = TestSignal.noise(frames: 12_000, seed: 15)
        controller.load(current: failing("A"), next: nil, playWhenReady: true)
        controller.setNext(track("B", b))
        controller.syncForTesting()
        let out = try OfflineRenderer(controller: controller, slice: 512).render(frames: 12_000 + 1_024, log: log)
        assertEqual(out.left[0..<12_000], out.right[0..<12_000], b)
        controller.syncForTesting()
        XCTAssertEqual(log.failures, ["A"])
        XCTAssertEqual(log.transitions, ["B"])
        XCTAssertEqual(log.states.last, .ended)
    }

    /// A next that failed to open is never transitioned into: the current track ends, and the owner
    /// (which got the failure) decides what comes after it.
    func testFailedNextEndsTheCurrentTrackWithoutATransition() throws {
        let (controller, log) = try makeController()
        let a = TestSignal.noise(frames: 6_000, seed: 16)
        controller.load(current: track("A", a), next: failing("B"), playWhenReady: true)
        controller.syncForTesting()
        let out = try OfflineRenderer(controller: controller, slice: 512).render(frames: 6_000 + 1_024, log: log)
        assertEqual(out.left[0..<6_000], out.right[0..<6_000], a)
        controller.syncForTesting()
        XCTAssertEqual(log.failures, ["B"])
        XCTAssertEqual(log.transitions, [])
        XCTAssertEqual(log.states.last, .ended)
        XCTAssertEqual(controller.position?.uid, "A")
    }

    /// The next failed to open once the current track's end was scheduled; a replacement next set
    /// before the end is heard carries on from the last frame, gapless.
    func testNextSetAfterAFailedNextStaysGapless() throws {
        let (controller, log) = try makeController(scheduleAhead: 0.5)
        let a = TestSignal.noise(frames: 12_000, seed: 17)
        let c = TestSignal.noise(frames: 6_000, seed: 18)
        controller.load(current: track("A", a), next: failing("B"), playWhenReady: true)
        controller.syncForTesting()
        let renderer = OfflineRenderer(controller: controller, slice: 512)
        let head = try renderer.render(frames: 2_048)
        controller.setNext(track("C", c))
        controller.syncForTesting()
        let rest = try renderer.render(frames: 18_000 - 2_048 + 1_024, log: log)
        let left = head.left + rest.left
        let right = head.right + rest.right
        assertEqual(left[0..<18_000], right[0..<18_000], a + c)
        XCTAssertTrue(left[18_000...].allSatisfy { $0 == 0 })
        controller.syncForTesting()
        XCTAssertEqual(log.failures, ["B"])
        XCTAssertEqual(log.transitions, ["C"])
        XCTAssertEqual(log.states.last, .ended)
    }

    /// A next that opened but failed before its first frame is transitioned into and ends at once.
    /// The owner hands over the track after it once the end is reported; it starts from its top.
    func testNextSetAfterAFailedTrackEndedStartsIt() throws {
        let (controller, log) = try makeController()
        let a = TestSignal.noise(frames: 6_000, seed: 19)
        let c = TestSignal.noise(frames: 6_000, seed: 20)
        controller.load(current: track("A", a), next: failing("B", atRead: true), playWhenReady: true)
        controller.syncForTesting()
        let renderer = OfflineRenderer(controller: controller, slice: 512)
        let head = try renderer.render(frames: 6_000 + 1_024, log: log)
        assertEqual(head.left[0..<6_000], head.right[0..<6_000], a)
        controller.syncForTesting()
        XCTAssertEqual(log.transitions, ["B"])
        XCTAssertEqual(log.states.last, .ended)

        controller.setNext(track("C", c))
        controller.syncForTesting()
        let rest = try renderer.render(frames: 6_000 + 1_024, log: log)
        assertEqual(rest.left[0..<6_000], rest.right[0..<6_000], c)
        controller.syncForTesting()
        XCTAssertEqual(log.failures, ["B"])
        XCTAssertEqual(log.transitions, ["B", "C"])
        XCTAssertEqual(log.states.suffix(3), [.ended, .playing, .ended])
    }

    // MARK: - Engine start

    /// The engine is paused while nothing plays (#691), so every play starts it again; one it can't
    /// start (the audio session didn't activate) must not play the node, which raises on a stopped
    /// engine, nor report playing.
    func testAPlayTheEngineCantStartStaysPaused() throws {
        let (controller, log) = try makeController()
        controller.load(current: track("A", TestSignal.noise(frames: 12_000, seed: 1)), next: nil, playWhenReady: false)
        controller.syncForTesting()
        controller.engine.stop()
        controller.startEngine = { _ in throw TrackSourceError.failed("session not active") }

        controller.play()
        controller.syncForTesting()
        // Already paused, so the state didn't change — the refusal is reported again anyway, for this track.
        XCTAssertEqual(log.states, [.loading, .paused, .paused])
        XCTAssertEqual(log.failures, [])

        controller.startEngine = { try $0.start() }
        controller.play()
        controller.syncForTesting()
        XCTAssertEqual(log.states, [.loading, .paused, .paused, .playing])
    }

    /// Each report carries the commands taken before it, so the owner can tell a paused report made before a play
    /// (a pause's, a load's) from that play's refusal (#708), and a playing one made before a pause from now.
    func testEachStateReportCountsTheCommandsTakenBeforeIt() throws {
        let (controller, log) = try makeController()
        controller.load(current: track("A", TestSignal.noise(frames: 12_000, seed: 1)), next: nil, playWhenReady: false)
        controller.play()
        controller.pause()
        controller.syncForTesting()
        XCTAssertEqual(log.states, [.loading, .paused, .playing, .paused])
        XCTAssertEqual(log.stateCommands, [1, 1, 2, 3])

        controller.engine.stop()
        controller.startEngine = { _ in throw TrackSourceError.failed("session not active") }
        controller.play()
        controller.syncForTesting()
        XCTAssertEqual(log.states.last, .paused)
        XCTAssertEqual(log.stateCommands.last, 4, "a refusal answers the play")

        controller.stop()
        controller.syncForTesting()
        XCTAssertEqual(log.stateCommands.last, 5)
    }

    /// A command that changes nothing is still answered at its count, or the owner would only hear reports from
    /// before it, superseded: a play reaching an engine already playing (a load that plays, then a play sent
    /// before its playing report came back) would leave the owner never hearing it play.
    func testACommandThatChangesNothingIsAnsweredWithTheStateItLeft() throws {
        let (controller, log) = try makeController()
        controller.load(current: track("A", TestSignal.noise(frames: 6_000, seed: 1)), next: nil, playWhenReady: true)
        controller.play()
        controller.syncForTesting()
        XCTAssertEqual(log.states, [.loading, .playing, .playing])
        XCTAssertEqual(log.stateCommands, [1, 1, 2])

        controller.pause()
        controller.pause()
        controller.syncForTesting()
        XCTAssertEqual(log.states.suffix(2), [.paused, .paused])
        XCTAssertEqual(log.stateCommands.suffix(2), [3, 4])
    }

    /// The end of a track is said once: a command after it that changes nothing doesn't end it again.
    func testACommandAfterTheEndDoesNotRepeatIt() throws {
        let (controller, log) = try makeController()
        controller.load(current: track("A", TestSignal.noise(frames: 6_000, seed: 1)), next: nil, playWhenReady: true)
        controller.syncForTesting()
        _ = try OfflineRenderer(controller: controller, slice: 512).render(frames: 6_000 + 4_096, log: log)
        controller.syncForTesting()
        XCTAssertEqual(log.states.last, .ended)
        let reports = log.states.count

        controller.play()
        controller.pause()
        controller.syncForTesting()
        XCTAssertEqual(log.states.count, reports)
    }

    func testALoadThatPlaysWhenTheEngineCantStartLoadsPausedAndStaysPaused() throws {
        let (controller, log) = try makeController()
        controller.engine.stop()
        controller.startEngine = { _ in throw TrackSourceError.failed("session not active") }

        controller.load(current: track("A", TestSignal.noise(frames: 12_000, seed: 1)), next: nil, playWhenReady: true)
        controller.syncForTesting()
        XCTAssertEqual(log.states, [.loading, .paused])
        XCTAssertEqual(log.failures, [], "a start failure is not a song that failed to decode")

        // The failed play is forgotten: a seek, which restarts the stream, doesn't try again.
        controller.startEngine = { try $0.start() }
        controller.seek(toMs: 100)
        controller.syncForTesting()
        XCTAssertEqual(log.states, [.loading, .paused])
    }

    // MARK: - Output activation (#687)

    /// A load that plays asks the owner to ready the output as it's made, so the session activates while the
    /// track opens rather than after it.
    func testALoadThatPlaysActivatesTheOutputWhileItsTrackOpens() throws {
        let (controller, log) = try makeController()
        let activating = DispatchSemaphore(value: 0)
        let activated = DispatchSemaphore(value: 0)
        controller.activateOutput = {
            activating.signal()
            // Done only once the open has seen it start: run after the open, this would never return true.
            return activated.wait(timeout: .now() + 2) == .success
        }
        var activationSeenByOpen = false
        let samples = TestSignal.noise(frames: 12_000, seed: 1)
        let track = PlaybackTrack(uid: "A") {
            OpenHookTrackSource(InMemoryTrackSource(samples: samples)) {
                activationSeenByOpen = activating.wait(timeout: .now() + 2) == .success
                activated.signal()
            }
        }

        controller.load(current: track, next: nil, playWhenReady: true)
        controller.syncForTesting()
        XCTAssertTrue(activationSeenByOpen)
        XCTAssertEqual(log.states, [.loading, .playing])
    }

    /// An output the owner won't ready (a call holds the session) refuses the play as an engine that won't
    /// start does: loaded paused, not failed, and the next play asks again.
    func testALoadThatPlaysWhoseOutputIsRefusedLoadsPausedUntilTheNextPlay() throws {
        let (controller, log) = try makeController()
        var activations = 0
        var refuse = true
        controller.activateOutput = {
            activations += 1
            return !refuse
        }

        controller.load(current: track("A", TestSignal.noise(frames: 12_000, seed: 1)), next: nil, playWhenReady: true)
        controller.syncForTesting()
        XCTAssertEqual(log.states, [.loading, .paused])
        XCTAssertEqual(log.failures, [])

        refuse = false
        controller.play()
        controller.syncForTesting()
        XCTAssertEqual(log.states, [.loading, .paused, .playing])
        XCTAssertEqual(activations, 2)
    }

    /// Only a play readies the output: a paused load, a pause and a stop ask nothing of the owner.
    func testOnlyAPlayActivatesTheOutput() throws {
        let (controller, log) = try makeController()
        var activations = 0
        controller.activateOutput = {
            activations += 1
            return true
        }

        controller.load(current: track("A", TestSignal.noise(frames: 12_000, seed: 1)), next: nil, playWhenReady: false)
        controller.pause()
        controller.syncForTesting()
        XCTAssertEqual(activations, 0)

        controller.play()
        controller.stop()
        controller.syncForTesting()
        XCTAssertEqual(activations, 1)
        XCTAssertEqual(log.states, [.loading, .paused, .paused, .playing, .idle], "the pause while paused is answered too")
    }

    // MARK: - Route change (an engine configuration change)

    /// What a route change does to the engine: it stops, and with it the node's clock.
    private func changeRoute(_ controller: MusicPlaybackController) {
        controller.engine.stop()
        NotificationCenter.default.post(name: .AVAudioEngineConfigurationChange, object: controller.engine)
        controller.syncForTesting()
    }

    /// Where `out` starts in `samples` (interleaved), from its first frames.
    private func offset(of out: (left: [Float], right: [Float]), in samples: [Float]) -> Int? {
        let left = samples.channel(0)
        let probe = Array(out.left.prefix(64))
        return (0...(left.count - probe.count)).first { Array(left[$0..<($0 + probe.count)]) == probe }
    }

    /// Repeats `syncForTesting` until `condition` holds or `timeout` passes.
    private func waitUntil(_ controller: MusicPlaybackController, timeout: TimeInterval = 2, _ condition: () -> Bool) {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            controller.syncForTesting()
            if condition() { return }
            Thread.sleep(forTimeInterval: 0.01)
        }
    }

    /// #714: the engine stops before the change is reported, so the node's clock can't be read. The
    /// stream is rebuilt where it was last heard, not at the start of the track.
    func testARouteChangeMidTrackCarriesOnWhereItWasHeard() throws {
        let (controller, log) = try makeController()
        let a = TestSignal.noise(frames: 96_000, seed: 21)
        controller.load(current: track("A", a), next: nil, playWhenReady: true)
        controller.syncForTesting()
        let renderer = OfflineRenderer(controller: controller, slice: 512)
        _ = try renderer.render(frames: 24_000)
        let heard = try XCTUnwrap(controller.position).ms

        changeRoute(controller)
        XCTAssertEqual(Double(try XCTUnwrap(controller.position).ms), Double(heard), accuracy: 15)
        let out = try renderer.render(frames: 9_600)

        let from = try XCTUnwrap(offset(of: out, in: a))
        XCTAssertEqual(Double(from), 24_000, accuracy: 1_024)
        assertEqual(out.left[0..<9_600], out.right[0..<9_600], Array(a[(from * 2)..<((from + 9_600) * 2)]))
        XCTAssertEqual(log.states, [.loading, .playing])
        XCTAssertEqual(log.failures, [])
    }

    /// A paused track stays where it was paused, and paused.
    func testARouteChangeWhilePausedKeepsThePausedPosition() throws {
        let (controller, log) = try makeController()
        let a = TestSignal.noise(frames: 96_000, seed: 22)
        controller.load(current: track("A", a), next: nil, playWhenReady: true)
        controller.syncForTesting()
        _ = try OfflineRenderer(controller: controller, slice: 512).render(frames: 24_000)
        controller.pause()
        controller.syncForTesting()
        let paused = try XCTUnwrap(controller.position).ms

        changeRoute(controller)
        XCTAssertEqual(controller.position?.ms, paused)
        XCTAssertEqual(log.states, [.loading, .playing, .paused])
    }

    /// A transcode can't be sought: the owner is told the position heard, to re-open the stream there.
    func testARouteChangeOnAnUnseekableTrackReportsThePositionHeard() throws {
        let (controller, log) = try makeController()
        controller.load(current: transcode("A", TestSignal.noise(frames: 96_000, seed: 23)), next: nil, playWhenReady: true)
        controller.syncForTesting()
        _ = try OfflineRenderer(controller: controller, slice: 512).render(frames: 24_000)

        changeRoute(controller)
        let reported = try XCTUnwrap(log.seeksUnsupported.last?.split(separator: " ").last.flatMap { Int64($0) })
        XCTAssertEqual(Double(reported), 500, accuracy: 15)
    }

    /// #813: a route change can post several changes in a row. Those queued together are one
    /// rebuild, so a transcode's owner re-opens the stream once.
    func testRouteChangesPostedTogetherRebuildOnce() throws {
        let (controller, log) = try makeController()
        let a = TestSignal.noise(frames: 96_000, seed: 26)
        controller.load(current: transcode("A", a), next: nil, playWhenReady: true)
        controller.syncForTesting()
        let renderer = OfflineRenderer(controller: controller, slice: 512)
        _ = try renderer.render(frames: 24_000)
        let before = log.seeksUnsupported.count

        controller.engine.stop()
        controller.onEngineQueueForTesting {
            for _ in 0..<3 {
                NotificationCenter.default.post(name: .AVAudioEngineConfigurationChange, object: controller.engine)
            }
        }
        controller.syncForTesting()
        XCTAssertEqual(log.seeksUnsupported.count, before + 1)
        XCTAssertEqual(log.states, [.loading, .playing])

        changeRoute(controller)
        XCTAssertEqual(log.seeksUnsupported.count, before + 2, "a later change still rebuilds")
    }

    /// #715: the engine won't start while the route settles. It shows loading and plays once a retry
    /// starts it, from where it was heard.
    func testAStartThatFailsAfterARouteChangeIsRetried() throws {
        let (controller, log) = try makeController()
        controller.startRetryDelay = .milliseconds(20)
        let a = TestSignal.noise(frames: 96_000, seed: 24)
        controller.load(current: track("A", a), next: nil, playWhenReady: true)
        controller.syncForTesting()
        let renderer = OfflineRenderer(controller: controller, slice: 512)
        _ = try renderer.render(frames: 24_000)
        controller.startEngine = { _ in throw TrackSourceError.failed("route settling") }

        changeRoute(controller)
        XCTAssertEqual(log.states, [.loading, .playing, .loading])
        controller.startEngine = { try $0.start() }
        waitUntil(controller) { log.states.last == .playing }
        XCTAssertEqual(log.states, [.loading, .playing, .loading, .playing])

        let out = try renderer.render(frames: 9_600)
        let from = try XCTUnwrap(offset(of: out, in: a))
        XCTAssertEqual(Double(from), 24_000, accuracy: 1_024)
    }

    func testAStartThatKeepsFailingAfterARouteChangeGivesUpPaused() throws {
        let (controller, log) = try makeController()
        controller.startRetryDelay = .milliseconds(10)
        controller.load(current: track("A", TestSignal.noise(frames: 96_000, seed: 25)), next: nil, playWhenReady: true)
        controller.syncForTesting()
        _ = try OfflineRenderer(controller: controller, slice: 512).render(frames: 9_600)
        var attempts = 0
        controller.startEngine = { _ in
            attempts += 1
            throw TrackSourceError.failed("route gone")
        }

        changeRoute(controller)
        waitUntil(controller) { log.states.last == .paused }
        XCTAssertEqual(log.states, [.loading, .playing, .loading, .paused])
        XCTAssertEqual(attempts, 1 + MusicPlaybackController.startRetryAttempts)
    }

    func testAPauseWhileAStartIsRetriedCancelsIt() throws {
        let (controller, log) = try makeController()
        controller.startRetryDelay = .milliseconds(50)
        controller.load(current: track("A", TestSignal.noise(frames: 96_000, seed: 26)), next: nil, playWhenReady: true)
        controller.syncForTesting()
        _ = try OfflineRenderer(controller: controller, slice: 512).render(frames: 9_600)
        controller.startEngine = { _ in throw TrackSourceError.failed("route settling") }

        changeRoute(controller)
        controller.pause()
        controller.startEngine = { try $0.start() }
        Thread.sleep(forTimeInterval: 0.3)
        controller.syncForTesting()
        XCTAssertEqual(log.states, [.loading, .playing, .loading, .paused])
    }

    // MARK: - DSP

    func testFlatEqualizerIsIdentity() throws {
        let (controller, _) = try makeController()
        let flatBand: [Double] = [1, 0, 0, 0, 0]
        controller.setEqualizer(EqualizerSettings(enabled: true, preampDb: 0,
                                                  coefficients: Array(repeating: flatBand, count: 10).flatMap { $0 }))
        let a = TestSignal.noise(frames: 20_000, seed: 9)
        controller.load(current: track("A", a), next: nil, playWhenReady: true)
        controller.syncForTesting()
        let out = try OfflineRenderer(controller: controller, slice: 1024).render(frames: 20_000)
        assertEqual(out.left[...], out.right[...], a)
    }

    /// A +6 dB band at 1 kHz as the shared Kotlin `EqualizerCascade` designs it for 48 kHz, pinned by
    /// `EqualizerCascadeTest` on the Kotlin side.
    private let kotlinBoostAt1kHz: [Double] = [
        1.024858815651008, -1.9333627896640524, 0.92518688531219, -1.9333627896640524, 0.9500457009631977,
    ]

    /// The level of `samples` from `start` on, in dB relative to full scale RMS.
    private func levelDb(_ samples: [Float], from start: Int) -> Double {
        let tail = samples[start...]
        let meanSquare = tail.reduce(0.0) { $0 + Double($1) * Double($1) } / Double(tail.count)
        return 10 * log10(meanSquare)
    }

    private func sine(frames: Int, frequency: Double, amplitude: Float) -> [Float] {
        (0..<frames).flatMap { frame -> [Float] in
            let value = amplitude * Float(sin(2 * Double.pi * frequency * Double(frame) / rate))
            return [value, value]
        }
    }

    func testAKotlinDesignedBandBoostsItsFrequencyBySixDecibelsAtTheEngineRate() throws {
        let (controller, _) = try makeController()
        XCTAssertEqual(controller.outputSampleRate, rate, "the coefficients are designed at the engine rate")
        let flatBand: [Double] = [1, 0, 0, 0, 0]
        let bands = Array(repeating: flatBand, count: 5).flatMap { $0 } + kotlinBoostAt1kHz
            + Array(repeating: flatBand, count: 4).flatMap { $0 }
        controller.setEqualizer(EqualizerSettings(enabled: true, preampDb: 0, coefficients: bands))
        let a = sine(frames: 24_000, frequency: 1_000, amplitude: 0.25)
        controller.load(current: track("A", a), next: nil, playWhenReady: true)
        controller.syncForTesting()
        let out = try OfflineRenderer(controller: controller, slice: 1024).render(frames: 24_000)

        // Past the filter's settling, well under the limiter's ceiling (0.25 boosted to 0.5).
        let gain = levelDb(out.left, from: 4_800) - levelDb(a.channel(0), from: 4_800)
        XCTAssertEqual(gain, 6, accuracy: 0.05)
    }

    func testTheEqualizersPreampScalesTheSignal() throws {
        let (controller, _) = try makeController()
        controller.setEqualizer(EqualizerSettings(enabled: true, preampDb: -6, coefficients: [1, 0, 0, 0, 0]))
        let a = sine(frames: 12_000, frequency: 1_000, amplitude: 0.25)
        controller.load(current: track("A", a), next: nil, playWhenReady: true)
        controller.syncForTesting()
        let out = try OfflineRenderer(controller: controller, slice: 1024).render(frames: 12_000)

        let gain = levelDb(out.left, from: 2_400) - levelDb(a.channel(0), from: 2_400)
        XCTAssertEqual(gain, -6, accuracy: 0.05)
    }

    func testReplayGainScalesSamples() throws {
        let (controller, _) = try makeController()
        let a = TestSignal.noise(frames: 20_000, seed: 11)
        controller.load(current: track("A", a, gainDb: -6), next: nil, playWhenReady: true)
        controller.syncForTesting()
        let out = try OfflineRenderer(controller: controller, slice: 1024).render(frames: 20_000)
        let scale = powf(10, -6 / 20) // 0.501187
        let expected = a.map { $0 * scale }
        for (i, value) in out.left.enumerated() {
            XCTAssertEqual(value, expected[i * 2], accuracy: 1e-6)
            if abs(value - expected[i * 2]) > 1e-6 { break }
        }
    }

    func testLimiterHoldsABoostedTrackUnderTheCeiling() throws {
        let (controller, _) = try makeController()
        let a = TestSignal.noise(frames: 20_000, seed: 12, amplitude: 0.9)
        controller.load(current: track("A", a, gainDb: 12), next: nil, playWhenReady: true)
        controller.syncForTesting()
        let out = try OfflineRenderer(controller: controller, slice: 1024).render(frames: 20_000)
        let ceiling = powf(10, -0.1 / 20)
        let peak = (out.left + out.right).map(abs).max() ?? 0
        XCTAssertLessThanOrEqual(peak, ceiling + 1e-4)
        XCTAssertGreaterThan(peak, 0.9, "the boost is limited, not undone")
    }
}

/// Runs `onOpen` inside the open of the source it passes through, as a slow open would block.
private final class OpenHookTrackSource: TrackPCMSource {
    private let inner: TrackPCMSource
    private let onOpen: () -> Void

    init(_ inner: TrackPCMSource, onOpen: @escaping () -> Void) {
        self.inner = inner
        self.onOpen = onOpen
    }

    func open(sampleRate: Double, channelCount: Int) throws -> Int64? {
        onOpen()
        return try inner.open(sampleRate: sampleRate, channelCount: channelCount)
    }

    func seek(toFrame frame: Int64) throws { try inner.seek(toFrame: frame) }
    func read(into buffer: UnsafeMutablePointer<Float>, maxFrames: Int) throws -> Int {
        try inner.read(into: buffer, maxFrames: maxFrames)
    }

    func cancel() { inner.cancel() }
    func interrupt() { inner.interrupt() }
}
