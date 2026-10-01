import Shared
import SwiftUI

@main
struct S2App: App {
    /// Read once at launch, as Android's shell does: a change in Settings applies from the next launch.
    private let startTab: AppTab

    init() {
        AppGraph.initialize()
        AccentTint.apply()
        startTab = AppTab(AppGraph.shared.shellViewModel.uiState.value.startTab)
    }

    var body: some Scene {
        WindowGroup {
            ContentView(startTab: startTab)
                .tint(.s2Accent)
                .task { LibraryImport.atLaunch() }
        }
    }
}
