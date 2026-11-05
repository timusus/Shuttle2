import Shared
import SwiftUI

/// Reorders with `.onMove`, the HIG's native affordance, and only when the playlist is sorted by position.
struct PlaylistDetailView: View {
    let id: Int64

    @Environment(Navigator.self) private var navigator: Navigator?
    @Environment(\.appServices) private var appServices
    private var services: AppServices { appServices.required }

    var body: some View {
        let route = Route.playlist(id: id)
        let models = services.viewModels.viewModel(route.cacheKey) {
            PlaylistDetailModels(graph: services.graph, playlistId: id)
        }
        Observing(models.playlist.uiState, models.covers.uiState, models.actions.uiState) { state, covers, actions in
            // Redrawn as a download moves on, so the hero and menus read the songs' downloads again
            Observing(services.graph.offlineDownloads.downloads) { _ in
                PlaylistDetailContent(
                    state: state,
                    covers: covers,
                    isPlaying: services.playerBinding.isPlaying,
                    onPlay: { index in
                        models.actions.send(MediaActionPlay(selection: MediaSelectionSongs(songs: state.songs.map(\.song)), position: Int32(index), context: state.playContext), intent: services.playIntent)
                    },
                    onShuffle: {
                        models.actions.send(MediaActionShuffle(selection: MediaSelectionSongs(songs: state.songs.map(\.song)), context: state.playContext), intent: services.playIntent)
                    },
                    onPlayNext: { songs in
                        models.actions.send(MediaActionPlayNext(selection: MediaSelectionSongs(songs: songs)), intent: services.playIntent)
                    },
                    onAddToQueue: { songs in
                        models.actions.send(MediaActionAddToQueue(selection: MediaSelectionSongs(songs: songs)), intent: services.playIntent)
                    },
                    onExclude: { song in
                        models.actions.send(MediaActionExclude(selection: MediaSelectionSongs(song: song)), intent: services.playIntent)
                    },
                    onPlaySongs: { songs in
                        models.actions.send(MediaActionPlay(selection: MediaSelectionSongs(songs: songs), position: 0, context: state.playContext), intent: services.playIntent)
                    },
                    onShuffleSongs: { songs in
                        models.actions.send(MediaActionShuffle(selection: MediaSelectionSongs(songs: songs), context: state.playContext), intent: services.playIntent)
                    },
                    onOpenAlbum: { navigator.openAsserting(.album($0)) },
                    onSortSelected: { models.playlist.onSortSelected(sort: $0) },
                    onToggleAlbum: { models.playlist.onToggleAlbum(album: $0) },
                    onExpandAll: { models.playlist.onExpandAll() },
                    onCollapseAll: { models.playlist.onCollapseAll() },
                    onShuffleAlbums: { models.playlist.onShuffleAlbums() },
                    onMove: { from, to in models.playlist.onMove(fromId: from, toId: to) },
                    onMoveFinished: { models.playlist.onMoveFinished() },
                    onRemove: { entry in
                        guard let playlist = state.playlist else { return }
                        models.actions.send(MediaActionRemoveFromPlaylist(
                            playlist: playlist, entries: [entry], before: state.songs
                        ), intent: services.playIntent)
                    },
                    onRename: { name in models.playlist.onRename(name: name) },
                    onDelete: { models.playlist.onDelete() },
                    downloads: DetailDownloads(actions: models.actions, intent: services.playIntent, downloads: services.graph.offlineDownloads)
                )
            }
            .mediaActionResults(actions.events, handled: { models.actions.onEventHandled(id: $0) }, send: { models.actions.send($0, intent: services.playIntent) })
            .playlistDetailEvents(state.events, handled: { models.playlist.onEventHandled(id: $0) })
        }
    }
}

/// The Playlist detail screen's ViewModels, cached together under its route's key.
final class PlaylistDetailModels: ViewModelGroup {
    let playlist: PlaylistDetailViewModel
    let covers: PlaylistDetailCoversViewModel
    let actions: MediaActionsViewModel

    init(graph: IosAppGraph, playlistId: Int64) {
        playlist = graph.playlistDetailViewModelFactory.create(playlistId: playlistId)
        covers = graph.playlistDetailCoversViewModelFactory.create(playlistId: playlistId)
        actions = graph.mediaActionsViewModel
    }

    var members: [Lifecycle_viewmodelViewModel] { [playlist, covers, actions] }
}

/// The Playlist detail screen from a `PlaylistDetailUiState`, in a `DetailScaffold` tinted from its first song's cover.
struct PlaylistDetailContent: View {
    let state: PlaylistDetailUiState
    /// The songs whose covers make up its mosaic, as its Library row draws (`PlaylistDetailCoversViewModel`).
    var covers: [Song] = []
    var isPlaying: Bool = false
    var onPlay: (Int) -> Void = { _ in }
    var onShuffle: () -> Void = {}
    var onPlayNext: ([Song]) -> Void = { _ in }
    var onAddToQueue: ([Song]) -> Void = { _ in }
    var onExclude: (Song) -> Void = { _ in }
    var onPlaySongs: ([Song]) -> Void = { _ in }
    var onShuffleSongs: ([Song]) -> Void = { _ in }
    var onOpenAlbum: (Album) -> Void = { _ in }
    var onSortSelected: (SongListSort) -> Void = { _ in }
    var onToggleAlbum: (Album) -> Void = { _ in }
    var onExpandAll: () -> Void = {}
    var onCollapseAll: () -> Void = {}
    var onShuffleAlbums: () -> Void = {}
    var onMove: (Int64, Int64) -> Void = { _, _ in }
    var onMoveFinished: () -> Void = {}
    var onRemove: (PlaylistSong) -> Void = { _ in }
    var onRename: (String) -> Void = { _ in }
    var onDelete: () -> Void = {}
    /// The playlist's and each song's Download and Remove Download, and the hero's download status.
    var downloads = DetailDownloads()

