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
    /// playback system, the recording of plays, their reporting to the server and their scrobbling to Last.fm. Call
    /// once, from `S2App.init`.
    @MainActor
    static func initialize() {
        guard _dependencies == nil else { return }
        // Each step's time goes to the Startup log (`StartupTrace`, docs/performance/ios-startup.md)
        let dependencies = StartupTrace.step("dependencies") { IosAppDependencies() }
        StartupTrace.step("telemetryStartup") { dependencies.graph.telemetryStartup.start() }
        StartupTrace.step("playbackSystem") { dependencies.playbackSystem.start() }
        StartupTrace.step("recordPlays") { dependencies.graph.recordPlays.start() }
        StartupTrace.step("playbackScrobbling") { dependencies.graph.playbackScrobbling.start() }
        StartupTrace.step("recordResumePoints") { dependencies.graph.recordResumePoints.start() }
        StartupTrace.step("playbackReporting") { dependencies.graph.playbackReporting.start() }
        StartupTrace.step("favouriteSender") { dependencies.graph.favouriteSender.start() }
        StartupTrace.step("searchIndexWarmUp") { dependencies.graph.librarySearchIndex.warmUp() }
        // Reattaches offline downloads' background session, so a download that finished while the app wasn't running
        // is delivered, and is there for a background relaunch's events (AppDelegate)
        StartupTrace.step("offlineDownloads") { _ = dependencies.graph.offlineDownloads }
        #if DEBUG
        if let override = UserDefaults.standard.string(forKey: DebugEntitlement.defaultsKey) {
            dependencies.graph.storeEntitlements.setDebugOverrideNamed(name: override)
        }
        #endif
        StartupTrace.step("storeKit") { dependencies.storeKit.start() }
        StartupTrace.step("paywallPresenter") { dependencies.paywallPresenter.start() }
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
        let audioPlayer = StartupTrace.step("audioEngine") { EngineAudioPlayer(engine: Self.makeEngine()) }
        let localLibrary = LocalLibrary()
        self.audioPlayer = audioPlayer
        self.localLibrary = localLibrary
        let telemetryConfig = TelemetryConfig.main
        let telemetry = IosTelemetry(
            crashReporter: SentryCrashReporter(config: telemetryConfig),
            analytics: PostHogProductAnalytics(config: telemetryConfig)
        )
        let graph = StartupTrace.step("createIosAppGraph") {
            IosAppGraphKt.createIosAppGraph(audioPlayer: audioPlayer, localFiles: localLibrary, telemetry: telemetry)
        }
        self.graph = graph
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
        let intent = playIntent
        playerBinding = StartupTrace.step("playerBinding") {
            PlayerBinding(viewModel: IosAppGraphKt.createPlayerViewModel(graph), intent: intent)
        }
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
