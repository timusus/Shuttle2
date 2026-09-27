import Shared
import SwiftUI

/// Smart playlist detail (P5-7): hero (icon placeholder, titled from its slug) and its songs in the smart
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
                onPlay: { index in
                    models.actions.dispatch(action: MediaActionPlay(selection: MediaSelectionSongs(songs: state.songs), position: Int32(index)))
                },
                onShuffle: {
                    models.actions.dispatch(action: MediaActionShuffle(selection: MediaSelectionSongs(songs: state.songs)))
                },
                onPlayNext: { song in
                    models.actions.dispatch(action: MediaActionPlayNext(selection: MediaSelectionSongs(song: song)))
                },
                onAddToQueue: { song in
                    models.actions.dispatch(action: MediaActionAddToQueue(selection: MediaSelectionSongs(song: song)))
                }
            )
            .mediaActionResults(actions.events, handled: { models.actions.onEventHandled(id: $0) })
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

/// The Smart Playlist detail screen from a `SmartPlaylistDetailUiState`.
struct SmartPlaylistDetailContent: View {
    let state: SmartPlaylistDetailUiState
    var onPlay: (Int) -> Void = { _ in }
    var onShuffle: () -> Void = {}
    var onPlayNext: (Song) -> Void = { _ in }
    var onAddToQueue: (Song) -> Void = { _ in }

    var body: some View {
        if state.loading {
            ProgressView()
        } else if let smartPlaylist = state.smartPlaylist {
            List {
                heroSection(smartPlaylist)
                ForEach(Array(state.songs.enumerated()), id: \.element.id) { index, song in
                    Button { onPlay(index) } label: {
                        AlbumSongRow(song: song, playing: song.id == state.currentSong?.id)
                    }
                    .buttonStyle(.plain)
                    .contextMenu {
                        Button("Play Next", systemImage: "text.line.first.and.arrowtriangle.forward") { onPlayNext(song) }
                        Button("Add to Queue", systemImage: "text.append") { onAddToQueue(song) }
                    }
                }
            }
            .listStyle(.plain)
            .navigationTitle(smartPlaylist.id.title)
            .navigationBarTitleDisplayMode(.inline)
        } else {
            EmptyState("Playlist Not Found", systemImage: "star")
        }
    }

    @ViewBuilder
    private func heroSection(_ smartPlaylist: SmartPlaylist) -> some View {
        Section {
            DetailHero(
                title: smartPlaylist.id.title,
                subtitle: pluralized(state.songs.count, "song"),
                onPlay: { onPlay(0) },
                onShuffle: onShuffle
            ) {
                DetailPlaceholderArtwork(systemImage: "star")
            }
            .listRowInsets(EdgeInsets())
        }
        .listRowSeparator(.hidden)
    }
}
