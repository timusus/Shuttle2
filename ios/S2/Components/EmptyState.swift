import SwiftUI

/// The app's one empty state, after Shuttle Podcasts' `ShuttleEmptyState`: a symbol, a rounded title, **one**
/// sentence, and at most **one** action, styled as a prominent capsule.
///
/// `ContentUnavailableView` is the container (it centres, scales and keeps up with the system); this wraps it so
/// every screen says it the same way. The action is a view rather than a closure because S2's are mostly
/// `NavigationLink`s (Library's and Home's "Add a Source" push `Route.sources`).
struct EmptyState<Action: View>: View {
    let title: String
    let systemImage: String
    let message: String?
    @ViewBuilder let action: () -> Action

    init(_ title: String, systemImage: String, message: String? = nil, @ViewBuilder action: @escaping () -> Action) {
        self.title = title
        self.systemImage = systemImage
        self.message = message
        self.action = action
    }

    var body: some View {
        ContentUnavailableView {
            VStack(spacing: Spacing.smallMedium) {
                Image(systemName: systemImage)
                    .font(.s2Glyph(size: IconSize.hero, relativeTo: .largeTitle))
                    .foregroundStyle(.secondary)
                    .accessibilityHidden(true)
                Text(title)
                    .font(.s2Title)
                    .accessibilityAddTraits(.isHeader)
            }
        } description: {
            if let message {
                Text(message)
            }
        } actions: {
            action()
                .buttonStyle(.borderedProminent)
                .buttonBorderShape(.capsule)
                .foregroundStyle(.s2OnAccent)
                .font(.s2Button)
        }
    }
}

extension EmptyState where Action == EmptyView {
    init(_ title: String, systemImage: String, message: String? = nil) {
        self.init(title, systemImage: systemImage, message: message) { EmptyView() }
    }
}

#Preview("With an action") {
    NavigationStack {
        EmptyState("No Music", systemImage: "music.note.house", message: "Connect a Jellyfin or Emby server to stream your music.") {
            NavigationLink("Add a Source", value: Route.sources)
        }
    }
}

#Preview("Message only") {
    EmptyState("No Songs", systemImage: "music.note", message: "Pull to refresh to import.")
}
