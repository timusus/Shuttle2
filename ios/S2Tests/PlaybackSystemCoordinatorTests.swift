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
        var playbackState: MPNowPlayingPlaybackState = .unknown {
            didSet { if playbackState != oldValue { playbackStates.append(playbackState) } }
        }
        /// Every write, in order.
        private(set) var history: [[String: Any]?] = []
        /// Every playback-state change, in order.
        private(set) var playbackStates: [MPNowPlayingPlaybackState] = []

        func value<T>(_ key: String) -> T? { nowPlayingInfo?[key] as? T }
    }

    private final class EngineSource {
        var make: () -> AudioEngine?
        init(_ engine: AudioEngine?) { make = { engine } }
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
    private let engineSource: EngineSource
    private var rebuiltEngines: [FakeAudioEngine] = []

    init() {
        let player = EngineAudioPlayer(engine: engine)
        graph = makeTestGraph(audioPlayer: player)
        let rebuilt = FakeAudioEngine()
        rebuiltEngines = [rebuilt]
        let source = EngineSource(rebuilt)
        engineSource = source
        coordinator = PlaybackSystemCoordinator(
            playback: graph.playerController,
            player: player,
            session: AudioSessionController(session: session, notificationCenter: center),
            nowPlaying: NowPlayingController(infoCenter: info, commandCenter: commands),
            makeEngine: { source.make() }
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

    private var defaultRate: Double? {
        info.value(MPNowPlayingInfoPropertyDefaultPlaybackRate)
    }

    private var intendsToPlay: Bool {
        graph.playerController.playWhenReadyFlow.value.boolValue
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

    /// Queues `songs` and asks for the first, leaving the engine unreported so playback stays loading.
    private func beginLoad(_ songs: [Song] = TestSongs.demo) async throws {
        let controller = graph.playerController
        _ = try await controller.queueOperations.setQueue(songs: songs, shuffleSongs: nil, position: 0, context: PlayContextNone.shared)
        controller.load(seekPosition: nil, skipUnloadable: false) { _ in }
        #expect(await waitUntil { !engine.loads.isEmpty && title == "Paranoid Android" })
    }

    @Test func aSkipHoldsThePauseButtonThroughLoading() async throws {
        _ = try await playQueue()
        graph.playerController.setPlaybackSpeed(multiplier: 1.5)
        #expect(await waitUntil { rate == 1.5 && info.playbackState == .playing })
        let statesBefore = info.playbackStates.count

        commands.fire(.nextTrack)

        #expect(await waitUntil { title == "Hyperballad" })
        let hyperballad = info.history.compactMap { $0 }.filter { $0[MPMediaItemPropertyTitle] as? String == "Hyperballad" }
        #expect(!hyperballad.isEmpty)
        #expect(hyperballad.allSatisfy { ($0[MPNowPlayingInfoPropertyElapsedPlaybackTime] as? TimeInterval) == 0 })
        #expect(hyperballad.allSatisfy { ($0[MPNowPlayingInfoPropertyPlaybackRate] as? Double) == 1.5 })
        #expect(hyperballad.allSatisfy { ($0[MPNowPlayingInfoPropertyDefaultPlaybackRate] as? Double) == 1.5 })
        #expect(!info.playbackStates.dropFirst(statesBefore).contains(.paused))
        #expect(info.playbackState == .playing)
        #expect(graph.playerController.playbackStateFlow.value is PlaybackState.Loading)
        #expect(engine.loads.last?.playWhenReady == true)

        let nextId = try #require(engine.loads.last?.current.id)
        engine.emit(.state(.playing, trackId: nextId))
        #expect(await waitUntil { graph.playerController.playbackStateFlow.value is PlaybackState.Playing })
        #expect(rate == 1.5)
        #expect(info.playbackState == .playing)
    }

    @Test func loadingShowsPlayUntilAskedAndTheButtonFollowsIntentImmediately() async throws {
        try await beginLoad()
        #expect(graph.playerController.playbackStateFlow.value is PlaybackState.Loading)
        #expect(!intendsToPlay)
        #expect(info.playbackState == .paused)
        #expect(rate == 0)

        graph.playerController.setPlaybackSpeed(multiplier: 1.5)
        #expect(await waitUntil { defaultRate == 1.5 })
        #expect(rate == 0)
        #expect(graph.playerController.playbackStateFlow.value is PlaybackState.Loading)

        commands.fire(.play)
        #expect(await waitUntil { info.playbackState == .playing && rate == 1.5 })
        #expect(graph.playerController.playbackStateFlow.value is PlaybackState.Loading)
        #expect(intendsToPlay)

        commands.fire(.pause)
        #expect(await waitUntil { info.playbackState == .paused && rate == 0 })
        #expect(defaultRate == 1.5)
        #expect(graph.playerController.playbackStateFlow.value is PlaybackState.Loading)
        #expect(!intendsToPlay)

        commands.fire(.togglePlayPause)
        #expect(await waitUntil { info.playbackState == .playing && rate == 1.5 })
        #expect(graph.playerController.playbackStateFlow.value is PlaybackState.Loading)
        #expect(intendsToPlay)
    }

    @Test func anInterruptionWhileLoadingWithIntentPausesAndResumes() async throws {
        try await beginLoad()
        commands.fire(.play)
        #expect(await waitUntil { info.playbackState == .playing })
        #expect(graph.playerController.playbackStateFlow.value is PlaybackState.Loading)

        center.post(name: AVAudioSession.interruptionNotification, object: session, userInfo: [
            AVAudioSessionInterruptionTypeKey: AVAudioSession.InterruptionType.began.rawValue,
        ])
        #expect(await waitUntil { !intendsToPlay })

        center.post(name: AVAudioSession.interruptionNotification, object: session, userInfo: [
            AVAudioSessionInterruptionTypeKey: AVAudioSession.InterruptionType.ended.rawValue,
            AVAudioSessionInterruptionOptionKey: AVAudioSession.InterruptionOptions.shouldResume.rawValue,
        ])
        #expect(await waitUntil { intendsToPlay && engine.commands.contains("play") })
    }

    @Test func aResetRepublishesUnchangedPausedPlaybackInsideTheThrottle() async throws {
        let id = try await loadQueue()
        graph.playerController.setPlaybackSpeed(multiplier: 1.25)
        #expect(await waitUntil { title == "Paranoid Android" && defaultRate == 1.25 })
        engine.emit(.position(trackId: id, ms: 12_000))
        #expect(await waitUntil { elapsed == 12 })

        info.nowPlayingInfo = nil
        info.playbackState = .unknown
        center.post(name: AVAudioSession.mediaServicesWereResetNotification, object: session)

        #expect(await waitUntil { title == "Paranoid Android" && elapsed == 12 && defaultRate == 1.25 })
        #expect(rate == 0)
        #expect(info.playbackState == .paused)
        #expect(!intendsToPlay)
        let rebuilt = try #require(rebuiltEngines.first)
        #expect(rebuilt.loads.first?.playWhenReady == false)
        #expect(!rebuilt.commands.contains("play"))
    }

    @Test func aResetWhileLoadingRepublishesAndRestoresPlayIntent() async throws {
        try await beginLoad()
        commands.fire(.nextTrack)
        #expect(await waitUntil { title == "Hyperballad" && info.playbackState == .playing })
        #expect(graph.playerController.playbackStateFlow.value is PlaybackState.Loading)
        graph.playerController.setPlaybackSpeed(multiplier: 1.5)
        #expect(await waitUntil { rate == 1.5 })

        info.nowPlayingInfo = nil
        info.playbackState = .unknown
        center.post(name: AVAudioSession.mediaServicesWereResetNotification, object: session)

        let rebuilt = try #require(rebuiltEngines.first)
        #expect(await waitUntil {
            title == "Hyperballad" && (rebuilt.commands.contains("play") || rebuilt.loads.contains { $0.playWhenReady })
        })
        #expect(intendsToPlay)
        #expect(info.playbackState == .playing)
        #expect(rate == 1.5)
        #expect(defaultRate == 1.5)
    }

    @Test func aResetWhilePlayingRestoresIntentInTheReplacementEngine() async throws {
        _ = try await playQueue()
        #expect(await waitUntil { rate == 1 && info.playbackState == .playing })
        info.nowPlayingInfo = nil
        info.playbackState = .unknown

        center.post(name: AVAudioSession.mediaServicesWereResetNotification, object: session)

        let rebuilt = try #require(rebuiltEngines.first)
        #expect(await waitUntil {
            title == "Paranoid Android" && (rebuilt.commands.contains("play") || rebuilt.loads.contains { $0.playWhenReady })
        })
        #expect(intendsToPlay)
        #expect(info.playbackState == .playing)
        #expect(rate == 1)
    }

    @Test func aResetOfAnEmptyQueueDoesNotRestartOrPublishStaleMetadata() async throws {
        _ = try await loadQueue()
        #expect(await waitUntil { title != nil })
        graph.playerController.clearQueue()
        #expect(await waitUntil { title == nil })

        info.nowPlayingInfo = ["planted": "stale"]
        let writes = info.history.count
        center.post(name: AVAudioSession.mediaServicesWereResetNotification, object: session)
        await drainMainQueue()

        #expect(info.history.count == writes)
        #expect(info.nowPlayingInfo?["planted"] as? String == "stale")
        let rebuilt = try #require(rebuiltEngines.first)
        #expect(rebuilt.loads.isEmpty)
        #expect(!rebuilt.commands.contains("play"))
        #expect(!intendsToPlay)
    }

    @Test func aResetWhoseEngineCannotBeBuiltDoesNotRestartOrPublish() async throws {
        _ = try await playQueue()
        #expect(await waitUntil { info.playbackState == .playing })
        let plays = engine.commands.filter { $0 == "play" }.count
        engineSource.make = { nil }
        info.nowPlayingInfo = nil
        info.playbackState = .unknown
        let writes = info.history.count

        center.post(name: AVAudioSession.mediaServicesWereResetNotification, object: session)
        await drainMainQueue()

        #expect(info.history.count == writes)
        #expect(title == nil)
        #expect(engine.commands.filter { $0 == "play" }.count == plays)
        #expect(intendsToPlay)
    }
}
