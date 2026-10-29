import Foundation
import PlaybackPlayer
import S2PlaybackTestSupport
import Shared
import Testing
@testable import S2

/// `PlayerBinding`'s mapping of the shared `PlayerViewModel` (built by the real graph over the player on its test rig)
/// and its commands forwarding back through the ViewModel.
@MainActor
struct PlayerBindingTests {
    private let rig = makeTestRig()
    private let graph: IosAppGraph
    private let binding: PlayerBinding

    init() {
        graph = makeTestGraph(rig: rig)
        binding = PlayerBinding(viewModel: IosAppGraphKt.createPlayerViewModel(graph), intent: PlayIntent(following: graph.playerController))
    }

    /// Queues the demo songs and loads the first, as far as the player having it ready, paused.
    private func loadQueue() async throws {
        let controller = graph.playerController
        _ = try await controller.queueOperations.setQueue(songs: TestSongs.demo, shuffleSongs: nil, position: 0, context: PlayContextNone.shared)
        controller.load(seekPosition: nil, skipUnloadable: false, playWhenReady: false) { _ in }
        #expect(await rig.pump { rig.player.state == .ready })
    }

    @Test func startsIdleWithNothingQueued() {
        #expect(graph.playerController.queueOperations.hasRestoredQueue)
        #expect(binding.nowPlaying == .idle)
        #expect(binding.miniPlayer == MiniPlayerState())
        #expect(!binding.isMiniPlayerVisible)
    }

    @Test func theMiniPlayerShowsWhileASongIsCurrentEvenPaused() async throws {
        try await loadQueue()
        #expect(await waitUntil { binding.isMiniPlayerVisible })

        #expect(await waitUntil { !binding.miniPlayer.isPlaying })
        #expect(binding.isMiniPlayerVisible)
    }

    @Test func queueChangeUpdatesTheCurrentSongAndQueue() async throws {
        try await loadQueue()
        #expect(await waitUntil { binding.nowPlaying.title == "Paranoid Android" })
        #expect(binding.nowPlaying.artist == "Radiohead")
        #expect(binding.nowPlaying.album == "OK Computer")
        #expect(binding.nowPlaying.artwork != nil)
        #expect(binding.nowPlaying.queue.count == 5)
        #expect(binding.nowPlaying.queue.first?.isCurrent == true)
        #expect(binding.miniPlayer.title == "Paranoid Android")
        #expect(binding.miniPlayer.artist == "Radiohead")
    }

    /// The player's own plays and pauses, as an interruption's, reach the transport.
    @Test func playingStateFollowsThePlayer() async throws {
        try await loadQueue()

        rig.player.play()
        #expect(await rig.pump { binding.miniPlayer.isPlaying && binding.nowPlaying.isPlaying })

        rig.player.pause()
        #expect(await rig.pump { !binding.miniPlayer.isPlaying })
    }

    @Test func playPauseForwardsToThePlayer() async throws {
        try await loadQueue()

        binding.actions.playPause()
        #expect(await rig.pump { rig.player.isPlaying })
    }

    /// The favourite flag lives in the library, so the song is seeded there: a demo song the library doesn't hold has
    /// nothing to flip.
    @Test func theFavouriteActionFlipsTheLibrarySongsFavourite() async throws {
        try await graph.songRepository.insert(songs: [TestSongs.demo[0]], mediaProviderType: .shuttle)
        let stored = try await graph.songRepository.loadSongs(
            query: SongQuery.All(includeExcluded: false, sortOrder: .default, providerType: nil)
        )
        let song = try #require(stored.first)
        _ = try await graph.playerController.queueOperations.setQueue(songs: [song], shuffleSongs: nil, position: 0, context: PlayContextNone.shared)
        graph.playerController.load(seekPosition: nil, skipUnloadable: false, playWhenReady: false) { _ in }
        #expect(await waitUntil { binding.nowPlaying.title == "Paranoid Android" })
        #expect(!binding.nowPlaying.isFavourite)

        binding.actions.toggleFavourite()
        #expect(await waitUntil { binding.nowPlaying.isFavourite })

        binding.actions.toggleFavourite()
        #expect(await waitUntil { !binding.nowPlaying.isFavourite })
    }

    /// The transport draws the listener's intent: pause and the spinner from the tap, before the player has played,
    /// and a second tap during the load pauses at once.
    @Test func playPauseShowsTheIntentFromTheTap() async throws {
        let controller = graph.playerController
        _ = try await controller.queueOperations.setQueue(songs: TestSongs.demo, shuffleSongs: nil, position: 0, context: PlayContextNone.shared)
        controller.load(seekPosition: nil, skipUnloadable: false, playWhenReady: false) { _ in }
        // Loaded with nothing decoded (the rig decodes only when pumped), so the play waits on its first audio.
        #expect(await waitUntil { binding.isMiniPlayerVisible && rig.player.state == .buffering })
        #expect(!binding.miniPlayer.isPlaying)

        binding.actions.playPause()
        #expect(binding.miniPlayer.isPlaying && binding.nowPlaying.isPlaying)
        #expect(await waitUntil { binding.miniPlayer.isPlaying && binding.miniPlayer.isLoading })
        #expect(binding.nowPlaying.isPlaying && binding.nowPlaying.isLoading)

        #expect(await rig.pump { !binding.miniPlayer.isLoading })
        #expect(binding.miniPlayer.isPlaying)

        binding.actions.playPause()
        #expect(!binding.miniPlayer.isPlaying && !binding.miniPlayer.isLoading)
        #expect(!binding.nowPlaying.isPlaying)
    }

