import Shared
import SwiftUI

/// Jump Back In (#633, #706): the most recent item as a wide resume card with the one Play button, which carries on
/// exactly where its queue was left (#670), then the rest as a compact grid of tiles after Apple Music's and Spotify's
/// recents, two columns on an iPhone, four on an iPad and one at the accessibility text sizes. Every card and tile
/// opens its item; long-press has the rest.
struct JumpBackInGrid: View {
    let items: [HomeItem]
    /// Where each item's queue was left, by key.
    var progress: [String: HomeItemProgress] = [:]
    let perform: (MediaAction) -> Void
    let open: (HomeItem) -> Void
    /// A tile's tap, by its key, before it opens: the tapped tile is the zoom source for the screen it opens.
    var onTapped: (String) -> Void = { _ in }
    /// The item whose play is under way (`PlayIntent.loadingKey`), by key.
    var pendingKey: String?
    /// Performs an action that plays an item, following it through; nil performs it as any other.
    var play: ((HomeItem, MediaAction) -> Void)?

    @Environment(\.layoutTier) private var layoutTier
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    /// The tiles under the card: three rows on an iPhone.
    static let maximumTiles = 6

    static func columnCount(tier: LayoutTier, accessibilitySize: Bool) -> Int {
        if accessibilitySize { return 1 }
        return tier == .compact ? 2 : 4
    }

    var body: some View {
        let count = Self.columnCount(tier: layoutTier, accessibilitySize: dynamicTypeSize.isAccessibilitySize)
        let columns = Array(repeating: GridItem(.flexible(), spacing: Spacing.small, alignment: .top), count: count)
        let open: (HomeItem) -> Void = { onTapped("jumpBackIn|\($0.key)"); open($0) }
        VStack(alignment: .leading, spacing: Spacing.small) {
            if let first = items.first {
                JumpBackInResumeCard(
                    item: first,
                    progress: progress[first.key],
                    tileKey: "jumpBackIn|\(first.key)",
                    perform: perform,
                    open: open,
                    pending: first.key == pendingKey,
                    play: play
                )
            }
            LazyVGrid(columns: columns, alignment: .leading, spacing: Spacing.small) {
                ForEach(items.dropFirst().prefix(Self.maximumTiles), id: \.key) { item in
                    JumpBackInCell(
                        item: item,
                        progress: progress[item.key],
                        tileKey: "jumpBackIn|\(item.key)",
                            perform: perform,
                        open: open,
                        play: play
                    )
                }
            }
        }
    }
}

/// What Jump Back In says about where an item's queue was left (#706).
enum JumpBackInText {
    /// A tile's second line: the kind, then the song it was left on, "Shuffled" or "Finished" ("Album · Airbag").
    static func detail(item: HomeItem, progress: HomeItemProgress?) -> String {
        guard let state = state(progress) else { return item.typeLabel }
        return "\(item.typeLabel) · \(state)"
    }

    /// "Finished", "Shuffled" (where in a shuffled queue says little) or the song the queue was left on; nil for no
    /// progress.
    static func state(_ progress: HomeItemProgress?) -> String? {
        guard let progress else { return nil }
        if progress.finished { return "Finished" }
        if progress.shuffled { return "Shuffled" }
        return progress.songName
    }

    /// What VoiceOver reads for a card or tile: the title, the kind and where it was left ("OK Computer, Album, on
    /// Airbag, 40% through"), and on the card when.
    static func accessibilityLabel(item: HomeItem, progress: HomeItemProgress?, now: Date? = nil) -> String {
        var parts = [item.title, item.typeLabel]
        if let progress {
            if progress.finished {
                parts.append("finished")
            } else {
                if let song = progress.songName { parts.append("on \(song)") }
                parts.append(progress.shuffled ? "shuffled" : "\(percent(progress))% through")
            }
            if let now { parts.append(relativeTime(progress, now: now)) }
        }
        return parts.joined(separator: ", ")
    }

    /// When the queue was left, "Yesterday" or "2 hours ago".
    static func relativeTime(_ progress: HomeItemProgress, now: Date = .now) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(progress.updatedAt.toEpochMilliseconds()) / 1000)
        return RelativeDateTimeFormatter.jumpBackIn.localizedString(for: min(date, now), relativeTo: now)
    }

    static func percent(_ progress: HomeItemProgress) -> Int {
        Int((Double(progress.fraction) * 100).rounded())
    }

    /// Whether the progress bar shows: the queue is under way, in order. A shuffled queue shows its glyph instead.
    static func showsBar(_ progress: HomeItemProgress?) -> Bool {
        guard let progress else { return false }
        return !progress.finished && !progress.shuffled && progress.fraction > 0
    }
}

