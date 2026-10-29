import Foundation
import PlaybackNowPlaying
import PlaybackRender
@testable import S2
import S2PlaybackTestSupport
import Shared

/// Waits, letting the main queue run (Kotlin's coroutines and the adapter's posted failures), until
/// `condition` holds; false on timing out.
@MainActor
func waitUntil(timeout: Duration = .seconds(2), _ condition: () -> Bool) async -> Bool {
    let deadline = ContinuousClock.now + timeout
    while !condition() {
        if ContinuousClock.now > deadline { return false }
        try? await Task.sleep(for: .milliseconds(5))
    }
    return true
}

/// Lets everything already queued on the main queue run.
@MainActor
func drainMainQueue() async {
    await withCheckedContinuation { continuation in
        DispatchQueue.main.async { continuation.resume() }
    }
}

/// AudioPlaybackKit's player on a manual clock: every item is 10 s of 8 Hz mono, and nothing decodes or plays until a
/// test pumps it.
@MainActor
func makeTestRig() -> PlayerTestRig<KitAudioPlayer.QueueKey> {
    PlayerTestRig { _ in FakeItemSource(format: PCMFormat(sampleRate: 8, channelCount: 1), frames: 80, chunkFrames: 2) }
}

/// The app's graph over a `KitAudioPlayer` on `rig`.
@MainActor
func makeTestGraph(rig: PlayerTestRig<KitAudioPlayer.QueueKey>) -> IosAppGraph {
    makeTestGraph(audioPlayer: KitAudioPlayer(player: rig.player))
}

/// The app's graph over a player no test drives.
@MainActor
func makeTestGraph() -> IosAppGraph {
    makeTestGraph(rig: makeTestRig())
}

/// The app's graph over `audioPlayer`, with storage of its own: preferences in their own suite (a graph restores the
/// queue and modes the last one saved, so graphs sharing the standard defaults would restore each other's; tests run
/// in parallel) and an empty in-memory library (not the simulator app's database, whose contents vary).
func makeTestGraph(audioPlayer: IosAudioPlayer) -> IosAppGraph {
    let suite = TestSuites.shared.make()
    return IosAppGraphKt.createIosAppGraph(audioPlayer: audioPlayer, isolatedStorage: suite, localFiles: IosLocalFilesNone.shared)
}

/// What views read from the environment, over `graph` and a view model cache of its own.
@MainActor
func makeTestServices(graph givenGraph: IosAppGraph? = nil) -> AppServices {
    let graph = givenGraph ?? makeTestGraph()
    let intent = PlayIntent(following: graph.playerController)
    return AppServices(
        graph: graph,
        viewModels: ViewModelCache(),
        playerBinding: PlayerBinding(viewModel: IosAppGraphKt.createPlayerViewModel(graph), intent: intent),
        playIntent: intent,
        storeKit: StoreKitManager(entitlements: graph.storeEntitlements, analytics: graph.monetisationAnalytics),
        localLibrary: LocalLibrary(),
        importState: graph.songImportStateProvider.songImportState,
        searchWarmUp: SearchWarmUp(index: graph.librarySearchIndex)
    )
}

/// The system surfaces over `rig`'s player and `graph`'s controller, publishing to `info` and `commands` in place of the
/// system's centres. Not started.
@MainActor
func makeTestCoordinator(
    rig: PlayerTestRig<KitAudioPlayer.QueueKey>,
    graph: IosAppGraph,
    intent: PlayIntent,
    info: FakeNowPlayingInfoCenter,
    commands: FakeRemoteCommandCenter
) -> PlaybackSystemCoordinator {
    PlaybackSystemCoordinator(playback: graph.playerController, intent: intent, player: rig.player, text: graph.systemSurfaceText) { player, metadata, artwork in
        NowPlayingSession(
            player: player, supportedRates: PlaybackSystemCoordinator.supportedRates, metadata: metadata, artwork: artwork,
            infoCenter: info, commandCenter: commands, now: { 0 }
        )
    }
}

extension PlayerTestRig {
    /// Pumps decodes and transport events until `condition` holds, letting Kotlin's main-queue work run between rounds;
    /// the output clock stands still. Bounded by rounds, not time: false once `rounds` pumps haven't satisfied it.
    @MainActor
    func pump(rounds: Int = 2_000, until condition: () -> Bool) async -> Bool {
        for _ in 0..<rounds {
            pump()
            if condition() { return true }
            await drainMainQueue()
        }
        return condition()
    }
}

/// The `S2Tests.<uuid>` defaults suites the graphs make. Swift Testing structs have no tearDown, so the run removes them
/// when it exits, and sweeps those a killed run left behind when it starts.
final class TestSuites: @unchecked Sendable {
    static let shared = TestSuites()

    private let lock = NSLock()
    private var made: [String] = []

    private init() {
        Self.sweepLeftovers()
        atexit { TestSuites.shared.removeAll() }
    }

    func make() -> String {
        let suite = "S2Tests.\(UUID().uuidString)"
        lock.withLock { made.append(suite) }
        return suite
    }

    fileprivate func removeAll() {
        let suites = lock.withLock { made }
        for suite in suites { UserDefaults().removePersistentDomain(forName: suite) }
    }

    private static func sweepLeftovers() {
        let library = FileManager.default.urls(for: .libraryDirectory, in: .userDomainMask).first
        let files = (try? FileManager.default.contentsOfDirectory(atPath: library?.appendingPathComponent("Preferences").path ?? "")) ?? []
        for file in files where file.hasPrefix("S2Tests.") && file.hasSuffix(".plist") {
            UserDefaults().removePersistentDomain(forName: String(file.dropLast(".plist".count)))
        }
    }
}
