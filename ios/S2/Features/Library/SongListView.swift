import Shared
import SwiftUI

/// Library > Songs (P5-6a): `SongListViewModel`'s songs, in its sort order. A tap plays the list from that song
/// through the shared `MediaAction.Play`, as Android's `MediaActionsHost` does; the context menu queues one song.
/// Shuffle is a toolbar button (#676). Pull to refresh imports.
struct SongListView: View {
    var body: some View {
        let models = ViewModelCache.shared.viewModel(Route.libraryCategory(.songs).cacheKey) {
            SongListModels(graph: AppGraph.shared)
        }
        LibraryNowPlayingReader { nowPlaying in
            Observing(models.songs.uiState, models.actions.uiState) { state, actions in
                SongListContent(
                    state: state,
                    nowPlaying: nowPlaying,
                    onPlay: { index in
                        models.actions.dispatch(action: MediaActionPlay(selection: MediaSelectionSongs(songs: state.songs), position: Int32(index)))
                    },
                    onPlayNext: { song in
                        models.actions.dispatch(action: MediaActionPlayNext(selection: MediaSelectionSongs(song: song)))
                    },
                    onAddToQueue: { song in
                        models.actions.dispatch(action: MediaActionAddToQueue(selection: MediaSelectionSongs(song: song)))
                    },
                    onExclude: { song in
                        models.actions.dispatch(action: MediaActionExclude(selection: MediaSelectionSongs(song: song)))
                    },
                    onShuffle: {
                        models.actions.dispatch(action: MediaActionShuffle(selection: MediaSelectionSongs(songs: state.songs)))
                    }
                )
                .mediaActionResults(actions.events, handled: { models.actions.onEventHandled(id: $0) }, send: { models.actions.dispatch(action: $0) })
            }
        }
        .refreshable { LibraryImport.refresh() }
        .navigationTitle(LibraryCategory.songs.title)
    }
}

/// The Songs screen's ViewModels, cached together under its route's key.
final class SongListModels: ViewModelGroup {
    let songs: SongListViewModel
    let actions: MediaActionsViewModel

    init(graph: IosAppGraph) {
        songs = graph.songListViewModel
        actions = graph.mediaActionsViewModel
    }

    var members: [Lifecycle_viewmodelViewModel] { [songs, actions] }
}

/// The Songs screen from a `SongListUiState`.
struct SongListContent: View {
    let state: SongListUiState
    var nowPlaying: LibraryNowPlaying = .none
    var onPlay: (Int) -> Void = { _ in }
    var onPlayNext: (Song) -> Void = { _ in }
    var onAddToQueue: (Song) -> Void = { _ in }
    var onExclude: (Song) -> Void = { _ in }
    var onShuffle: () -> Void = {}

    var body: some View {
        content
            .toolbar {
                if !state.songs.isEmpty {
                    ShuffleButton(identifier: "songs.shuffle", action: onShuffle)
                }
            }
    }

    @ViewBuilder
    private var content: some View {
        switch state.loadingState {
        case .loading:
            LibraryListSkeleton()
        case .scanning where state.songs.isEmpty:
            LibraryScanningView(progress: state.scanProgress)
        case .empty:
            EmptyState("No Songs", systemImage: "music.note", message: "Pull to refresh to import.")
        case .ready, .scanning:
            LetterIndexedList(
                items: state.songs,
                id: \.id,
                sections: LetterIndex.sections(state.letterIndex, items: state.songs, id: \.id)
            ) { index, song in
                let playback = nowPlaying.playback(song: song)
                Button { onPlay(index) } label: { SongRow(song: song, playback: playback) }
                    .buttonStyle(.pressScale)
                    .contextMenu {
                        SongRowMenu(song: song, onPlayNext: onPlayNext, onAddToQueue: onAddToQueue, onExclude: onExclude)
                    }
                    .nowPlayingRowBackground(playback)
            }
        }
    }
}