private extension RelativeDateTimeFormatter {
    static let jumpBackIn: RelativeDateTimeFormatter = {
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .full
        formatter.dateTimeStyle = .named
        formatter.formattingContext = .beginningOfSentence
        return formatter
    }()
}

/// The most recent item (#706): its artwork with how far through it the queue was left as a thin bar, the kind and
/// when, the title, and the song it was left on (a shuffle glyph before it when shuffled), with the one Play button
/// that carries on exactly there, showing a spinner until it plays. A tap anywhere else opens the item.
struct JumpBackInResumeCard: View {
    let item: HomeItem
    let progress: HomeItemProgress?
    let tileKey: String
    let perform: (MediaAction) -> Void
    let open: (HomeItem) -> Void
    /// Its play is under way (`PlayIntent.loadingKey`): the play button shows a spinner.
    var pending = false
    /// Performs an action that plays the item, following it through; nil performs it as any other.
    var play: ((HomeItem, MediaAction) -> Void)?
    var now: Date = .now

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @ScaledMetric(relativeTo: .headline) private var scaledArtwork = ArtworkSize.resumeCard

    private var accessibilitySize: Bool { dynamicTypeSize.isAccessibilitySize }

    /// The play button's label: Resume while the queue's under way, Shuffle for a genre with none, else Play.
    static func playLabel(item: HomeItem, progress: HomeItemProgress?) -> String {
        if let progress, !progress.finished { return "Resume \(item.title)" }
        return item is HomeItemGenreItem ? "Shuffle \(item.title)" : "Play \(item.title)"
    }

    var body: some View {
        let artworkSide = min(scaledArtwork, ArtworkSize.resumeCard * 1.5)
        // At the accessibility sizes the card stacks, so the text has the full width and the Play disc sits under it.
        let layout = accessibilitySize
            ? AnyLayout(VStackLayout(alignment: .leading, spacing: Spacing.smallMedium))
            : AnyLayout(HStackLayout(spacing: Spacing.smallMedium))
        layout {
            Button { open(item) } label: {
                HStack(alignment: accessibilitySize ? .top : .center, spacing: Spacing.smallMedium) {
                    artwork(side: artworkSide)
                    VStack(alignment: .leading, spacing: Spacing.tiny) {
                        Text(overline)
                            .font(.caption)
                            .foregroundStyle(.s2TextSecondary)
                            .lineLimit(1)
                        Text(item.title)
                            .font(.headline)
                            .foregroundStyle(.primary)
                            .lineLimit(accessibilitySize ? nil : 2)
                        songLine
                    }
                    .fixedSize(horizontal: false, vertical: true)
                    Spacer(minLength: 0)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.pressScale)
            .accessibilityLabel(JumpBackInText.accessibilityLabel(item: item, progress: progress, now: now))
            .accessibilityIdentifier("homeGrid.card")
            .zoomSource(for: item, tileKey: tileKey)
            .homeItemActions(HomeItemActions(item: item, perform: performTracked, open: open, resumes: true))

            playButton
        }
        .padding(Spacing.small)
        .background(.s2SurfaceContainer)
        .clipShape(S2Shape.card)
        .artworkTint(from: item.tintSource)
    }

    /// The kind, then when the queue was left: "Album · Yesterday".
    private var overline: String {
        guard let progress else { return item.typeLabel }
        return "\(item.typeLabel) · \(JumpBackInText.relativeTime(progress, now: now))"
    }

    @ViewBuilder
    private var songLine: some View {
        if let progress {
            let text = progress.finished ? "Finished · Play again" : progress.songName ?? JumpBackInText.state(progress)
            if let text {
                HStack(spacing: Spacing.xsmall) {
                    if progress.shuffled && !progress.finished {
                        Image(systemName: "shuffle")
                            .imageScale(.small)
                            .accessibilityIdentifier("homeGrid.shuffled")
                    }
                    Text(text)
                        .lineLimit(accessibilitySize ? nil : 1)
                }
                .font(.subheadline)
                .foregroundStyle(.s2TextSecondary)
            }
        }
    }

    private func artwork(side: CGFloat) -> some View {
        HomeItemArtwork(item: item, size: side, shape: S2Shape.artworkRow)
            .overlay(alignment: .bottom) {
                if JumpBackInText.showsBar(progress), let progress {
                    JumpBackInProgressBar(fraction: Double(progress.fraction))
                        .clipShape(S2Shape.artworkRow)
                }
            }
    }

    private var playButton: some View {
        let shuffles = item is HomeItemGenreItem && (progress == nil || progress?.finished == true)
        return ResumePlayDisc(shuffles: shuffles, pending: pending) { performTracked(item.resumeAction()) }
            .accessibilityLabel(Self.playLabel(item: item, progress: progress))
            .accessibilityValue(pending ? "Starting" : "")
            .accessibilityIdentifier("homeGrid.play")
    }

    private func performTracked(_ action: MediaAction) {
        item.perform(action, perform: perform, play: play)
    }
}

/// The resume card's Play button: a disc in the item's artwork tint (`\.artworkTint`, scheme-safe) with the ink that
/// clears AA on it, the accent with no artwork (#741). Its own view so it reads the tint the card provides. The disc
/// grows with the glyph's text size, up to `maximumScale` times the 44 pt target, so the glyph never outgrows it.
private struct ResumePlayDisc: View {
    let shuffles: Bool
    let pending: Bool
    let action: () -> Void

