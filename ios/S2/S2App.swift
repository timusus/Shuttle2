import Shared
import SwiftUI

@main
struct S2App: App {
    /// Read once at launch, as Android's shell does: a change in Settings applies from the next launch.
    private let startTab: AppTab
    @Environment(\.scenePhase) private var scenePhase

    init() {
        AppGraph.initialize()
        AccentTint.apply()
        startTab = AppTab(AppGraph.shared.shellViewModel.uiState.value.startTab)
    }

    var body: some Scene {
        WindowGroup {
            ContentView(startTab: startTab)
                .tint(.s2Accent)
                .task {
                    LibraryImport.atLaunch()
                    LibraryImport.syncIfStale()
                    await LibraryImport.whenLocalFilesChange()
                }
                .onChange(of: scenePhase) { _, phase in
                    switch phase {
                    case .active:
                        LibraryImport.syncIfStale()
                        Task { await LibraryImport.whenLocalFilesChange() }
                    case .background:
                        BackgroundRefresh.schedule()
                    default:
                        break
                    }
                }
        }
        .backgroundTask(.appRefresh(BackgroundRefresh.identifier)) {
            await BackgroundRefresh.run()
        }
    }
}
