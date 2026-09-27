import Shared
import SwiftUI

/// Library > Songs (P5-6a): `SongListViewModel`'s songs, in its sort order. A tap plays the list from that song
/// through the shared `MediaAction.Play`, as Android's `MediaActionsHost` does; the context menu queues one song.
/// Pull to refresh imports.
struct SongListView: View {
    var body: some View {
        let models = ViewModelCache.shared.viewModel(Route.libraryCategory(.songs).cacheKey) {
            SongListModels(graph: AppGraph.shared)
        }
        Observing(models.songs.uiState, models.actions.uiState) { state, actions in
            SongListContent(
                state: state,
                onPlay: { index in
                    models.actions.dispatch(action: MediaActionPlay(selection: MediaSelectionSongs(songs: state.songs), position: Int32(index)))
                },
                onPlayNext: { song in
                    models.actions.dispatch(action: MediaActionPlayNext(selection: MediaSelectionSongs(song: song)))
                },
                onAddToQueue: { song in
                    models.actions.dispatch(action: MediaActionAddToQueue(selection: MediaSelectionSongs(song: song)))
                },
                onShuffle: {
                    models.actions.dispatch(action: MediaActionShuffle(selection: MediaSelectionSongs(songs: state.songs)))
                }
            )
            .mediaActionResults(actions.events, handled: { models.actions.onEventHandled(id: $0) })
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
    var onPlay: (Int) -> Void = { _ in }
    var onPlayNext: (Song) -> Void = { _ in }
    var onAddToQueue: (Song) -> Void = { _ in }
    var onShuffle: () -> Void = {}

    var body: some View {
        switch state.loadingState {
        case .loading:
            ProgressView()
        case .scanning:
            LibraryScanningView(progress: state.scanProgress)
        case .empty:
            ContentUnavailableView("No Songs", systemImage: "music.note", description: Text("Pull to refresh to import."))
        case .ready:
            List {
                ForEach(Array(state.songs.enumerated()), id: \.element.id) { index, song in
                    Button { onPlay(index) } label: { SongRow(song: song) }
                        .buttonStyle(.plain)
                        .contextMenu {
                            Button("Play Next", systemImage: "text.line.first.and.arrowtriangle.forward") { onPlayNext(song) }
                            Button("Add to Queue", systemImage: "text.append") { onAddToQueue(song) }
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

struct SongRow: View {
    let song: Song

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(song.name ?? "Unknown").lineLimit(1)
                Text([song.friendlyArtistName, song.album].compactMap { $0 }.joined(separator: " · "))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            Spacer()
            Text(Duration.milliseconds(Int64(song.duration)).formatted(.time(pattern: .minuteSecond)))
                .font(.subheadline.monospacedDigit())
                .foregroundStyle(.secondary)
        }
        .contentShape(Rectangle())
    }
}

/// A list's placeholder while the library's first import runs, with how far through it is when known.
struct LibraryScanningView: View {
    let progress: Shared.Progress?

    var body: some View {
        VStack(spacing: 12) {
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
    /// Consumes `MediaActionsViewModel`'s results: the messages that matter on the POC's screens (playback
    /// failed, nothing to play) as an alert; the rest are dropped until the snackbar lands.
    func mediaActionResults(_ events: [PendingEvent<any MediaActionResult>], handled: @escaping (Int64) -> Void) -> some View {
        modifier(MediaActionResultsModifier(events: events, handled: handled))
    }
}

private struct MediaActionResultsModifier: ViewModifier {
    let events: [PendingEvent<any MediaActionResult>]
    let handled: (Int64) -> Void
    @State private var alert: String?

    func body(content: Content) -> some View {
        content
            .consumeEvents(events, handled: handled) { result in
                if let message = (result as? MediaActionResultMessage).flatMap({ MediaActionText.alert(for: $0.message) }) {
                    alert = message
                }
            }
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
