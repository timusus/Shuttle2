import Shared
import SwiftUI

struct AlbumDetailView: View {
    let albumKey: String?
    let albumArtistKey: String?
    var albumIdentity: String? = nil

    @Environment(Navigator.self) private var navigator: Navigator?
    @Environment(\.appServices) private var appServices
    private var services: AppServices { appServices.required }

    var body: some View {
        let route = Route.album(albumKey: albumKey, albumArtistKey: albumArtistKey, albumIdentity: albumIdentity)
        let models = services.viewModels.viewModel(route.cacheKey) {
            AlbumDetailModels(
                graph: services.graph,
                groupKey: AlbumGroupKey(key: albumKey, albumArtistGroupKey: albumArtistKey.map { AlbumArtistGroupKey(key: $0) }, identity: albumIdentity)
            )
        }
        Observing(models.album.uiState, models.actions.uiState, services.graph.offlineDownloads.downloads) { state, actions, _ in
            AlbumDetailContent(
                state: state,
                isPlaying: services.playerBinding.isPlaying,
                onPlay: { index in
                    models.actions.send(MediaActionPlay(selection: MediaSelectionSongs(songs: state.songs), position: Int32(index), context: state.playContext), intent: services.playIntent)
                },
                onShuffle: { models.actions.send(state.shuffleAction, intent: services.playIntent) },
                onPlayWork: { songs in
                    models.actions.send(MediaActionPlay(selection: MediaSelectionSongs(songs: songs), position: 0, context: state.playContext), intent: services.playIntent)
                },
                onToggleWork: { models.album.onToggleWork(firstSongId: $0) },
                onOpenRecording: { key in
                    navigator.openAsserting(.album(albumKey: key.key, albumArtistKey: key.albumArtistGroupKey?.key, albumIdentity: key.identity))
                },
                // TODO: pass the composer page's open closure once its route lands (parallel composer-page work).
                onOpenComposer: nil,
                onPlayNext: { songs in
                    models.actions.send(MediaActionPlayNext(selection: MediaSelectionSongs(songs: songs)), intent: services.playIntent)
                },
                onAddToQueue: { songs in
                    models.actions.send(MediaActionAddToQueue(selection: MediaSelectionSongs(songs: songs)), intent: services.playIntent)
                },
                onPlayAlbumNext: { models.actions.send(MediaActionPlayNext(selection: MediaSelectionAlbums(album: $0)), intent: services.playIntent) },
                onAddAlbumToQueue: { models.actions.send(MediaActionAddToQueue(selection: MediaSelectionAlbums(album: $0)), intent: services.playIntent) },
                onGoToArtist: state.album.flatMap(AlbumArtistLink.init).map { link in
                    { navigator.openAsserting(link.route) }
                },
                onAlbumTap: { navigator.openAsserting(.album($0)) },
                albumActions: DetailAlbumActions(
                    onPlay: { models.actions.send(MediaActionPlay(selection: MediaSelectionAlbums(album: $0), position: 0), intent: services.playIntent) },
                    onPlayNext: { models.actions.send(MediaActionPlayNext(selection: MediaSelectionAlbums(album: $0)), intent: services.playIntent) },
                    onAddToQueue: { models.actions.send(MediaActionAddToQueue(selection: MediaSelectionAlbums(album: $0)), intent: services.playIntent) }
                ),
                downloads: DetailDownloads(actions: models.actions, intent: services.playIntent, downloads: services.graph.offlineDownloads)
            )
            .mediaActionResults(actions.events, handled: { models.actions.onEventHandled(id: $0) })
        }
    }
}

extension AlbumDetailUiState {
    /// A classical album shuffles by work, keeping each work's movements together and in order.
    var shuffleAction: any MediaAction {
        let selection = MediaSelectionSongs(songs: songs)
        return rows == nil ? MediaActionShuffle(selection: selection, context: playContext) : MediaActionShuffleWorks(selection: selection, context: playContext)
    }
}

/// The Album detail screen's ViewModels, cached together under its route's key.
final class AlbumDetailModels: ViewModelGroup {
    let album: AlbumDetailViewModel
    let actions: MediaActionsViewModel

    init(graph: IosAppGraph, groupKey: AlbumGroupKey) {
        album = graph.albumDetailViewModelFactory.create(groupKey: groupKey)
        actions = graph.mediaActionsViewModel
    }

    var members: [Lifecycle_viewmodelViewModel] { [album, actions] }
}