    @Test func nextAndPreviousForwardToTheEngineAndUpdateTheQueue() async throws {
        try await loadQueue()
        #expect(await waitUntil { binding.nowPlaying.title == "Paranoid Android" })

        binding.actions.next()
        #expect(await waitUntil { binding.nowPlaying.title == "Hyperballad" })

        binding.actions.previous()
        #expect(await waitUntil { binding.nowPlaying.title == "Paranoid Android" })
    }

    @Test func selectingAQueueRowSkipsToThatItem() async throws {
        try await loadQueue()
        #expect(await waitUntil { binding.nowPlaying.queue.count == 5 })

        binding.actions.selectQueueItem(binding.nowPlaying.queue[2].id)
        #expect(await waitUntil { binding.nowPlaying.title == "Teardrop" })
        #expect(binding.nowPlaying.queue[2].isCurrent)
    }

    @Test func movingARowPutsItAfterTheGivenOne() async throws {
        try await loadQueue()
        #expect(await waitUntil { binding.nowPlaying.queue.count == 5 })
        let ids = binding.nowPlaying.queue.map(\.id)

        binding.actions.moveQueueItem(ids[4], ids[0])
        #expect(await waitUntil { binding.nowPlaying.queue.map(\.id) == [ids[0], ids[4], ids[1], ids[2], ids[3]] })

        binding.actions.moveQueueItem(ids[2], nil)
        #expect(await waitUntil { binding.nowPlaying.queue.first?.id == ids[2] })
    }

    @Test func playNextMovesARowAfterTheCurrentSong() async throws {
        try await loadQueue()
        #expect(await waitUntil { binding.nowPlaying.queue.count == 5 })
        let ids = binding.nowPlaying.queue.map(\.id)

        binding.actions.playNext(ids[3])
        #expect(await waitUntil { binding.nowPlaying.queue.map(\.id) == [ids[0], ids[3], ids[1], ids[2], ids[4]] })
    }

    @Test func removingARowOffersUndo() async throws {
        try await loadQueue()
        #expect(await waitUntil { binding.nowPlaying.queue.count == 5 })
        let removed = binding.nowPlaying.queue[3].id

        binding.actions.removeQueueItem(removed)
        #expect(await waitUntil { binding.nowPlaying.queue.count == 4 })
        #expect(!binding.nowPlaying.queue.contains { $0.id == removed })
        #expect(await waitUntil { binding.events.contains { $0.value is PlayerUiEventQueueItemRemoved } })

        let event = try #require(binding.events.last)
        guard case .notice(let notice) = binding.outcome(for: try #require(event.value)) else {
            Issue.record("Expected a notice")
            return
        }
        binding.eventHandled(event.id)
        #expect(await waitUntil { binding.events.isEmpty })
        notice.action?()
        #expect(await waitUntil { binding.nowPlaying.queue.count == 5 })
    }

    @Test func clearingTheQueueOffersUndo() async throws {
        try await loadQueue()
        #expect(await waitUntil { binding.nowPlaying.queue.count == 5 })

        binding.actions.clearQueue()
        #expect(await waitUntil { binding.nowPlaying.queue.isEmpty })
        #expect(await waitUntil { binding.events.contains { $0.value is PlayerUiEventQueueCleared } })

        let event = try #require(binding.events.last)
        guard case .notice(let notice) = binding.outcome(for: try #require(event.value)) else {
            Issue.record("Expected a notice")
            return
        }
        #expect(notice.message == "Queue cleared")
        notice.action?()
        #expect(await waitUntil { binding.nowPlaying.queue.count == 5 })
    }

    @Test func theCurrentSongOffersTheActionsIOSCanRun() async throws {
        try await loadQueue()
        #expect(await waitUntil { binding.nowPlaying.songActions.contains(.goToAlbum) })
        #expect(binding.nowPlaying.songActions.contains(.addToPlaylist))
        #expect(binding.nowPlaying.songActions.contains(.goToArtist))
    }

