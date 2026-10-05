import Foundation
import S2Playback
import Shared
import SwiftUI

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

    /// Builds the graph, applies the crash reporting choice before anything else runs, then starts the playback system,
    /// the recording of plays, their reporting to the server and their scrobbling to Last.fm. Call once, from
    /// `S2App.init`. Analytics waits for the first frame (`startAfterFirstFrame()`) and the search index for the first
    /// content (`warmUpSearch()`), so neither holds up the launch.
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
        // Siri, Shortcuts, widgets and controls run their intents through the app (#758)
        IntentPerformers.playback = dependencies.intentPerformer
        IntentPerformers.library = dependencies.intentPerformer
        _dependencies = dependencies
    }

    private static var startedAfterFirstFrame = false
    private static var searchWarmUpStarted = false

    /// Sets PostHog up and applies the analytics choice. Call once the first frame is on screen (`ContentView`):
    /// PostHog's set-up takes tens of milliseconds on the main thread, and events sent before it are dropped anyway.
    @MainActor
    static func startAfterFirstFrame() {
        guard !startedAfterFirstFrame else { return }
        startedAfterFirstFrame = true
        StartupTrace.step("analyticsStartup") { shared.telemetryStartup.startAnalytics() }
    }

    /// Starts building the search index in the background, once: when Home or a Library list first shows its content,
    /// or when Search opens, whichever is first. Started at launch it competed with that first content for the
    /// library's songs; a search opened before it has run builds the index itself.
    @MainActor
    static func warmUpSearch() {
        guard !searchWarmUpStarted else { return }
        searchWarmUpStarted = true
        StartupTrace.step("searchIndexWarmUp") { shared.librarySearchIndex.warmUp() }
    }
}

extension View {
    /// Starts the search index's warm-up (`AppGraph.warmUpSearch()`) once [loaded], this screen's first content, is up.
    func warmsUpSearch(once loaded: Bool) -> some View {
        onChange(of: loaded, initial: true) { _, loaded in
            if loaded { AppGraph.warmUpSearch() }
        }
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
    /// Carries out the App Intents: Siri, Shortcuts, the widgets' buttons and the Control Center control (#758).
    let intentPerformer: AppIntentPerformer

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
        // Before anything opens a stream or fetches artwork: their sessions ask it for the server's headers and certificate (#921)
        ServerConnections.policy = KotlinServerConnectionPolicy(graph.serverRequestPolicy)
        audioSession = AudioSessionController()
        nowPlaying = NowPlayingController()
        playIntent = PlayIntent(following: graph.playerController)
        playbackSystem = PlaybackSystemCoordinator(
            playback: graph.playerController,
            intent: playIntent,
            player: audioPlayer,
            session: audioSession,
            nowPlaying: nowPlaying,
            widgets: NowPlayingWidgetPublisher(),
            makeEngine: { try? MusicPlaybackController() }
        )
        let intent = playIntent
        playerBinding = StartupTrace.step("playerBinding") {
            PlayerBinding(viewModel: IosAppGraphKt.createPlayerViewModel(graph), intent: intent)
        }
        storeKit = StoreKitManager(entitlements: graph.storeEntitlements, analytics: graph.monetisationAnalytics)
        paywallPresenter = PaywallPresenter(requests: graph.observePaywallRequests, store: storeKit)
        intentPerformer = AppIntentPerformer(graph: graph, intent: playIntent)
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
