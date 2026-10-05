import Shared
import SwiftUI

/// Smart playlist detail (P5-7): hero (the generated artwork its Library row draws, titled from its slug) and its songs in the smart
/// playlist's own sort. Modeled on Android's `SmartPlaylistDetailScreen.kt`, the simplest of the five screens.
struct SmartPlaylistDetailView: View {
    let id: String

    var body: some View {
        let route = Route.smartPlaylist(id: id)
        let models = ViewModelCache.shared.viewModel(route.cacheKey) {
            SmartPlaylistDetailModels(graph: AppGraph.shared, smartPlaylistId: id)
        }
        Observing(models.playlist.uiState, models.actions.uiState) { state, actions in
            SmartPlaylistDetailContent(
                state: state,
                isPlaying: AppGraph.dependencies.playerBinding.isPlaying,
                onPlay: { index in
                    models.actions.send(MediaActionPlay(selection: MediaSelectionSongs(songs: state.songs), position: Int32(index), context: state.playContext))
                },
                onShuffle: {
                    models.actions.send(MediaActionShuffle(selection: MediaSelectionSongs(songs: state.songs), context: state.playContext))
                },
                onPlayNext: { song in
                    models.actions.send(MediaActionPlayNext(selection: MediaSelectionSongs(song: song)))
                },
                onAddToQueue: { song in
                    models.actions.send(MediaActionAddToQueue(selection: MediaSelectionSongs(song: song)))
                },
                onExclude: { song in
                    models.actions.send(MediaActionExclude(selection: MediaSelectionSongs(song: song)))
                },
                downloads: DetailDownloads(actions: models.actions)
            )
            .mediaActionResults(actions.events, handled: { models.actions.onEventHandled(id: $0) }, send: { models.actions.send($0) })
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
    var onPlayNext: (Song) -> Void = { _ in }
    var onAddToQueue: (Song) -> Void = { _ in }
    var onExclude: (Song) -> Void = { _ in }
    var downloads = DetailDownloads()

    @State private var songInfo: SongInfoTarget?

    var body: some View {
        if state.loading {
            ProgressView()
        } else if let smartPlaylist = state.smartPlaylist {
            DetailScaffold(title: smartPlaylist.id.title, tintSource: nil) { layout in
                DetailHero(
                    title: smartPlaylist.id.title,
                    subtitle: eyebrow(pluralized(state.songs.count, "song"), totalDuration(state.songs)),
                    layout: layout,
                    onPlay: { onPlay(0) },
                    onShuffle: onShuffle
                ) { points in
                    // The generated artwork its Library row draws (#652): a smart playlist has no cover songs.
                    CoverMosaic(covers: [], seed: smartPlaylist.id.title, symbol: smartPlaylist.id.symbol, shape: .artworkHero)
                        .frame(width: points, height: points)
                }
            } rows: {
                ForEach(Array(state.songs.enumerated()), id: \.element.id) { index, song in
                    Button { onPlay(index) } label: {
                        SongRow(song: song, playback: rowPlayback(song, current: state.currentSong, isPlaying: isPlaying), key: SongRowKey(smartPlaylist: smartPlaylist.id))
                    }
                    .buttonStyle(.plain)
                    .rowSeparator(.none)
                    .contextMenu {
                        SongRowMenu(song: song, onPlayNext: onPlayNext, onAddToQueue: onAddToQueue, onExclude: onExclude, onSongInfo: { songInfo = SongInfoTarget(songID: $0.id) }, downloads: downloads)
                    }
                }
            }
            .songInfoSheet($songInfo)
        } else {
            EmptyState("Playlist Not Found", systemImage: "star")
        }
    }
}
