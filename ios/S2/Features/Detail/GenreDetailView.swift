import Shared
import SwiftUI

/// Genre detail (P5-7): hero (icon placeholder — a genre has no artwork of its own), Play/Shuffle, the albums its
/// songs come from as a shelf, then every one of its songs. Modeled on Android's `GenreDetailScreen.kt`.
struct GenreDetailView: View {
    let name: String

    @Environment(Navigator.self) private var navigator: Navigator?

    var body: some View {
        let route = Route.genre(name: name)
        let models = ViewModelCache.shared.viewModel(route.cacheKey) {
            GenreDetailModels(graph: AppGraph.shared, genreName: name)
        }
        Observing(models.genre.uiState, models.actions.uiState) { state, actions in
            GenreDetailContent(
                state: state,
                isPlaying: AppGraph.dependencies.playerBinding.isPlaying,
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
                },
                onAlbumTap: { navigator?.open(.album($0)) }
            )
            .mediaActionResults(actions.events, handled: { models.actions.onEventHandled(id: $0) })
        }
    }
}

/// The Genre detail screen's ViewModels, cached together under its route's key.
final class GenreDetailModels: ViewModelGroup {
    let genre: GenreDetailViewModel
    let actions: MediaActionsViewModel

    init(graph: IosAppGraph, genreName: String) {
        genre = graph.genreDetailViewModelFactory.create(genreName: genreName)
        actions = graph.mediaActionsViewModel
    }

    var members: [Lifecycle_viewmodelViewModel] { [genre, actions] }
}

/// The Genre detail screen from a `GenreDetailUiState`, in a `DetailScaffold` with the app's accent (a genre has no
/// cover to tint from).
struct GenreDetailContent: View {
    let state: GenreDetailUiState
    var isPlaying: Bool = false
    var onPlay: (Int) -> Void = { _ in }
    var onShuffle: () -> Void = {}
    var onPlayNext: (Song) -> Void = { _ in }
    var onAddToQueue: (Song) -> Void = { _ in }
    var onAlbumTap: (Album) -> Void = { _ in }

    var body: some View {
        if state.loading {
            ProgressView()
        } else if let genre = state.genre {
            DetailScaffold(title: genre.name, tintSource: nil) { layout in
                DetailHero(
                    title: genre.name,
                    subtitle: eyebrow(pluralized(Int(genre.songCount), "song"), totalDuration(state.songs)),
                    layout: layout,
                    onPlay: { onPlay(0) },
                    onShuffle: onShuffle
                ) { points in
                    DetailPlaceholderArtwork(systemImage: "guitars", points: points)
                }
            } rows: {
                if !state.albums.isEmpty {
                    DetailAlbumShelf(title: "Albums", albums: state.albums, subtitle: { $0.albumArtist }, onAlbumTap: onAlbumTap)
                }
                Section {
                    SectionHeader("Songs")
                        .listRowSeparator(.hidden)
                    ForEach(Array(state.songs.enumerated()), id: \.element.id) { index, song in
                        Button { onPlay(index) } label: {
                            DetailSongRow(song: song, playback: rowPlayback(song, current: state.currentSong, isPlaying: isPlaying))
                        }
                        .buttonStyle(.plain)
                        .contextMenu {
                            Button("Play Next", systemImage: "text.line.first.and.arrowtriangle.forward") { onPlayNext(song) }
                            Button("Add to Queue", systemImage: "text.append") { onAddToQueue(song) }
                        }
                    }
                }
            }
        } else {
            EmptyState("Genre Not Found", systemImage: "guitars")
        }
    }
}
