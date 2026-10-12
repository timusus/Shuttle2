import Foundation
import PlaybackPlayer
import PlaybackRender
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

    // MARK: - With the Kotlin controller

    /// A graph whose controller drives `sut`; its listener replaces the test's.
    private func queue(_ songs: [Song]) async throws -> IosPlayerController {
        let controller = makeTestGraph(audioPlayer: sut).playerController
        _ = try await controller.queueOperations.setQueue(songs: songs, shuffleSongs: nil, position: 0, context: PlayContextNone.shared)
        controller.load(seekPosition: nil, skipUnloadable: true, playWhenReady: true) { _ in }
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

        #expect(await pumpUntil { currentSongId(controller) == 3 && rig.player.isPlaying })
        #expect(rig.player.currentItem?.songId == 3)
    }

    @Test func skippingStopsAfterTheCapOfFailuresInARow() async throws {
        let cap = 15 // PlaybackPolicy.MAX_LOAD_ATTEMPTS
        let controller = try await queue((1 ... cap).map { failing(Int64($0)) } + [TestSongs.demo[0]])

        #expect(await pumpUntil(20) { currentSongId(controller) == -Int64(cap) && controller.playbackStateFlow.value is Shared.PlaybackState.Paused })
        rig.run(for: 2)
        #expect(currentSongId(controller) == -Int64(cap))
        #expect(!rig.player.isPlaying)
    }

    private func failing(_ id: Int64) -> Song {
        TestSongs.song(-id, "Unplayable \(id)", artist: "Nobody", album: "Nowhere", durationMs: 10_000)
    }

    /// Pumps the rig (decodes and transport events) until `condition` holds, letting Kotlin's main-queue work run.
    private func pumpUntil(_ timeout: TimeInterval = 10, _ condition: () -> Bool) async -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            rig.pump()
            if condition() { return true }
            try? await Task.sleep(nanoseconds: 20_000_000)
        }
        return condition()
    }
}

/// An item that can't be opened, as a stream the resolver refused.
private final class FailingItemSource: ItemSource, @unchecked Sendable {
    struct Refused: Error {}

    func begin(epoch _: UInt64) {}
    func open() throws -> PCMFormat { throw Refused() }
    func seek(toFrame _: Int64) throws -> Int64 { throw Refused() }
    func nextChunk() throws -> [Float]? { throw Refused() }
    func interrupt(through _: UInt64) {}
}

private final class RecordingListener: NSObject, IosAudioPlayerListener {
    var lastState: String?
    var transitions: [String] = []
    var failures: [String] = []
    var pausedAtEnd: [String] = []

    func onStateChanged(trackId: String, state: IosAudioPlayerState, superseded _: Bool) {
        lastState = "\(trackId) \(state.name.lowercased())"
    }

    func onTransition(trackId: String) { transitions.append(trackId) }
    func onFailed(trackId: String, message _: String) { failures.append(trackId) }
    func onPosition(trackId _: String, positionMs _: Int64) {}
    func onSeekUnsupported(trackId _: String, positionMs _: Int64) {}
    func onPausedAtEnd(trackId: String) { pausedAtEnd.append(trackId) }
}
