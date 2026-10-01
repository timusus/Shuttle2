import Shared
import SwiftUI

/// Jump Back In (#633): the last things played, as a compact grid of cells rather than a shelf, after Apple Music's
/// and Spotify's recents. Two columns by four rows on an iPhone, four by two on an iPad; one full-width column at
/// the accessibility text sizes. A cell opens its item; its trailing button plays it on from where its queue was left
/// (#670), and says how far in that was, showing a spinner until it plays.
struct JumpBackInGrid: View {
    let items: [HomeItem]
    /// How far into each item, by key, its queue was left.
    var progress: [String: HomeItemProgress] = [:]
    let perform: (MediaAction) -> Void
    let open: (HomeItem) -> Void
    /// The tile the last tap came from, so only it is the zoom source for the screen it opens.
    var zoomSourceKey: String?
    var onTapped: (String) -> Void = { _ in }
    /// The item whose play is under way (`PendingPlay`), by key.
    var pendingKey: String?
    /// Performs an action that plays an item, following it through; nil performs it as any other.
    var play: ((HomeItem, MediaAction) -> Void)?

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
                    progress: progress[item.key],
                    tileKey: "jumpBackIn|\(item.key)",
                    zoomSourceKey: zoomSourceKey,
                    perform: perform,
                    open: { onTapped("jumpBackIn|\($0.key)"); open($0) },
                    pending: item.key == pendingKey,
                    play: play
                )
            }
        }
    }
}

