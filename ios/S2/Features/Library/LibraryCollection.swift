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

/// Adaptive columns of tiles, at least `minimumTile` wide, within the readable content width: two on every iPhone
/// in portrait (beside the letter index), more in landscape, more again on an iPad. With an `index`, the letter strip
/// down the trailing edge scrolls to each section's first tile, by the tile's id.
struct LibraryGrid<Content: View>: View {
    var index: [LetterIndexSection]?
    @ViewBuilder let content: () -> Content

    init(index: [LetterIndexSection]? = nil, @ViewBuilder content: @escaping () -> Content) {
        self.index = index
        self.content = content
    }

    @Environment(\.layoutTier) private var layoutTier
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    private var columns: [GridItem] {
        let minimum = Self.minimumTile(layoutTier, accessibilitySize: dynamicTypeSize.isAccessibilitySize)
        return [GridItem(.adaptive(minimum: minimum), spacing: AdaptiveLayout.gridSpacing, alignment: .top)]
    }

    /// The narrowest a tile gets. Compact width takes `ArtworkSize.gridMinimumCompact`, so two fit beside the index
    /// on a 320 pt screen; wider tiers `ArtworkSize.gridMinimum`. Twice that at the accessibility text sizes, whose
    /// titles two narrow tiles can't hold: one full-width column can.
    static func minimumTile(_ tier: LayoutTier, accessibilitySize: Bool = false) -> CGFloat {
        let minimum = tier == .compact ? ArtworkSize.gridMinimumCompact : ArtworkSize.gridMinimum
        return accessibilitySize ? minimum * 2 : minimum
    }

    /// The columns the grid lays out in a container `width` wide, with an index strip `indexWidth` wide down its
    /// trailing edge: the width left inside the content insets (and `AdaptiveLayout.contentMaxWidth`), filled as
    /// SwiftUI fills an adaptive `GridItem`, with as many `minimumTile`s as fit `gridSpacing` apart.
    static func columnCount(width: CGFloat, tier: LayoutTier, indexWidth: CGFloat = LetterIndexStrip.baseWidth, accessibilitySize: Bool = false) -> Int {
        let spacing = AdaptiveLayout.gridSpacing
        let available = min(width - indexWidth, AdaptiveLayout.contentMaxWidth) - AdaptiveLayout.contentInset(tier) * 2
        let minimum = minimumTile(tier, accessibilitySize: accessibilitySize)
        return max(1, Int(((available + spacing) / (minimum + spacing)).rounded(.down)))
    }

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVGrid(columns: columns, spacing: Spacing.large) {
                    content()
                }
                .padding(.horizontal, AdaptiveLayout.contentInset(layoutTier))
                .padding(.vertical, Spacing.medium)
                .frame(maxWidth: AdaptiveLayout.contentMaxWidth)
                .frame(maxWidth: .infinity)
            }
            .letterIndex(index) { proxy.scrollTo($0.anchor, anchor: .top) }
        }
    }
}

/// A grid tile: square artwork (`S2Shape.artworkTile`, an artist's a circle) filling the column, with the title and a secondary line
/// under it. The playing item's title takes the tint and its artwork the animated indicator.
struct LibraryTile: View {
    let title: String
    let subtitle: String?
    let artwork: ArtworkSource
    var placeholderSymbol: String = "music.note"
    var playback: MediaRowPlayback = .none

    @Environment(\.artworkTint) private var tint

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.small) {
            cover
            VStack(alignment: .leading, spacing: Spacing.tiny) {
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
                        .foregroundStyle(.s2TextSecondary)
                        .lineLimit(1)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
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
            .artworkStyle(.artwork(.artworkTile, for: artwork))
    }
}

/// A list's Shuffle, as a toolbar button next to the view-mode and settings items (#676).
struct ShuffleButton: View {
    let identifier: String
    let action: () -> Void

    var body: some View {
        Button("Shuffle", systemImage: "shuffle", action: action)
            .accessibilityIdentifier(identifier)
    }
}

/// Rows of `MediaRowSkeleton` in place of a bare spinner, while a list's first page loads: a plain `List`, with the
/// letter index's width kept clear, so each row sits where the loaded list's will (#643).
struct LibraryListSkeleton: View {
    var artworkSize: CGFloat = ArtworkSize.row
    var artworkShape: S2Shape = .artworkRow
    var rows = 12

    var body: some View {
        List {
            ForEach(0 ..< rows, id: \.self) { _ in
                MediaRowSkeleton(artworkSize: artworkSize, artworkShape: artworkShape)
            }
        }
        .listStyle(.plain)
        .scrollDisabled(true)
        .safeAreaPadding(.trailing, LetterIndexStrip.baseWidth)
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

/// Grid tiles' skeleton, while a grid's first page loads: `LibraryGrid`'s columns, beside the letter index's width,
/// and `LibraryTile`'s title and subtitle lines, so each tile sits where the loaded grid's will (#643).
struct LibraryGridSkeleton: View {
    var artworkShape: S2Shape = .artworkTile
    var tiles = 12

    @ScaledMetric(relativeTo: .subheadline) private var titleHeight: CGFloat = 12
    @ScaledMetric(relativeTo: .footnote) private var subtitleHeight: CGFloat = 10
    @ScaledMetric(relativeTo: .subheadline) private var titleLine: CGFloat = 18
    @ScaledMetric(relativeTo: .footnote) private var subtitleLine: CGFloat = 16

    var body: some View {
        LibraryGrid {
            ForEach(0 ..< tiles, id: \.self) { _ in
                VStack(alignment: .leading, spacing: Spacing.small) {
                    artworkShape.fill(.s2SurfaceFill)
                        .aspectRatio(1, contentMode: .fit)
                    VStack(alignment: .leading, spacing: Spacing.tiny) {
                        Capsule().fill(Color(.systemGray5)).frame(height: titleHeight).padding(.trailing, Spacing.large)
                            .frame(height: titleLine)
                        Capsule().fill(Color(.systemGray6)).frame(height: subtitleHeight).padding(.trailing, Spacing.xlarge * 2)
                            .frame(height: subtitleLine)
                    }
                }
            }
        }
        .safeAreaPadding(.trailing, LetterIndexStrip.baseWidth)
        .shimmer()
        .scrollDisabled(true)
        .accessibilityElement()
        .accessibilityLabel("Loading")
        .accessibilityIdentifier("library.loading")
    }
}
