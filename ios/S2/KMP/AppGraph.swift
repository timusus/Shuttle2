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
    /// content (`SearchWarmUp`), so neither holds up the launch.
    @MainActor
    static func initialize() {
        guard _dependencies == nil else { return }
        OsLogLoggerKt.installNativeLogging(sink: Self.kotlinLogSink)
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
        // Builds the album index on a background dispatcher while the shell comes up; Home and Library need it for
        // their first content, and would otherwise build it on their own path
        StartupTrace.step("albumIndex") { _ = dependencies.graph.albumIndexProvider }
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

    private static let kotlinLogSink = KotlinLogSink()
    private static var startedAfterFirstFrame = false

    /// Sets PostHog up and applies the analytics choice. Call once the first frame is on screen (`ContentView`):
    /// PostHog's set-up takes tens of milliseconds on the main thread, and events sent before it are dropped anyway.
    @MainActor
    static func startAfterFirstFrame() {
        guard !startedAfterFirstFrame else { return }
        startedAfterFirstFrame = true
        StartupTrace.step("analyticsStartup") { shared.telemetryStartup.startAnalytics() }
    }
}

/// The app's composition point: each object built once, with plain constructors.
@MainActor
final class IosAppDependencies {
    /// The Kotlin `IosAudioPlayer` over AudioPlaybackKit's `Player`.
    let audioPlayer: KitAudioPlayer
    /// This device's music files: Documents and the folders picked in Files (#590).
    let localLibrary: LocalLibrary
    let graph: IosAppGraph
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
    /// Plays what Siri's media domain resolved (#951).
    let siriPlayer: SiriMediaPlayer
    /// Tells Siri about the library: its size, and the artists and playlists to listen for.
    let siriContext: SiriLibraryContext
    /// The library import's state, one flow for every root's toolbar: built once, so `Observing` keeps one
    /// collection rather than restarting on each render.
    let importState: SkieSwiftStateFlow<SongImportState>
    let searchWarmUp: SearchWarmUp

    /// What views read from the environment; shares `ViewModelCache.shared` with `Navigator`.
    lazy var services = AppServices(
        graph: graph, viewModels: .shared, playerBinding: playerBinding, playIntent: playIntent,
        storeKit: storeKit, localLibrary: localLibrary, importState: importState, searchWarmUp: searchWarmUp
    )

    init() {
        // The player opens items through the graph, which is built over the player: the closure reads it once it's there.
        var builtGraph: IosAppGraph!
        let audioPlayer = StartupTrace.step("audioPlayer") {
            KitAudioPlayer.live { uid, startMs, done in
                let resolver = builtGraph.playerController.itemResolver
                if startMs > 0 {
                    resolver.resolveAt(uid: uid, startPositionMs: startMs, onResult: done)
                } else {
                    resolver.resolve(uid: uid, onResult: done)
                }
            }
        }
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
        builtGraph = graph
        // Before anything opens a stream or fetches artwork: their sessions ask it for the server's headers and certificate (#921)
        ServerConnections.policy = KotlinServerConnectionPolicy(graph.serverRequestPolicy)
        StreamWarmUp.shared.isConfiguredServer = { origin in
            MediaProviderType.signInTypes.contains { StreamWarmUp.address(graph.readServerLogin.invoke(type: $0).address, isAt: origin) }
        }
        DispatchQueue.global(qos: .utility).async { StreamStore.prepareAtLaunch() }
        playIntent = PlayIntent(following: graph.playerController)
        playbackSystem = PlaybackSystemCoordinator(
            playback: graph.playerController,
            intent: playIntent,
            player: audioPlayer.player,
            widgets: NowPlayingWidgetPublisher()
        )
        let intent = playIntent
        playerBinding = StartupTrace.step("playerBinding") {
            PlayerBinding(viewModel: IosAppGraphKt.createPlayerViewModel(graph), intent: intent)
        }
        storeKit = StoreKitManager(entitlements: graph.storeEntitlements, analytics: graph.monetisationAnalytics)
        paywallPresenter = PaywallPresenter(requests: graph.observePaywallRequests, store: storeKit)
        intentPerformer = AppIntentPerformer(graph: graph, intent: playIntent)
        siriPlayer = SiriMediaPlayer(graph: graph, performer: intentPerformer)
        siriContext = SiriLibraryContext(graph: graph)
        importState = graph.songImportStateProvider.songImportState
        searchWarmUp = SearchWarmUp(index: graph.librarySearchIndex)
    }
}