/// A song row's context menu in the Library's song lists: Play Next, Add to Queue, and Exclude (#650), marked
/// destructive as in Now Playing's menu, which hides the song from the library through the shared action.
struct SongRowMenu: View {
    let song: Song
    let onPlayNext: (Song) -> Void
    let onAddToQueue: (Song) -> Void
    let onExclude: (Song) -> Void

    var body: some View {
        Button("Play Next", systemImage: "text.line.first.and.arrowtriangle.forward") { onPlayNext(song) }
        Button("Add to Queue", systemImage: "text.append") { onAddToQueue(song) }
        let exclude = NowPlayingSongAction.exclude
        Button(exclude.title, systemImage: exclude.systemImage, role: .destructive) { onExclude(song) }
    }
}

struct SongRow: View {
    let song: Song
    var playback: MediaRowPlayback = .none

    var body: some View {
        MediaRow(
            song.name ?? "Unknown",
            subtitle: [song.friendlyArtistName, song.album].compactMap { $0 }.joined(separator: " · "),
            artwork: .song(song),
            playback: playback,
            titleIdentifier: "songRow.title"
        ) {
            Text(Duration.milliseconds(Int64(song.duration)).formatted(.time(pattern: .minuteSecond)))
                .font(.s2RowTime)
                .foregroundStyle(.s2SecondaryText)
        }
    }
}

/// A list's placeholder while the library's first import runs, with how far through it is when known. A later
/// import (pull to refresh, Sources' rescan) keeps showing what's already imported, which the shared list states
/// still carry while `.scanning` (#623); the Library root shows its progress.
struct LibraryScanningView: View {
    let progress: Shared.Progress?

    var body: some View {
        VStack(spacing: Spacing.smallMedium) {
            if let progress {
                ProgressView(value: Double(progress.asFloat()))
                    .frame(maxWidth: 240)
            } else {
                ProgressView()
            }
            Text("Importing your library…").foregroundStyle(.secondary)
        }
    }
}

extension View {
    /// Consumes `MediaActionsViewModel`'s results: playback failed and nothing to play as an alert; with `send`, the
    /// messages Now Playing shows as a notice (an Exclude, with its Undo, #650) show the same `PlayerNotice` here,
    /// its button sending the result's snackbar action back through `send`. The rest are dropped.
    func mediaActionResults(
        _ events: [PendingEvent<any MediaActionResult>],
        handled: @escaping (Int64) -> Void,
        send: ((any MediaAction) -> Void)? = nil
    ) -> some View {
        modifier(MediaActionResultsModifier(events: events, handled: handled, send: send))
    }
}

private struct MediaActionResultsModifier: ViewModifier {
    let events: [PendingEvent<any MediaActionResult>]
    let handled: (Int64) -> Void
    let send: ((any MediaAction) -> Void)?
    @State private var alert: String?
    @State private var notice: PlayerNotice?

    func body(content: Content) -> some View {
        content
            .consumeEvents(events, handled: handled) { result in
                if let message = (result as? MediaActionResultMessage).flatMap({ MediaActionText.alert(for: $0.message) }) {
                    alert = message
                } else if let send, case .notice(let shown) = PlayerEventOutcome.resolve(result, send: send) {
                    notice = shown
                }
            }
            .playerNotice($notice)
            .alert(alert ?? "", isPresented: Binding(get: { alert != nil }, set: { if !$0 { alert = nil } })) {
                Button("OK", role: .cancel) {}
            }
    }
}

/// The text for the few `MediaActionMessage`s the POC shows; phase 5's string catalogue replaces this.
enum MediaActionText {
    static func alert(for message: any MediaActionMessage) -> String? {
        switch message {
        case let failed as MediaActionMessagePlaybackFailed:
            failed.reason.map { "Couldn't play: \($0)" } ?? "Couldn't play."
        case is MediaActionMessageNoSongs:
            "There's nothing to play."
        default:
            nil
        }
    }
}
