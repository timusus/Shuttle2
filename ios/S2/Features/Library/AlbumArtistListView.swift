import Shared
import SwiftUI

/// Library > Album Artists (P5-6b): `AlbumArtistListViewModel`'s artists as a list; a row pushes the artist's
/// detail route (a placeholder for now) and shows its artwork via `ArtworkUrls.url(albumArtist:)`. Context menu
/// plays or queues through the shared `MediaAction`s; there's no per-artist shuffle on the ViewModel, so the
/// toolbar shuffle dispatches `MediaActionShuffle` over every artist, same as it would over a multi-selection.
struct AlbumArtistListView: View {
    var body: some View {
        let models = ViewModelCache.shared.viewModel(Route.libraryCategory(.albumArtists).cacheKey) {
            AlbumArtistListModels(graph: AppGraph.shared)
        }
        Observing(models.albumArtists.uiState, models.actions.uiState) { state, actions in
            AlbumArtistListContent(
                state: state,
                onPlay: { artist in
                    models.actions.dispatch(action: MediaActionPlay(selection: MediaSelectionAlbumArtists(albumArtist: artist), position: 0))
                },
                onPlayNext: { artist in
                    models.actions.dispatch(action: MediaActionPlayNext(selection: MediaSelectionAlbumArtists(albumArtist: artist)))
                },
                onAddToQueue: { artist in
                    models.actions.dispatch(action: MediaActionAddToQueue(selection: MediaSelectionAlbumArtists(albumArtist: artist)))
                },
                onShuffle: {
                    models.actions.dispatch(action: MediaActionShuffle(selection: MediaSelectionAlbumArtists(albumArtists: state.albumArtists)))
                }
            )
            .mediaActionResults(actions.events, handled: { models.actions.onEventHandled(id: $0) })
        }
        .refreshable { LibraryImport.refresh() }
        .navigationTitle(LibraryCategory.albumArtists.title)
    }
}

/// The Album Artists screen's ViewModels, cached together under its route's key.
final class AlbumArtistListModels: ViewModelGroup {
    let albumArtists: AlbumArtistListViewModel
    let actions: MediaActionsViewModel

    init(graph: IosAppGraph) {
        albumArtists = graph.albumArtistListViewModel
        actions = graph.mediaActionsViewModel
    }

    var members: [Lifecycle_viewmodelViewModel] { [albumArtists, actions] }
}

extension Route {
    /// The album artist's detail route, keyed the same way `RouteDestinationView` reads it back out.
    static func albumArtist(_ albumArtist: AlbumArtist) -> Route {
        .albumArtist(albumArtistKey: albumArtist.groupKey.key)
    }
}

/// The Album Artists screen from an `AlbumArtistListUiState`.
struct AlbumArtistListContent: View {
    let state: AlbumArtistListUiState
    var onPlay: (AlbumArtist) -> Void = { _ in }
    var onPlayNext: (AlbumArtist) -> Void = { _ in }
    var onAddToQueue: (AlbumArtist) -> Void = { _ in }
    var onShuffle: () -> Void = {}

    var body: some View {
        switch state.loadingState {
        case .loading:
            ProgressView()
        case .scanning:
            LibraryScanningView(progress: state.scanProgress)
        case .empty:
            ContentUnavailableView("No Artists", systemImage: "person.2", description: Text("Pull to refresh to import."))
        case .ready:
            List {
                ForEach(Array(state.albumArtists.enumerated()), id: \.offset) { _, artist in
                    NavigationLink(value: Route.albumArtist(artist)) { AlbumArtistRow(albumArtist: artist) }
                        .contextMenu {
                            Button("Play", systemImage: "play") { onPlay(artist) }
                            Button("Play Next", systemImage: "text.line.first.and.arrowtriangle.forward") { onPlayNext(artist) }
                            Button("Add to Queue", systemImage: "text.append") { onAddToQueue(artist) }
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

struct AlbumArtistRow: View {
    let albumArtist: AlbumArtist

    var body: some View {
        HStack(spacing: 12) {
            ArtworkAsyncImage(load: { try await AppGraph.shared.artworkUrls.url(albumArtist: albumArtist) }, points: 44)
                .frame(width: 44, height: 44)
                .clipShape(Circle())
            VStack(alignment: .leading, spacing: 2) {
                Text(albumArtist.name ?? albumArtist.friendlyArtistName ?? "Unknown Artist").lineLimit(1)
                Text(subtitle)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
        }
    }

    private var subtitle: String {
        let albums = albumArtist.albumCount == 1 ? "1 album" : "\(albumArtist.albumCount) albums"
        let songs = albumArtist.songCount == 1 ? "1 song" : "\(albumArtist.songCount) songs"
        return "\(albums) · \(songs)"
    }
}
