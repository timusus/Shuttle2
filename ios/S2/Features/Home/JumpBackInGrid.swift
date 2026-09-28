import Shared
import SwiftUI

/// Jump Back In (#633): the last things played, as a compact grid of cells rather than a shelf, after Apple Music's
/// and Spotify's recents. Two columns by four rows on an iPhone, four by two on an iPad; one full-width column at
/// the accessibility text sizes. A cell opens its item; its trailing button plays it.
struct JumpBackInGrid: View {
    let items: [HomeItem]
    let perform: (MediaAction) -> Void
    let open: (HomeItem) -> Void
    /// The tile the last tap came from, so only it is the zoom source for the screen it opens.
    var zoomSourceKey: String?
    var onTapped: (String) -> Void = { _ in }

    @Environment(\.layoutTier) private var layoutTier
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    /// The grid holds two full rows at most, of whichever width it is.
    static let maximumItems = 8

    static func columnCount(tier: LayoutTier, accessibilitySize: Bool) -> Int {
        if accessibilitySize { return 1 }
        return tier == .compact ? 2 : 4
    }

    var body: some View {
        let count = Self.columnCount(tier: layoutTier, accessibilitySize: dynamicTypeSize.isAccessibilitySize)
        let columns = Array(repeating: GridItem(.flexible(), spacing: Spacing.small, alignment: .top), count: count)
        LazyVGrid(columns: columns, alignment: .leading, spacing: Spacing.small) {
            ForEach(items.prefix(Self.maximumItems), id: \.key) { item in
                JumpBackInCell(
                    item: item,
                    tileKey: "jumpBackIn|\(item.key)",
                    zoomSourceKey: zoomSourceKey,
                    perform: perform,
                    open: { onTapped("jumpBackIn|\($0.key)"); open($0) }
                )
            }
        }
    }
}

/// One cell, after Spotify's recents: the cover flush with the cell's leading edge, the title over up to two lines and
/// the kind of item, on a rounded fill, with a play button at the end. Long-press has the rest (Shuffle, queue, Go to).
private struct JumpBackInCell: View {
    let item: HomeItem
    let tileKey: String
    let zoomSourceKey: String?
    let perform: (MediaAction) -> Void
    let open: (HomeItem) -> Void

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    private static let accessibilityTitleLines = 6

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: ArtworkCorner.row, style: .continuous)
        HStack(spacing: 0) {
            Button { open(item) } label: {
                HStack(alignment: .center, spacing: Spacing.small) {
                    // Square corners: the cell's own shape rounds the cover's outer ones.
                    HomeItemArtwork(item: item, size: ArtworkSize.albumRow, cornerRadius: 0)
                        .frame(maxHeight: .infinity, alignment: .top)
                    VStack(alignment: .leading, spacing: Spacing.tiny) {
                        Text(item.title)
                            .font(.footnote.weight(.semibold))
                            .foregroundStyle(.primary)
                            // Two lines reserved, so every cell in a row is as tall as the others; at the
                            // accessibility sizes (one column) the title takes what it needs.
                            .lineLimit(dynamicTypeSize.isAccessibilitySize ? Self.accessibilityTitleLines : 2, reservesSpace: !dynamicTypeSize.isAccessibilitySize)
                            .fixedSize(horizontal: false, vertical: true)
                        Text(item.typeLabel)
                            .font(.caption2)
                            .foregroundStyle(.s2SecondaryText)
                            .lineLimit(1)
                    }
                    .padding(.vertical, Spacing.xsmall)
                    Spacer(minLength: 0)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.pressScale)
            .accessibilityIdentifier("homeGrid.cell")
            .zoomSource(for: item, tileKey: tileKey, activeKey: zoomSourceKey)
            .homeItemActions(HomeItemActions(item: item, perform: perform, open: open))

            Button { perform(item.playAction()) } label: {
                Image(systemName: item is HomeItemGenreItem ? "shuffle" : "play.fill")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(.tint)
                    .frame(width: Spacing.xlarge + Spacing.xsmall)
                    .frame(maxHeight: .infinity)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(item is HomeItemGenreItem ? "Shuffle \(item.title)" : "Play \(item.title)")
            .accessibilityIdentifier("homeGrid.play")
        }
        .frame(minHeight: ArtworkSize.albumRow)
        .fixedSize(horizontal: false, vertical: true)
        .background(Color(.secondarySystemBackground))
        .clipShape(shape)
    }
}

extension View {
    /// The zoom source for the screen an album or artist tile opens (`zoomSource(id:tileKey:activeKey:)`); a
    /// playlist or genre pushes without a zoom.
    @ViewBuilder
    func zoomSource(for item: HomeItem, tileKey: String, activeKey: String?) -> some View {
        switch onEnum(of: item) {
        case .albumItem(let it): zoomSource(id: Route.album(it.album).cacheKey, tileKey: tileKey, activeKey: activeKey)
        case .artistItem(let it):
            zoomSource(id: Route.albumArtist(albumArtistKey: it.albumArtist.groupKey.key).cacheKey, tileKey: tileKey, activeKey: activeKey)
        default: self
        }
    }
}
