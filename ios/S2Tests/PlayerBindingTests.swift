import Foundation
import Shared
import Testing
@testable import S2

/// `PlayerBinding`'s mapping of the shared `PlayerViewModel` (built by the real graph over a fake engine, as
/// `PlaybackSystemCoordinatorTests` drives it) and its commands forwarding back through the ViewModel.
@MainActor
struct PlayerBindingTests {
    private let engine = FakeAudioEngine()
    private let graph: IosAppGraph
    private let binding: PlayerBinding

    init() {
        graph = makeTestGraph(audioPlayer: EngineAudioPlayer(engine: engine))
        binding = PlayerBinding(viewModel: IosAppGraphKt.createPlayerViewModel(graph), intent: PlayIntent(following: graph.playerController))
    }

    /// Queues the demo songs and loads the first, as far as the engine reporting it ready.
    private func loadQueue() async throws -> String {
        let controller = graph.playerController
        _ = try await controller.queueOperations.setQueue(songs: TestSongs.demo, shuffleSongs: nil, position: 0, context: PlayContextNone.shared)
        controller.load(seekPosition: nil, skipUnloadable: false) { _ in }
        #expect(await waitUntil { !engine.loads.isEmpty })
        let id = try #require(engine.loads.first?.current.id)
        engine.emit(.state(.paused, trackId: id))
        return id
    }

    @Test func startsIdleWithNothingQueued() {
        #expect(graph.playerController.queueOperations.hasRestoredQueue)
        #expect(binding.nowPlaying == .idle)
        #expect(binding.miniPlayer == MiniPlayerState())
        #expect(!binding.isMiniPlayerVisible)
    }

    @Test func theMiniPlayerShowsWhileASongIsCurrentEvenPaused() async throws {
        let id = try await loadQueue()
        #expect(await waitUntil { binding.isMiniPlayerVisible })

        engine.emit(.state(.paused, trackId: id))
        #expect(await waitUntil { !binding.miniPlayer.isPlaying })
        #expect(binding.isMiniPlayerVisible)
    }

    @Test func queueChangeUpdatesTheCurrentSongAndQueue() async throws {
        _ = try await loadQueue()
        #expect(await waitUntil { binding.nowPlaying.title == "Paranoid Android" })
        #expect(binding.nowPlaying.artist == "Radiohead")
        #expect(binding.nowPlaying.album == "OK Computer")
        #expect(binding.nowPlaying.artwork != nil)
        #expect(binding.nowPlaying.queue.count == 5)
        #expect(binding.nowPlaying.queue.first?.isCurrent == true)
        #expect(binding.miniPlayer.title == "Paranoid Android")
        #expect(binding.miniPlayer.artist == "Radiohead")
    }

    @Test func playingStateFollowsTheEngine() async throws {
        let id = try await loadQueue()

        engine.emit(.state(.playing, trackId: id))
        #expect(await waitUntil { binding.miniPlayer.isPlaying && binding.nowPlaying.isPlaying })

        engine.emit(.state(.paused, trackId: id))
        #expect(await waitUntil { !binding.miniPlayer.isPlaying })
    }

    @Test func playPauseForwardsToTheEngine() async throws {
        _ = try await loadQueue()

        binding.actions.playPause()
        #expect(await waitUntil { engine.commands.contains("play") })
    }

    /// The transport draws the listener's intent: pause and the spinner from the tap, before the engine has played,
    /// and a second tap during the load pauses at once.
    @Test func playPauseShowsTheIntentFromTheTap() async throws {
        let id = try await loadQueue()
        #expect(await waitUntil { binding.isMiniPlayerVisible })
        #expect(!binding.miniPlayer.isPlaying)

        binding.actions.playPause()
        #expect(binding.miniPlayer.isPlaying && binding.miniPlayer.isLoading)
        #expect(binding.nowPlaying.isPlaying && binding.nowPlaying.isLoading)

        engine.emit(.state(.playing, trackId: id))
        #expect(await waitUntil { !binding.miniPlayer.isLoading })
        #expect(binding.miniPlayer.isPlaying)

        binding.actions.playPause()
        #expect(!binding.miniPlayer.isPlaying && !binding.miniPlayer.isLoading)
        #expect(!binding.nowPlaying.isPlaying)
    }

    @Test func nextAndPreviousForwardToTheEngineAndUpdateTheQueue() async throws {
        _ = try await loadQueue()
        #expect(await waitUntil { binding.nowPlaying.title == "Paranoid Android" })

        binding.actions.next()
        #expect(await waitUntil { binding.nowPlaying.title == "Hyperballad" })

        binding.actions.previous()
        #expect(await waitUntil { binding.nowPlaying.title == "Paranoid Android" })
    }