/// One cell, after Spotify's recents: the artwork in a square slot flush with the cell's top, bottom and leading edges,
/// the title over up to two lines and how far into the item its queue was left, centred beside it, on a rounded fill,
/// with a play button at the end that carries on from there. The artwork's shape says what the item is (an artist is a
/// circle centred in the slot; a playlist's or genre's covers carry its glyph), so no line names the kind; VoiceOver
/// says it. Long-press has the rest (Play from Start, Shuffle, queue, Go to).
struct JumpBackInCell: View {
    let item: HomeItem
    let progress: HomeItemProgress?
    let tileKey: String
    let zoomSourceKey: String?
    let perform: (MediaAction) -> Void
    let open: (HomeItem) -> Void
    /// Its play is under way (`PendingPlay`): the play button shows a spinner.
    var pending = false
    /// Performs an action that plays the item, following it through; nil performs it as any other.
    var play: ((HomeItem, MediaAction) -> Void)?

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.homeCovers) private var covers
    /// The artwork slot's side, which is the cell's height: `ArtworkSize.albumRow`, growing with the text beside it.
    @ScaledMetric(relativeTo: .footnote) private var scaledSlot = ArtworkSize.albumRow

    private static let accessibilityTitleLines = 6

    /// An artist's circle is drawn this far in from the slot's edge.
    private static let artistInset = Spacing.small

    /// At the accessibility sizes the text can outgrow any slot, so the slot stops growing here and the cell grows
    /// with the text instead.
    private static let largestSlot = ArtworkSize.albumRow * 1.5

    /// The side the item's artwork draws at within a slot of side `slot`.
    static func artworkSide(for item: HomeItem, slot: CGFloat = ArtworkSize.albumRow) -> CGFloat {
        item is HomeItemArtistItem ? slot - 2 * artistInset : slot
    }

    /// What VoiceOver reads for the cell: its title, kind and progress ("Alice In Chains, Artist, Track 24 of 31").
    static func accessibilityLabel(item: HomeItem, progress: HomeItemProgress?) -> String {
        [item.title, item.typeLabel, progress?.localized()].compactMap { $0 }.joined(separator: ", ")
    }

    private var accessibilitySize: Bool { dynamicTypeSize.isAccessibilitySize }

    private var slot: CGFloat { accessibilitySize ? min(scaledSlot, Self.largestSlot) : scaledSlot }

    var body: some View {
        let slot = slot
        HStack(spacing: 0) {
            Button { open(item) } label: {
                HStack(alignment: accessibilitySize ? .top : .center, spacing: Spacing.small) {
                    artwork(slot: slot)
                    VStack(alignment: .leading, spacing: Spacing.tiny) {
                        Text(item.title)
                            .font(.footnote.weight(.semibold))
                            .foregroundStyle(.primary)
                            .lineLimit(accessibilitySize ? Self.accessibilityTitleLines : 2)
                        if let progress {
                            Text(progress.localized())
                                .font(.caption2)
                                .foregroundStyle(.s2TextSecondary)
                                .lineLimit(1)
                        }
                    }
                    // At the accessibility sizes (one column) the cell is as tall as its text, which needs room.
                    .padding(.vertical, accessibilitySize ? Spacing.small : 0)
                    .fixedSize(horizontal: false, vertical: accessibilitySize)
                    Spacer(minLength: 0)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.pressScale)
            .accessibilityLabel(Self.accessibilityLabel(item: item, progress: progress))
            .accessibilityIdentifier("homeGrid.cell")
            .zoomSource(for: item, tileKey: tileKey, activeKey: zoomSourceKey)
            .homeItemActions(HomeItemActions(item: item, perform: performTracked, open: open, resumes: true))

            Button {
                performTracked(item.resumeAction())
            } label: {
                ZStack {
                    if pending {
                        ProgressView()
                            .controlSize(.small)
                    } else {
                        Image(systemName: item is HomeItemGenreItem ? "shuffle" : "play.fill")
                            .font(.footnote.weight(.semibold))
                            .foregroundStyle(.tint)
                    }
                }
                .frame(width: Spacing.xlarge + Spacing.xsmall)
                .frame(maxHeight: .infinity)
                .contentShape(Rectangle())
            }
            .buttonStyle(JumpBackInPlayButtonStyle())
            .accessibilityLabel(item is HomeItemGenreItem ? "Shuffle \(item.title)" : "Play \(item.title)")
            .accessibilityValue(pending ? "Starting" : "")
            .accessibilityIdentifier("homeGrid.play")
            .disabled(pending)
        }
        // Every cell is the slot's height, so the artwork sits the same in each; at the accessibility sizes the text
        // sets it.
        .frame(height: accessibilitySize ? nil : slot)
        .frame(minHeight: slot)
        .background(.s2SurfaceContainer)
        .clipShape(S2Shape.artworkRow)
    }

    /// The artwork in its slot: a cover flush with it (square corners: the cell's own shape rounds the outer ones),
    /// an artist's circle inset and centred, on the cell's fill.
    private func artwork(slot: CGFloat) -> some View {
        HomeItemArtwork(item: item, size: Self.artworkSide(for: item, slot: slot), shape: S2Shape(.rounded(0)))
            .frame(width: slot, height: slot)
            .overlay(alignment: .bottomTrailing) { kindBadge }
    }

    /// A playlist's or genre's covers could pass for an album's, so they carry its glyph; its generated artwork
    /// already does.
    @ViewBuilder
    private var kindBadge: some View {
        let symbol: String? = switch onEnum(of: item) {
        case .playlistItem: GeneratedArtwork.playlistSymbol
        case .genreItem: GeneratedArtwork.genreSymbol
        default: nil
        }
        if let symbol, !(covers[item.key] ?? []).isEmpty {
            Image(systemName: symbol)
                .font(.caption2.weight(.semibold))
                .imageScale(.small)
                .foregroundStyle(.white)
                .padding(Spacing.xsmall)
                .background(.black.opacity(0.55), in: Circle())
                .padding(Spacing.xsmall)
                .accessibilityHidden(true)
        }
    }

    /// Performs `action`, through `play` if it plays the item.
    private func performTracked(_ action: MediaAction) {
        if let play, action is MediaActionResume || action is MediaActionPlay || action is MediaActionShuffle {
            play(item, action)
        } else {
            perform(action)
        }
    }
}

/// The cell's play button: its strip of the cell darkens while pressed, as a list row's highlight does.
struct JumpBackInPlayButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .background(configuration.isPressed ? Color.primary.opacity(0.12) : .clear)
            .animation(Motion.press, value: configuration.isPressed)
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
