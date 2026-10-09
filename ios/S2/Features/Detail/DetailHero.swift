import Shared
import SwiftUI

/// A detail screen's hero: the cover (with the hero shadow), a two-line title, the eyebrow line (artist · year ·
/// N songs · duration) and the Play/Shuffle capsules in the screen's tint. Centred when stacked, leading in the
/// two-column layout's hero column. `artwork` draws the cover at the size it's given, already clipped.
struct DetailHero<Artwork: View>: View {
    let title: String
    let subtitle: String?
    /// An artist line above the subtitle that opens the artist (a text button in the tint); nil leaves it out.
    var artist: String? = nil
    var onArtist: (() -> Void)? = nil
    var layout: DetailHeroLayout = .stacked
    var onPlay: () -> Void = {}
    var onShuffle: () -> Void = {}
    /// Whether the screen's songs are downloaded or downloading (#759), shown under the subtitle.
    var downloadStatus: DownloadSummary.Status = .notDownloaded
    @ViewBuilder let artwork: (CGFloat) -> Artwork

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.artworkTint) private var tint

    private var alignment: HorizontalAlignment { layout == .stacked ? .center : .leading }
    private var textAlignment: TextAlignment { layout == .stacked ? .center : .leading }

    var body: some View {
        VStack(alignment: alignment, spacing: Spacing.medium) {
            artwork(layout.artworkSize(isAccessibilitySize: dynamicTypeSize.isAccessibilitySize))
                .artworkShadow(.hero)
                .padding(.bottom, Spacing.xsmall)
            VStack(alignment: alignment, spacing: Spacing.xsmall) {
                Text(title)
                    .font(.s2HeroTitle)
                    .multilineTextAlignment(textAlignment)
                    .lineLimit(dynamicTypeSize.isAccessibilitySize ? nil : 2)
                    .accessibilityAddTraits(.isHeader)
                    .detailTitleProbe()
                if let artist, let onArtist {
                    Button(action: onArtist) {
                        Text(artist)
                            .font(.s2Eyebrow)
                            .foregroundStyle(tint)
                            .multilineTextAlignment(textAlignment)
                            .padding(.vertical, 12)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    // The label's padding grows the hit area toward 44 pt; this takes it back out of the layout.
                    .padding(.vertical, -12)
                    .accessibilityLabel("Go to \(artist)")
                    .accessibilityHint("Opens the artist")
                }
                if let subtitle, !subtitle.isEmpty {
                    Text(subtitle)
                        .font(.s2Eyebrow)
                        .foregroundStyle(.s2TextSecondary)
                        .multilineTextAlignment(textAlignment)
                }
                DownloadStatusLabel(status: downloadStatus)
            }
            HeroActions(onPlay: onPlay, onShuffle: onShuffle)
                .frame(maxWidth: layout == .stacked ? ArtworkSize.heroRegular + Spacing.xlarge : .infinity)
        }
        .frame(maxWidth: .infinity, alignment: layout == .stacked ? .center : .leading)
    }
}

/// Play and Shuffle as two capsules sharing the width, in the tint in scope: Play filled with the tint, Shuffle a
/// tonal capsule (a light wash of the tint); glass on iOS 26, bordered below. At the accessibility sizes the
/// capsules show their glyphs only (the titles stay their accessibility labels): side by side the titles truncate,
/// and stacked the second capsule fell below the first screen, under the mini player (#782).
struct HeroActions: View {
    let onPlay: () -> Void
    let onShuffle: () -> Void

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        HStack(spacing: Spacing.smallMedium) {
            Button(action: onPlay) {
                Label("Play", systemImage: "play.fill").frame(maxWidth: .infinity)
            }
            .capsuleButton(prominent: true)
            .accessibilityLabel("Play")
            Button(action: onShuffle) {
                Label("Shuffle", systemImage: "shuffle").frame(maxWidth: .infinity)
            }
            .capsuleButton(prominent: false)
            .accessibilityLabel("Shuffle")
        }
        .labelStyle(GlyphOnlyAtAccessibilitySizes(isAccessibilitySize: dynamicTypeSize.isAccessibilitySize))
        .fontWeight(.semibold)
        .controlSize(.large)
    }
}

/// `HeroActions`' labels: the glyph alone at the accessibility sizes, glyph and title otherwise.
private struct GlyphOnlyAtAccessibilitySizes: LabelStyle {
    let isAccessibilitySize: Bool

    func makeBody(configuration: Configuration) -> some View {
        if isAccessibilitySize {
            configuration.icon
        } else {
            Label(configuration)
        }
    }
}

extension View {
    /// A capsule button in the tint in scope: `.glassProminent` on iOS 26, `.borderedProminent` in a capsule below.
    /// A prominent button is filled with the tint, its label in `\.artworkTintInk`, which clears AA on the tint fill
    /// whatever the cover; the other is tonal, a light wash of the tint with the label in the tint itself, so it
    /// stands off the page in either scheme (a white or grey capsule all but vanished on the light page).
    func capsuleButton(prominent: Bool) -> some View {
        modifier(CapsuleButtonModifier(prominent: prominent))
    }
}

private struct CapsuleButtonModifier: ViewModifier {
    let prominent: Bool
    @Environment(\.artworkTint) private var tint
    @Environment(\.artworkTintInk) private var ink
    @Environment(\.detailPalette) private var detailPalette
    @Environment(\.colorScheme) private var colorScheme

    /// The tonal capsule's fill: the hero's wash colour (the tint, in dark mode or with no cover colour) over the page,
    /// a little stronger in dark mode, where a faint wash reads as grey.
    private var tonalFill: Color {
        let palette = detailPalette ?? .inheriting(isDarkScheme: colorScheme == .dark)
        return (palette.wash.map(ContrastSafeTint.color) ?? tint).opacity(palette.tonalOpacity)
    }

    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            if prominent {
                content.foregroundStyle(ink).buttonStyle(.glassProminent)
            } else {
                content.foregroundStyle(tint).buttonStyle(.glassProminent).tint(tonalFill)
            }
        } else {
            if prominent {
                content.foregroundStyle(ink).buttonStyle(.borderedProminent).buttonBorderShape(.capsule)
            } else {
                content.foregroundStyle(tint).buttonStyle(.borderedProminent).buttonBorderShape(.capsule).tint(tonalFill)
            }
        }
    }
}
