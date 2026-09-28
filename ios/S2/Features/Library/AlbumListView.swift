import Shared
import SwiftUI

/// Library > Albums (P5-6a): `AlbumListViewModel`'s albums as a grid of covers (the default) or a list, switched
/// from the toolbar and kept by the ViewModel (`setViewMode`, Android's saved library view setting). A tile or row
/// pushes the album's route, and its context menu plays or queues the album through the shared `MediaAction`s.
/// Shuffle heads the list or grid and is the ViewModel's own (a random album's songs). The playing album is marked.
/// Pull to refresh imports.
struct AlbumListView: View {
    var body: some View {
        let models = ViewModelCache.shared.viewModel(Route.libraryCategory(.albums).cacheKey) {
            AlbumListModels(graph: AppGraph.shared)
        }
        LibraryNowPlayingReader { nowPlaying in
            Observing(models.albums.uiState, models.actions.uiState) { state, actions in
                AlbumListContent(
                    state: state,
                    nowPlaying: nowPlaying,
                    onPlay: { album in
                        models.actions.dispatch(action: MediaActionPlay(selection: MediaSelectionAlbums(album: album), position: 0))
                    },
                    onPlayNext: { album in
                        models.actions.dispatch(action: MediaActionPlayNext(selection: MediaSelectionAlbums(album: album)))
                    },
                    onAddToQueue: { album in
                        models.actions.dispatch(action: MediaActionAddToQueue(selection: MediaSelectionAlbums(album: album)))
                    },
                    onShuffle: { models.albums.onShuffle() },
                    onViewMode: { models.albums.setViewMode(mode: $0) }
                )
                .albumListEvents(state.events, handled: { models.albums.onEventHandled(id: $0) })
                .mediaActionResults(actions.events, handled: { models.actions.onEventHandled(id: $0) })
            }
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
        .album(albumKey: album.groupKey?.key, albumArtistKey: album.groupKey?.albumArtistGroupKey?.key, albumIdentity: album.groupKey?.identity)
    }
}

/// The Albums screen from an `AlbumListUiState`: its view mode picks the grid or the list.
struct AlbumListContent: View {
    let state: AlbumListUiState
    var nowPlaying: LibraryNowPlaying = .none
    var onPlay: (Album) -> Void = { _ in }
    var onPlayNext: (Album) -> Void = { _ in }
    var onAddToQueue: (Album) -> Void = { _ in }
    var onShuffle: () -> Void = {}
    var onViewMode: (ViewMode) -> Void = { _ in }

    var body: some View {
        content
            .toolbar {
                if state.loadingState != .empty {
                    ViewModeToggle(mode: state.viewMode, onChange: onViewMode)
                }
            }
    }

    @ViewBuilder
    private var content: some View {
        switch state.loadingState {
        case .loading:
            if state.viewMode == .grid { LibraryGridSkeleton() } else { LibraryListSkeleton(artworkSize: ArtworkSize.albumRow) }
        case .scanning where state.albums.isEmpty:
            LibraryScanningView(progress: state.scanProgress)
        case .empty:
            EmptyState("No Albums", systemImage: "square.stack", message: "Pull to refresh to import.")
        case .ready, .scanning:
            let index = LetterIndex.sections(state.letterIndex, items: state.albums, id: \.stableId)
            if state.viewMode == .grid {
                LibraryGrid(index: index, header: { shuffle }) {
                    ForEach(state.albums, id: \.stableId) { album in
                        NavigationLink(value: Route.album(album)) {
                            LibraryTile(
                                title: album.name ?? "Unknown",
                                subtitle: album.albumArtist,
                                artwork: .album(album),
                                playback: nowPlaying.playback(album: album)
                            )
                            .zoomSource(id: Route.album(album))
                        }
                        .buttonStyle(.pressScale)
                        .contextMenu { menu(album) }
                    }
                }
            } else {
                LetterIndexedList(items: state.albums, id: \.stableId, sections: index, header: { shuffle }) { _, album in
                    let playback = nowPlaying.playback(album: album)
                    LibraryRowLink(route: Route.album(album)) { AlbumRow(album: album, playback: playback) }
                        .contextMenu { menu(album) }
                        .nowPlayingRowBackground(playback)
                }
            }
        }
    }

    private var shuffle: some View {
        ShuffleRow(count: state.albums.count, noun: "album", action: onShuffle)
            .accessibilityIdentifier("albums.shuffle")
    }

    @ViewBuilder
    private func menu(_ album: Album) -> some View {
        Button("Play", systemImage: "play") { onPlay(album) }
        Button("Play Next", systemImage: "text.line.first.and.arrowtriangle.forward") { onPlayNext(album) }
        Button("Add to Queue", systemImage: "text.append") { onAddToQueue(album) }
    }
}

struct AlbumRow: View {
    let album: Album
    var playback: MediaRowPlayback = .none

    var body: some View {
        MediaRow(album.name ?? "Unknown", subtitle: subtitle, artwork: .album(album), artworkSize: ArtworkSize.albumRow, playback: playback)
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
