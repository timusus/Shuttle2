import Foundation
import PlaybackDecode
import PlaybackPlayer
import PlaybackRender
import PlaybackStreaming
@testable import S2
import S2PlaybackTestSupport
import Shared
import Testing

/// KitAudioPlayer over a real `Player` on the package's test rig: Kotlin's calls reach the player, and the player's
/// moves reach Kotlin's listener under queue uids.
@MainActor
struct KitAudioPlayerTests {
    private let rig: PlayerTestRig<KitAudioPlayer.QueueKey>
    private let sut: KitAudioPlayer
    private let listener = RecordingListener()

    /// Songs with a negative id can't be opened, as a stream the resolver refused.
    init() {
        rig = PlayerTestRig { key in
            if key.songId < 0 { return FailingItemSource() }
            return FakeItemSource(format: PCMFormat(sampleRate: 8, channelCount: 1), frames: 80, chunkFrames: 2)
        }
        sut = KitAudioPlayer(player: rig.player)
        sut.setListener(listener: listener)
    }

    private func entries(_ uids: [Int64], failing: Set<Int64> = []) -> [IosQueueEntry] {
        uids.map { IosQueueEntry(uid: $0, songId: failing.contains($0) ? -$0 : $0) }
    }

    @Test func theWholeOrderIsFedAndPlaysFromTheIndexAsked() async {
        sut.setItems(order: entries([11, 12, 13]), currentIndex: 1, startMs: 0, playWhenReady: true)
        rig.settle()

        #expect(rig.player.items.map(\.uid) == [11, 12, 13])
        #expect(rig.player.currentIndex == 1)
        #expect(await pumpUntil { listener.lastState == "12 playing" })
    }

    @Test func anEditKeepingTheCurrentItemKeepsPlayingIt() async {
        sut.setItems(order: entries([11, 12, 13]), currentIndex: 0, startMs: 0, playWhenReady: true)
        rig.settle()
        rig.run(for: 2)

        sut.replaceItems(order: entries([13, 11, 12]), currentIndex: 1)
        rig.settle()

        #expect(rig.player.items.map(\.uid) == [13, 11, 12])
        #expect(rig.player.currentItem?.uid == 11)
        #expect(rig.player.position > 1)
        #expect(listener.transitions.isEmpty)
    }

    @Test func theNextItemJoinsAndKotlinHearsTheTransition() async {
        sut.setItems(order: entries([11, 12]), currentIndex: 0, startMs: 0, playWhenReady: true)
        rig.settle()

        rig.run(for: 12)

        #expect(rig.player.currentItem?.uid == 12)
        #expect(await pumpUntil { listener.transitions == ["12"] })
    }

    @Test func aFailedItemIsReportedUnderItsUid() async {
        sut.setItems(order: entries([11, 12], failing: [11]), currentIndex: 0, startMs: 0, playWhenReady: true)
        rig.settle()

        #expect(await pumpUntil { listener.failures == ["11"] })
    }

    @Test func skippingOnPausedFromAFailedItemPreparesTheNext() async {
        sut.setItems(order: entries([11, 12], failing: [11]), currentIndex: 0, startMs: 0, playWhenReady: false)
        rig.settle()
        #expect(await pumpUntil { listener.failures == ["11"] })

        sut.skipToNext(playWhenReady: false)

        #expect(await pumpUntil { listener.lastState == "12 paused" })
    }

    @Test func movingOnPausedFromAFailedItemPreparesTheOneMovedTo() async {
        sut.setItems(order: entries([11, 12, 13], failing: [11]), currentIndex: 0, startMs: 0, playWhenReady: false)
        rig.settle()
        #expect(await pumpUntil { listener.failures == ["11"] })

        sut.skipTo(index: 2, startMs: 0, playWhenReady: false)

        #expect(await pumpUntil { listener.lastState == "13 paused" })
    }

    @Test func repeatModeIsThePlayers() {
        sut.setRepeatMode(mode: .one)
        #expect(rig.player.repeatMode == .one)
        sut.setRepeatMode(mode: .all)
        #expect(rig.player.repeatMode == .all)
        sut.setRepeatMode(mode: .off)
        #expect(rig.player.repeatMode == .off)
    }

    @Test func repeatOneReplaysTheItemAndKotlinHearsIt() async {
        sut.setItems(order: entries([11, 12]), currentIndex: 0, startMs: 0, playWhenReady: true)
        sut.setRepeatMode(mode: .one)
        rig.settle()

        rig.run(for: 12)

        #expect(rig.player.currentItem?.uid == 11)
        #expect(await pumpUntil { listener.transitions == ["11"] })
    }

