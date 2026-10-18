import SwiftUI

/// The row is not a button, so wrap it in a `Button` or `NavigationLink`.
struct MediaRow<Trailing: View>: View {
    let title: String
    let subtitle: String?
    let artwork: ArtworkSource?
    /// A playlist's or genre's artwork, in place of `artwork`.
    let mosaic: CoverMosaic?
    /// An auto playlist's glyph, drawn as an `AutoPlaylistArtwork` tile in place of `artwork`.
    let tintedGlyph: String?
    let artworkSize: CGFloat
    let placeholderSymbol: String
    let playback: MediaRowPlayback
    /// Set on the title `Text`, for Maestro flows (`songRow.title`).
    let titleIdentifier: String?
    let trailing: Trailing

    @Environment(\.artworkTint) private var tint
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    init(
        _ title: String,
        subtitle: String? = nil,
        artwork: ArtworkSource? = nil,
        mosaic: CoverMosaic? = nil,
        tintedGlyph: String? = nil,
        artworkSize: CGFloat = ArtworkSize.row,
        placeholderSymbol: String = "music.note",
        playback: MediaRowPlayback = .none,
        titleIdentifier: String? = nil,
        @ViewBuilder trailing: () -> Trailing
    ) {
        self.title = title
        self.subtitle = subtitle
        self.artwork = artwork
        self.mosaic = mosaic
        self.tintedGlyph = tintedGlyph
        self.artworkSize = artworkSize
        self.placeholderSymbol = placeholderSymbol
        self.playback = playback
        self.titleIdentifier = titleIdentifier
        self.trailing = trailing()
    }

    var body: some View {
        HStack(spacing: Spacing.smallMedium) {
            artworkView
            VStack(alignment: .leading, spacing: Spacing.tiny) {
                Text(title)
                    .textRole(.rowTitle)
                    .lineLimit(dynamicTypeSize.isAccessibilitySize ? 2 : 1)
                    .foregroundStyle(playback == .none ? AnyShapeStyle(.primary) : AnyShapeStyle(tint))
                    .fontWeight(playback == .none ? nil : .semibold)
                    .accessibilityIdentifier(ifPresent: titleIdentifier)
                if let subtitle, !subtitle.isEmpty {
                    Text(subtitle)
                        .textRole(.rowSubtitle)
                        .foregroundStyle(.s2TextSecondary)
                        .lineLimit(1)
                }
                if dynamicTypeSize.isAccessibilitySize {
                    trailing
                }
            }
            if !dynamicTypeSize.isAccessibilitySize {
                Spacer(minLength: Spacing.small)
                trailing
            }
        }
        .contentShape(Rectangle())
    }

    @ViewBuilder
    private var artworkView: some View {
        let image = Group {
            if let mosaic {
                mosaic
            } else if let tintedGlyph {
                AutoPlaylistArtwork(symbol: tintedGlyph)
            } else if let artwork {
                RemoteArtwork(artwork, points: artworkSize) {
                    ArtworkPlaceholder(symbol: placeholderSymbol)
                }
            } else {
                ArtworkPlaceholder(symbol: placeholderSymbol)
            }
        }
        .overlay {
            if playback != .none {
                NowPlayingIndicator(isAnimating: playback == .playing)
                    .padding(artworkSize * 0.25)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(.black.opacity(0.35))
            }
        }

        if mosaic != nil {
            // The mosaic draws its own corners and hairline.
            image.frame(width: artworkSize, height: artworkSize)
                .accessibilityIdentifier("mediaRow.artwork")
        } else {
            image.artworkTile(artworkSize, shape: .artwork(.artworkRow, for: artwork))
                .accessibilityIdentifier("mediaRow.artwork")
        }
    }
}

extension MediaRow where Trailing == EmptyView {
    init(
        _ title: String,
        subtitle: String? = nil,
        artwork: ArtworkSource? = nil,
        mosaic: CoverMosaic? = nil,
        tintedGlyph: String? = nil,
        artworkSize: CGFloat = ArtworkSize.row,
        placeholderSymbol: String = "music.note",
        playback: MediaRowPlayback = .none,
        titleIdentifier: String? = nil
    ) {
        self.init(
            title, subtitle: subtitle, artwork: artwork, mosaic: mosaic, tintedGlyph: tintedGlyph, artworkSize: artworkSize,
            placeholderSymbol: placeholderSymbol, playback: playback, titleIdentifier: titleIdentifier
        ) { EmptyView() }
    }
}

/// Whether a row is the one playing.
enum MediaRowPlayback: Equatable {
    case none
    /// Current and playing: the indicator animates.
    case playing
    /// Current but paused: the indicator is still.
    case paused
}

/// The equaliser glyph that marks the playing item, over its artwork (`MediaRow`) or in place of a track number
/// (a detail screen's track list). White, for drawing over artwork or a tint fill; set `foregroundStyle` to
/// change it. It animates while `isAnimating`, unless Reduce Motion is on.
struct NowPlayingIndicator: View {
    var isAnimating: Bool = true

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        Image(systemName: "waveform")
            .resizable()
            .scaledToFit()
            .fontWeight(.semibold)
            .foregroundStyle(.white)
            .symbolEffect(.variableColor.iterative.dimInactiveLayers, options: .repeating, isActive: isAnimating && !reduceMotion)
            .accessibilityLabel(isAnimating ? "Now playing" : "Paused")
    }
}

extension View {
    /// `accessibilityIdentifier(_:)` when `id` is non-nil; otherwise the view unchanged.
    @ViewBuilder
    func accessibilityIdentifier(ifPresent id: String?) -> some View {
        if let id {
            accessibilityIdentifier(id)
        } else {
            self
        }
    }
}
