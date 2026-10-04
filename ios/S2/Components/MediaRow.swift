import SwiftUI

/// The one list row for songs, albums, artists, genres, playlists and the queue: artwork, a title, an
/// optional subtitle and a trailing accessory, with a playing state.
///
/// ```swift
/// MediaRow(song.name ?? "Unknown", subtitle: "Radiohead · OK Computer", artwork: .song(song),
///          playback: isCurrent ? .playing : .none) {
///     Text(duration).font(.s2RowMeta).foregroundStyle(.s2TextSecondary)
/// }
/// ```
///
/// - Artwork is drawn by `RemoteArtwork` at `artworkSize` (`ArtworkSize.row`, or `.albumRow` for albums),
///   in `S2Shape.artworkRow` (an artist's a circle), with the hairline and no shadow. A nil source draws
///   the tinted `ArtworkPlaceholder` with `placeholderSymbol`. A playlist or genre passes its `mosaic` instead.
/// - `playback` other than `.none` tints the title with `\.artworkTint` and lays an equaliser glyph over the
///   artwork, animated while `.playing` (still under Reduce Motion, and while `.paused`).
/// - At accessibility text sizes the trailing accessory moves under the title and subtitle, so the title gets the
///   row's width (#782).
/// - The row is not a button: wrap it in the `Button` or `NavigationLink` that says what a tap does.
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
                    .font(.s2RowTitle)
                    .lineLimit(dynamicTypeSize.isAccessibilitySize ? 2 : 1)
                    .foregroundStyle(playback == .none ? AnyShapeStyle(.primary) : AnyShapeStyle(tint))
                    .fontWeight(playback == .none ? nil : .semibold)
                    .accessibilityIdentifier(ifPresent: titleIdentifier)
                if let subtitle, !subtitle.isEmpty {
                    Text(subtitle)
                        .font(.s2RowSubtitle)
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
