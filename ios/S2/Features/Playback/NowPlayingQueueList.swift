import Shared
import SwiftUI

/// The queue, from Now Playing's queue button, in the player's tint: one plain list in three sections under the same
/// headers, as Apple Music lays out its queue: Now Playing (the playing song, its cover carrying the playing
/// indicator), Up Next (the songs after it), and Played (those before it). Every song is the same `MediaRow`. Tap a row
/// to skip to it. Edit mode (the Edit/Done button) reorders Up Next by dragging; a swipe, or Remove from Queue in a
/// row's context menu, takes an item out (with Undo); Play Next moves it after the current song; Clear empties the
/// queue (with Undo). Moves and removals show at once and the player's queue replaces them when it catches up. Rows
/// are keyed by the queue item's uid, never its position.
struct NowPlayingQueueList: View {
    let queue: [NowPlayingQueueRow]
    /// What the queue was started from, shown as "Playing from <title>" over the list; tapping it opens it.
    var source: HomeItem?
    var isPlaying = false
    var actions: PlayerActions = .none
    var notice: Binding<PlayerNotice?> = .constant(nil)

    @State private var rows: [NowPlayingQueueRow]
    @State private var editMode: EditMode = .inactive

    init(
        queue: [NowPlayingQueueRow],
        source: HomeItem? = nil,
        isPlaying: Bool = false,
        actions: PlayerActions = .none,
        notice: Binding<PlayerNotice?> = .constant(nil)
    ) {
        self.queue = queue
        self.source = source
        self.isPlaying = isPlaying
        self.actions = actions
        self.notice = notice
        _rows = State(initialValue: queue)
    }

