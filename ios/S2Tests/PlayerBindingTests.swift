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
        graph = IosAppGraphKt.createIosAppGraph(audioPlayer: EngineAudioPlayer(engine: engine))
        binding = PlayerBinding(viewModel: IosAppGraphKt.createPlayerViewModel(graph))
    }

    /// Queues the demo songs and loads the first, as far as the engine reporting it ready.
    private func loadQueue() async throws -> String {
        let controller = graph.playerController
        _ = try await controller.queueOperations.setQueue(songs: TestSongs.demo, shuffleSongs: nil, position: 0)
        controller.load(seekPosition: nil, skipUnloadable: false) { _ in }
        #expect(await waitUntil { !engine.loads.isEmpty })
        let id = try #require(engine.loads.first?.current.id)
        engine.emit(.state(.paused, trackId: id))
        return id
    }

    @Test func startsIdleWithNothingQueued() {
        #expect(binding.nowPlaying == .idle)
        #expect(binding.miniPlayer == MiniPlayerState())
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

        binding.actions.selectQueueItem(2)
        #expect(await waitUntil { binding.nowPlaying.title == "Teardrop" })
        #expect(binding.nowPlaying.queue[2].isCurrent)
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

        binding.actions.startSleepTimer(15)
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
