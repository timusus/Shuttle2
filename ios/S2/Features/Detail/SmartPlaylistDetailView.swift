import Shared
import SwiftUI

/// Smart playlist detail (P5-7): hero (the generated artwork its Library row draws, titled from its slug) and its songs, grouped
/// by album or flat in the sort chosen for it. Modeled on Android's `SmartPlaylistDetailScreen.kt`.
struct SmartPlaylistDetailView: View {
    let id: String

    @Environment(Navigator.self) private var navigator: Navigator?
    @Environment(\.appServices) private var appServices
    private var services: AppServices { appServices.required }

    var body: some View {
        let route = Route.smartPlaylist(id: id)
        let models = services.viewModels.viewModel(route.cacheKey) {
            SmartPlaylistDetailModels(graph: services.graph, smartPlaylistId: id)
        }
        Observing(models.playlist.uiState, models.actions.uiState) { state, actions in
            SmartPlaylistDetailContent(
                state: state,
                isPlaying: services.playerBinding.isPlaying,
                onPlay: { index in
                    models.actions.send(MediaActionPlay(selection: MediaSelectionSongs(songs: state.songs), position: Int32(index), context: state.playContext), intent: services.playIntent)
                },
                onShuffle: {
                    models.actions.send(MediaActionShuffle(selection: MediaSelectionSongs(songs: state.songs), context: state.playContext), intent: services.playIntent)
                },
                onPlayNext: { songs in
                    models.actions.send(MediaActionPlayNext(selection: MediaSelectionSongs(songs: songs)), intent: services.playIntent)
                },
                onAddToQueue: { songs in
                    models.actions.send(MediaActionAddToQueue(selection: MediaSelectionSongs(songs: songs)), intent: services.playIntent)
                },
                onExclude: { song in
                    models.actions.send(MediaActionExclude(selection: MediaSelectionSongs(song: song)), intent: services.playIntent)
                },
                onPlaySongs: { songs in
                    models.actions.send(MediaActionPlay(selection: MediaSelectionSongs(songs: songs), position: 0, context: state.playContext), intent: services.playIntent)
                },
                onShuffleSongs: { songs in
                    models.actions.send(MediaActionShuffle(selection: MediaSelectionSongs(songs: songs), context: state.playContext), intent: services.playIntent)
                },
                onOpenAlbum: { navigator.openAsserting(.album($0)) },
                onSortSelected: { models.playlist.onSortSelected(sort: $0) },
                onToggleAlbum: { models.playlist.onToggleAlbum(album: $0) },
                onExpandAll: { models.playlist.onExpandAll() },
                onCollapseAll: { models.playlist.onCollapseAll() },
                onShuffleAlbums: { models.playlist.onShuffleAlbums() },
                downloads: DetailDownloads(actions: models.actions, intent: services.playIntent, downloads: services.graph.offlineDownloads)
            )
            .mediaActionResults(actions.events, handled: { models.actions.onEventHandled(id: $0) }, send: { models.actions.send($0, intent: services.playIntent) })
            .smartPlaylistDetailEvents(state.events, handled: { models.playlist.onEventHandled(id: $0) })
        }
    }
}

/// The Smart Playlist detail screen's ViewModels, cached together under its route's key.
final class SmartPlaylistDetailModels: ViewModelGroup {
    let playlist: SmartPlaylistDetailViewModel
    let actions: MediaActionsViewModel

    init(graph: IosAppGraph, smartPlaylistId: String) {
        playlist = graph.smartPlaylistDetailViewModelFactory.create(smartPlaylistId: smartPlaylistId)
        actions = graph.mediaActionsViewModel
    }

    var members: [Lifecycle_viewmodelViewModel] { [playlist, actions] }
}

/// The Smart Playlist detail screen from a `SmartPlaylistDetailUiState`, in a `DetailScaffold` with the app's accent.
struct SmartPlaylistDetailContent: View {
    let state: SmartPlaylistDetailUiState
    var isPlaying: Bool = false
    var onPlay: (Int) -> Void = { _ in }
    var onShuffle: () -> Void = {}
    var onPlayNext: ([Song]) -> Void = { _ in }
    var onAddToQueue: ([Song]) -> Void = { _ in }
    var onExclude: (Song) -> Void = { _ in }
    var onPlaySongs: ([Song]) -> Void = { _ in }
    var onShuffleSongs: ([Song]) -> Void = { _ in }
    var onOpenAlbum: (Album) -> Void = { _ in }
    var onSortSelected: (SongListSort) -> Void = { _ in }
    var onToggleAlbum: (Album) -> Void = { _ in }
    var onExpandAll: () -> Void = {}
    var onCollapseAll: () -> Void = {}
    var onShuffleAlbums: () -> Void = {}
    var downloads = DetailDownloads()

