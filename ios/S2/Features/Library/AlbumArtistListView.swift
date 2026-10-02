import Shared
import SwiftUI

/// Library > Artists (P5-6b): `AlbumArtistListViewModel`'s artists as a list (the default) or a grid of their
/// pictures, switched from the toolbar and kept by the ViewModel (`setViewMode`). A row or tile pushes the artist's
/// detail route and shows its artwork via `ArtworkUrls.requests(albumArtist:)`. Context menu plays or queues through the
/// shared `MediaAction`s. Shuffle heads the list or grid; there's no per-artist shuffle on the ViewModel, so it dispatches
/// `MediaActionShuffle` over every artist, as over a multi-selection. The playing artist is marked.
struct AlbumArtistListView: View {
    var body: some View {
        let models = ViewModelCache.shared.viewModel(Route.libraryCategory(.albumArtists).cacheKey) {
            AlbumArtistListModels(graph: AppGraph.shared)
        }
        LibraryNowPlayingReader { nowPlaying in
            Observing(models.albumArtists.uiState, models.actions.uiState) { state, actions in
                AlbumArtistListContent(
                    state: state,
                    nowPlaying: nowPlaying,
                    onPlay: { artist in
                        models.actions.send(MediaActionPlay(selection: MediaSelectionAlbumArtists(albumArtist: artist), position: 0))
                    },
                    onPlayNext: { artist in
                        models.actions.send(MediaActionPlayNext(selection: MediaSelectionAlbumArtists(albumArtist: artist)))
                    },
                    onAddToQueue: { artist in
                        models.actions.send(MediaActionAddToQueue(selection: MediaSelectionAlbumArtists(albumArtist: artist)))
                    },
                    onShuffle: {
                        models.actions.send(MediaActionShuffle(selection: MediaSelectionAlbumArtists(albumArtists: state.albumArtists)))
                    },
                    onViewMode: { models.albumArtists.setViewMode(mode: $0) },
                    onSortOrder: { models.albumArtists.setSortOrder(sortOrder: $0) }
                )
                .mediaActionResults(actions.events, handled: { models.actions.onEventHandled(id: $0) })
            }
        }
        .refreshable { LibraryImport.refresh() }
        .navigationTitle(LibraryCategory.albumArtists.title)
    }
}

/// The Artists screen's ViewModels, cached together under its route's key.
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

/// The Artists screen from an `AlbumArtistListUiState`: its view mode picks the list or the grid.
struct AlbumArtistListContent: View {
    let state: AlbumArtistListUiState
    var nowPlaying: LibraryNowPlaying = .none
    var onPlay: (AlbumArtist) -> Void = { _ in }
    var onPlayNext: (AlbumArtist) -> Void = { _ in }
    var onAddToQueue: (AlbumArtist) -> Void = { _ in }
    var onShuffle: () -> Void = {}
    var onViewMode: (ViewMode) -> Void = { _ in }
    var onSortOrder: (AlbumArtistSortOrder) -> Void = { _ in }

    var body: some View {
        content
            .toolbar {
                if !state.albumArtists.isEmpty {
                    ShuffleButton(identifier: "albumArtists.shuffle", action: onShuffle)
                }
                if state.loadingState != .empty {
                    LibrarySortMenu(
                        identifier: "albumArtists.sortMenu",
                        orders: AlbumArtistSortOrder.libraryMenuOrder,
                        sortOrder: state.sortOrder,
                        title: \.libraryMenuTitle,
                        onSelect: onSortOrder
                    )
                }
                if state.loadingState != .empty {
                    ViewModeToggle(mode: state.viewMode, onChange: onViewMode)
                }
            }
    }

    @ViewBuilder
    private var content: some View {
        switch state.loadingState {
        case .loading:
            if state.viewMode == .grid { LibraryGridSkeleton(artworkShape: .artist) } else { LibraryListSkeleton(artworkShape: .artist) }
        case .scanning where state.albumArtists.isEmpty:
            LibraryScanningView(progress: state.scanProgress)
        case .empty:
            EmptyState("No Artists", systemImage: "person.2", message: "Pull to refresh to import.")
        case .ready, .scanning:
            let index = LetterIndex.sections(state.letterIndex, items: state.albumArtists, id: \.stableId)
            if state.viewMode == .grid {
                LibraryGrid(index: index) {
                    ForEach(state.albumArtists, id: \.stableId) { artist in
                        NavigationLink(value: Route.albumArtist(artist)) {
                            LibraryTile(
                                title: AlbumArtistRow.title(artist),
                                subtitle: AlbumArtistRow.subtitle(artist),
                                artwork: .albumArtist(artist),
                                placeholderSymbol: "music.mic",
                                playback: nowPlaying.playback(albumArtist: artist)
                            )
                        }
                        .buttonStyle(.pressScale)
                        .contextMenu { menu(artist) }
                        .id(artist.stableId)
                    }
                }
            } else {
                LetterIndexedList(items: state.albumArtists, id: \.stableId, sections: index) { _, artist in
                    let playback = nowPlaying.playback(albumArtist: artist)
                    LibraryRowLink(route: Route.albumArtist(artist)) { AlbumArtistRow(albumArtist: artist, playback: playback) }
                        .contextMenu { menu(artist) }
                        .nowPlayingRowBackground(playback)
                }
            }
        }
    }

    @ViewBuilder
    private func menu(_ artist: AlbumArtist) -> some View {
        Button("Play", systemImage: "play") { onPlay(artist) }
        Button("Play Next", systemImage: "text.line.first.and.arrowtriangle.forward") { onPlayNext(artist) }
        Button("Add to Queue", systemImage: "text.append") { onAddToQueue(artist) }
    }
}

struct AlbumArtistRow: View {
    let albumArtist: AlbumArtist
    var playback: MediaRowPlayback = .none

    var body: some View {
        MediaRow(
            Self.title(albumArtist),
            subtitle: Self.subtitle(albumArtist),
            artwork: .albumArtist(albumArtist),
            placeholderSymbol: "music.mic",
            playback: playback
        )
    }

    static func title(_ albumArtist: AlbumArtist) -> String {
        albumArtist.name ?? albumArtist.friendlyArtistName ?? "Unknown Artist"
    }

    /// "3 albums · 12 songs" in the Library and Search alike; a track artist found by their credits (#637) has no
    /// albums of their own, so only their songs.
    static func subtitle(_ albumArtist: AlbumArtist) -> String {
        eyebrow(albumArtist.isAlbumArtist ? pluralized(Int(albumArtist.albumCount), "album") : nil, pluralized(Int(albumArtist.songCount), "song"))
    }
}