    @Test func repeatAllWrapsFromTheLastItemToTheFirst() async {
        sut.setItems(order: entries([11, 12]), currentIndex: 1, startMs: 0, playWhenReady: true)
        sut.setRepeatMode(mode: .all)
        rig.settle()

        rig.run(for: 12)

        #expect(rig.player.currentItem?.uid == 11)
        #expect(await pumpUntil { listener.transitions == ["11"] })
    }

    @Test func pauseAtEndPausesOnTheNextItemAndTellsKotlin() async {
        sut.setItems(order: entries([11, 12]), currentIndex: 0, startMs: 0, playWhenReady: true)
        sut.setPauseAtEnd(enabled: true)
        rig.settle()

        rig.run(for: 12)

        #expect(await pumpUntil { !listener.pausedAtEnd.isEmpty })
        #expect(!rig.player.playWhenReady)
    }

    @Test func aMediaServicesResetRecoversPaused() async {
        sut.setItems(order: entries([11, 12]), currentIndex: 0, startMs: 0, playWhenReady: true)
        rig.settle()
        rig.run(for: 1)
        #expect(await pumpUntil { listener.lastState == "11 playing" })

        // iOS tells the session and the output alike.
        rig.session.mediaServicesReset()
        rig.output.injectReset()
        rig.pump()

        #expect(await pumpUntil { listener.lastState == "11 paused" })
    }

    @Test func positionsAreMillisecondsForKotlin() async {
        sut.setItems(order: entries([11]), currentIndex: 0, startMs: 2000, playWhenReady: false)
        rig.settle()

        #expect(sut.positionMs() == 2000)
        // The duration is known once the source opens.
        #expect(await pumpUntil { sut.durationMs() == 10000 })
    }

    /// An item of samples 0, 1, 2, 3 at a real rate (the limiter's lookahead is milliseconds): off, the limiter holds
    /// it under its ceiling; on, flat at −12 dB, it plays at a quarter, under the ceiling and so untouched.
    @Test func theEqualizerSettingsReachTheProcessorsThePlayerRuns() async {
        let rig = PlayerTestRig<KitAudioPlayer.QueueKey> { _ in
            FakeItemSource(format: PCMFormat(sampleRate: 8000, channelCount: 1), frames: 4, chunkFrames: 4)
        }
        let sut = KitAudioPlayer(player: rig.player)
        let equalizer = makeTestGraph(audioPlayer: sut).equalizerViewModel
        let listener = RecordingListener()
        sut.setListener(listener: listener)
        let ceiling: Float = 0.988554 // −0.1 dBFS
        func playToTheEnd() async -> [Float] {
            let before = rig.output.enqueues.count
            sut.setItems(order: entries([11]), currentIndex: 0, startMs: 0, playWhenReady: true)
            rig.settle()
            rig.run(for: 1)
            #expect(await pumpUntil(rig) { listener.lastState == "11 ended" })
            return rig.output.enqueues.dropFirst(before).flatMap(\.samples)
        }

        equalizer.onEnabledChange(enabled: false)
        let off = await playToTheEnd()
        #expect(off.count == 4)
        #expect(off.allSatisfy { $0 <= ceiling })
        #expect(off.contains { $0 > 0.9 })

        equalizer.onPreampGainChange(gainDb: -12)
        equalizer.onEnabledChange(enabled: true)
        let on = await playToTheEnd()
        #expect(on.count == 4)
        for (index, sample) in on.enumerated() {
            #expect(abs(sample - 0.251189 * Float(index)) < 1e-4)
        }
    }

    // MARK: - With the Kotlin controller

    /// A graph whose controller drives `sut`; its listener replaces the test's.
    private func queue(
        _ songs: [Song],
        playWhenReady: Bool = true,
        loaded: @escaping (Any?) -> Void = { _ in }
    ) async throws -> IosPlayerController {
        let controller = makeTestGraph(audioPlayer: sut).playerController
        _ = try await controller.queueOperations.setQueue(songs: songs, shuffleSongs: nil, position: 0, context: PlayContextNone.shared)
        controller.load(seekPosition: nil, skipUnloadable: true, playWhenReady: playWhenReady) { loaded($0) }
        return controller
    }