/// The Album detail screen from an `AlbumDetailUiState`, in a `DetailScaffold` tinted from the album's cover.
struct AlbumDetailContent: View {
    let state: AlbumDetailUiState
    /// Whether the player is playing, so the current track's indicator animates or holds still.
    var isPlaying: Bool = false
    var onPlay: (Int) -> Void = { _ in }
    var onShuffle: () -> Void = {}
    /// Plays a work's movements (a work header tap or Play button).
    var onPlayWork: ([Song]) -> Void = { _ in }
    /// Folds or unfolds a long work, by its first song's id.
    var onToggleWork: (Int64) -> Void = { _ in }
    var onOpenRecording: (AlbumGroupKey) -> Void = { _ in }
    /// Opens a composer page by their tag as written; nil leaves the composer eyebrow and headings untappable.
    var onOpenComposer: ((String) -> Void)? = nil
    var onPlayNext: ([Song]) -> Void = { _ in }
    var onAddToQueue: ([Song]) -> Void = { _ in }
    var onPlayAlbumNext: (Album) -> Void = { _ in }
    var onAddAlbumToQueue: (Album) -> Void = { _ in }
    /// Opens the album's artist (`AlbumArtistLink`); nil, or an album without a usable link, hides the menu item and the hero's link.
    var onGoToArtist: (() -> Void)? = nil
    /// Opens an album of the More by shelf.
    var onAlbumTap: (Album) -> Void = { _ in }
    var albumActions = DetailAlbumActions()
    /// The album's and each track's Download and Remove Download, and the hero's download status.
    var downloads = DetailDownloads()

    @State private var songInfo: SongInfoTarget?

    var body: some View {
        switch state.loadingState {
        case .loading:
            ProgressView()
        case .empty:
            EmptyState("Album Not Found", systemImage: "square.stack")
        case .ready:
            if let album = state.album {
                DetailScaffold(title: album.name ?? "Album", tintSource: .album(album)) { layout in
                    DetailHero(
                        title: album.name ?? "Unknown Album",
                        subtitle: subtitle(album),
                        artist: state.rows.map(\.eyebrow) ?? artistLink(album)?.name,
                        onArtist: heroArtistAction,
                        artistLabel: state.rows?.eyebrow.map { "Open composer \($0)" },
                        credits: state.rows?.credits,
                        layout: layout,
                        onPlay: { onPlay(0) },
                        onShuffle: onShuffle,
                        downloadStatus: downloads.summary?(state.songs).status ?? .notDownloaded
                    ) { points in
                        RemoteArtwork(.album(album), points: points)
                            .artworkTile(points, shape: .artworkHero)
                    }
                } rows: {
                    if let classical = state.rows {
                        ClassicalAlbumRowsView(
                            rows: classical.rows,
                            album: album,
                            otherRecordings: state.otherRecordings,
                            isPlaying: isPlaying,
                            actions: classicalActions,
                            downloads: downloads,
                            onSongInfo: { songInfo = SongInfoTarget(songID: $0.id) }
                        )
                    } else {
                        ForEach(discs, id: \.disc) { group in
                            Section {
                                ForEach(group.songs, id: \.id) { song in
                                    trackRow(song, album: album)
                                }
                            } header: {
                                if discs.count > 1 {
                                    SectionHeader("Disc \(group.disc)", style: .disc)
                                        .pinnedHeader()
                                }
                            }
                        }
                    }
                    if let name = moreByName(album) {
                        DetailAlbumShelf(
                            title: "More by \(name)",
                            albums: state.moreByArtist,
                            subtitle: { $0.year.map { String($0.intValue) } },
                            onAlbumTap: onAlbumTap,
                            albumActions: albumActions,
                            tileIdentifier: "detailTile.moreBy"
                        )
                    }
                }
                .songInfoSheet($songInfo)
                .toolbar {
                    Menu {
                        Button("Play Next", systemImage: "text.line.first.and.arrowtriangle.forward") { onPlayAlbumNext(album) }
                        Button("Add to Queue", systemImage: "text.append") { onAddAlbumToQueue(album) }
                        if let onGoToArtist, artistLink(album) != nil {
                            Button("Go to Artist", systemImage: "music.mic", action: onGoToArtist)
                        }
                        DownloadMenuItems(songs: state.songs, downloads: downloads)
                    } label: {
                        Label("More", systemImage: "ellipsis.circle")
                    }
                    .accessibilityIdentifier("albumDetail.more")
                }
            } else {
                EmptyState("Album Not Found", systemImage: "square.stack")
            }
        }
    }

