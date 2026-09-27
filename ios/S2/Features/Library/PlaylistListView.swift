import Shared
import SwiftUI

/// Library > Playlists (P5-6b): `PlaylistListViewModel`'s smart playlists (always shown) and user playlists (with
/// their first cover song's artwork) as a list. Create and rename go through an `alert` with a `TextField`, per
/// the HIG; delete is a destructive swipe with a confirmation. Context menu plays or queues through the shared
/// `MediaAction`s, same as the other library lists.
struct PlaylistListView: View {
    var body: some View {
        let models = ViewModelCache.shared.viewModel(Route.libraryCategory(.playlists).cacheKey) {
            PlaylistListModels(graph: AppGraph.shared)
        }
        Observing(models.playlists.uiState, models.actions.uiState) { state, actions in
            PlaylistListContent(
                state: state,
                onPlay: { playlist in
                    models.actions.dispatch(action: MediaActionPlay(selection: MediaSelectionPlaylists(playlist: playlist), position: 0))
                },
                onPlayNext: { playlist in
                    models.actions.dispatch(action: MediaActionPlayNext(selection: MediaSelectionPlaylists(playlist: playlist)))
                },
                onAddToQueue: { playlist in
                    models.actions.dispatch(action: MediaActionAddToQueue(selection: MediaSelectionPlaylists(playlist: playlist)))
                },
                onShuffle: {
                    models.actions.dispatch(action: MediaActionShuffle(selection: MediaSelectionPlaylists(playlists: state.playlists)))
                },
                onCreate: { name in models.playlists.onCreatePlaylist(name: name) },
                onRename: { playlist, name in models.playlists.onRename(playlist: playlist, name: name) },
                onDelete: { playlist in models.playlists.onDelete(playlist: playlist) }
            )
            .mediaActionResults(actions.events, handled: { models.actions.onEventHandled(id: $0) })
        }
        .refreshable { LibraryImport.refresh() }
        .navigationTitle(LibraryCategory.playlists.title)
    }
}

/// The Playlists screen's ViewModels, cached together under its route's key.
final class PlaylistListModels: ViewModelGroup {
    let playlists: PlaylistListViewModel
    let actions: MediaActionsViewModel

    init(graph: IosAppGraph) {
        playlists = graph.playlistListViewModel
        actions = graph.mediaActionsViewModel
    }

    var members: [Lifecycle_viewmodelViewModel] { [playlists, actions] }
}

extension Route {
    static func playlist(_ playlist: Playlist) -> Route { .playlist(id: playlist.id) }
    static func smartPlaylist(_ smartPlaylist: SmartPlaylist) -> Route { .smartPlaylist(id: smartPlaylist.id.id) }
}

/// A playlist name with leading/trailing whitespace removed — the create/rename alerts' primary button is
/// disabled when this is empty, and this is what's actually passed to the ViewModel. A free function so
/// `PlaylistListTests` can exercise it without a view.
func trimmedPlaylistName(_ name: String) -> String {
    name.trimmingCharacters(in: .whitespacesAndNewlines)
}

extension SmartPlaylistId {
    /// A display title for the POC; phase 5's string catalogue replaces this (matches `MediaActionText`'s
    /// existing hardcoded-copy precedent). Internal (not file-private) so `SmartPlaylistDetailView` can use it
    /// as the detail screen's title too.
    var title: String {
        switch id {
        case "favourites": "Favourites"
        case "recently-added": "Recently Added"
        case "most-played": "Most Played"
        case "history": "History"
        default: "Playlist"
        }
    }

    /// The symbol its placeholder artwork shows.
    var symbol: String {
        switch id {
        case "favourites": "heart.fill"
        case "recently-added": "calendar.badge.plus"
        case "most-played": "flame.fill"
        case "history": "clock.arrow.circlepath"
        default: "music.note.list"
        }
    }
}

/// The Playlists screen from a `PlaylistListUiState`.
struct PlaylistListContent: View {
    let state: PlaylistListUiState
    var onPlay: (Playlist) -> Void = { _ in }
    var onPlayNext: (Playlist) -> Void = { _ in }
    var onAddToQueue: (Playlist) -> Void = { _ in }
    var onShuffle: () -> Void = {}
    var onCreate: (String) -> Void = { _ in }
    var onRename: (Playlist, String) -> Void = { _, _ in }
    var onDelete: (Playlist) -> Void = { _ in }

    var body: some View {
        switch state.loadingState {
        case .loading:
            LibraryListSkeleton()
        case .scanning where state.playlists.isEmpty:
            LibraryScanningView(progress: state.scanProgress)
        case .ready, .scanning:
            PlaylistListReadyView(
                state: state, onPlay: onPlay, onPlayNext: onPlayNext, onAddToQueue: onAddToQueue,
                onShuffle: onShuffle, onCreate: onCreate, onRename: onRename, onDelete: onDelete
            )
        }
    }
}

