import AVFoundation
import MediaPlayer
import Shared
import Testing
@testable import S2

/// The system surfaces wired to the real Kotlin controller over a fake engine: Now Playing follows the
/// queue, remote commands and session events become controller calls, and the session is activated
/// before anything plays.
@MainActor
struct PlaybackSystemCoordinatorTests {
    private final class FakeSession: AudioSession {
        private(set) var configured = 0
        private(set) var activations: [Bool] = []
        var failActivation = false
        private(set) var failedActivations = 0

        func setCategory(
            _ category: AVAudioSession.Category,
            mode: AVAudioSession.Mode,
            policy: AVAudioSession.RouteSharingPolicy,
            options: AVAudioSession.CategoryOptions
        ) throws {
            configured += 1
        }

        func setActive(_ active: Bool, options: AVAudioSession.SetActiveOptions) throws {
            if active && failActivation {
                failedActivations += 1
                throw NSError(domain: "FakeSession", code: 1)
            }
            activations.append(active)
        }
    }

    private final class FakeInfoCenter: NowPlayingInfoCenter {
        var nowPlayingInfo: [String: Any]? {
            didSet { history.append(nowPlayingInfo) }
        }
        var playbackState: MPNowPlayingPlaybackState = .unknown
        /// Every write, in order.
        private(set) var history: [[String: Any]?] = []

        func value<T>(_ key: String) -> T? { nowPlayingInfo?[key] as? T }
    }

    private final class FakeCommandCenter: RemoteCommandCenter {
        var handlers: [RemoteCommand: (RemoteCommandEvent) -> MPRemoteCommandHandlerStatus] = [:]

        func setHandler(for command: RemoteCommand, _ handler: ((RemoteCommandEvent) -> MPRemoteCommandHandlerStatus)?) {
            handlers[command] = handler
        }

        func setSkipIntervals(forward: TimeInterval, backward: TimeInterval) {}

        func fire(_ command: RemoteCommand, _ event: RemoteCommandEvent = .plain) {
            _ = handlers[command]?(event)
        }
    }

    private let engine = FakeAudioEngine()
    private let session = FakeSession()
    private let center = NotificationCenter()
    private let info = FakeInfoCenter()
    private let commands = FakeCommandCenter()
    private let graph: IosAppGraph
    private let coordinator: PlaybackSystemCoordinator
    private var rebuiltEngines: [FakeAudioEngine] = []

    init() {
        let player = EngineAudioPlayer(engine: engine)
        graph = makeTestGraph(audioPlayer: player)
        let rebuilt = FakeAudioEngine()
        rebuiltEngines = [rebuilt]
        coordinator = PlaybackSystemCoordinator(
            playback: graph.playerController,
            player: player,
            session: AudioSessionController(session: session, notificationCenter: center),
            nowPlaying: NowPlayingController(infoCenter: info, commandCenter: commands),
            makeEngine: { rebuilt }
        )
        coordinator.start()
    }

    private var title: String? {
        info.nowPlayingInfo?[MPMediaItemPropertyTitle] as? String
    }

    private var elapsed: TimeInterval? {
        info.value(MPNowPlayingInfoPropertyElapsedPlaybackTime)
    }

    private var rate: Double? {
        info.value(MPNowPlayingInfoPropertyPlaybackRate)
    }

    /// Queues `songs` and loads the first, as far as the engine reporting it ready.
    private func loadQueue(_ songs: [Song] = TestSongs.demo) async throws -> String {
        let controller = graph.playerController
        _ = try await controller.queueOperations.setQueue(songs: songs, shuffleSongs: nil, position: 0, context: PlayContextNone.shared)
        controller.load(seekPosition: nil, skipUnloadable: false) { _ in }
        #expect(await waitUntil { !engine.loads.isEmpty })
        let id = try #require(engine.loads.first?.current.id)
        engine.emit(.state(.paused, trackId: id))
        return id
    }

    @Test func startConfiguresTheSessionAndRegistersTheCommands() {
        #expect(session.configured == 1)
        #expect(commands.handlers[.play] != nil)
        #expect(commands.handlers[.nextTrack] != nil)
    }

    @Test func nowPlayingFollowsTheQueue() async throws {
        _ = try await loadQueue()
        #expect(await waitUntil { title == "Paranoid Android" })
        #expect(info.nowPlayingInfo?[MPMediaItemPropertyArtist] as? String == "Radiohead")
        #expect(info.nowPlayingInfo?[MPMediaItemPropertyPlaybackDuration] as? TimeInterval == 386)

        commands.fire(.nextTrack)
        #expect(await waitUntil { title == "Hyperballad" })
        #expect(engine.loads.last?.current.url.absoluteString == "demo://2")
    }

    /// Loads the queue and plays it, as far as the engine reporting it playing.
    private func playQueue() async throws -> String {
        let id = try await loadQueue()
        commands.fire(.play)
        #expect(await waitUntil { engine.commands.contains("play") })
        engine.emit(.state(.playing, trackId: id))
        return id
    }

