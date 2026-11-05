import Shared
import SwiftUI

struct GenreDetailView: View {
    let name: String

    @Environment(Navigator.self) private var navigator: Navigator?
    @Environment(\.appServices) private var appServices
    private var services: AppServices { appServices.required }

    var body: some View {
        let route = Route.genre(name: name)
        let models = services.viewModels.viewModel(route.cacheKey) {
            GenreDetailModels(graph: services.graph, genreName: name)
        }
        Observing(models.genre.uiState, models.covers.uiState, models.actions.uiState) { state, covers, actions in
            GenreDetailContent(
                state: state,
                covers: covers,
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
                onSortSelected: { models.genre.onSortSelected(sort: $0) },
                onToggleAlbum: { models.genre.onToggleAlbum(album: $0) },
                onExpandAll: { models.genre.onExpandAll() },
                onCollapseAll: { models.genre.onCollapseAll() },
                onShuffleAlbums: { models.genre.onShuffleAlbums() },
                downloads: DetailDownloads(actions: models.actions, intent: services.playIntent, downloads: services.graph.offlineDownloads)
            )
            .mediaActionResults(actions.events, handled: { models.actions.onEventHandled(id: $0) }, send: { models.actions.send($0, intent: services.playIntent) })
            .modifier(ShuffleAlbumsFailedAlert(events: state.events, handled: { models.genre.onEventHandled(id: $0) }) { ($0 as? GenreDetailEventShuffleAlbumsFailed).map { $0.reason } })
        }
    }
}

/// The Genre detail screen's ViewModels, cached together under its route's key.
final class GenreDetailModels: ViewModelGroup {
    let genre: GenreDetailViewModel
    let covers: GenreDetailCoversViewModel
    let actions: MediaActionsViewModel

    init(graph: IosAppGraph, genreName: String) {
        genre = graph.genreDetailViewModelFactory.create(genreName: genreName)
        covers = graph.genreDetailCoversViewModelFactory.create(genreName: genreName)
        actions = graph.mediaActionsViewModel
    }

    var members: [Lifecycle_viewmodelViewModel] { [genre, covers, actions] }
}

/// The Genre detail screen from a `GenreDetailUiState`, in a `DetailScaffold` with the app's accent (a genre has no
/// cover to tint from).
struct GenreDetailContent: View {
    let state: GenreDetailUiState
    /// The songs whose covers make up its mosaic, as its Library row draws (`GenreDetailCoversViewModel`).
    var covers: [Song] = []
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
        } else if let genre = state.genre {
            DetailScaffold(title: genre.name, tintSource: nil) { layout in
                DetailHero(
                    title: genre.name,
                    subtitle: eyebrow(pluralized(Int(genre.songCount), .song), totalDuration(state.songs)),
                    layout: layout,
                    onPlay: { onPlay(0) },
                    onShuffle: onShuffle
                ) { points in
                    CoverMosaic.genre(genre.name, covers: covers, shape: .artworkHero)
                        .frame(width: points, height: points)
                }
            } rows: {
                if !state.songs.isEmpty {
                    songsHeader
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
                    songRow(song, index: index, numbered: numbered)
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
                    .accessibilityIdentifier("genreDetail.more")
                }
            }
        } else {
            EmptyState("Genre Not Found", systemImage: "guitars")
        }
    }

    private var songsHeader: some View {
        let inset = AdaptiveLayout.contentInset(layoutTier)
        return Section {
            SongListHeader(
                sort: state.sort,
                choices: state.kind.sortChoices,
                showsAlbums: state.sections.hasAlbumSections,
                allExpanded: state.allAlbumsExpanded,
                idPrefix: "genreDetail",
                onSortSelected: onSortSelected,
                onExpandAll: onExpandAll,
                onCollapseAll: onCollapseAll
            )
            .rowSeparator(.none)
            .listRowInsets(EdgeInsets(top: 0, leading: inset, bottom: 0, trailing: max(0, inset - Spacing.smallMedium)))
        }
        .listSectionSpacing(0)
    }

    private func songRow(_ song: Song, index: Int, numbered: Bool) -> some View {
        Button { onPlay(index) } label: {
            let playback = rowPlayback(song, current: state.currentSong, isPlaying: isPlaying)
            if numbered {
                TrackRow(number: song.track.map { Int($0.intValue) }, title: song.name ?? "Unknown", durationMs: Int64(song.duration), playback: playback)
            } else {
                SongRow(song: song, playback: playback)
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