    private func currentSongId(_ controller: IosPlayerController) -> Int64? {
        controller.queueOperations.queueStateFlow.value.currentItem?.song.id
    }

    @Test func aShuffleToggleHandsOverTheNewOrderAndKeepsTheCurrentItem() async throws {
        let controller = try await queue(TestSongs.demo)
        #expect(await pumpUntil { rig.player.items.map(\.songId) == [1, 2, 3, 4, 5] && rig.player.isPlaying })
        rig.run(for: 1)
        let playing = try #require(rig.player.currentItem)

        try await controller.queueOperations.toggleShuffleMode()

        let shuffled = controller.queueOperations.queueStateFlow.value.items.map(\.uid)
        #expect(await pumpUntil { rig.player.items.map(\.uid) == shuffled })
        #expect(rig.player.currentItem == playing)
        #expect(rig.player.position >= 1)
    }

    @Test func failedSongsAreSkippedForTheNextThatPlays() async throws {
        let controller = try await queue([failing(1), failing(2), TestSongs.demo[2]])

        // A wait per skip, as in the test below.
        #expect(await pumpUntil { [-2, 3].contains(currentSongId(controller)) })
        #expect(await pumpUntil { currentSongId(controller) == 3 && rig.player.isPlaying })
        #expect(rig.player.currentItem?.songId == 3)
    }

    @Test func aPausedLoadSkipsAFailedSongAndCompletesPausedOnTheNext() async throws {
        var completed = false
        let controller = try await queue([failing(1), TestSongs.demo[1]], playWhenReady: false) { _ in completed = true }

        #expect(await pumpUntil { completed })
        #expect(currentSongId(controller) == 2)
        #expect(controller.playbackStateFlow.value is Shared.PlaybackState.Paused)
    }

    @Test func skippingStopsAfterTheCapOfFailuresInARow() async throws {
        let cap = 15 // PlaybackPolicy.MAX_LOAD_ATTEMPTS
        let controller = try await queue((1 ... cap).map { failing(Int64($0)) } + [TestSongs.demo[0]])

        // A wait per failure, so a slow host stretches each step rather than failing one budget for all of them.
        for id in 1 ... cap {
            #expect(await pumpUntil { (currentSongId(controller) ?? 0) <= -Int64(id) })
        }
        #expect(await pumpUntil { controller.playbackStateFlow.value is Shared.PlaybackState.Paused })
        rig.run(for: 2)
        #expect(currentSongId(controller) == -Int64(cap))
        #expect(!rig.player.isPlaying)
    }

    private func failing(_ id: Int64) -> Song {
        TestSongs.song(-id, "Unplayable \(id)", artist: "Nobody", album: "Nowhere", durationMs: 10_000)
    }

    /// Pumps the rig (decodes and transport events) until `condition` holds, letting Kotlin's main-queue work run.
    private func pumpUntil(_ timeout: TimeInterval = 10, _ condition: () -> Bool) async -> Bool {
        await pumpUntil(rig, timeout, condition)
    }

    private func pumpUntil(
        _ rig: PlayerTestRig<KitAudioPlayer.QueueKey>,
        _ timeout: TimeInterval = 10,
        _ condition: () -> Bool
    ) async -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            rig.pump()
            if condition() { return true }
            try? await Task.sleep(nanoseconds: 20_000_000)
        }
        return condition()
    }
}

/// The decode thread's open while Kotlin is still resolving the stream: a seek or skip interrupts it there.
@MainActor
struct ResolvingByteReaderTests {
    private final class Box: @unchecked Sendable {
        var error: Error?
        var done = false
        var asked = 0
        var askedAtMs: Int64?
        var answer: ((IosStream?, String?) -> Void)?
    }

    private let box = Box()

    private func reader(startMs: Int64 = 0) -> ResolvingByteReader {
        ResolvingByteReader(uid: 7, startMs: startMs) { [box] _, startMs, completion in
            box.asked += 1
            box.askedAtMs = startMs
            box.answer = completion
        }
    }

    private func readOnDecodeThread(_ reader: ResolvingByteReader) {
        box.done = false
        DispatchQueue.global().async { [box] in
            var byte: UInt8 = 0
            do { _ = try reader.read(into: &byte, maxLength: 1) } catch { box.error = error }
            box.done = true
        }
    }

    private func waitUntil(_ condition: () -> Bool) async -> Bool {
        let deadline = Date().addingTimeInterval(5)
        while Date() < deadline, !condition() {
            try? await Task.sleep(nanoseconds: 10_000_000)
        }
        return condition()
    }

