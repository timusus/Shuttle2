import MediaPlayer
import PlaybackNowPlaying
import PlaybackPlayer
import S2PlaybackTestSupport
import Shared
import Testing
@testable import S2

/// The lock screen's buttons as `NowPlayingPlayer` offers them, and where they go: through the Kotlin controller, so
/// its policy decides, not the Player's. The rig's items are 10 s long.
@MainActor
struct NowPlayingPlayerTests {
    private let rig = makeTestRig()
    private let info = FakeNowPlayingInfoCenter()
    private let commands = FakeRemoteCommandCenter()
    private let graph: IosAppGraph
    private let coordinator: PlaybackSystemCoordinator
    private let intent: PlayIntent

    private static let audiobook = TestSongs.song(9, "Chapter One", artist: "Narrator", album: "The Book", durationMs: 600_000, path: "demo://audiobooks/9.m4b")
    private static let podcast = TestSongs.song(10, "Episode One", artist: "Host", album: "The Show", durationMs: 600_000, path: "demo://podcasts/10")

    init() {
        graph = makeTestGraph(rig: rig)
        intent = PlayIntent(following: graph.playerController)
        coordinator = makeTestCoordinator(rig: rig, graph: graph, intent: intent, info: info, commands: commands)
        coordinator.start()
    }

    private var title: String? { info[MPMediaItemPropertyTitle] }

    /// Queues `songs` and loads the one at `position`, paused and ready, with the lock screen showing it.
    private func loadQueue(_ songs: [Song] = TestSongs.demo, position: Int32 = 0) async throws {
        let controller = graph.playerController
        _ = try await controller.queueOperations.setQueue(songs: songs, shuffleSongs: nil, position: position, context: PlayContextNone.shared)
        controller.load(seekPosition: nil, skipUnloadable: false, playWhenReady: false) { _ in }
        #expect(await rig.pump { controller.playbackStateFlow.value is Shared.PlaybackState.Paused && rig.player.isSeekable })
        #expect(await waitUntil { title == songs[Int(position)].name })
        await coordinator.session?.settle()
    }

    @Test func musicSkipsBetweenSongs() async throws {
        try await loadQueue()

        #expect(commands.enabled.isSuperset(of: [.nextTrack, .previousTrack]))
        #expect(commands.enabled.isDisjoint(with: [.skipForward, .skipBackward]))
        #expect(commands.intervals == nil)
    }

    @Test(arguments: [audiobook, podcast])
    func spokenWordSkipsWithinTheSong(_ song: Song) async throws {
        try await loadQueue([song])

        // Next and previous stay registered for controls that send only them (CarPlay, headphones).
        #expect(commands.enabled.isSuperset(of: [.skipForward, .skipBackward]))
        #expect(commands.intervals?.forward == 30)
        #expect(commands.intervals?.backward == 10)
    }

    @Test func skipForwardSeeksThroughTheController() async throws {
        try await loadQueue([Self.audiobook])

        commands.press(.skipForward, .interval(3))

        #expect(await rig.pump { rig.player.position == 3 })
        #expect(await waitUntil { graph.playerController.getProgress()?.int32Value == 3000 })
    }

    /// Past the restart threshold, previous restarts the song (Kotlin's rule); the Player's own would go back a song.
    @Test func previousPastTheThresholdRestartsTheSong() async throws {
        try await loadQueue(position: 1)
        commands.press(.play)
        rig.run(for: 4)
        #expect(await waitUntil { graph.playerController.getProgress()?.int32Value ?? 0 >= 4000 })

        commands.press(.previousTrack)

        #expect(await rig.pump { rig.player.position == 0 })
        #expect(rig.player.currentItem?.songId == 2)
        #expect(title == "Hyperballad")
    }

    /// Next at the end of the queue wraps to its start (Kotlin's rule), so the button stays enabled.
    @Test func nextAtTheEndWrapsTheQueue() async throws {
        try await loadQueue(Array(TestSongs.demo.prefix(2)), position: 1)
        #expect(commands.enabled.contains(.nextTrack))

        commands.press(.nextTrack)

        #expect(await waitUntil { title == "Paranoid Android" })
        #expect(rig.player.currentItem?.songId == 1)
    }

    @Test func aRateCommandSetsTheControllersSpeed() async throws {
        try await loadQueue()

        commands.press(.changePlaybackRate, .rate(1.5))

        #expect(await waitUntil { graph.playerController.getPlaybackSpeed() == 1.5 })
        #expect(await waitUntil { info[MPNowPlayingInfoPropertyDefaultPlaybackRate] as Double? == 1.5 })
    }

    @Test func aRemotePlayIsTheListeners() async throws {
        try await loadQueue()

        commands.press(.play)

        #expect(intent.lastCommand == PlayIntent.Command(plays: true, source: .remoteCommand))
        #expect(rig.player.playWhenReady)
    }
}