    @Environment(\.artworkTint) private var tint
    @Environment(\.artworkTintInk) private var ink
    @ScaledMetric(relativeTo: .body) private var scaledDisc = TouchTarget.minimum

    /// The glyph's text size is capped where the disc stops growing: `accessibility1`'s body is about 1.6 times the
    /// default's, so at the cap the glyph fills the disc about as the default glyph fills 44 pt.
    static let maximumScale: CGFloat = 1.6

    var body: some View {
        let disc = min(scaledDisc, TouchTarget.minimum * Self.maximumScale)
        Button(action: action) {
            ZStack {
                if pending {
                    ProgressView()
                        .tint(ink)
                } else {
                    Image(systemName: shuffles ? "shuffle" : "play.fill")
                        .font(.body.weight(.semibold))
                        .foregroundStyle(ink)
                        .dynamicTypeSize(...DynamicTypeSize.accessibility1)
                }
            }
            .frame(width: disc, height: disc)
            .background(tint, in: Circle())
            .contentShape(Circle())
        }
        .buttonStyle(.pressScale)
        .disabled(pending)
    }
}

private extension HomeItem {
    /// The cover the resume card tints from (#741); nil for an item with no single cover, which keeps the accent. An
    /// artist tints from the artist page's hero source (`AlbumArtistDetailView`, before its view model has picked an
    /// online image or a fallback album), so the two share a cache key and a colour.
    var tintSource: ArtworkSource? {
        switch onEnum(of: self) {
        case .albumItem(let it): .album(it.album)
        case .artistItem(let it): .artistHero(ArtistHeroArtwork(artist: it.albumArtist, onlineLookup: false, fallbackAlbum: nil))
        case .playlistItem, .smartPlaylistItem, .genreItem: nil
        }
    }
}

/// How far through an item its queue was left, as a thin bar along the bottom of its artwork (#706).
struct JumpBackInProgressBar: View {
    let fraction: Double

    static let height: CGFloat = 3

    var body: some View {
        GeometryReader { geometry in
            ZStack(alignment: .leading) {
                Rectangle().fill(.black.opacity(0.35))
                Rectangle().fill(.tint).frame(width: geometry.size.width * min(max(fraction, 0), 1))
            }
        }
        .frame(height: Self.height)
        .accessibilityHidden(true)
    }
}

/// One tile, after Spotify's recents: the artwork in a square slot flush with the tile's top, bottom and leading edges,
/// with how far through the item its queue was left as a thin bar along its foot, and beside it the title over up to
/// two lines and a line saying the kind and where it was left ("Album · Airbag", "Playlist · Shuffled", "Genre ·
/// Finished"), on a rounded fill. A tap opens the item; long-press has the rest (Play, Play from Start, Shuffle,
/// queue, Go to).
struct JumpBackInCell: View {
    let item: HomeItem
    let progress: HomeItemProgress?
    let tileKey: String
    let perform: (MediaAction) -> Void
    let open: (HomeItem) -> Void
    /// Performs an action that plays the item, following it through; nil performs it as any other.
    var play: ((HomeItem, MediaAction) -> Void)?

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.homeCovers) private var covers
    /// The artwork slot's side, which is the tile's height: `ArtworkSize.albumRow`, growing with the text beside it.
    @ScaledMetric(relativeTo: .footnote) private var scaledSlot = ArtworkSize.albumRow