    @Test func selectingAQueueRowSkipsToThatItem() async throws {
        _ = try await loadQueue()
        #expect(await waitUntil { binding.nowPlaying.queue.count == 5 })

        binding.actions.selectQueueItem(binding.nowPlaying.queue[2].id)
        #expect(await waitUntil { binding.nowPlaying.title == "Teardrop" })
        #expect(binding.nowPlaying.queue[2].isCurrent)
    }

    @Test func movingARowPutsItAfterTheGivenOne() async throws {
        _ = try await loadQueue()
        #expect(await waitUntil { binding.nowPlaying.queue.count == 5 })
        let ids = binding.nowPlaying.queue.map(\.id)

        binding.actions.moveQueueItem(ids[4], ids[0])
        #expect(await waitUntil { binding.nowPlaying.queue.map(\.id) == [ids[0], ids[4], ids[1], ids[2], ids[3]] })

        binding.actions.moveQueueItem(ids[2], nil)
        #expect(await waitUntil { binding.nowPlaying.queue.first?.id == ids[2] })
    }

    @Test func playNextMovesARowAfterTheCurrentSong() async throws {
        _ = try await loadQueue()
        #expect(await waitUntil { binding.nowPlaying.queue.count == 5 })
        let ids = binding.nowPlaying.queue.map(\.id)

        binding.actions.playNext(ids[3])
        #expect(await waitUntil { binding.nowPlaying.queue.map(\.id) == [ids[0], ids[3], ids[1], ids[2], ids[4]] })
    }

    @Test func removingARowOffersUndo() async throws {
        _ = try await loadQueue()
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
        _ = try await loadQueue()
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
        _ = try await loadQueue()
        #expect(await waitUntil { binding.nowPlaying.songActions.contains(.goToAlbum) })
        #expect(binding.nowPlaying.songActions.contains(.addToPlaylist))
        #expect(binding.nowPlaying.songActions.contains(.goToArtist))
    }

    @Test func goToAlbumAsksNowPlayingToOpenIt() async throws {
        _ = try await loadQueue()
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
        _ = try await loadQueue()
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
        _ = try await loadQueue()

        binding.actions.setSpeed(1.5)
        #expect(await waitUntil { binding.nowPlaying.playbackSpeed == 1.5 })
    }

    @Test func theSleepTimerRunsUntilStopped() async throws {
        _ = try await loadQueue()

        binding.actions.startSleepTimer(15, false)
        #expect(await waitUntil { binding.nowPlaying.sleepTimerActive })

        binding.actions.stopSleepTimer()
        #expect(await waitUntil { !binding.nowPlaying.sleepTimerActive })
    }

    @Test func seekShowsTheTargetStraightAway() async throws {
        _ = try await loadQueue()
        binding.seek(toMs: 42_000)
        #expect(binding.nowPlaying.positionMs == 42_000)
    }

    @Test func aProgressTickMovesNowPlayingButNotTheMiniPlayer() async throws {
        let id = try await loadQueue()
        engine.emit(.state(.playing, trackId: id))
        #expect(await waitUntil { binding.miniPlayer.isPlaying && binding.miniPlayer.title != nil })

        let miniPlayerChanged = ChangeFlag()
        withObservationTracking { _ = binding.miniPlayer } onChange: { miniPlayerChanged.set() }
        engine.emit(.position(trackId: id, ms: 30_000))
        #expect(await waitUntil { binding.nowPlaying.positionMs == 30_000 })
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

/// The seek target stays on screen until the player's reported position catches up with it.
struct SeekHoldTests {
    private let start = Date(timeIntervalSinceReferenceDate: 0)

    @Test func passesReportedPositionsThroughWithoutASeek() {
        var hold = SeekHold()
        #expect(hold.displayed(reportedMs: 1_000, now: start) == 1_000)
    }

    @Test func holdsTheTargetUntilTheReportCatchesUp() {
        var hold = SeekHold()
        hold.begin(60_000, now: start)
        #expect(hold.displayed(reportedMs: 10_000, now: start.addingTimeInterval(0.3)) == 60_000)
        #expect(hold.displayed(reportedMs: 59_000, now: start.addingTimeInterval(0.6)) == 59_000)
        #expect(hold.targetMs == nil)
        #expect(hold.displayed(reportedMs: 10_000, now: start.addingTimeInterval(0.9)) == 10_000)
    }

    @Test func releasesTheHoldAfterTheTimeout() {
        var hold = SeekHold()
        hold.begin(60_000, now: start)
        #expect(hold.displayed(reportedMs: 10_000, now: start.addingTimeInterval(SeekHold.timeout)) == 10_000)
        #expect(hold.targetMs == nil)
    }
}
