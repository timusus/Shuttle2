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
