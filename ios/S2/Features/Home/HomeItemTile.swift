import Shared
import SwiftUI

/// What Home's tiles and grid cells say about a `HomeItem` and the actions they offer on it (#633). The item's
/// `playAction()` is the shared model's: an album, artist or playlist plays from its start, a genre shuffles.
extension HomeItem {
    var title: String {
        switch onEnum(of: self) {
        case .albumItem(let it): it.album.name ?? "Unknown"
        case .artistItem(let it): it.albumArtist.name ?? it.albumArtist.friendlyArtistName ?? "Unknown"
        case .playlistItem(let it): it.playlist.name
        case .smartPlaylistItem(let it): it.smartPlaylistId.title
        case .genreItem(let it): it.genre.name
        }
    }

    /// The kind of item, shown where a section mixes kinds (Jump Back In's grid, a mixed shelf's subtitle).
    var typeLabel: String {
        switch onEnum(of: self) {
        case .albumItem: "Album"
        case .artistItem: "Artist"
        case .playlistItem, .smartPlaylistItem: "Playlist"
        case .genreItem: "Genre"
        }
    }

    /// A shelf tile's accessibility identifier: its kind, with a smart playlist kept apart from a playlist (#633).
    var tileIdentifier: String {
        switch onEnum(of: self) {
        case .albumItem: "homeTile.album"
        case .artistItem: "homeTile.artist"
        case .playlistItem: "homeTile.playlist"
        case .smartPlaylistItem: "homeTile.smartPlaylist"
        case .genreItem: "homeTile.genre"
        }
    }

    /// The line under a shelf tile's title: the album's artist, the artist's album count, a playlist's or genre's
    /// song count. Nil when there's nothing beyond the kind.
    var detail: String? {
        switch onEnum(of: self) {
        case .albumItem(let it): it.album.albumArtist ?? it.album.friendlyArtistName
        case .artistItem(let it): it.albumArtist.albumCount == 1 ? "1 album" : "\(it.albumArtist.albumCount) albums"
        case .playlistItem(let it): Self.songCount(Int(it.playlist.songCount))
        case .smartPlaylistItem: nil
        case .genreItem(let it): Self.songCount(Int(it.genre.songCount))
        }
    }

    /// A shelf tile's subtitle: in a section of one kind just the detail, in a mixed one the kind first
    /// ("Artist · 12 albums"), so a row of covers says which are albums and which are artists.
    func subtitle(mixed: Bool) -> String {
        guard let detail else { return typeLabel }
        return mixed ? "\(typeLabel) · \(detail)" : detail
    }

    /// The label of the action that opens the item's screen.
    var goToLabel: String { "Go to \(typeLabel)" }

    /// The songs the item stands for, as the queue actions take them.
    var selection: MediaSelection {
        switch onEnum(of: self) {
        case .albumItem(let it): MediaSelectionAlbums(album: it.album)
        case .artistItem(let it): MediaSelectionAlbumArtists(albumArtist: it.albumArtist)
        case .playlistItem(let it): MediaSelectionPlaylists(playlist: it.playlist)
        case .smartPlaylistItem(let it): MediaSelectionSongsMatching(query: it.smartPlaylistId.songQuery)
        case .genreItem(let it): MediaSelectionGenres(genre: it.genre)
        }
    }

    /// Plays the item in order from its start, recording where it was played from. The same as `playAction()`
    /// except for a genre, whose tile shuffles but whose Play plays.
    func playInOrderAction() -> MediaAction {
        switch onEnum(of: self) {
        case .genreItem: MediaActionPlay(selection: selection, position: 0, context: playContext)
        default: playAction()
        }
    }

    func shuffleAction() -> MediaAction {
        MediaActionShuffle(selection: selection, context: playContext)
    }

    private static func songCount(_ count: Int) -> String {
        count == 1 ? "1 song" : "\(count) songs"
    }
}

/// Plays, shuffles, queues or opens an item; the context menu and the VoiceOver actions of every Home tile.
struct HomeItemActions {
    let item: HomeItem
    let perform: (MediaAction) -> Void
    let open: (HomeItem) -> Void

    @ViewBuilder
    var menu: some View {
        Button("Play", systemImage: "play") { perform(item.playInOrderAction()) }
        Button("Shuffle", systemImage: "shuffle") { perform(item.shuffleAction()) }
        Button("Play Next", systemImage: "text.line.first.and.arrowtriangle.forward") {
            perform(MediaActionPlayNext(selection: item.selection))
        }
        Button("Add to Queue", systemImage: "text.append") { perform(MediaActionAddToQueue(selection: item.selection)) }
        Divider()
        Button(item.goToLabel, systemImage: "arrow.forward") { open(item) }
    }
}