    @State private var songInfo: SongInfoTarget?
    /// The song download badges, read in each song's menu so Download and Remove Download follow it.
    @Environment(\.downloadBadges) private var downloadBadges
    @Environment(\.layoutTier) private var layoutTier
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        if state.loading {
            ProgressView()
        } else if let smartPlaylist = state.smartPlaylist {
            DetailScaffold(title: smartPlaylist.id.title, tintSource: nil) { layout in
                DetailHero(
                    title: smartPlaylist.id.title,
                    subtitle: eyebrow(pluralized(state.songs.count, .song), totalDuration(state.songs)),
                    layout: layout,
                    onPlay: { onPlay(0) },
                    onShuffle: onShuffle
                ) { points in
                    // The generated artwork its Library row draws: a smart playlist has no cover songs.
                    CoverMosaic(covers: [], seed: smartPlaylist.id.title, symbol: smartPlaylist.id.symbol, shape: .artworkHero)
                        .frame(width: points, height: points)
                }
            } rows: {
                if let kind = state.kind, !state.songs.isEmpty {
                    songsHeader(kind)
                }
                PlaylistSongSections(
                    sections: state.sections,
                    expandedAlbums: state.expandedAlbums,
                    onToggleAlbum: onToggleAlbum,
                    onPlaySection: onPlaySongs,
                    onShuffleSection: onShuffleSongs,
                    onPlayNext: onPlayNext,
                    onAddToQueue: onAddToQueue,
                    onOpenAlbum: onOpenAlbum
                ) { song, index, numbered in
                    songRow(song, index: index, numbered: numbered, smartPlaylist: smartPlaylist)
                }
            }
            .songInfoSheet($songInfo)
            .animation(Motion.disclosure.reduced(reduceMotion), value: state.expandedAlbums)
            .toolbar {
                if state.sort.groupsByAlbum {
                    Menu {
                        Button("Shuffle by Album", systemImage: "square.stack", action: onShuffleAlbums)
                    } label: {
                        Label("More", systemImage: "ellipsis.circle")
                    }
                    .accessibilityIdentifier("playlistDetail.more")
                }
            }
        } else {
            EmptyState("Playlist Not Found", systemImage: "star")
        }
    }

    private func songsHeader(_ kind: PlaylistKind) -> some View {
        let inset = AdaptiveLayout.contentInset(layoutTier)
        return Section {
            SongListHeader(
                sort: state.sort,
                choices: kind.sortChoices,
                showsAlbums: state.sections.hasAlbumSections,
                allExpanded: state.allAlbumsExpanded,
                idPrefix: "playlistDetail",
                onSortSelected: onSortSelected,
                onExpandAll: onExpandAll,
                onCollapseAll: onCollapseAll
            )
            .rowSeparator(.none)
            .listRowInsets(EdgeInsets(top: 0, leading: inset, bottom: 0, trailing: max(0, inset - Spacing.smallMedium)))
        }
        .listSectionSpacing(0)
    }

    private func songRow(_ song: Song, index: Int, numbered: Bool, smartPlaylist: SmartPlaylist) -> some View {
        Button { onPlay(index) } label: {
            let playback = rowPlayback(song, current: state.currentSong, isPlaying: isPlaying)
            if numbered {
                TrackRow(number: song.track.map { Int($0.intValue) }, title: song.name ?? "Unknown", durationMs: Int64(song.duration), playback: playback)
            } else {
                SongRow(song: song, playback: playback, key: SongRowKey(smartPlaylist: smartPlaylist.id))
            }
        }
        .buttonStyle(.plain)
        .rowSeparator(numbered ? .system : .none)
        .contextMenu {
            // Reading the song's badge rebuilds this menu when its download starts, finishes or is removed.
            let _ = downloadBadges[song.path]
            SongRowMenu(song: song, onPlayNext: { onPlayNext([$0]) }, onAddToQueue: { onAddToQueue([$0]) }, onExclude: onExclude, onSongInfo: { songInfo = SongInfoTarget(songID: $0.id) }, downloads: downloads)
        }
    }
}

extension View {
    /// A Shuffle by Album that couldn't start.
    func smartPlaylistDetailEvents(_ events: [PendingEvent<any SmartPlaylistDetailEvent>], handled: @escaping (Int64) -> Void) -> some View {
        modifier(ShuffleAlbumsFailedAlert(events: events, handled: handled) { ($0 as? SmartPlaylistDetailEventShuffleAlbumsFailed).map { $0.reason } })
    }
}

/// Alerts when a playlist screen's Shuffle by Album fails; `failure` returns a failed event's reason, nil for other events.
struct ShuffleAlbumsFailedAlert<Event: AnyObject>: ViewModifier {
    let events: [PendingEvent<Event>]
    let handled: (Int64) -> Void
    /// The outer optional is whether the event is a shuffle failure; the inner is the player's reason, if it gave one.
    let failure: (Event) -> String??
    var onOther: (Event) -> Void = { _ in }
    @State private var alert: String?

    func body(content: Content) -> some View {
        content
            .consumeEvents(events, handled: handled) { event in
                if let reason = failure(event) {
                    alert = reason.map { "Couldn't shuffle: \($0)" } ?? "Couldn't shuffle."
                } else {
                    onOther(event)
                }
            }
            .alert(alert ?? "", isPresented: Binding(get: { alert != nil }, set: { if !$0 { alert = nil } })) {
                Button("OK", role: .cancel) {}
            }
    }
}
