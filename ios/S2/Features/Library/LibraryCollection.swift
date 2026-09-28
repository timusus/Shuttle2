import Shared
import SwiftUI

/// The pieces the library's lists share: the grid/list toggle, a grid tile, the adaptive grid and the loading
/// skeletons. The grid follows Shuttle Podcasts' subscriptions grid: adaptive columns of at least
/// `ArtworkSize.gridMinimum`, square covers, the title and a secondary line under each.

/// The toolbar button that flips a list between grid and list. Shows what it switches to.
struct ViewModeToggle: View {
    let mode: ViewMode
    let onChange: (ViewMode) -> Void

    var body: some View {
        let isGrid = mode == .grid
        Button(isGrid ? "Show as List" : "Show as Grid", systemImage: isGrid ? "list.bullet" : "square.grid.2x2") {
            onChange(isGrid ? .list : .grid)
        }
        .accessibilityIdentifier("library.viewMode")
    }
}

/// Adaptive columns of tiles, at least `ArtworkSize.gridMinimum` wide, within the readable content width.
struct LibraryGrid<Content: View>: View {
    @ViewBuilder let content: () -> Content

    @Environment(\.layoutTier) private var layoutTier
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    private var columns: [GridItem] {
        // Two narrow tiles can't hold accessibility-size titles; one full-width column can.
        let minimum = dynamicTypeSize.isAccessibilitySize ? ArtworkSize.gridMinimum * 2 : ArtworkSize.gridMinimum
        return [GridItem(.adaptive(minimum: minimum), spacing: AdaptiveLayout.gridSpacing, alignment: .top)]
    }

    var body: some View {
        ScrollView {
            LazyVGrid(columns: columns, spacing: Spacing.large) {
                content()
            }
            .padding(.horizontal, AdaptiveLayout.contentInset(layoutTier))
            .padding(.vertical, Spacing.medium)
            .frame(maxWidth: AdaptiveLayout.contentMaxWidth)
            .frame(maxWidth: .infinity)
        }
    }
}

/// A grid tile: square (or, for an artist, round) artwork filling the column, with the title and a secondary line
/// under it. The playing item's title takes the tint and its artwork the animated indicator.
struct LibraryTile: View {
    let title: String
    let subtitle: String?
    let artwork: ArtworkSource
    var artworkShape: MediaRowArtworkShape = .rounded
    var placeholderSymbol: String = "music.note"
    var playback: MediaRowPlayback = .none

    @Environment(\.artworkTint) private var tint

    var body: some View {
        VStack(alignment: artworkShape == .circle ? .center : .leading, spacing: Spacing.small) {
            cover
            VStack(alignment: artworkShape == .circle ? .center : .leading, spacing: Spacing.tiny) {
                HStack(spacing: Spacing.xsmall) {
                    if playback != .none {
                        NowPlayingIndicator(isAnimating: playback == .playing)
                            .foregroundStyle(tint)
                            .frame(width: Spacing.smallMedium, height: Spacing.smallMedium)
                            .accessibilityHidden(true)
                    }
                    Text(title)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(playback == .none ? AnyShapeStyle(.primary) : AnyShapeStyle(tint))
                        .lineLimit(1)
                }
                if let subtitle, !subtitle.isEmpty {
                    Text(subtitle)
                        .font(.footnote)
                        .foregroundStyle(.s2SecondaryText)
                        .lineLimit(1)
                }
            }
            .multilineTextAlignment(artworkShape == .circle ? .center : .leading)
        }
        .frame(maxWidth: .infinity, alignment: artworkShape == .circle ? .center : .leading)
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(playback == .none ? [] : .isSelected)
        .accessibilityValue(playback == .playing ? "Now playing" : playback == .paused ? "Paused" : "")
    }

    private var cover: some View {
        Color.clear
            .aspectRatio(1, contentMode: .fit)
            .overlay {
                RemoteArtwork(artwork, points: ArtworkSize.gridMinimum * 1.5) {
                    ArtworkPlaceholder(symbol: placeholderSymbol)
                }
            }
            .modifier(TileShape(shape: artworkShape))
    }
}

private struct TileShape: ViewModifier {
    let shape: MediaRowArtworkShape

    func body(content: Content) -> some View {
        switch shape {
        case .rounded:
            content.artworkStyle(cornerRadius: ArtworkCorner.tile)
        case .circle:
            content
                .clipShape(Circle())
                .overlay { Circle().strokeBorder(ArtworkHairline.color, lineWidth: ArtworkHairline.width) }
        }
    }
}

/// Rows of `MediaRowSkeleton` in place of a bare spinner, while a list's first page loads.
struct LibraryListSkeleton: View {
    var artworkSize: CGFloat = ArtworkSize.row
    var rows = 10

    var body: some View {
        VStack(spacing: Spacing.smallMedium) {
            ForEach(0 ..< rows, id: \.self) { _ in
                MediaRowSkeleton(artworkSize: artworkSize)
            }
        }
        .padding(.horizontal, Spacing.medium)
        .padding(.vertical, Spacing.small)
        .frame(maxHeight: .infinity, alignment: .top)
        .clipped()
        .accessibilityElement()
        .accessibilityLabel("Loading")
        .accessibilityIdentifier("library.loading")
    }
}

extension View {
    /// A wash of the tint behind the list row that's playing.
    func nowPlayingRowBackground(_ playback: MediaRowPlayback) -> some View {
        modifier(NowPlayingRowBackground(playback: playback))
    }
}

private struct NowPlayingRowBackground: ViewModifier {
    let playback: MediaRowPlayback

    @Environment(\.artworkTint) private var tint

    func body(content: Content) -> some View {
        content.listRowBackground(playback == .none ? nil : tint.opacity(0.12))
    }
}

/// Grid tiles' skeleton, while a grid's first page loads.
struct LibraryGridSkeleton: View {
    var artworkShape: MediaRowArtworkShape = .rounded
    var tiles = 8

    @ScaledMetric(relativeTo: .subheadline) private var titleHeight: CGFloat = 12

    var body: some View {
        LibraryGrid {
            ForEach(0 ..< tiles, id: \.self) { _ in
                VStack(alignment: .leading, spacing: Spacing.small) {
                    Group {
                        if artworkShape == .circle {
                            Circle().fill(Color(.systemGray5))
                        } else {
                            RoundedRectangle(cornerRadius: ArtworkCorner.tile, style: .continuous).fill(Color(.systemGray5))
                        }
                    }
                    .aspectRatio(1, contentMode: .fit)
                    Capsule().fill(Color(.systemGray5)).frame(height: titleHeight).padding(.trailing, Spacing.large)
                    Capsule().fill(Color(.systemGray6)).frame(height: titleHeight).padding(.trailing, Spacing.xlarge * 2)
                }
            }
        }
        .shimmer()
        .scrollDisabled(true)
        .accessibilityElement()
        .accessibilityLabel("Loading")
        .accessibilityIdentifier("library.loading")
    }
}
