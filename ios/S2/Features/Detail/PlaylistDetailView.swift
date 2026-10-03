import Shared
import SwiftUI

/// Playlist detail (P5-7): hero (the same `CoverMosaic` its Library row draws, #652, and its name), Play/Shuffle, then its songs in the
/// playlist's own order. In `EditMode`, rows reorder with `.onMove` when the playlist is sorted by position
/// (`canReorder`) — the HIG's native affordance for a manual order, replacing Android's `sh.calvin.reorderable`
/// drag handle. Rename and delete are an alert/confirmationDialog, the same pattern `PlaylistListView` uses.
/// Multi-select removal, the sort menu and m3u export are out of scope for this screen (tracked as a follow-up);
/// a swipe removes one song from the playlist.
struct PlaylistDetailView: View {
    let id: Int64

    var body: some View {
        let route = Route.playlist(id: id)
        let models = ViewModelCache.shared.viewModel(route.cacheKey) {
            PlaylistDetailModels(graph: AppGraph.shared, playlistId: id)
        }
        Observing(models.playlist.uiState, models.playlist.covers, models.actions.uiState) { state, covers, actions in
            PlaylistDetailContent(
                state: state,
                covers: covers,
                isPlaying: AppGraph.dependencies.playerBinding.isPlaying,
                onPlay: { index in
                    models.actions.send(MediaActionPlay(selection: MediaSelectionSongs(songs: state.songs.map(\.song)), position: Int32(index), context: state.playContext))
                },
                onShuffle: {
                    models.actions.send(MediaActionShuffle(selection: MediaSelectionSongs(songs: state.songs.map(\.song)), context: state.playContext))
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
                onMove: { from, to in models.playlist.onMove(fromId: from, toId: to) },
                onMoveFinished: { models.playlist.onMoveFinished() },
                onRemove: { entry in
                    guard let playlist = state.playlist else { return }
                    models.actions.send(MediaActionRemoveFromPlaylist(
                        playlist: playlist, entries: [entry], before: state.songs
                    ))
                },
                onRename: { name in models.playlist.onRename(name: name) },
                onDelete: { models.playlist.onDelete() }
            )
            .mediaActionResults(actions.events, handled: { models.actions.onEventHandled(id: $0) }, send: { models.actions.send($0) })
            .playlistDetailEvents(state.events, handled: { models.playlist.onEventHandled(id: $0) })
        }
    }
}

/// The Playlist detail screen's ViewModels, cached together under its route's key.
final class PlaylistDetailModels: ViewModelGroup {
    let playlist: PlaylistDetailViewModel
    let actions: MediaActionsViewModel

    init(graph: IosAppGraph, playlistId: Int64) {
        playlist = graph.playlistDetailViewModelFactory.create(playlistId: playlistId)
        actions = graph.mediaActionsViewModel
    }

    var members: [Lifecycle_viewmodelViewModel] { [playlist, actions] }
}

/// The Playlist detail screen from a `PlaylistDetailUiState`, in a `DetailScaffold` tinted from its first song's cover.
struct PlaylistDetailContent: View {
    let state: PlaylistDetailUiState
    /// The songs whose covers make up its mosaic, as its Library row draws (`PlaylistDetailViewModel.covers`).
    var covers: [Song] = []
    var isPlaying: Bool = false
    var onPlay: (Int) -> Void = { _ in }
    var onShuffle: () -> Void = {}
    var onPlayNext: (Song) -> Void = { _ in }
    var onAddToQueue: (Song) -> Void = { _ in }
    var onExclude: (Song) -> Void = { _ in }
    var onMove: (Int64, Int64) -> Void = { _, _ in }
    var onMoveFinished: () -> Void = {}
    var onRemove: (PlaylistSong) -> Void = { _ in }
    var onRename: (String) -> Void = { _ in }
    var onDelete: () -> Void = {}

    @State private var songInfo: SongInfoTarget?

    @State private var editMode: EditMode = .inactive
    @State private var isRenaming = false
    @State private var renameText = ""
    @State private var isDeleting = false