    var body: some View {
        NavigationStack {
            Group {
                if rows.isEmpty {
                    ContentUnavailableView("Queue Empty", systemImage: "list.bullet", description: Text("Songs you play show up here."))
                } else {
                    list
                }
            }
            .navigationTitle("Queue")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                if !rows.isEmpty {
                    ToolbarItem(placement: .topBarLeading) {
                        Button("Clear", role: .destructive, action: actions.clearQueue)
                            .accessibilityIdentifier("queue.clear")
                    }
                    ToolbarItem(placement: .topBarTrailing) {
                        Button(editMode.isEditing ? "Done" : "Edit") {
                            withAnimation { editMode = editMode.isEditing ? .inactive : .active }
                        }
                        .fontWeight(editMode.isEditing ? .semibold : .regular)
                        .accessibilityIdentifier("queue.edit")
                    }
                }
            }
            .environment(\.editMode, $editMode)
            .onChange(of: queue) { _, newQueue in rows = newQueue }
            .playerNotice(notice)
        }
    }

    // MARK: - Sections

    private var currentIndex: Int? { rows.firstIndex(where: \.isCurrent) }

    /// Where Up Next starts in `rows`: after the playing song, or the top when nothing is current.
    private var upNextStart: Int { currentIndex.map { $0 + 1 } ?? 0 }

    private var upNext: ArraySlice<NowPlayingQueueRow> { rows[upNextStart...] }

    private var played: ArraySlice<NowPlayingQueueRow> { rows[..<(currentIndex ?? 0)] }

    private var list: some View {
        List {
            if let source {
                sourceLine(source)
            }

            if let currentIndex {
                Section {
                    row(rows[currentIndex])
                        .deleteDisabled(true)
                } header: {
                    sectionHeader("Now Playing")
                }
            }

            Section {
                if upNext.isEmpty {
                    Text("Nothing up next")
                        .font(.subheadline)
                        .foregroundStyle(.s2TextSecondary)
                        .rowSeparator(.none)
                } else {
                    ForEach(upNext) { item in
                        row(item)
                    }
                    .onMove(perform: moveUpNext)
                    .onDelete { offsets in
                        offsets.map { upNext[upNextStart + $0].id }.forEach(remove)
                    }
                }
            } header: {
                sectionHeader("Up Next", subtitle: Self.upNextSummary(Array(upNext)))
            }

            if !played.isEmpty {
                Section {
                    ForEach(played) { item in
                        row(item)
                    }
                    .onDelete { offsets in
                        offsets.map { played[$0].id }.forEach(remove)
                    }
                } header: {
                    sectionHeader("Played")
                }
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .background(.s2SurfaceElevated)
    }

    /// "Playing from <title>": the album, artist, playlist or genre the queue was started from, which a tap opens.
    private func sourceLine(_ source: HomeItem) -> some View {
        Button {
            actions.openRoute(HomeView.route(source))
        } label: {
            Text("Playing from \(source.title)")
                .font(.subheadline)
                .foregroundStyle(.s2TextSecondary)
                .lineLimit(1)
                .truncationMode(.tail)
        }
        .buttonStyle(.plain)
        .accessibilityHint(source.goToLabel)
        .accessibilityIdentifier("queue.source")
        .rowSeparator(.none)
    }

    private func sectionHeader(_ title: String, subtitle: String? = nil) -> some View {
        SectionHeader(title, subtitle: subtitle)
            .textCase(nil)
            .padding(.vertical, Spacing.xsmall)
            .pinnedHeader(.s2SurfaceElevated)
    }

    /// A queue row; the playing song's cover carries the playing indicator and VoiceOver says it's playing.
    private func row(_ item: NowPlayingQueueRow) -> some View {
        Button { actions.selectQueueItem(item.id) } label: {
            MediaRow(
                item.title,
                subtitle: item.artist,
                artwork: item.artwork,
                playback: item.isCurrent ? (isPlaying ? .playing : .paused) : .none
            ) {
                if item.durationMs > 0 {
                    SongDurationText(durationMs: Int64(item.durationMs))
                }
            }
        }
        .buttonStyle(.plain)
        .rowSeparator(.none)
        .accessibilityLabel(Self.accessibilityLabel(for: item))
        .accessibilityIdentifier(item.isCurrent ? "queue.nowPlaying" : "queue.row")
        .contextMenu {
            NowPlayingQueueRowMenu(item: item, playNext: actions.playNext, remove: remove, exclude: actions.excludeQueueItem)
        }
    }

    /// What VoiceOver reads for a row, which replaces its content: title, artist, whether it's playing, and its
    /// length when known.
    static func accessibilityLabel(for item: NowPlayingQueueRow, locale: Locale = .current) -> String {
        [
            item.title,
            item.artist,
            item.isCurrent ? "now playing" : nil,
            item.durationMs > 0 ? spokenDuration(ms: Int64(item.durationMs), locale: locale) : nil,
        ].compactMap { $0 }.joined(separator: ", ")
    }

    // MARK: - Up Next summary

    /// The time the songs in `rows` run for, as every hero's total ("43 min", "1 hr, 12 min" in English); nil under
    /// a minute, or when any song's length is unknown, since a total that skips it would undercount.
    static func remainingTime(_ rows: [NowPlayingQueueRow], locale: Locale = .current) -> String? {
        guard !rows.contains(where: { $0.durationMs <= 0 }) else { return nil }
        return runtime(ms: rows.reduce(Int64(0)) { $0 + Int64($1.durationMs) }, locale: locale)
    }

    /// Up Next's header line, "3 songs · 12 min": the count, then the time left when known; nil with nothing up next.
    /// The playing song's remainder is left out, so the line doesn't change with every progress tick.
    static func upNextSummary(_ rows: [NowPlayingQueueRow], locale: Locale = .current) -> String? {
        guard !rows.isEmpty else { return nil }
        return eyebrow(pluralized(rows.count, .song), remainingTime(rows, locale: locale))
    }

    // MARK: - Editing

    /// `List.onMove` inside Up Next: its offsets are Up Next's, `move` works on the whole queue.
    private func moveUpNext(from source: IndexSet, to destination: Int) {
        let start = upNextStart
        move(from: IndexSet(source.map { $0 + start }), to: destination + start)
    }

    private func move(from source: IndexSet, to destination: Int) {
        guard let result = Self.move(rows, from: source, to: destination) else { return }
        rows = result.rows
        actions.moveQueueItem(result.uid, result.afterUid)
    }

    private func remove(_ uid: Int64) {
        rows.removeAll { $0.id == uid }
        actions.removeQueueItem(uid)
    }

    /// `rows` after moving the row at `source` to `destination` (`List.onMove`'s offsets), with the moved row's uid
    /// and the uid of the row it now follows (nil at the top): the ViewModel moves by uid. Nil when nothing moves.
    static func move(
        _ rows: [NowPlayingQueueRow], from source: IndexSet, to destination: Int
    ) -> (rows: [NowPlayingQueueRow], uid: Int64, afterUid: Int64?)? {
        guard source.count == 1, let from = source.first, rows.indices.contains(from) else { return nil }
        var moved = rows
        moved.move(fromOffsets: source, toOffset: destination)
        guard moved != rows, let to = moved.firstIndex(where: { $0.id == rows[from].id }) else { return nil }
        return (moved, rows[from].id, to == 0 ? nil : moved[to - 1].id)
    }
}

/// A queue row's context menu: Play Next (not for the playing song), Remove from Queue, and Exclude (#650), which hides
/// the song from the library and so drops it from the queue, with Undo in the notice.
struct NowPlayingQueueRowMenu: View {
    let item: NowPlayingQueueRow
    let playNext: (Int64) -> Void
    let remove: (Int64) -> Void
    let exclude: (Int64) -> Void

    var body: some View {
        if !item.isCurrent {
            Button("Play Next", systemImage: "text.line.first.and.arrowtriangle.forward") { playNext(item.id) }
        }
        Button("Remove from Queue", systemImage: "minus.circle", role: .destructive) { remove(item.id) }
        let excludeAction = NowPlayingSongAction.exclude
        Button(excludeAction.title, systemImage: excludeAction.systemImage, role: .destructive) { exclude(item.id) }
    }
}
