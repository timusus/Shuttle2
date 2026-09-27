import Shared
import SwiftUI

/// Genre detail (P5-7): hero (icon placeholder — a genre has no artwork of its own), Play/Shuffle, the albums its
/// songs come from as a shelf, then every one of its songs. Modeled on Android's `GenreDetailScreen.kt`.
struct GenreDetailView: View {
    let name: String

    var body: some View {
        let route = Route.genre(name: name)
        let models = ViewModelCache.shared.viewModel(route.cacheKey) {
            GenreDetailModels(graph: AppGraph.shared, genreName: name)
        }
        Observing(models.genre.uiState, models.actions.uiState) { state, actions in
            GenreDetailContent(
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

/// The Genre detail screen from a `GenreDetailUiState`.
struct GenreDetailContent: View {
    let state: GenreDetailUiState
    var onPlay: (Int) -> Void = { _ in }
    var onShuffle: () -> Void = {}
    var onPlayNext: (Song) -> Void = { _ in }
    var onAddToQueue: (Song) -> Void = { _ in }

    var body: some View {
        if state.loading {
            ProgressView()
        } else if let genre = state.genre {
            List {
                heroSection(genre)
                if !state.albums.isEmpty {
                    Section("Albums") {
                        albumShelf.listRowInsets(EdgeInsets())
                    }
                }
                Section("Songs") {
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
            }
            .listStyle(.plain)
            .navigationTitle(genre.name)
            .navigationBarTitleDisplayMode(.inline)
        } else {
            EmptyState("Genre Not Found", systemImage: "guitars")
        }
    }

    private var albumShelf: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(alignment: .top, spacing: Spacing.medium) {
                ForEach(state.albums, id: \.stableId) { album in
                    NavigationLink(value: Route.album(album)) {
                        VStack(alignment: .leading, spacing: Spacing.xsmall) {
                            RemoteArtwork(id: album.stableId, points: ArtworkSize.shelf) {
                                try await AppGraph.shared.artworkUrls.url(album: album)
                            }
                            .frame(width: ArtworkSize.shelf, height: ArtworkSize.shelf)
                            .clipShape(RoundedRectangle(cornerRadius: ArtworkCorner.tile, style: .continuous))
                            Text(album.name ?? "Unknown")
                                .font(.footnote)
                                .lineLimit(1)
                                .frame(width: ArtworkSize.shelf, alignment: .leading)
                        }
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal)
        }
    }

    @ViewBuilder
    private func heroSection(_ genre: Genre) -> some View {
        Section {
            DetailHero(
                title: genre.name,
                subtitle: pluralized(Int(genre.songCount), "song"),
                onPlay: { onPlay(0) },
                onShuffle: onShuffle
            ) {
                DetailPlaceholderArtwork(systemImage: "guitars")
            }
            .listRowInsets(EdgeInsets())
        }
        .listRowSeparator(.hidden)
    }
}
