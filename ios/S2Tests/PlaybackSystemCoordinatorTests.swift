import MediaPlayer
import PlaybackNowPlaying
import PlaybackPlayer
import S2PlaybackTestSupport
import Shared
import Testing
@testable import S2

/// The system surfaces wired to the real Kotlin controller over the player on its test rig: Now Playing follows the
/// queue and the player, and remote commands become controller calls. The rig's items are 10 s long.
@MainActor
struct PlaybackSystemCoordinatorTests {
    private let rig = makeTestRig()
    private let info = FakeNowPlayingInfoCenter()
    private let commands = FakeRemoteCommandCenter()
    private let graph: IosAppGraph
    private let coordinator: PlaybackSystemCoordinator
    private let intent: PlayIntent

    init() {
        graph = makeTestGraph(rig: rig)
        intent = PlayIntent(following: graph.playerController)
        coordinator = makeTestCoordinator(rig: rig, graph: graph, intent: intent, info: info, commands: commands)
        coordinator.start()
    }

    private var title: String? { info[MPMediaItemPropertyTitle] }
    private var elapsed: TimeInterval? { info[MPNowPlayingInfoPropertyElapsedPlaybackTime] }
    private var duration: TimeInterval? { info[MPMediaItemPropertyPlaybackDuration] }
    private var rate: Double? { info[MPNowPlayingInfoPropertyPlaybackRate] }
    private var defaultRate: Double? { info[MPNowPlayingInfoPropertyDefaultPlaybackRate] }

    private var intendsToPlay: Bool {
        graph.playerController.playWhenReadyFlow.value.boolValue
    }

    /// Lets the session publish what the player's last change scheduled.
    private func settled() async {
        await coordinator.session?.settle()
    }

    /// Queues `songs` and loads the first, as far as the player having it ready, paused, and the lock screen's scrubber
    /// enabled (once the item is seekable).
    private func loadQueue(_ songs: [Song] = TestSongs.demo) async throws {
        try await beginLoad(songs)
        #expect(await rig.pump { graph.playerController.playbackStateFlow.value is Shared.PlaybackState.Paused && rig.player.isSeekable })
        await settled()
    }

    /// Queues `songs` and loads the first, paused, leaving the rig unpumped so nothing is decoded: a play then waits on
    /// its first audio.
    private func beginLoad(_ songs: [Song] = TestSongs.demo) async throws {
        let controller = graph.playerController
        _ = try await controller.queueOperations.setQueue(songs: songs, shuffleSongs: nil, position: 0, context: PlayContextNone.shared)
        controller.load(seekPosition: nil, skipUnloadable: false, playWhenReady: false) { _ in }
        #expect(await waitUntil { title == songs.first?.name && controller.playbackStateFlow.value is Shared.PlaybackState.Paused })
    }

    /// Loads the queue and plays it from the lock screen, as far as the player playing.
    private func playQueue() async throws {
        try await loadQueue()
        commands.press(.play)
        #expect(await rig.pump { graph.playerController.playbackStateFlow.value is Shared.PlaybackState.Playing })
    }

    @Test func startTakesTheTransportCommands() async {
        await settled()
        #expect(commands.enabled.isSuperset(of: [.play, .pause, .togglePlayPause]))
        #expect(commands.rates == [0.8, 1, 1.2, 1.5, 2])
    }

    @Test func nowPlayingFollowsTheQueue() async throws {
        try await loadQueue()
        #expect(await waitUntil { title == "Paranoid Android" })
        #expect(info[MPMediaItemPropertyArtist] as String? == "Radiohead")
        #expect(info[MPMediaItemPropertyAlbumTitle] as String? == "OK Computer")
        #expect(duration == 386)

        commands.press(.nextTrack)
        #expect(await waitUntil { title == "Hyperballad" })
        #expect(rig.player.currentItem?.songId == 2)
    }

    @Test func aMovementOfAWorkShowsTheComposerAndWork() async throws {
        let work = [
            TestSongs.song(11, "Allegro", artist: "Orchestra", album: "Disc", durationMs: 300_000, composer: "Beethoven", work: "Symphony No. 5", movementName: "Allegro", movementNumber: 1),
            TestSongs.song(12, "Andante", artist: "Orchestra", album: "Disc", durationMs: 300_000, composer: "Beethoven", work: "Symphony No. 5", movementName: "Andante", movementNumber: 2),
        ]
        let controller = graph.playerController
        _ = try await controller.queueOperations.setQueue(songs: work, shuffleSongs: nil, position: 0, context: PlayContextNone.shared)
        controller.load(seekPosition: nil, skipUnloadable: false, playWhenReady: false) { _ in }
        #expect(await waitUntil { title == "I. Allegro" })
        #expect(info[MPMediaItemPropertyArtist] as String? == "Beethoven: Symphony No. 5")
        #expect(info[MPMediaItemPropertyAlbumTitle] as String? == "Disc")

        commands.press(.nextTrack)
        #expect(await waitUntil { title == "II. Andante" })
    }