    @State private var songInfo: SongInfoTarget?
    @Environment(\.layoutTier) private var layoutTier
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

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
                    subtitle: eyebrow(pluralized(Int(playlist.songCount), .song), totalDuration(state.songs.map(\.song))),
                    layout: layout,
                    onPlay: { onPlay(0) },
                    onShuffle: onShuffle,
                    downloadStatus: downloads.summary?(state.songs.map(\.song)).status ?? .notDownloaded
                ) { points in
                    CoverMosaic.playlist(playlist.name, covers: covers, shape: .artworkHero)
                        .frame(width: points, height: points)
                }
            } rows: {
                if !state.songs.isEmpty {
                    songsHeader
                }
                if state.sections.hasAlbumSections {
                    PlaylistSongSections(
                        sections: state.sections,
                        expandedAlbums: state.expandedAlbums,
                        onToggleAlbum: onToggleAlbum,
                        onPlaySection: onPlaySongs,
                        onShuffleSection: onShuffleSongs,
                        onPlayNext: onPlayNext,
                        onAddToQueue: onAddToQueue,
                        onOpenAlbum: onOpenAlbum
                    ) { _, index, numbered in
                        // Grouped, a row is the entry at its place in the displayed order
                        entryRow(state.songs[index], index: index, numbered: numbered)
                    }
                } else {
                    Section {
                        ForEach(Array(state.songs.enumerated()), id: \.element.id) { index, entry in
                            entryRow(entry, index: index, numbered: false)
                        }
                        .onMove(perform: state.canReorder ? { source, destination in move(source, to: destination) } : nil)
                    }
                }
            }
            .songInfoSheet($songInfo)
            .animation(Motion.disclosure.reduced(reduceMotion), value: state.expandedAlbums)
            .environment(\.editMode, $editMode)
            // The Edit/Done button goes away with Custom order, so leave edit mode with it or the list stays stuck there
            .onChange(of: state.canReorder) { _, canReorder in
                if !canReorder { editMode = .inactive }
            }
            .toolbar {
                if state.canReorder {
                    EditButton()
                }
                Menu {
                    if state.sort.groupsByAlbum {
                        Button("Shuffle by Album", systemImage: "square.stack", action: onShuffleAlbums)
                    }
                    Button("Rename", systemImage: "pencil") {
                        renameText = playlist.name
                        isRenaming = true
                    }
                    DownloadMenuItems(songs: state.songs.map(\.song), downloads: downloads)
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

    private var songsHeader: some View {
        let inset = AdaptiveLayout.contentInset(layoutTier)
        let choices = PlaylistKind.user.sortChoices
        return Section {
            SongListHeader(
                sort: state.sort,
                choices: choices,
                showsAlbums: state.sections.hasAlbumSections,
                allExpanded: state.allAlbumsExpanded,
                idPrefix: "playlistDetail",
                onSortSelected: onSortSelected,
                onExpandAll: onExpandAll,
                onCollapseAll: onCollapseAll
            )
            .rowSeparator(.none)
            .listRowInsets(EdgeInsets(top: 0, leading: inset, bottom: 0, trailing: max(0, inset - Spacing.smallMedium)))
            if !state.canReorder {
                let label = choices.first { $0.sort == state.sort }?.title ?? ""
                Text("Sorted by \(label). Choose Custom to reorder.")
                    .font(.footnote)
                    .foregroundStyle(.s2TextSecondary)
                    .rowSeparator(.none)
                    .accessibilityIdentifier("playlistDetail.reorderHint")
            }
        }
        .listSectionSpacing(0)
    }

    private func entryRow(_ entry: PlaylistSong, index: Int, numbered: Bool) -> some View {
        Button { onPlay(index) } label: {
            let playback = rowPlayback(entry.song, current: state.currentSong, isPlaying: isPlaying)
            if numbered {
                TrackRow(number: entry.song.track.map { Int($0.intValue) }, title: entry.song.name ?? "Unknown", durationMs: Int64(entry.song.duration), playback: playback)
            } else {
                SongRow(song: entry.song, playback: playback)
            }
        }
        .buttonStyle(.plain)
        .rowSeparator(numbered ? .system : .none)
        .contextMenu {
            SongRowMenu(song: entry.song, onPlayNext: { onPlayNext([$0]) }, onAddToQueue: { onAddToQueue([$0]) }, onExclude: onExclude, onSongInfo: { songInfo = SongInfoTarget(songID: $0.id) }, downloads: downloads)
            Button("Remove from Playlist", systemImage: "minus.circle", role: .destructive) { onRemove(entry) }
        }
        .swipeActions(edge: .trailing) {
            Button(role: .destructive) { onRemove(entry) } label: {
                Label("Remove", systemImage: "minus.circle")
            }
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
    /// Delete pops back to the playlists list; export is out of scope for this screen, so its events are dropped here.
    func playlistDetailEvents(_ events: [PendingEvent<any PlaylistDetailEvent>], handled: @escaping (Int64) -> Void) -> some View {
        modifier(PlaylistDetailEventsModifier(events: events, handled: handled))
    }
}

private struct PlaylistDetailEventsModifier: ViewModifier {
    let events: [PendingEvent<any PlaylistDetailEvent>]
    let handled: (Int64) -> Void
    @Environment(\.dismiss) private var dismiss

    func body(content: Content) -> some View {
        content.modifier(ShuffleAlbumsFailedAlert(
            events: events,
            handled: handled,
            failure: { ($0 as? PlaylistDetailEventShuffleAlbumsFailed).map { $0.reason } },
            onOther: { if $0 is PlaylistDetailEventDeleted { dismiss() } }
        ))
    }
}