/// The `.ready` list: split out of `PlaylistListContent.body` so the type checker solves the `List`, its
/// toolbar and its three alerts/dialog as one manageable subview instead of a branch of a larger switch.
private struct PlaylistListReadyView: View {
    let state: PlaylistListUiState
    let onPlay: (Playlist) -> Void
    let onPlayNext: (Playlist) -> Void
    let onAddToQueue: (Playlist) -> Void
    let onShuffle: () -> Void
    let onCreate: (String) -> Void
    let onRename: (Playlist, String) -> Void
    let onDelete: (Playlist) -> Void

    @State private var isCreating = false
    @State private var newPlaylistName = ""
    @State private var renaming: Playlist?
    @State private var renameText = ""
    @State private var deleting: Playlist?

    var body: some View {
        List {
            Section("Smart Playlists") {
                ForEach(state.smartPlaylists, id: \.id.id) { smartPlaylist in
                    SmartPlaylistRow(smartPlaylist: smartPlaylist)
                }
            }
            Section("Playlists") {
                if state.playlists.isEmpty {
                    Text("No playlists yet. Tap + to create one.").foregroundStyle(.secondary)
                }
                ForEach(state.playlists, id: \.id) { playlist in
                    playlistRow(playlist)
                }
            }
        }
        .listStyle(.plain)
        .toolbar {
            Button("Shuffle", systemImage: "shuffle", action: onShuffle)
            Button("New Playlist", systemImage: "plus") {
                newPlaylistName = ""
                isCreating = true
            }
        }
        .alert("New Playlist", isPresented: $isCreating) {
            TextField("Name", text: $newPlaylistName)
            Button("Cancel", role: .cancel) {}
            Button("Create") { onCreate(trimmedNewPlaylistName) }
                .disabled(trimmedNewPlaylistName.isEmpty)
        }
        .alert("Rename Playlist", isPresented: Binding(get: { renaming != nil }, set: { if !$0 { renaming = nil } })) {
            TextField("Name", text: $renameText)
            Button("Cancel", role: .cancel) {}
            Button("Rename") {
                if let renaming { onRename(renaming, trimmedRenameText) }
            }
            .disabled(trimmedRenameText.isEmpty)
        }
        .confirmationDialog(
            "Delete \(deleting?.name ?? "this playlist")?",
            isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } }),
            titleVisibility: .visible
        ) {
            Button("Delete", role: .destructive) {
                if let deleting { onDelete(deleting) }
            }
            Button("Cancel", role: .cancel) {}
        }
    }

    private var trimmedNewPlaylistName: String { trimmedPlaylistName(newPlaylistName) }
    private var trimmedRenameText: String { trimmedPlaylistName(renameText) }

    private func requestRename(_ playlist: Playlist) {
        renameText = playlist.name
        renaming = playlist
    }

    private func playlistRow(_ playlist: Playlist) -> PlaylistListRow {
        let coverSong: Song? = state.covers[KotlinLong(value: playlist.id)]?.first
        return PlaylistListRow(
            playlist: playlist,
            coverSong: coverSong,
            onPlay: onPlay,
            onPlayNext: onPlayNext,
            onAddToQueue: onAddToQueue,
            onRename: { requestRename(playlist) },
            onRequestDelete: { deleting = playlist }
        )
    }
}

private struct SmartPlaylistRow: View {
    let smartPlaylist: SmartPlaylist

    var body: some View {
        NavigationLink(value: Route.smartPlaylist(smartPlaylist)) {
            MediaRow(smartPlaylist.id.title, placeholderSymbol: smartPlaylist.id.symbol)
        }
    }
}

/// One user playlist row: its `NavigationLink`, context menu and destructive swipe-to-delete, split out of
/// `PlaylistListContent`'s `List` so the type checker isn't asked to solve one giant view expression.
private struct PlaylistListRow: View {
    let playlist: Playlist
    let coverSong: Song?
    let onPlay: (Playlist) -> Void
    let onPlayNext: (Playlist) -> Void
    let onAddToQueue: (Playlist) -> Void
    let onRename: () -> Void
    let onRequestDelete: () -> Void

    var body: some View {
        NavigationLink(value: Route.playlist(playlist)) {
            PlaylistRow(playlist: playlist, coverSong: coverSong)
        }
        .contextMenu {
            Button("Play", systemImage: "play") { onPlay(playlist) }
            Button("Play Next", systemImage: "text.line.first.and.arrowtriangle.forward") { onPlayNext(playlist) }
            Button("Add to Queue", systemImage: "text.append") { onAddToQueue(playlist) }
            Button("Rename", action: onRename)
            Button("Delete", systemImage: "trash", role: .destructive, action: onRequestDelete)
        }
        .swipeActions(edge: .trailing) {
            Button(role: .destructive, action: onRequestDelete) {
                Label("Delete", systemImage: "trash")
            }
        }
    }
}

struct PlaylistRow: View {
    let playlist: Playlist
    let coverSong: Song?

    var body: some View {
        MediaRow(
            playlist.name,
            subtitle: playlist.songCount == 1 ? "1 song" : "\(playlist.songCount) songs",
            artwork: coverSong.map { .song($0) },
            placeholderSymbol: "music.note.list"
        )
    }
}
