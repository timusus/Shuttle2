import Foundation
import Shared
import Testing
@testable import S2

/// `PlayerModel`'s mapping off the real `IosPlayerController` (over a fake engine, as
/// `PlaybackSystemCoordinatorTests` drives it) and its commands forwarding back through the controller.
@MainActor
struct PlayerModelTests {
    private let engine = FakeAudioEngine()
    private let graph: IosAppGraph
    private let model: PlayerModel

    init() {
        graph = IosAppGraphKt.createIosAppGraph(audioPlayer: EngineAudioPlayer(engine: engine))
        model = PlayerModel(playback: graph.playerController)
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

    @Test func queueChangeUpdatesTheCurrentSongAndQueue() async throws {
        _ = try await loadQueue()
        #expect(await waitUntil { model.title == "Paranoid Android" })
        #expect(model.artist == "Radiohead")
        #expect(model.album == "OK Computer")
        #expect(model.queue.count == 5)
        #expect(model.queue.first?.isCurrent == true)
        #expect(model.queuePosition == 0)
    }

    @Test func playingStateFollowsTheEngine() async throws {
        let id = try await loadQueue()

        engine.emit(.state(.playing, trackId: id))
        #expect(await waitUntil { model.isPlaying })

        engine.emit(.state(.paused, trackId: id))
        #expect(await waitUntil { !model.isPlaying })
    }

    @Test func togglePlayPauseForwardsToTheEngine() async throws {
        _ = try await loadQueue()

        model.togglePlayPause()
        #expect(await waitUntil { engine.commands.contains("play") })
    }

    @Test func nextAndPreviousForwardToTheEngineAndUpdateTheQueue() async throws {
        _ = try await loadQueue()

        model.next()
        #expect(await waitUntil { model.title == "Hyperballad" })

        model.previous()
        #expect(await waitUntil { model.title == "Paranoid Android" })
    }

    @Test func playAtPositionSkipsToThatQueueItem() async throws {
        _ = try await loadQueue()

        model.play(at: 2)
        #expect(await waitUntil { model.title == "Teardrop" })
    }

    @Test func shuffleAndRepeatFollowTheQueueModes() async throws {
        _ = try await loadQueue()
        #expect(!model.shuffleOn)
        #expect(model.repeatMode == .off)

        model.toggleShuffle()
        #expect(await waitUntil { model.shuffleOn })

        model.toggleRepeat()
        #expect(await waitUntil { model.repeatMode == .all })
        model.toggleRepeat()
        #expect(await waitUntil { model.repeatMode == .one })
        #expect(model.nowPlayingState.repeatMode == .one)
        #expect(model.nowPlayingState.artwork != nil)
    }

    @Test func seekShowsTheTargetStraightAway() async throws {
        _ = try await loadQueue()
        model.seek(toMs: 42_000)
        #expect(model.positionMs == 42_000)
    }
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
