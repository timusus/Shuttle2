import Shared
import SwiftUI

/// The server types iOS can sign in to, as large tappable cards.
struct SourceTypeCards: View {
    let types: [MediaProviderType]
    let onSelect: (MediaProviderType) -> Void
    /// The first run's card for this device's music, above the servers; nil leaves it out.
    var onUseThisDevice: (() -> Void)?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.medium) {
                VStack(alignment: .leading, spacing: Spacing.xsmall) {
                    Text("Where's your music?")
                        .font(.s2Title)
                        .accessibilityAddTraits(.isHeader)
                    Text(onUseThisDevice == nil ? "Choose the server your library lives on." : "Play the music on this iPhone, or choose the server your library lives on.")
                        .foregroundStyle(.s2TextSecondary)
                }
                .padding(.bottom, Spacing.small)
                if let onUseThisDevice {
                    Button(action: onUseThisDevice) {
                        SourceTypeCard(type: .shuttle)
                    }
                    .buttonStyle(.pressScale)
                    .accessibilityIdentifier("onboarding.useThisDevice")
                }
                ForEach(types, id: \.self) { type in
                    Button { onSelect(type) } label: {
                        SourceTypeCard(type: type)
                    }
                    .buttonStyle(.pressScale)
                    .accessibilityIdentifier("serverTypePicker.\(type.name)")
                }
            }
            .padding(Spacing.medium)
            .frame(maxWidth: ServerSignInContent.readableWidth)
            .frame(maxWidth: .infinity)
        }
        .background(Color(.systemGroupedBackground))
        .navigationTitle(onUseThisDevice == nil ? "Add a Server" : "Add Your Music")
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct SourceTypeCard: View {
    let type: MediaProviderType

    var body: some View {
        HStack(spacing: Spacing.medium) {
            IconSquare(glyph: type.glyph, style: .filled(type.color), size: .large)
            VStack(alignment: .leading, spacing: Spacing.tiny) {
                Text(type.setupTitle).font(.s2Headline)
                Text(type.setupBlurb)
                    .font(.subheadline)
                    .foregroundStyle(.s2TextSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: Spacing.small)
            Image(systemName: "chevron.forward")
                .font(.footnote.weight(.semibold))
                .foregroundStyle(.tertiary)
                .accessibilityHidden(true)
        }
        .multilineTextAlignment(.leading)
        .foregroundStyle(.primary)
        .padding(Spacing.medium)
        .background(
            S2Shape.card
                .fill(Color(.secondarySystemGroupedBackground))
        )
        .contentShape(S2Shape.card)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isButton)
    }
}

extension MediaProviderType {
    /// A source's name on its setup card.
    var setupTitle: String {
        switch self {
        case .shuttle, .mediaStore: "Use Music on This iPhone"
        case .jellyfin, .emby, .plex, .subsonic: title
        }
    }

    /// A source's line on its setup card.
    var setupBlurb: String {
        switch self {
        case .jellyfin: "The free, open-source media server. Sign in with Quick Connect or a password."
        case .emby: "Sign in with your Emby server's address and account."
        case .plex: "Sign in with your Plex account."
        case .subsonic: "Navidrome, gonic, Airsonic and other Subsonic-compatible music servers."
        case .shuttle, .mediaStore: "Songs copied into Shuttle Music in the Files app or Finder, and folders you add from Files."
        }
    }
}