extension View {
    /// The item's context menu and the same actions for VoiceOver's rotor.
    func homeItemActions(_ actions: HomeItemActions) -> some View {
        contextMenu { actions.menu }
            .accessibilityAction(named: "Play") { actions.perform(actions.item.playInOrderAction()) }
            .accessibilityAction(named: "Shuffle") { actions.perform(actions.item.shuffleAction()) }
            .accessibilityAction(named: "Play Next") { actions.perform(MediaActionPlayNext(selection: actions.item.selection)) }
            .accessibilityAction(named: "Add to Queue") { actions.perform(MediaActionAddToQueue(selection: actions.item.selection)) }
            .accessibilityAction(named: actions.item.goToLabel) { actions.open(actions.item) }
    }
}

/// An item's artwork at `size`: an album's cover or an artist's picture (same corners for both, the owner's call),
/// and for a playlist, smart playlist or genre, which have no artwork of their own, `GeneratedArtwork`.
struct HomeItemArtwork: View {
    let item: HomeItem
    let size: CGFloat
    let cornerRadius: CGFloat

    var body: some View {
        artwork.artworkTile(size, cornerRadius: cornerRadius)
    }

    @ViewBuilder
    private var artwork: some View {
        switch onEnum(of: item) {
        case .albumItem(let it):
            RemoteArtwork(.album(it.album), points: size) { ArtworkPlaceholder(symbol: "square.stack") }
        case .artistItem(let it):
            RemoteArtwork(.albumArtist(it.albumArtist), points: size) { ArtworkPlaceholder(symbol: "music.mic") }
        case .playlistItem(let it):
            GeneratedArtwork(seed: it.playlist.name, symbol: GeneratedArtwork.playlistSymbol)
        case .smartPlaylistItem(let it):
            GeneratedArtwork(seed: it.smartPlaylistId.id, symbol: it.smartPlaylistId.symbol)
        case .genreItem(let it):
            GeneratedArtwork(seed: it.genre.name, symbol: GeneratedArtwork.genreSymbol)
        }
    }
}

/// Artwork for an item that has none of its own (#646): one of a few muted tones, picked by the item's name so a
/// genre or playlist keeps its tone from launch to launch, screen to screen and platform to platform (Android's
/// `GeneratedArtwork`), under a small glyph for its kind. The title under the tile says which one it is.
struct GeneratedArtwork: View {
    let seed: String
    let symbol: String

    /// A genre's glyph; every genre shares it, as on Android.
    static let genreSymbol = "music.quarternote.3"
    /// A playlist's glyph.
    static let playlistSymbol = "music.note.list"

    var body: some View {
        GeometryReader { proxy in
            let glyph = min(proxy.size.width, proxy.size.height) * ArtworkPalette.generatedGlyphScale
            ZStack {
                ArtworkPalette.tone(seed)
                Image(systemName: symbol)
                    .resizable()
                    .scaledToFit()
                    .fontWeight(.medium)
                    .frame(width: glyph, height: glyph)
                    .foregroundStyle(ArtworkPalette.generatedGlyph)
            }
        }
        .accessibilityHidden(true)
    }
}

/// A shelf tile: the item's artwork at `ArtworkSize.shelf(tier)` with the tile corner, its title and a subtitle.
/// Albums and artists alike, the owner's call (no round artist pictures).
struct HomeShelfTileLabel: View {
    let item: HomeItem
    let mixed: Bool

    @Environment(\.layoutTier) private var layoutTier

    var body: some View {
        let size = ArtworkSize.shelf(layoutTier)
        VStack(alignment: .leading, spacing: Spacing.xsmall) {
            HomeItemArtwork(item: item, size: size, cornerRadius: ArtworkCorner.tile)
                .padding(.bottom, Spacing.xsmall)
            Text(item.title)
                .font(.subheadline.weight(.medium))
                .foregroundStyle(.primary)
                .lineLimit(1)
            Text(item.subtitle(mixed: mixed))
                .font(.caption)
                .foregroundStyle(.s2SecondaryText)
                .lineLimit(1)
        }
        .frame(width: size, alignment: .leading)
        .contentShape(Rectangle())
    }
}
