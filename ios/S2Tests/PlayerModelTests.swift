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
        graph = IosAppGraph(audioPlayer: EngineAudioPlayer(engine: engine))
        model = PlayerModel(playback: graph.playerController)
    }

    /// Queues the demo library and loads its first song, as far as the engine reporting it ready.
    private func loadQueue() async throws -> String {
        let controller = graph.playerController
        _ = try await controller.queueOperations.setQueue(songs: graph.librarySongs.value, shuffleSongs: nil, position: 0)
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
}