    @Test func goToAlbumAsksNowPlayingToOpenIt() async throws {
        try await loadQueue()
        #expect(await waitUntil { binding.nowPlaying.title != nil })

        binding.actions.songAction(.goToAlbum)
        #expect(await waitUntil { !binding.events.isEmpty })
        let event = try #require(binding.events.last?.value)
        // A demo song isn't in the library, so the ViewModel either opens the album or says it's missing.
        switch binding.outcome(for: event) {
        case .open(let route):
            if case .album = route {} else { Issue.record("Expected the album, got \(route)") }
        case .notice(let notice): #expect(notice.message == "Not in your library")
        case nil: Issue.record("Expected an outcome")
        }
    }

    @Test func shuffleAndRepeatFollowTheQueueModes() async throws {
        try await loadQueue()
        #expect(await waitUntil { binding.nowPlaying.title != nil })
        #expect(!binding.nowPlaying.shuffleOn)
        #expect(binding.nowPlaying.repeatMode == .off)

        binding.actions.toggleShuffle()
        #expect(await waitUntil { binding.nowPlaying.shuffleOn })

        binding.actions.toggleRepeat()
        #expect(await waitUntil { binding.nowPlaying.repeatMode == .all })
        binding.actions.toggleRepeat()
        #expect(await waitUntil { binding.nowPlaying.repeatMode == .one })
    }

    @Test func speedFollowsTheController() async throws {
        try await loadQueue()

        binding.actions.setSpeed(1.5)
        #expect(await waitUntil { binding.nowPlaying.playbackSpeed == 1.5 })
    }

    @Test func theSleepTimerRunsUntilStopped() async throws {
        try await loadQueue()

        binding.actions.startSleepTimer(15, false)
        #expect(await waitUntil { binding.nowPlaying.sleepTimerActive })

        binding.actions.stopSleepTimer()
        #expect(await waitUntil { !binding.nowPlaying.sleepTimerActive })
    }

    @Test func seekShowsTheTargetStraightAway() async throws {
        try await loadQueue()
        binding.seek(toMs: 42_000)
        #expect(binding.nowPlaying.positionMs == 42_000)
    }

    @Test func aProgressTickMovesNowPlayingButNotTheMiniPlayer() async throws {
        try await loadQueue()
        rig.player.play()
        #expect(await rig.pump { binding.miniPlayer.isPlaying && binding.miniPlayer.title != nil })

        let miniPlayerChanged = ChangeFlag()
        withObservationTracking { _ = binding.miniPlayer } onChange: { miniPlayerChanged.set() }
        rig.run(for: 3)
        #expect(await waitUntil { binding.nowPlaying.positionMs == 3_000 })
        #expect(!miniPlayerChanged.value)
    }
}

/// Whether an observation's `onChange` fired; `onChange` may run off the main actor.
private final class ChangeFlag: @unchecked Sendable {
    private let lock = NSLock()
    private var fired = false

    var value: Bool { lock.withLock { fired } }

    func set() { lock.withLock { fired = true } }
}

/// A work of two movements in the queue: Now Playing and the mini player show it as classical text.
@MainActor
struct PlayerBindingClassicalTests {
    private let rig = makeTestRig()
    private let graph: IosAppGraph
    private let binding: PlayerBinding

    init() {
        graph = makeTestGraph(rig: rig)
        binding = PlayerBinding(viewModel: IosAppGraphKt.createPlayerViewModel(graph), intent: PlayIntent(following: graph.playerController))
    }

    @Test func aWorkSongGetsClassicalTextAndARibbon() async throws {
        let songs = [
            TestSongs.song(1, "Allegro", artist: "Kleiber", album: "Beethoven 5", durationMs: 400_000, composer: "Beethoven", work: "Symphony No. 5", movementName: "Allegro", movementNumber: 1),
            TestSongs.song(2, "Andante", artist: "Kleiber", album: "Beethoven 5", durationMs: 600_000, composer: "Beethoven", work: "Symphony No. 5", movementName: "Andante", movementNumber: 2),
        ]
        let controller = graph.playerController
        _ = try await controller.queueOperations.setQueue(songs: songs, shuffleSongs: nil, position: 0, context: PlayContextNone.shared)
        controller.load(seekPosition: nil, skipUnloadable: false, playWhenReady: false) { _ in }
        #expect(await waitUntil { binding.nowPlaying.work != nil })

        let work = try #require(binding.nowPlaying.work)
        #expect(work.work == "Symphony No. 5")
        #expect(work.movements.count == 2)
        #expect(binding.miniPlayer.title == work.miniTitle)
        #expect(binding.miniPlayer.artist == work.miniSubtitle)
    }

    @Test func aPlainSongHasNoWork() async throws {
        let controller = graph.playerController
        _ = try await controller.queueOperations.setQueue(songs: TestSongs.demo, shuffleSongs: nil, position: 0, context: PlayContextNone.shared)
        controller.load(seekPosition: nil, skipUnloadable: false, playWhenReady: false) { _ in }
        #expect(await waitUntil { binding.nowPlaying.title == "Paranoid Android" })
        #expect(binding.nowPlaying.work == nil)
        #expect(binding.miniPlayer.artist == "Radiohead")
    }
}