    @Test func anInterruptEndsAnOpenWaitingOnTheResolve() async {
        let sut = reader()
        readOnDecodeThread(sut)
        #expect(await waitUntil { box.asked == 1 })

        sut.interrupt()

        #expect(await waitUntil { box.done })
        #expect(box.error as? StreamByteReaderError == .interrupted)
        sut.cancel()
    }

    @Test func anOpenAfterTheInterruptIsClearedWaitsOnTheSameResolve() async {
        let sut = reader()
        readOnDecodeThread(sut)
        #expect(await waitUntil { box.asked == 1 })
        sut.interrupt()
        #expect(await waitUntil { box.done })

        sut.clearInterrupt()
        readOnDecodeThread(sut)
        try? await Task.sleep(nanoseconds: 50_000_000)
        #expect(!box.done)
        box.answer?(nil, "refused")

        #expect(await waitUntil { box.done })
        #expect(box.error as? StreamByteReaderError == .failed(StreamFailure(.internal, detail: "refused")))
        #expect(box.asked == 1)
    }

    @Test func aTranscodeReOpenedPartwayAsksThereAndNamesTheWholeSongsDuration() async {
        let sut = reader(startMs: 90_000)
        readOnDecodeThread(sut)
        #expect(await waitUntil { box.asked == 1 })

        box.answer?(stream(bitrateKbps: 128, sizeBytes: -1, durationMs: 110_000, transcoded: true), nil)

        #expect(await waitUntil { box.done })
        #expect(box.askedAtMs == 90_000)
        #expect(sut.transcodeDurationMs == 200_000)
        sut.cancel()
    }

    @Test func aStreamServedAsStoredIsNoTranscode() async {
        let sut = reader()
        readOnDecodeThread(sut)
        #expect(await waitUntil { box.asked == 1 })

        box.answer?(stream(bitrateKbps: 128, sizeBytes: 8_000_000, durationMs: 200_000), nil)

        #expect(await waitUntil { box.done })
        #expect(sut.transcodeDurationMs == nil)
        sut.cancel()
    }

    @Test func aStreamOnAnExpensiveNetworkDownloadsAMinuteAtTheLibrarysBitrateAhead() {
        #expect(ResolvingByteReader.readAhead(stream(bitrateKbps: 128, sizeBytes: 8_000_000, durationMs: 200_000)) == GrowingFileReadAhead(bytes: 960_000))
        #expect(ResolvingByteReader.readAhead(stream(bitrateKbps: -1, sizeBytes: 6_000_000, durationMs: 240_000)) == GrowingFileReadAhead(bytes: 1_500_000))
        #expect(ResolvingByteReader.readAhead(stream(bitrateKbps: -1, sizeBytes: -1, durationMs: -1)) == GrowingFileReadAhead(bytes: 2_400_000))
    }

    private func stream(bitrateKbps: Int32, sizeBytes: Int64, durationMs: Int64, transcoded: Bool = false) -> IosStream {
        IosStream(
            url: "https://example.com/a.flac", headers: [:], gainDb: 0, opensAtPosition: true, transcoded: transcoded,
            bitrateKbps: bitrateKbps, sizeBytes: sizeBytes, durationMs: durationMs
        )
    }
}

/// An item that can't be opened, as a stream the resolver refused.
private final class FailingItemSource: ItemSource, @unchecked Sendable {
    struct Refused: Error {}

    func begin(epoch _: UInt64) {}
    func open() throws -> PCMFormat { throw Refused() }
    var frameCount: Int64? { nil }
    func seek(toFrame _: Int64) throws -> Int64 { throw Refused() }
    func nextChunk() throws -> [Float]? { throw Refused() }
    func interrupt(through _: UInt64) {}
    func release() {}
}

private final class RecordingListener: NSObject, IosAudioPlayerListener {
    var lastState: String?
    var transitions: [String] = []
    var failures: [String] = []
    var pausedAtEnd: [String] = []

    func onStateChanged(trackId: String, state: IosAudioPlayerState) {
        lastState = "\(trackId) \(state.name.lowercased())"
    }

    func onTransition(trackId: String) { transitions.append(trackId) }
    func onFailed(trackId: String, message _: String) { failures.append(trackId) }
    func onPosition(trackId _: String, positionMs _: Int64) {}
    func onPausedAtEnd(trackId: String) { pausedAtEnd.append(trackId) }
}
