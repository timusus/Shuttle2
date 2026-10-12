import MediaPlayer
import PlaybackPlayer
import S2PlaybackTestSupport
import Shared
import Testing
@testable import S2

/// The system surfaces wired to the real Kotlin controller over the player on its test rig: Now Playing follows the
/// queue and the player, and remote commands become controller calls. The rig's items are 10 s long.
@MainActor
struct PlaybackSystemCoordinatorTests {
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

    private let rig = makeTestRig()
    private let info = FakeInfoCenter()
    private let commands = FakeCommandCenter()
    private let graph: IosAppGraph
    private let coordinator: PlaybackSystemCoordinator
    private let intent: PlayIntent

    init() {
        graph = makeTestGraph(rig: rig)
        intent = PlayIntent(following: graph.playerController)
        coordinator = PlaybackSystemCoordinator(
            playback: graph.playerController,
            intent: intent,
            nowPlaying: NowPlayingController(infoCenter: info, commandCenter: commands)
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

    /// Queues `songs` and loads the first, as far as the player having it ready, paused.
    private func loadQueue(_ songs: [Song] = TestSongs.demo) async throws {
        try await beginLoad(songs)
        #expect(await rig.pump { graph.playerController.playbackStateFlow.value is Shared.PlaybackState.Paused })
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
        commands.fire(.play)
        #expect(await rig.pump { graph.playerController.playbackStateFlow.value is Shared.PlaybackState.Playing })
    }

    @Test func startRegistersTheCommands() {
        #expect(commands.handlers[.play] != nil)
        #expect(commands.handlers[.nextTrack] != nil)
    }

    @Test func nowPlayingFollowsTheQueue() async throws {
        try await loadQueue()
        #expect(await waitUntil { title == "Paranoid Android" })
        #expect(info.nowPlayingInfo?[MPMediaItemPropertyArtist] as? String == "Radiohead")
        #expect(info.nowPlayingInfo?[MPMediaItemPropertyPlaybackDuration] as? TimeInterval == 386)

        commands.fire(.nextTrack)
        #expect(await waitUntil { title == "Hyperballad" })
        #expect(rig.player.currentItem?.songId == 2)
    }

    @Test func playAndPauseFromTheLockScreenReflectBack() async throws {
        try await playQueue()
        #expect(await waitUntil { rate == 1 && info.playbackState == .playing })

        rig.run(for: 4)
        #expect(await waitUntil { elapsed == 4 })
        commands.fire(.pause)
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

        commands.fire(.play)

        #expect(await waitUntil { graph.playerController.bufferingFlow.value.boolValue })
        #expect(await waitUntil { rate == 0 && info.playbackState == .playing })
        #expect(graph.playerController.playbackStateFlow.value is Shared.PlaybackState.Loading)

        #expect(await rig.pump { rate == 1 && info.playbackState == .playing })
    }

    /// A play still being prepared (a `MediaAction` reading its songs) shows on the lock screen as playing from the tap,
    /// and its failure as paused again.
    @Test func aPendingPlayShowsAsPlayingOnTheLockScreen() async throws {
        try await loadQueue()
        #expect(await waitUntil { title == "Paranoid Android" && rate == 0 })

        let ticket = intent.begin()
        #expect(rate == 1 && info.playbackState == .playing)

        intent.finished(ticket, result: MediaActionResultMessage(message: MediaActionMessageNoSongs.shared, action: nil))
        #expect(rate == 0 && info.playbackState == .paused)
    }

    @Test func aRemoteSeekMovesThePlayerAndReflectsBack() async throws {
        try await loadQueue()

        commands.fire(.changePlaybackPosition, .position(6))

        #expect(await rig.pump { rig.player.position == 6 })
        #expect(await waitUntil { elapsed == 6 })
        #expect(rate == 0)
    }

    /// The new song's metadata is never published with the last one's position, whichever of the queue
    /// and progress flows Swift hears about first.
    @Test func aTrackChangePublishesTheNewSongFromItsStart() async throws {
        try await playQueue()
        rig.run(for: 4)
        #expect(await waitUntil { elapsed == 4 })

        rig.run(for: 6)

        #expect(await waitUntil { title == "Hyperballad" })
        let published = info.history.compactMap { $0 }.filter { $0[MPMediaItemPropertyTitle] as? String == "Hyperballad" }
        #expect(published.allSatisfy { ($0[MPNowPlayingInfoPropertyElapsedPlaybackTime] as? TimeInterval) == 0 })
        #expect(info.value(MPMediaItemPropertyPlaybackDuration) == 321.0)
        #expect(rate == 1)
    }

    @Test func aSongWithoutADurationShowsThePlayers() async throws {
        let song = TestSongs.song(9, "Untagged", artist: "Nobody", album: "Nothing", durationMs: 0)
        try await loadQueue([song])
        #expect(await waitUntil { title == "Untagged" })

        commands.fire(.play)
        rig.run(for: 1)

        #expect(await waitUntil { info.value(MPMediaItemPropertyPlaybackDuration) == 10.0 })
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

        #expect(commands.handlers.values.isEmpty)
        #expect(title == nil)
    }

    @Test func aSkipHoldsThePauseButtonThroughLoading() async throws {
        try await playQueue()
        graph.playerController.setPlaybackSpeed(multiplier: 1.5)
        #expect(await waitUntil { rate == 1.5 && info.playbackState == .playing })
        let statesBefore = info.playbackStates.count

        commands.fire(.nextTrack)

        #expect(await waitUntil { title == "Hyperballad" })
        let hyperballad = info.history.compactMap { $0 }.filter { $0[MPMediaItemPropertyTitle] as? String == "Hyperballad" }
        #expect(!hyperballad.isEmpty)
        #expect(hyperballad.allSatisfy { ($0[MPNowPlayingInfoPropertyElapsedPlaybackTime] as? TimeInterval) == 0 })
        // The player stays ready until its transport answers the skip, so the restart reads as a stall and may stop the
        // clock (rate 0) for a moment; the pause button holds.
        #expect(hyperballad.allSatisfy { [0, 1.5].contains($0[MPNowPlayingInfoPropertyPlaybackRate] as? Double) })
        #expect(hyperballad.allSatisfy { ($0[MPNowPlayingInfoPropertyDefaultPlaybackRate] as? Double) == 1.5 })
        #expect(!info.playbackStates.dropFirst(statesBefore).contains(.paused))
        #expect(info.playbackState == .playing)
        #expect(rig.player.playWhenReady)

        #expect(await rig.pump { graph.playerController.playbackStateFlow.value is Shared.PlaybackState.Playing })
        #expect(rate == 1.5)
        #expect(info.playbackState == .playing)
    }

    @Test func theButtonFollowsIntentImmediatelyThroughLoading() async throws {
        try await beginLoad()
        #expect(!intendsToPlay)
        #expect(info.playbackState == .paused)
        #expect(rate == 0)

        graph.playerController.setPlaybackSpeed(multiplier: 1.5)
        #expect(await waitUntil { defaultRate == 1.5 })
        #expect(rate == 0)

        commands.fire(.play)
        #expect(info.playbackState == .playing)
        #expect(intendsToPlay)
        #expect(await waitUntil { graph.playerController.playbackStateFlow.value is Shared.PlaybackState.Loading })
        #expect(info.playbackState == .playing)

        commands.fire(.pause)
        #expect(await waitUntil { info.playbackState == .paused && rate == 0 })
        #expect(defaultRate == 1.5)
        #expect(!intendsToPlay)

        commands.fire(.togglePlayPause)
        #expect(info.playbackState == .playing)
        #expect(intendsToPlay)
        #expect(await rig.pump { graph.playerController.playbackStateFlow.value is Shared.PlaybackState.Playing })
        #expect(await waitUntil { rate == 1.5 && info.playbackState == .playing })
    }
}
