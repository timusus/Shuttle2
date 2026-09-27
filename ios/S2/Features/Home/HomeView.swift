import SwiftUI

/// Placeholder until the Home screen lands (#589).
struct HomeView: View {
    let navigator: Navigator

    var body: some View {
        ContentUnavailableView("Home", systemImage: AppTab.home.systemImage, description: Text("Coming soon"))
            .navigationTitle(AppTab.home.title)
            .settingsGear(navigator)
    }
}