    @Test func playAndPauseFromTheLockScreenReflectBack() async throws {
        try await playQueue()
        #expect(await waitUntil { rate == 1 && info.playbackState == .playing })

        rig.run(for: 4)
        #expect(await waitUntil { elapsed == 4 })
        commands.press(.pause)
        #expect(!rig.player.playWhenReady)
        #expect(intent.lastCommand == PlayIntent.Command(plays: false, source: .remoteCommand))

        #expect(await rig.pump { rate == 0 && info.playbackState == .paused })
        #expect(elapsed == 4)
        #expect(title == "Paranoid Android")
    }

    /// A play waiting on its stream stops the lock screen's clock and keeps its pause button; the clock runs once the
    /// stream flows (#897).
    @Test func bufferingStopsTheLockScreenClockUntilItPlays() async throws {
        try await beginLoad()

        commands.press(.play)

        #expect(await waitUntil { graph.playerController.bufferingFlow.value.boolValue })
        #expect(await waitUntil { rate == 0 && info.playbackState == .playing })
        #expect(graph.playerController.playbackStateFlow.value is Shared.PlaybackState.Loading)

        #expect(await rig.pump { rate == 1 && info.playbackState == .playing })
    }

    /// A play still being prepared (a `MediaAction` reading its songs) shows the pause button from the tap, its clock
    /// stopped until audio, and its failure shows play again.
    @Test func aPendingPlayShowsAsPlayingOnTheLockScreen() async throws {
        try await loadQueue()
        #expect(await waitUntil { title == "Paranoid Android" && info.playbackState == .paused })

        let ticket = intent.begin()
        await settled()
        #expect(info.playbackState == .playing)
        #expect(rate == 0)

        intent.finished(ticket, result: MediaActionResultMessage(message: MediaActionMessageNoSongs.shared, action: nil))
        await settled()
        #expect(info.playbackState == .paused)
    }

    @Test func aRemoteSeekMovesThePlayerAndReflectsBack() async throws {
        try await loadQueue()

        commands.press(.changePlaybackPosition, .position(6))

        #expect(await rig.pump { rig.player.position == 6 })
        #expect(await waitUntil { elapsed == 6 })
        #expect(rate == 0)
    }

    /// The new song's metadata is published from its start, with its own duration.
    @Test func aTrackChangePublishesTheNewSongFromItsStart() async throws {
        try await playQueue()
        rig.run(for: 4)
        #expect(await waitUntil { elapsed == 4 })

        rig.run(for: 6)

        #expect(await waitUntil { title == "Hyperballad" })
        #expect(elapsed == 0)
        #expect(duration == 321)
        #expect(rate == 1)
    }

    @Test func aSongWithoutADurationShowsThePlayers() async throws {
        let song = TestSongs.song(9, "Untagged", artist: "Nobody", album: "Nothing", durationMs: 0)
        try await loadQueue([song])
        #expect(await waitUntil { title == "Untagged" })

        commands.press(.play)
        rig.run(for: 1)

        #expect(await waitUntil { duration == 10 })
    }

    /// The player's own pause for a call reaches the lock screen through the controller.
    @Test func anInterruptionPausesThePlayer() async throws {
        try await playQueue()

        rig.session.interruptionBegan()

        #expect(await rig.pump { !intendsToPlay && info.playbackState == .paused })
    }

    @Test func stopClearsNowPlaying() async throws {
        try await loadQueue()
        #expect(await waitUntil { title != nil })

        coordinator.stop()

        #expect(commands.enabled.isEmpty)
        #expect(title == nil)
    }

    @Test func aSkipHoldsThePauseButtonThroughLoading() async throws {
        try await playQueue()
        graph.playerController.setPlaybackSpeed(multiplier: 1.5)
        #expect(await waitUntil { rate == 1.5 && info.playbackState == .playing })

        commands.press(.nextTrack)

        #expect(await waitUntil { title == "Hyperballad" })
        #expect(elapsed == 0)
        #expect(defaultRate == 1.5)
        #expect(info.playbackState == .playing)
        #expect(rig.player.playWhenReady)

        #expect(await rig.pump { graph.playerController.playbackStateFlow.value is Shared.PlaybackState.Playing })
        #expect(await waitUntil { rate == 1.5 && info.playbackState == .playing })
    }

    @Test func theButtonFollowsIntentImmediatelyThroughLoading() async throws {
        try await beginLoad()
        #expect(!intendsToPlay)
        #expect(info.playbackState == .paused)
        #expect(rate == 0)

        graph.playerController.setPlaybackSpeed(multiplier: 1.5)
        #expect(await waitUntil { defaultRate == 1.5 })
        #expect(rate == 0)

        commands.press(.play)
        await settled()
        #expect(info.playbackState == .playing)
        #expect(intendsToPlay)
        #expect(await waitUntil { graph.playerController.playbackStateFlow.value is Shared.PlaybackState.Loading })
        #expect(info.playbackState == .playing)

        commands.press(.pause)
        #expect(await waitUntil { info.playbackState == .paused && rate == 0 })
        #expect(defaultRate == 1.5)
        #expect(!intendsToPlay)

        commands.press(.togglePlayPause)
        await settled()
        #expect(info.playbackState == .playing)
        #expect(intendsToPlay)
        #expect(await rig.pump { graph.playerController.playbackStateFlow.value is Shared.PlaybackState.Playing })
        #expect(await waitUntil { rate == 1.5 && info.playbackState == .playing })
    }
}
