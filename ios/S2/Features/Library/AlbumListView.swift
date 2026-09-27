import Shared
import SwiftUI

/// Library > Albums (P5-6a): `AlbumListViewModel`'s albums as a list; a row pushes the album's route, and its context
/// menu plays or queues the album through the shared `MediaAction`s. Shuffle is the ViewModel's own (a random
/// album's songs). Pull to refresh imports.
struct AlbumListView: View {
    var body: some View {
        let models = ViewModelCache.shared.viewModel(Route.libraryCategory(.albums).cacheKey) {
            AlbumListModels(graph: AppGraph.shared)
        }
        Observing(models.albums.uiState, models.actions.uiState) { state, actions in
            AlbumListContent(
                state: state,
                onPlay: { album in
                    models.actions.dispatch(action: MediaActionPlay(selection: MediaSelectionAlbums(album: album), position: 0))
                },
                onPlayNext: { album in
                    models.actions.dispatch(action: MediaActionPlayNext(selection: MediaSelectionAlbums(album: album)))
                },
                onAddToQueue: { album in
                    models.actions.dispatch(action: MediaActionAddToQueue(selection: MediaSelectionAlbums(album: album)))
                },
                onShuffle: { models.albums.onShuffle() }
            )
            .mediaActionResults(actions.events, handled: { models.actions.onEventHandled(id: $0) })
            .albumListEvents(state.events, handled: { models.albums.onEventHandled(id: $0) })
        }
        .refreshable { LibraryImport.refresh() }
        .navigationTitle(LibraryCategory.albums.title)
    }
}

/// The Albums screen's ViewModels, cached together under its route's key.
final class AlbumListModels: ViewModelGroup {
    let albums: AlbumListViewModel
    let actions: MediaActionsViewModel

    init(graph: IosAppGraph) {
        albums = graph.albumListViewModel
        actions = graph.mediaActionsViewModel
    }

    var members: [Lifecycle_viewmodelViewModel] { [albums, actions] }
}

extension Route {
    /// The album's detail route, keyed as Android's `AlbumRoute`.
    static func album(_ album: Album) -> Route {
        .album(albumKey: album.groupKey?.key, albumArtistKey: album.groupKey?.albumArtistGroupKey?.key)
    }
}

/// The Albums screen from an `AlbumListUiState`. The grid view mode is phase 5's P5-6b; this is always a list.
struct AlbumListContent: View {
    let state: AlbumListUiState
    var onPlay: (Album) -> Void = { _ in }
    var onPlayNext: (Album) -> Void = { _ in }
    var onAddToQueue: (Album) -> Void = { _ in }
    var onShuffle: () -> Void = {}

    var body: some View {
        switch state.loadingState {
        case .loading:
            ProgressView()
        case .scanning where state.albums.isEmpty:
            LibraryScanningView(progress: state.scanProgress)
        case .empty:
            EmptyState("No Albums", systemImage: "square.stack", message: "Pull to refresh to import.")
        case .ready, .scanning:
            List {
                ForEach(state.albums, id: \.stableId) { album in
                    NavigationLink(value: Route.album(album)) { AlbumRow(album: album) }
                        .contextMenu {
                            Button("Play", systemImage: "play") { onPlay(album) }
                            Button("Play Next", systemImage: "text.line.first.and.arrowtriangle.forward") { onPlayNext(album) }
                            Button("Add to Queue", systemImage: "text.append") { onAddToQueue(album) }
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

struct AlbumRow: View {
    let album: Album

    var body: some View {
        HStack(spacing: Spacing.smallMedium) {
            RemoteArtwork(.album(album), points: ArtworkSize.albumRow)
                .artworkTile(ArtworkSize.albumRow)
            VStack(alignment: .leading, spacing: Spacing.tiny) {
                Text(album.name ?? "Unknown").lineLimit(1)
                Text(subtitle)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
        }
    }

    private var subtitle: String {
        var parts: [String] = []
        if let artist = album.albumArtist { parts.append(artist) }
        if let year = album.year { parts.append(String(year.intValue)) }
        parts.append(album.songCount == 1 ? "1 song" : "\(album.songCount) songs")
        return parts.joined(separator: " · ")
    }
}

extension View {
    /// `AlbumListViewModel`'s own events: a shuffle that found nothing to play.
    func albumListEvents(_ events: [PendingEvent<any AlbumListEvent>], handled: @escaping (Int64) -> Void) -> some View {
        modifier(AlbumListEventsModifier(events: events, handled: handled))
    }
}

private struct AlbumListEventsModifier: ViewModifier {
    let events: [PendingEvent<any AlbumListEvent>]
    let handled: (Int64) -> Void
    @State private var alert: String?

    func body(content: Content) -> some View {
        content
            .consumeEvents(events, handled: handled) { event in
                if let failed = event as? AlbumListEventShuffleFailed {
                    alert = failed.reason.map { "Couldn't shuffle: \($0)" } ?? "Couldn't shuffle."
                }
            }
            .alert(alert ?? "", isPresented: Binding(get: { alert != nil }, set: { if !$0 { alert = nil } })) {
                Button("OK", role: .cancel) {}
            }
    }
}
