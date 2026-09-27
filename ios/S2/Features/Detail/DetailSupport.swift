import SwiftUI

/// The hero header shared by the P5-7 detail screens (album, album artist, genre, playlist, smart playlist):
/// artwork, title/subtitle, then Play/Shuffle — a `List` section item, the same "hero as list item" pattern
/// Android uses rather than a collapsing bar (`AlbumDetailScreen.kt`, `docs/architecture/ios-port/phase-5-ios-app.md`
/// section 3). On `regular`/`wide` the hero is constrained to a readable width and centred instead of given its
/// own column, the simpler of the two layouts the brief allows for wider size classes.
struct DetailHero<Artwork: View>: View {
    let title: String
    let subtitle: String?
    var onPlay: () -> Void = {}
    var onShuffle: () -> Void = {}
    @ViewBuilder let artwork: () -> Artwork

    @Environment(\.layoutTier) private var layoutTier

    var body: some View {
        VStack(spacing: Spacing.smallMedium) {
            artwork()
            VStack(spacing: Spacing.xsmall) {
                Text(title)
                    .font(.s2Title2)
                    .accessibilityAddTraits(.isHeader)
                    .multilineTextAlignment(.center)
                    .lineLimit(3)
                if let subtitle, !subtitle.isEmpty {
                    Text(subtitle)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                }
            }
            HStack(spacing: Spacing.smallMedium) {
                Button(action: onPlay) {
                    Label("Play", systemImage: "play.fill").frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                Button(action: onShuffle) {
                    Label("Shuffle", systemImage: "shuffle").frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
            }
            .padding(.top, Spacing.xsmall)
        }
        .frame(maxWidth: layoutTier == .compact ? .infinity : 440)
        .frame(maxWidth: .infinity)
        .padding(.vertical, Spacing.smallMedium)
    }
}

/// A generic placeholder tile for detail heroes without artwork of their own (genre, playlist, smart playlist),
/// `GenreRow`'s icon-placeholder pattern at hero size.
struct DetailPlaceholderArtwork: View {
    let systemImage: String
    var points: CGFloat = ArtworkSize.hero

    var body: some View {
        Color(.secondarySystemBackground)
            .overlay {
                Image(systemName: systemImage)
                    .font(.system(size: points * 0.35))
                    .foregroundStyle(.primary.opacity(0.15))
            }
            .frame(width: points, height: points)
            .clipShape(RoundedRectangle(cornerRadius: ArtworkCorner.hero, style: .continuous))
    }
}

/// "1 song" / "N songs" (or any other noun), the pluralisation every detail hero's subtitle repeats.
func pluralized(_ count: Int, _ noun: String) -> String { count == 1 ? "1 \(noun)" : "\(count) \(noun)s" }
