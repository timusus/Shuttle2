import SwiftUI

/// Placeholder until the Search screen lands (#589).
struct SearchView: View {
    var body: some View {
        EmptyState("Search", systemImage: AppTab.search.systemImage, message: "Coming soon.")
            .navigationTitle(AppTab.search.title)
    }
}