    @Test func playAndPauseFromTheLockScreenReflectBack() async throws {
        let id = try await playQueue()
        #expect(await waitUntil { rate == 1 && info.playbackState == .playing })

        engine.emit(.position(trackId: id, ms: 42_000))
        commands.fire(.pause)
        #expect(await waitUntil { engine.commands.last == "pause" })
        engine.emit(.state(.paused, trackId: id))

        #expect(await waitUntil { rate == 0 && info.playbackState == .paused })
        #expect(elapsed == 42)
        #expect(title == "Paranoid Android")
    }

    @Test func aRemoteSeekMovesThePlayerAndReflectsBack() async throws {
        _ = try await loadQueue()
        #expect(await waitUntil { title != nil })

        commands.fire(.changePlaybackPosition, .position(90))

        #expect(await waitUntil { engine.commands.contains("seek 90000") })
        #expect(await waitUntil { elapsed == 90 })
        #expect(rate == 0)
    }

    /// The new song's metadata is never published with the last one's position, whichever of the queue
    /// and progress flows Swift hears about first.
    @Test func aTrackChangePublishesTheNewSongFromItsStart() async throws {
        let id = try await playQueue()
        engine.emit(.position(trackId: id, ms: 200_000))
        #expect(await waitUntil { elapsed == 200 })
        #expect(await waitUntil { engine.nexts.last??.id != nil })
        let nextId = try #require(engine.nexts.last??.id)

        engine.emit(.transition(trackId: nextId))

        #expect(await waitUntil { title == "Hyperballad" })
        let published = info.history.compactMap { $0 }.filter { $0[MPMediaItemPropertyTitle] as? String == "Hyperballad" }
        #expect(published.allSatisfy { ($0[MPNowPlayingInfoPropertyElapsedPlaybackTime] as? TimeInterval) == 0 })
        #expect(info.value(MPMediaItemPropertyPlaybackDuration) == 321.0)
        #expect(rate == 1)
    }

    @Test func aSongWithoutADurationShowsThePlayers() async throws {
        let song = TestSongs.song(9, "Untagged", artist: "Nobody", album: "Nothing", durationMs: 0)
        let id = try await loadQueue([song])
        #expect(await waitUntil { title == "Untagged" })
        #expect(info.nowPlayingInfo?[MPMediaItemPropertyPlaybackDuration] == nil)

        engine.position = (uid: id, ms: 1_000)
        engine.durationMs = 200_000
        engine.emit(.position(trackId: id, ms: 1_000))

        #expect(await waitUntil { info.value(MPMediaItemPropertyPlaybackDuration) == 200.0 })
    }

    @Test func playActivatesTheSessionBeforeTheEnginePlays() async throws {
        _ = try await loadQueue()

        commands.fire(.play)
        #expect(await waitUntil { engine.commands.contains("play") })
        #expect(session.activations == [true])
    }

    @Test func aPlayTheSessionWontActivateForStaysPaused() async throws {
        _ = try await loadQueue()
        #expect(await waitUntil { title == "Paranoid Android" && info.playbackState == .paused })
        session.failActivation = true

        commands.fire(.play)
        // The lock screen's play reaches the player, which asks for the session; the engine never plays.
        #expect(await waitUntil { session.failedActivations == 1 })
        #expect(!engine.commands.contains("play"))
        #expect(graph.playerController.playbackStateFlow.value is PlaybackState.Paused)
        #expect(info.playbackState == .paused)
        #expect(rate == 0)
    }

    @Test func anInterruptionPausesThePlayer() async throws {
        let id = try await loadQueue()
        commands.fire(.play)
        engine.emit(.state(.playing, trackId: id))
        #expect(await waitUntil { graph.playerController.playbackStateFlow.value is PlaybackState.Playing })

        center.post(name: AVAudioSession.interruptionNotification, object: session, userInfo: [
            AVAudioSessionInterruptionTypeKey: AVAudioSession.InterruptionType.began.rawValue,
        ])

        #expect(await waitUntil { engine.commands.contains("pause") })
    }

    @Test func aMediaServicesResetReloadsIntoANewEngine() async throws {
        _ = try await loadQueue()

        center.post(name: AVAudioSession.mediaServicesWereResetNotification, object: session)

        let rebuilt = try #require(rebuiltEngines.first)
        #expect(await waitUntil { !rebuilt.loads.isEmpty })
        #expect(rebuilt.loads.first?.current.url.absoluteString == "demo://1")
        #expect(engine.commands.last == "stop")
    }

    @Test func stopClearsNowPlaying() async throws {
        _ = try await loadQueue()
        #expect(await waitUntil { title != nil })

        coordinator.stop()

        #expect(commands.handlers.values.isEmpty)
        #expect(title == nil)
    }
}
