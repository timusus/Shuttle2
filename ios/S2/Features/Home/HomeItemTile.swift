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

/// Plays, shuffles, queues or opens an item; the context menu and the VoiceOver actions of every Home tile. A tile
/// that `resumes` (Jump Back In's, #670) plays on from where the item's queue was left, with Play from Start after Play.
struct HomeItemActions {
    let item: HomeItem
    let perform: (MediaAction) -> Void
    let open: (HomeItem) -> Void
    var resumes = false

    var playAction: MediaAction {
        resumes ? MediaActionResume(fromStart: item.playInOrderAction(), context: item.playContext) : item.playInOrderAction()
    }

    @ViewBuilder
    var menu: some View {
        Button("Play", systemImage: "play") { perform(playAction) }
        if resumes {
            Button("Play from Start", systemImage: "arrow.counterclockwise") { perform(item.playInOrderAction()) }
        }
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
            .accessibilityAction(named: "Play") { actions.perform(actions.playAction) }
            .accessibilityActions {
                if actions.resumes {
                    Button("Play from Start") { actions.perform(actions.item.playInOrderAction()) }
                }
            }
            .accessibilityAction(named: "Shuffle") { actions.perform(actions.item.shuffleAction()) }
            .accessibilityAction(named: "Play Next") { actions.perform(MediaActionPlayNext(selection: actions.item.selection)) }
            .accessibilityAction(named: "Add to Queue") { actions.perform(MediaActionAddToQueue(selection: actions.item.selection)) }
            .accessibilityAction(named: actions.item.goToLabel) { actions.open(actions.item) }
    }
}

/// An item's artwork at `size`: an album's cover or an artist's picture (same corners for both, the owner's call);
/// for a playlist or genre, which have no artwork of their own, its `CoverMosaic` from Home's covers (#643); for a
/// smart playlist, its `GeneratedArtwork`.
struct HomeItemArtwork: View {
    let item: HomeItem
    let size: CGFloat
    /// The shape for everything but an artist, who is always `S2Shape.artist`.
    let shape: S2Shape

    @Environment(\.homeCovers) private var covers

    var body: some View {
        switch onEnum(of: item) {
        case .albumItem(let it):
            RemoteArtwork(.album(it.album), points: size) { ArtworkPlaceholder(symbol: "square.stack") }
                .artworkTile(size, shape: shape)
        case .artistItem(let it):
            RemoteArtwork(.albumArtist(it.albumArtist), points: size) { ArtworkPlaceholder(symbol: "music.mic") }
                .artworkTile(size, shape: .artist)
        case .playlistItem(let it):
            CoverMosaic.playlist(it.playlist.name, covers: covers[item.key] ?? [], shape: shape)
                .frame(width: size, height: size)
        case .smartPlaylistItem(let it):
            GeneratedArtwork(seed: it.smartPlaylistId.id, symbol: it.smartPlaylistId.symbol)
                .artworkTile(size, shape: shape)
        case .genreItem(let it):
            CoverMosaic.genre(it.genre.name, covers: covers[item.key] ?? [], shape: shape)
                .frame(width: size, height: size)
        }
    }
}

extension EnvironmentValues {
    /// Home's playlist and genre covers by `HomeItem.key` (`HomeUiState.covers`), for their mosaics.
    @Entry var homeCovers: [String: [Song]] = [:]
}

/// A shelf tile: the item's artwork at `ArtworkSize.shelf(tier)` with the tile corner (an artist's a circle), its
/// title and a subtitle.
struct HomeShelfTileLabel: View {
    let item: HomeItem
    let mixed: Bool

    @Environment(\.layoutTier) private var layoutTier

    var body: some View {
        let size = ArtworkSize.shelf(layoutTier)
        VStack(alignment: .leading, spacing: Spacing.xsmall) {
            HomeItemArtwork(item: item, size: size, shape: .artworkTile)
                .padding(.bottom, Spacing.xsmall)
            Text(item.title)
                .font(.subheadline.weight(.medium))
                .foregroundStyle(.primary)
                .lineLimit(1)
            Text(item.subtitle(mixed: mixed))
                .font(.caption)
                .foregroundStyle(.s2TextSecondary)
                .lineLimit(1)
        }
        .frame(width: size, alignment: .leading)
        .contentShape(Rectangle())
    }
}
