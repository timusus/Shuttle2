import Shared
import SwiftUI

/// Library > Genres (P5-6b): `GenreListViewModel`'s genres as a list; a row pushes the genre's detail route. A genre
/// has no artwork of its own, so each row draws a `CoverMosaic` of its albums' covers (`GenreCoversViewModel`,
/// #643), which fill in after the list shows. A Shuffle row heads the list, shuffling every genre. Context menu plays
/// or queues through the shared `MediaAction`s.
struct GenreListView: View {
    var body: some View {
        let _ = StartupTrace.mark(.genres, .body)
        let models = ViewModelCache.shared.viewModel(Route.libraryCategory(.genres).cacheKey) {
            GenreListModels(graph: AppGraph.shared)
        }
        Observing(models.genres.uiState, models.covers.uiState, models.actions.uiState) { state, covers, actions in
            let _ = StartupTrace.content(.genres, loaded: state.loadingState != .loading)
            GenreListContent(
                state: state,
                covers: covers,
                onPlay: { genre in
                    models.actions.send(MediaActionPlay(selection: MediaSelectionGenres(genre: genre), position: 0))
                },
                onPlayNext: { genre in
                    models.actions.send(MediaActionPlayNext(selection: MediaSelectionGenres(genre: genre)))
                },
                onAddToQueue: { genre in
                    models.actions.send(MediaActionAddToQueue(selection: MediaSelectionGenres(genre: genre)))
                },
                onShuffle: {
                    models.actions.send(MediaActionShuffle(selection: MediaSelectionGenres(genres: state.genres)))
                },
                onSortOrder: { models.genres.setSortOrder(sortOrder: $0) }
            )
            .mediaActionResults(actions.events, handled: { models.actions.onEventHandled(id: $0) })
            .warmsUpSearch(once: state.loadingState != .loading)
        }
        .refreshable { LibraryImport.refresh() }
        .onAppear { StartupTrace.mark(.genres, .appear) }
        .navigationTitle(LibraryCategory.genres.title)
    }
}

/// The Genres screen's ViewModels, cached together under its route's key.
final class GenreListModels: ViewModelGroup {
    let genres: GenreListViewModel
    let covers: GenreCoversViewModel
    let actions: MediaActionsViewModel

    init(graph: IosAppGraph) {
        genres = graph.genreListViewModel
        covers = graph.genreCoversViewModel
        actions = graph.mediaActionsViewModel
    }

    var members: [Lifecycle_viewmodelViewModel] { [genres, covers, actions] }
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
    /// Each genre's cover songs by name, for its mosaic.
    var covers: [String: [Song]] = [:]
    var onPlay: (Genre) -> Void = { _ in }
    var onPlayNext: (Genre) -> Void = { _ in }
    var onAddToQueue: (Genre) -> Void = { _ in }
    var onShuffle: () -> Void = {}
    var onSortOrder: (GenreSortOrder) -> Void = { _ in }

    var body: some View {
        content
            .toolbar {
                if !state.genres.isEmpty {
                    ShuffleButton(identifier: "genres.shuffle", action: onShuffle)
                }
                if state.loadingState != .empty {
                    LibrarySortMenu(
                        identifier: "genres.sortMenu",
                        orders: GenreSortOrder.libraryMenuOrder,
                        sortOrder: state.sortOrder,
                        title: \.libraryMenuTitle,
                        onSelect: onSortOrder
                    )
                }
            }
    }

    @ViewBuilder
    private var content: some View {
        switch state.loadingState {
        case .loading:
            LibraryListSkeleton()
        case .scanning where state.genres.isEmpty:
            LibraryScanningView(progress: state.scanProgress)
        case .empty:
            EmptyState("No Genres", systemImage: "guitars", message: "Pull to refresh to import.")
        case .ready, .scanning:
            LetterIndexedList(items: state.genres, id: \.name, sections: LetterIndex.sections(state.letterIndex, items: state.genres, id: \.name)) { _, genre in
                LibraryRowLink(route: Route.genre(genre)) { GenreRow(genre: genre, covers: covers[genre.name] ?? []) }
                    .contextMenu {
                        Button("Play", systemImage: "play") { onPlay(genre) }
                        Button("Play Next", systemImage: "text.line.first.and.arrowtriangle.forward") { onPlayNext(genre) }
                        Button("Add to Queue", systemImage: "text.append") { onAddToQueue(genre) }
                    }
            }
        }
    }
}

struct GenreRow: View {
    let genre: Genre
    var covers: [Song] = []
    var artworkSize: CGFloat = ArtworkSize.row

    var body: some View {
        // Trimmed for show only: a server's genre may keep the space after a tag's separator (" Folk"), which set
        // its title apart from every other list's (#643); the name itself stays the key.
        MediaRow(
            genre.name.trimmingCharacters(in: .whitespacesAndNewlines),
            subtitle: genre.songCount == 1 ? "1 song" : "\(genre.songCount) songs",
            mosaic: .genre(genre.name, covers: covers),
            artworkSize: artworkSize
        )
    }
}