    /// The eyebrow composer opens their page; on a classical album the artist line is the composer's, never the artist's.
    private var heroArtistAction: (() -> Void)? {
        guard let rows = state.rows else { return onGoToArtist }
        guard let composer = rows.eyebrowComposer, let onOpenComposer else { return nil }
        return { onOpenComposer(composer) }
    }

    private var classicalActions: ClassicalAlbumActions {
        ClassicalAlbumActions(
            onPlayFrom: onPlay,
            onPlayWork: onPlayWork,
            onToggleWork: onToggleWork,
            onPlayNext: onPlayNext,
            onAddToQueue: onAddToQueue,
            onOpenRecording: onOpenRecording,
            onOpenComposer: onOpenComposer
        )
    }

    private func trackRow(_ song: Song, album: Album) -> some View {
        let index = state.songs.firstIndex(where: { $0.id == song.id }) ?? 0
        return Button { onPlay(index) } label: {
            TrackRow(
                number: song.track.map { Int($0.intValue) },
                title: song.name ?? "Unknown",
                subtitle: trackArtist(song, album: album),
                durationMs: Int64(song.duration),
                playback: rowPlayback(song, current: state.currentSong, isPlaying: isPlaying)
            )
        }
        .buttonStyle(.plain)
        .songContextMenu(song, onPlayNext: onPlayNext, onAddToQueue: onAddToQueue, onSongInfo: { songInfo = SongInfoTarget(songID: $0.id) }, downloads: downloads)
    }

    /// Songs grouped by disc (falling back to disc 1) in the order the ViewModel already sorted them, as
    /// Android's screen does (`songs.groupBy { it.disc ?: 1 }.toSortedMap()`).
    private var discs: [(disc: Int32, songs: [Song])] {
        var order: [Int32] = []
        var groups: [Int32: [Song]] = [:]
        for song in state.songs {
            let disc = song.disc?.int32Value ?? 1
            if groups[disc] == nil { order.append(disc) }
            groups[disc, default: []].append(song)
        }
        return order.sorted().map { (disc: $0, songs: groups[$0] ?? []) }
    }

    /// A track's own artist, only where it differs from the album's (a compilation, a guest): on every row of a
    /// one-artist album it would repeat the hero.
    private func trackArtist(_ song: Song, album: Album) -> String? {
        guard let artist = song.friendlyArtistName, artist != (album.friendlyArtistName ?? album.albumArtist) else { return nil }
        return artist
    }

    /// The More by shelf's title: the album's credited artists (the view model falls back to the raw
    /// album artist tag), only when there are other albums to show.
    private func moreByName(_ album: Album) -> String? {
        guard !state.moreByArtist.isEmpty, !state.moreByArtistNames.isEmpty else { return nil }
        return state.moreByArtistNames.joined(separator: ", ")
    }

    private func artistName(_ album: Album) -> String? {
        album.friendlyArtistName ?? album.albumArtist
    }

    /// The album artist's link, only when it's both openable and offered.
    private func artistLink(_ album: Album) -> AlbumArtistLink? {
        onGoToArtist == nil ? nil : AlbumArtistLink(album)
    }

    /// With a tappable artist line above it the eyebrow leaves the artist out, else it leads with it.
    private func subtitle(_ album: Album) -> String {
        // A classical page leads with the composer and credits, so the subtitle drops the artist and counts tracks.
        if state.rows != nil {
            return eyebrow(
                album.year.map { String($0.intValue) },
                localizedPlural("album_detail_tracks", Int(album.songCount)),
                totalDuration(state.songs),
                AudioQuality.sharedBadge(of: state.songs)
            )
        }
        return eyebrow(
            artistLink(album) == nil ? artistName(album) : nil,
            album.year.map { String($0.intValue) },
            pluralized(state.songs.count, .song),
            totalDuration(state.songs),
            AudioQuality.sharedBadge(of: state.songs)
        )
    }
}


/// Where an album's artist line goes: the album artist, by name and group key. Nil unless both are real, so the
/// link never opens `Route.albumArtist(nil)` or names someone other than the page it opens (the track artists
/// of a compilation read "A, B, C" while it opens "Various Artists").
struct AlbumArtistLink: Equatable {
    let name: String
    let route: Route

    init?(_ album: Album) {
        // The primary album artist: an album of several opens its first, "A feat. B"'s opens A
        self.init(name: album.albumArtist, key: album.albumArtistKeys.first?.key)
    }

    init?(name: String?, key: String?) {
        guard let key, let name = name?.trimmingCharacters(in: .whitespacesAndNewlines), !name.isEmpty else { return nil }
        self.name = name
        route = .albumArtist(albumArtistKey: key)
    }
}
