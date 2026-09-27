import SwiftUI

/// Placeholder until the Search screen lands (#589).
struct SearchView: View {
    var body: some View {
        ContentUnavailableView("Search", systemImage: AppTab.search.systemImage, description: Text("Coming soon"))
            .navigationTitle(AppTab.search.title)
    }
}
