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

        func setCategory(
            _ category: AVAudioSession.Category,
            mode: AVAudioSession.Mode,
            policy: AVAudioSession.RouteSharingPolicy,
            options: AVAudioSession.CategoryOptions
        ) throws {
            configured += 1
        }

        func setActive(_ active: Bool, options: AVAudioSession.SetActiveOptions) throws {
            activations.append(active)
        }
    }

    private final class FakeInfoCenter: NowPlayingInfoCenter {
        var nowPlayingInfo: [String: Any]?
        var playbackState: MPNowPlayingPlaybackState = .unknown
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

    @Test func playActivatesTheSessionBeforeTheEnginePlays() async throws {
        _ = try await loadQueue()

        commands.fire(.play)
        #expect(await waitUntil { engine.commands.contains("play") })
        #expect(session.activations == [true])
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
