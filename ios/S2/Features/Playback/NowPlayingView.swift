import SwiftUI

/// Placeholder Now Playing screen (#588).
struct NowPlayingView: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ContentUnavailableView("Now Playing", systemImage: "play.circle", description: Text("Nothing is playing"))
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Close", systemImage: "chevron.down") { dismiss() }
                    }
                }
        }
    }
}
