import Intents
import S2Playback
import Shared
import SwiftUI

@main
struct S2App: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    /// Read once at launch, as Android's shell does: a change in Settings applies from the next launch.
    private let startTab: AppTab
    @Environment(\.scenePhase) private var scenePhase

    init() {
        StartupTrace.mark("appInit")
        AppGraph.initialize()
        AccentTint.apply()
        startTab = AppTab(AppGraph.shared.shellViewModel.uiState.value.startTab)
        StartupTrace.mark("appInitDone")
    }

    var body: some Scene {
        WindowGroup {
            ContentView(startTab: startTab, onFirstFrame: AppGraph.startAfterFirstFrame)
                .environment(\.appServices, AppGraph.dependencies.services)
                .tint(.s2Accent)
                .task {
                    StartupTrace.step("libraryImportAtLaunch") { LibraryImport.atLaunch(graph: AppGraph.shared) }
                    LibraryImport.syncIfStale(graph: AppGraph.shared)
                    QuickActionRouter.shared.install()
                    // Siri and Spotlight learn the playlists' names for "Play <playlist> in Shuttle Music"
                    ShuttleShortcuts.updateAppShortcutParameters()
                    // ... and "Play <artist> on Shuttle Music" learns the library (#951)
                    AppGraph.dependencies.siriContext.start()
                    await LibraryImport.whenLocalFilesChange(graph: AppGraph.shared, localLibrary: AppGraph.dependencies.localLibrary)
                }
                .onChange(of: scenePhase) { _, phase in
                    switch phase {
                    case .active:
                        // The next tap's stream finds its server's connection open
                        StreamWarmUp.shared.warm()
                        SiriAuthorization.askIfDue()
                        LibraryImport.syncIfStale(graph: AppGraph.shared)
                        Task { await LibraryImport.whenLocalFilesChange(graph: AppGraph.shared, localLibrary: AppGraph.dependencies.localLibrary) }
                        // Scrobbles queued while offline or suspended
                        AppGraph.shared.scrobbleFlushScheduler.scheduleFlush()
                    case .background:
                        BackgroundRefresh.schedule()
                        ScrobbleFlush.schedule()
                    default:
                        break
                    }
                }
        }
        .backgroundTask(.appRefresh(BackgroundRefresh.identifier)) {
            await BackgroundRefresh.run(graph: AppGraph.shared)
        }
        .backgroundTask(.appRefresh(ScrobbleFlush.identifier)) {
            await ScrobbleFlush.run(graph: AppGraph.shared)
        }
    }
}

/// The UIKit callbacks SwiftUI has no modifier for.
final class AppDelegate: NSObject, UIApplicationDelegate {
    /// Kept for the app's life: iOS doesn't retain the handler it is given.
    private lazy var siriHandler = SiriMediaHandler(player: AppGraph.dependencies.siriPlayer)

    /// iOS relaunched (or woke) the app because offline downloads' background session has events (#759): the session,
    /// recreated by `AppGraph.initialize()`, delivers them, then calls `completionHandler` so iOS can suspend the app
    /// again and refresh its snapshot.
    func application(
        _ application: UIApplication,
        handleEventsForBackgroundURLSession identifier: String,
        completionHandler: @escaping () -> Void
    ) {
        if !AppGraph.shared.urlSessionDownloads.handleBackgroundEvents(identifier: identifier, completionHandler: completionHandler) {
            completionHandler()
        }
    }

    /// A cold launch from a home-screen quick action carries it in the connection options (the scene delegate only
    /// hears of one while the app runs). CarPlay keeps its manifest configuration.
    func application(
        _ application: UIApplication,
        configurationForConnecting connectingSceneSession: UISceneSession,
        options: UIScene.ConnectionOptions
    ) -> UISceneConfiguration {
        if connectingSceneSession.role == .carTemplateApplication {
            return UISceneConfiguration(name: "CarPlay", sessionRole: connectingSceneSession.role)
        }
        if let item = options.shortcutItem, let action = HomeScreenQuickAction(item) {
            Task { @MainActor in QuickActionRouter.shared.pending = action }
        }
        let configuration = UISceneConfiguration(name: nil, sessionRole: connectingSceneSession.role)
        configuration.delegateClass = QuickActionSceneDelegate.self
        return configuration
    }

    /// Siri's "play <something>" (#951): the media intent is handled here, in the app's process.
    func application(_ application: UIApplication, handlerFor intent: INIntent) -> Any? {
        intent is INPlayMediaIntent ? siriHandler : nil
    }

    /// Siri resolved the request and the handler answered `.handleInApp`: start playing, with the app awake in the
    /// background.
    func application(_ application: UIApplication, handle intent: INIntent, completionHandler: @escaping (INIntentResponse) -> Void) {
        guard let intent = intent as? INPlayMediaIntent else {
            completionHandler(INIntentResponse())
            return
        }
        Task { @MainActor in
            let code = await AppGraph.dependencies.siriPlayer.play(intent)
            completionHandler(INPlayMediaIntentResponse(code: code, userActivity: nil))
        }
    }
}
