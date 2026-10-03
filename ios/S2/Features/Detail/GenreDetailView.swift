import Shared
import SwiftUI

/// Genre detail (P5-7): hero (a genre has no artwork of its own, so the same `CoverMosaic` its Library row draws, #652), Play/Shuffle, the albums its
/// songs come from as a shelf, then every one of its songs. Modeled on Android's `GenreDetailScreen.kt`.
struct GenreDetailView: View {
    let name: String

    @Environment(Navigator.self) private var navigator: Navigator?

    var body: some View {
        let route = Route.genre(name: name)
        let models = ViewModelCache.shared.viewModel(route.cacheKey) {
            GenreDetailModels(graph: AppGraph.shared, genreName: name)
        }
        Observing(models.genre.uiState, models.covers.uiState, models.actions.uiState) { state, covers, actions in
            GenreDetailContent(
                state: state,
                covers: covers,
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
                onAlbumTap: { navigator.openAsserting(.album($0)) }
            )
            .mediaActionResults(actions.events, handled: { models.actions.onEventHandled(id: $0) }, send: { models.actions.send($0) })
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
    var onPlayNext: (Song) -> Void = { _ in }
    var onAddToQueue: (Song) -> Void = { _ in }
    var onExclude: (Song) -> Void = { _ in }
    var onAlbumTap: (Album) -> Void = { _ in }

    @State private var songInfo: SongInfoTarget?

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
                    CoverMosaic.genre(genre.name, covers: covers, shape: .artworkHero)
                        .frame(width: points, height: points)
                }
            } rows: {
                if !state.albums.isEmpty {
                    DetailAlbumShelf(title: "Albums", albums: state.albums, subtitle: { $0.albumArtist }, onAlbumTap: onAlbumTap)
                }
                Section {
                    SectionHeader("Songs")
                        .rowSeparator(.none)
                    ForEach(Array(state.songs.enumerated()), id: \.element.id) { index, song in
                        Button { onPlay(index) } label: {
                            SongRow(song: song, playback: rowPlayback(song, current: state.currentSong, isPlaying: isPlaying))
                        }
                        .buttonStyle(.plain)
                        .rowSeparator(.none)
                        .contextMenu {
                            SongRowMenu(song: song, onPlayNext: onPlayNext, onAddToQueue: onAddToQueue, onExclude: onExclude, onSongInfo: { songInfo = SongInfoTarget(songID: $0.id) })
                        }
                    }
                }
            }
            .songInfoSheet($songInfo)
        } else {
            EmptyState("Genre Not Found", systemImage: "guitars")
        }
    }
}
