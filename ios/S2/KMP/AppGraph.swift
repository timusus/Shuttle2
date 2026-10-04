import Foundation
import S2Playback
import Shared

/// Central access point for the Kotlin dependency graph (the Metro `IosAppGraph` in :shared's iosMain) and
/// the platform objects it runs on, built once by `initialize()` at launch (as in Shuttle Podcasts).
enum AppGraph {
    private static var _dependencies: IosAppDependencies?

    /// Everything the app built at launch. Crashes if read before `initialize()`.
    static var dependencies: IosAppDependencies {
        guard let dependencies = _dependencies else {
            fatalError("AppGraph.initialize() must be called before AppGraph is used")
        }
        return dependencies
    }

    /// The Kotlin graph.
    static var shared: IosAppGraph {
        dependencies.graph
    }

    /// Builds the graph, applies the crash reporting and analytics choices before anything else runs, then starts the
    /// playback system, the recording of plays and their reporting to the server. Call once, from `S2App.init`.
    @MainActor
    static func initialize() {
        guard _dependencies == nil else { return }
        let dependencies = IosAppDependencies()
        dependencies.graph.telemetryStartup.start()
        dependencies.playbackSystem.start()
        dependencies.graph.recordPlays.start()
        dependencies.graph.recordResumePoints.start()
        dependencies.graph.playbackReporting.start()
        dependencies.graph.librarySearchIndex.warmUp()
        #if DEBUG
        if let override = UserDefaults.standard.string(forKey: DebugEntitlement.defaultsKey) {
            dependencies.graph.storeEntitlements.setDebugOverrideNamed(name: override)
        }
        #endif
        dependencies.storeKit.start()
        dependencies.paywallPresenter.start()
        _dependencies = dependencies
    }
}

/// The app's composition point: each object built once, with plain constructors.
@MainActor
final class IosAppDependencies {
    /// The Kotlin `IosAudioPlayer` over the S2Playback engine.
    let audioPlayer: EngineAudioPlayer
    /// This device's music files: Documents and the folders picked in Files (#590).
    let localLibrary: LocalLibrary
    let graph: IosAppGraph
    let audioSession: AudioSessionController
    let nowPlaying: NowPlayingController
    let playbackSystem: PlaybackSystemCoordinator
    /// Whether the listener wants playback running, which every play/pause control draws (`PlayIntent`).
    let playIntent: PlayIntent
    /// The mini player and Now Playing screens' shared state, off the shared `PlayerViewModel` (#588, #587).
    /// Built here, not lazily behind a cache key, so there is exactly one ViewModel and one subscription to
    /// its state for the app's lifetime.
    let playerBinding: PlayerBinding
    /// StoreKit for Shuttle Music Pro, reporting the user's purchases to the graph's `StoreEntitlements`.
    let storeKit: StoreKitManager
    /// Opens the paywall when a gated action asks for it.
    let paywallPresenter: PaywallPresenter

    init() {
        audioPlayer = EngineAudioPlayer(engine: Self.makeEngine())
        localLibrary = LocalLibrary()
        let telemetryConfig = TelemetryConfig.main
        let telemetry = IosTelemetry(
            crashReporter: SentryCrashReporter(config: telemetryConfig),
            analytics: PostHogProductAnalytics(config: telemetryConfig)
        )
        graph = IosAppGraphKt.createIosAppGraph(audioPlayer: audioPlayer, localFiles: localLibrary, telemetry: telemetry)
        audioSession = AudioSessionController()
        nowPlaying = NowPlayingController()
        playIntent = PlayIntent(following: graph.playerController)
        playbackSystem = PlaybackSystemCoordinator(
            playback: graph.playerController,
            intent: playIntent,
            player: audioPlayer,
            session: audioSession,
            nowPlaying: nowPlaying,
            makeEngine: { try? MusicPlaybackController() }
        )
        playerBinding = PlayerBinding(viewModel: IosAppGraphKt.createPlayerViewModel(graph), intent: playIntent)
        storeKit = StoreKitManager(entitlements: graph.storeEntitlements, analytics: graph.monetisationAnalytics)
        paywallPresenter = PaywallPresenter(requests: graph.observePaywallRequests, store: storeKit)
    }

    /// The engine only fails to build without a stereo float format, which every device has.
    private static func makeEngine() -> AudioEngine {
        do {
            return try MusicPlaybackController()
        } catch {
            fatalError("S2: the playback engine could not be built: \(error)")
        }
    }
}
