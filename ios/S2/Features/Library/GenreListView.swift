import Shared
import SwiftUI

/// Library > Genres (P5-6b): `GenreListViewModel`'s genres as a list; a row pushes the genre's detail route
/// (a placeholder for now). No artwork: Android shows only a static placeholder tile for genres too, since a
/// genre has no artwork url of its own. Context menu plays or queues through the shared `MediaAction`s.
struct GenreListView: View {
    var body: some View {
        let models = ViewModelCache.shared.viewModel(Route.libraryCategory(.genres).cacheKey) {
            GenreListModels(graph: AppGraph.shared)
        }
        Observing(models.genres.uiState, models.actions.uiState) { state, actions in
            GenreListContent(
                state: state,
                onPlay: { genre in
                    models.actions.dispatch(action: MediaActionPlay(selection: MediaSelectionGenres(genre: genre), position: 0))
                },
                onPlayNext: { genre in
                    models.actions.dispatch(action: MediaActionPlayNext(selection: MediaSelectionGenres(genre: genre)))
                },
                onAddToQueue: { genre in
                    models.actions.dispatch(action: MediaActionAddToQueue(selection: MediaSelectionGenres(genre: genre)))
                },
                onShuffle: {
                    models.actions.dispatch(action: MediaActionShuffle(selection: MediaSelectionGenres(genres: state.genres)))
                }
            )
            .mediaActionResults(actions.events, handled: { models.actions.onEventHandled(id: $0) })
        }
        .refreshable { LibraryImport.refresh() }
        .navigationTitle(LibraryCategory.genres.title)
    }
}

/// The Genres screen's ViewModels, cached together under its route's key.
final class GenreListModels: ViewModelGroup {
    let genres: GenreListViewModel
    let actions: MediaActionsViewModel

    init(graph: IosAppGraph) {
        genres = graph.genreListViewModel
        actions = graph.mediaActionsViewModel
    }

    var members: [Lifecycle_viewmodelViewModel] { [genres, actions] }
}

extension Route {
    /// The genre's detail route, keyed by its name (genres have no other stable id).
    static func genre(_ genre: Genre) -> Route {
        .genre(name: genre.name)
    }
}

/// The Genres screen from a `GenreListUiState`.
struct GenreListContent: View {
    let state: GenreListUiState
    var onPlay: (Genre) -> Void = { _ in }
    var onPlayNext: (Genre) -> Void = { _ in }
    var onAddToQueue: (Genre) -> Void = { _ in }
    var onShuffle: () -> Void = {}

    var body: some View {
        switch state.loadingState {
        case .loading:
            LibraryListSkeleton()
        case .scanning where state.genres.isEmpty:
            LibraryScanningView(progress: state.scanProgress)
        case .empty:
            EmptyState("No Genres", systemImage: "guitars", message: "Pull to refresh to import.")
        case .ready, .scanning:
            List {
                ForEach(state.genres, id: \.name) { genre in
                    NavigationLink(value: Route.genre(genre)) { GenreRow(genre: genre) }
                        .contextMenu {
                            Button("Play", systemImage: "play") { onPlay(genre) }
                            Button("Play Next", systemImage: "text.line.first.and.arrowtriangle.forward") { onPlayNext(genre) }
                            Button("Add to Queue", systemImage: "text.append") { onAddToQueue(genre) }
                        }
                }
            }
            .listStyle(.plain)
            .toolbar {
                Button("Shuffle", systemImage: "shuffle", action: onShuffle)
            }
        }
    }
}

struct GenreRow: View {
    let genre: Genre

    var body: some View {
        MediaRow(genre.name, subtitle: genre.songCount == 1 ? "1 song" : "\(genre.songCount) songs", placeholderSymbol: "guitars")
    }
}