    private static let accessibilityTitleLines = 6

    /// An artist's circle is drawn this far in from the slot's edge.
    private static let artistInset = Spacing.small

    /// At the accessibility sizes the text can outgrow any slot, so the slot stops growing here and the tile grows
    /// with the text instead.
    private static let largestSlot = ArtworkSize.albumRow * 1.5

    /// The side the item's artwork draws at within a slot of side `slot`.
    static func artworkSide(for item: HomeItem, slot: CGFloat = ArtworkSize.albumRow) -> CGFloat {
        item is HomeItemArtistItem ? slot - 2 * artistInset : slot
    }

    private var accessibilitySize: Bool { dynamicTypeSize.isAccessibilitySize }

    private var slot: CGFloat { accessibilitySize ? min(scaledSlot, Self.largestSlot) : scaledSlot }

    var body: some View {
        let slot = slot
        Button { open(item) } label: {
            HStack(alignment: accessibilitySize ? .top : .center, spacing: Spacing.small) {
                artwork(slot: slot)
                VStack(alignment: .leading, spacing: Spacing.tiny) {
                    Text(item.title)
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(.primary)
                        .lineLimit(accessibilitySize ? Self.accessibilityTitleLines : 2)
                    Text(JumpBackInText.detail(item: item, progress: progress))
                        .font(.caption2)
                        .foregroundStyle(.s2TextSecondary)
                        .lineLimit(1)
                }
                // At the accessibility sizes (one column) the tile is as tall as its text, which needs room.
                .padding(.vertical, accessibilitySize ? Spacing.small : 0)
                .fixedSize(horizontal: false, vertical: accessibilitySize)
                Spacer(minLength: Spacing.small)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.pressScale)
        .accessibilityLabel(JumpBackInText.accessibilityLabel(item: item, progress: progress))
        .accessibilityIdentifier("homeGrid.cell")
        .zoomSource(for: item, tileKey: tileKey)
        .homeItemActions(HomeItemActions(item: item, perform: performTracked, open: open, resumes: true))
        // Every tile is the slot's height, so the artwork sits the same in each; at the accessibility sizes the text
        // sets it.
        .frame(height: accessibilitySize ? nil : slot)
        .frame(minHeight: slot)
        .background(.s2SurfaceContainer)
        .clipShape(S2Shape.artworkRow)
    }

    /// The artwork in its slot: a cover flush with it (square corners: the tile's own shape rounds the outer ones),
    /// an artist's circle inset and centred, on the tile's fill; the progress bar along the slot's foot.
    private func artwork(slot: CGFloat) -> some View {
        HomeItemArtwork(item: item, size: Self.artworkSide(for: item, slot: slot), shape: S2Shape(.rounded(0)))
            .frame(width: slot, height: slot)
            .overlay(alignment: .bottomTrailing) { kindBadge }
            .overlay(alignment: .bottom) {
                if JumpBackInText.showsBar(progress), let progress {
                    JumpBackInProgressBar(fraction: Double(progress.fraction))
                }
            }
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

    private func performTracked(_ action: MediaAction) {
        item.perform(action, perform: perform, play: play)
    }
}

extension HomeItem {
    /// Performs `action` on the item, through `play` if it plays it (following it through), else through `perform`.
    func perform(_ action: MediaAction, perform: (MediaAction) -> Void, play: ((HomeItem, MediaAction) -> Void)?) {
        if let play, action is MediaActionResume || action is MediaActionPlay || action is MediaActionShuffle {
            play(self, action)
        } else {
            perform(action)
        }
    }
}

extension View {
    /// The zoom source for the screen an album or artist tile opens (`zoomSource(id:tileKey:)`); a
    /// playlist or genre pushes without a zoom.
    @ViewBuilder
    func zoomSource(for item: HomeItem, tileKey: String) -> some View {
        switch onEnum(of: item) {
        case .albumItem(let it): zoomSource(id: Route.album(it.album).cacheKey, tileKey: tileKey)
        case .artistItem(let it):
            zoomSource(id: Route.albumArtist(albumArtistKey: it.albumArtist.groupKey.key).cacheKey, tileKey: tileKey)
        default: self
        }
    }
}