    var body: some View {
        if state.loading {
            ProgressView()
        } else if let playlist = state.playlist {
            let cover = state.songs.first.map { ArtworkSource.song($0.song) }
            DetailScaffold(title: playlist.name, tintSource: cover) { layout in
                DetailHero(
                    title: playlist.name,
                    subtitle: eyebrow(pluralized(Int(playlist.songCount), "song"), totalDuration(state.songs.map(\.song))),
                    layout: layout,
                    onPlay: { onPlay(0) },
                    onShuffle: onShuffle
                ) { points in
                    CoverMosaic.playlist(playlist.name, covers: covers, shape: .artworkHero)
                        .frame(width: points, height: points)
                }
            } rows: {
                Section {
                    ForEach(state.songs, id: \.id) { entry in
                        Button {
                            if let index = state.songs.firstIndex(where: { $0.id == entry.id }) { onPlay(index) }
                        } label: {
                            SongRow(song: entry.song, playback: rowPlayback(entry.song, current: state.currentSong, isPlaying: isPlaying))
                        }
                        .buttonStyle(.plain)
                        .rowSeparator(.none)
                        .contextMenu {
                            SongRowMenu(song: entry.song, onPlayNext: onPlayNext, onAddToQueue: onAddToQueue, onExclude: onExclude, onSongInfo: { songInfo = SongInfoTarget(songID: $0.id) })
                            Button("Remove from Playlist", systemImage: "minus.circle", role: .destructive) { onRemove(entry) }
                        }
                        .swipeActions(edge: .trailing) {
                            Button(role: .destructive) { onRemove(entry) } label: {
                                Label("Remove", systemImage: "minus.circle")
                            }
                        }
                    }
                    .onMove { source, destination in move(source, to: destination) }
                }
            }
            .songInfoSheet($songInfo)
            .environment(\.editMode, $editMode)
            .toolbar {
                if state.canReorder {
                    EditButton()
                }
                Menu {
                    Button("Rename", systemImage: "pencil") {
                        renameText = playlist.name
                        isRenaming = true
                    }
                    Button("Delete", systemImage: "trash", role: .destructive) { isDeleting = true }
                } label: {
                    Image(systemName: "ellipsis.circle")
                }
            }
            .alert("Rename Playlist", isPresented: $isRenaming) {
                TextField("Name", text: $renameText)
                Button("Cancel", role: .cancel) {}
                Button("Rename") {
                    let trimmed = trimmedPlaylistName(renameText)
                    if !trimmed.isEmpty { onRename(trimmed) }
                }
                .disabled(trimmedPlaylistName(renameText).isEmpty)
            }
            .confirmationDialog("Delete \(playlist.name)?", isPresented: $isDeleting, titleVisibility: .visible) {
                Button("Delete", role: .destructive, action: onDelete)
                Button("Cancel", role: .cancel) {}
            }
        } else {
            EmptyState("Playlist Not Found", systemImage: "music.note.list")
        }
    }

    /// Applies `source`/`destination` (SwiftUI's `.onMove` indices) as the from/to ids the ViewModel's
    /// `onMove(fromId:toId:)` expects, then persists the drop.
    private func move(_ source: IndexSet, to destination: Int) {
        guard let from = source.first else { return }
        let songs = state.songs
        guard from != destination, songs.indices.contains(from) else { return }
        let toIndex = destination > from ? destination - 1 : destination
        guard songs.indices.contains(toIndex) else { return }
        onMove(songs[from].id, songs[toIndex].id)
        onMoveFinished()
    }
}

extension View {
    /// `PlaylistDetailViewModel`'s own events: on delete, pop back to the playlists list; export is out of
    /// scope for this screen (see the file header), so its events are dropped here.
    func playlistDetailEvents(_ events: [PendingEvent<any PlaylistDetailEvent>], handled: @escaping (Int64) -> Void) -> some View {
        modifier(PlaylistDetailEventsModifier(events: events, handled: handled))
    }
}

private struct PlaylistDetailEventsModifier: ViewModifier {
    let events: [PendingEvent<any PlaylistDetailEvent>]
    let handled: (Int64) -> Void
    @Environment(\.dismiss) private var dismiss

    func body(content: Content) -> some View {
        content.consumeEvents(events, handled: handled) { event in
            if event is PlaylistDetailEventDeleted {
                dismiss()
            }
        }
    }
}
