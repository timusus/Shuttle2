import SwiftUI

/// Now Playing, after Shuttle Podcasts' player: the cover, a large title and artist, a scrubber with
/// monospaced elapsed and remaining times, the transport, and shuffle, repeat and the queue along the bottom.
/// Presented by `nowPlayingPresentation` (a full-screen cover in `compact`, a form sheet otherwise); the queue
/// opens through `playerSheet` (a sheet in `compact`, a popover otherwise). Bound to the shared `PlayerViewModel`
/// through `PlayerBinding` only.
///
/// The title row has the favourite toggle and the song's actions menu (Add to Playlist, Go to Album/Artist, Exclude);
/// the bottom bar has the AirPlay route picker. The ViewModel's one-shot events are consumed here while Now Playing is
/// up: a notice (`PlayerNotice`, with Undo where the ViewModel offers it) or a screen to open, which closes Now
/// Playing and pushes the route through `onOpen`.
struct NowPlayingView: View {
    let binding: PlayerBinding
    let onOpen: (Route) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var notice: PlayerNotice?

    /// `binding` defaults to the same `PlayerBinding` `MiniPlayerView` uses (`AppGraph.dependencies.playerBinding`),
    /// so the two always show the same state. `onOpen` gets the route a song action opens, once Now Playing has
    /// been asked to close.
    init(binding: PlayerBinding = AppGraph.dependencies.playerBinding, onOpen: @escaping (Route) -> Void = { _ in }) {
        self.binding = binding
        self.onOpen = onOpen
    }

    var body: some View {
        NowPlayingContent(state: binding.nowPlaying, actions: binding.actions, notice: $notice, onClose: { dismiss() })
            .consumeEvents(binding.events, handled: { binding.eventHandled($0) }) { event in
                switch binding.outcome(for: event) {
                case .notice(let next): notice = next
                case .open(let route):
                    dismiss()
                    onOpen(route)
                case nil: break
                }
            }
    }
}

/// The screen's content: plain values in, so it previews and tests (ViewInspector) without Kotlin.
struct NowPlayingContent: View {
    let state: NowPlayingState
    var actions: PlayerActions = .none
    /// The notice showing, if any; the queue shows it instead while it's open.
    var notice: Binding<PlayerNotice?> = .constant(nil)
    var onClose: () -> Void = {}

    @Environment(\.layoutTier) private var tier
    @State private var showQueue = false
    @State private var showNewPlaylist = false
    @State private var newPlaylistName = ""

    var body: some View {
        VStack(spacing: 0) {
            closeRow
            if state.title == nil {
                EmptyState("Nothing Playing", systemImage: "play.circle", message: "Play a song from your library.")
                    .frame(maxHeight: .infinity)
            } else {
                player
            }
        }
        .background(Color(.systemBackground))
        .playerNotice(showQueue ? .constant(nil) : notice)
        .alert("New Playlist", isPresented: $showNewPlaylist) {
            TextField("Playlist Name", text: $newPlaylistName)
            Button("Cancel", role: .cancel) { newPlaylistName = "" }
            Button("Create") {
                actions.addToPlaylist(.new(name: newPlaylistName))
                newPlaylistName = ""
            }
            .disabled(PlaylistChoice.trimmedName(newPlaylistName) == nil)
        }
    }

    private var closeRow: some View {
        HStack {
            Button(action: onClose) {
                Image(systemName: "chevron.down")
                    .font(.title3.weight(.semibold))
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .foregroundStyle(.secondary)
            .accessibilityLabel("Close")
            .accessibilityIdentifier("nowPlaying.close")
            Spacer()
        }
        .padding(.horizontal, Spacing.small)
    }

    private var player: some View {
        VStack(spacing: Spacing.large) {
            artwork
                .frame(maxHeight: .infinity)

            HStack(spacing: Spacing.small) {
                favouriteButton
                VStack(spacing: Spacing.tiny) {
                    Text(state.title ?? "")
                        .font(.s2Title2)
                        .multilineTextAlignment(.center)
                        .lineLimit(2)
                        .accessibilityAddTraits(.isHeader)
                    if let subtitle {
                        Text(subtitle)
                            .font(.body)
                            .foregroundStyle(.s2SecondaryText)
                            .multilineTextAlignment(.center)
                            .lineLimit(1)
                    }
                }
                .frame(maxWidth: .infinity)
                songMenu
            }

            NowPlayingScrubber(positionMs: state.positionMs, durationMs: state.durationMs, onSeek: actions.seek)

            NowPlayingTransport(isPlaying: state.isPlaying, actions: actions)

            bottomBar
        }
        .padding(.horizontal, Spacing.large)
        .padding(.bottom, Spacing.medium)
    }

    /// The cover: square, as large as the space left allows, decoded at the tier's largest size.
    private var artwork: some View {
        Group {
            if let source = state.artwork {
                RemoteArtwork(source, points: artworkPoints)
            } else {
                ArtworkPlaceholder()
            }
        }
        .aspectRatio(1, contentMode: .fit)
        .frame(maxWidth: artworkPoints)
        .clipShape(RoundedRectangle(cornerRadius: Radius.large, style: .continuous))
        .shadow(color: .black.opacity(0.15), radius: 12, y: 6)
        .accessibilityHidden(true)
    }

    /// The largest the cover draws: a phone's width in `compact`, the form sheet's column otherwise.
    private var artworkPoints: CGFloat {
        tier == .compact ? 420 : 440
    }

    private var subtitle: String? {
        let parts = [state.artist, state.album].compactMap { $0 }
        return parts.isEmpty ? nil : parts.joined(separator: " · ")
    }

    private var bottomBar: some View {
        HStack {
            Button(action: actions.toggleShuffle) {
                Image(systemName: "shuffle")
                    .modeGlyph(isOn: state.shuffleOn)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Shuffle")
            .accessibilityValue(state.shuffleOn ? "On" : "Off")
            .accessibilityIdentifier("nowPlaying.shuffle")

            Spacer()

            Button(action: actions.toggleRepeat) {
                Image(systemName: state.repeatMode == .one ? "repeat.1" : "repeat")
                    .modeGlyph(isOn: state.repeatMode != .off)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Repeat")
            .accessibilityValue(repeatValue)
            .accessibilityIdentifier("nowPlaying.repeat")

            Spacer()

            speedMenu

            Spacer()

            sleepTimerMenu

            Spacer()

            AirPlayButton(activeTint: .accentColor, inactiveTint: .s2SecondaryText)
                .frame(width: 44, height: 44)
                .accessibilityIdentifier("nowPlaying.airPlay")

            Spacer()

            Button { showQueue = true } label: {
                Image(systemName: "list.bullet")
                    .modeGlyph(isOn: false)
            }
            .buttonStyle(.plain)
            .disabled(state.queue.isEmpty)
            .accessibilityLabel("Queue")
            .accessibilityIdentifier("nowPlaying.queue")
            .playerSheet(isPresented: $showQueue, tier: tier) {
                NowPlayingQueueList(queue: state.queue, actions: actions, notice: notice)
            }
        }
    }

    private var favouriteButton: some View {
        Button(action: actions.toggleFavourite) {
            Image(systemName: state.isFavourite ? "heart.fill" : "heart")
                .modeGlyph(isOn: state.isFavourite)
        }
        .buttonStyle(.plain)
        .disabled(state.title == nil)
        .accessibilityLabel("Favorite")
        .accessibilityValue(state.isFavourite ? "On" : "Off")
        .accessibilityIdentifier("nowPlaying.favourite")
    }

    /// The current song's actions, those the ViewModel offers that iOS has a screen for.
    private var songMenu: some View {
        Menu {
            ForEach(state.songActions, id: \.self) { action in
                switch action {
                case .addToPlaylist:
                    Menu {
                        Button("New Playlist…", systemImage: "plus") { showNewPlaylist = true }
                        Button("Favorites", systemImage: "heart") { actions.addToPlaylist(.favourites) }
                        ForEach(state.playlists) { playlist in
                            Button(playlist.name) { actions.addToPlaylist(.playlist(id: playlist.id)) }
                        }
                    } label: {
                        Label(action.title, systemImage: action.systemImage)
                    }
                case .exclude:
                    Button(action.title, systemImage: action.systemImage, role: .destructive) { actions.songAction(action) }
                default:
                    Button(action.title, systemImage: action.systemImage) { actions.songAction(action) }
                }
            }
        } label: {
            Image(systemName: "ellipsis.circle")
                .modeGlyph(isOn: false)
        }
        .disabled(state.songActions.isEmpty)
        .accessibilityLabel("More")
        .accessibilityIdentifier("nowPlaying.more")
    }

    /// The playback speeds offered, 1 being normal.
    static let speeds: [Float] = [0.5, 0.75, 1, 1.25, 1.5, 2]

    /// The sleep timer's durations, in minutes.
    static let sleepTimerMinutes = [15, 30, 45, 60]

    private var speedMenu: some View {
        Menu {
            ForEach(Self.speeds, id: \.self) { speed in
                Button { actions.setSpeed(speed) } label: {
                    if speed == state.playbackSpeed {
                        Label(Self.speedText(speed), systemImage: "checkmark")
                    } else {
                        Text(Self.speedText(speed))
                    }
                }
            }
        } label: {
            Image(systemName: "gauge.with.dots.needle.50percent")
                .modeGlyph(isOn: state.playbackSpeed != 1)
        }
        .accessibilityLabel("Playback Speed")
        .accessibilityValue(Self.speedText(state.playbackSpeed))
        .accessibilityIdentifier("nowPlaying.speed")
    }

    private var sleepTimerMenu: some View {
        Menu {
            ForEach(Self.sleepTimerMinutes, id: \.self) { minutes in
                Button("\(minutes) Minutes") { actions.startSleepTimer(minutes) }
            }
            if state.sleepTimerActive {
                Button("Turn Off Timer", role: .destructive, action: actions.stopSleepTimer)
            }
        } label: {
            Image(systemName: state.sleepTimerActive ? "moon.zzz.fill" : "moon.zzz")
                .modeGlyph(isOn: state.sleepTimerActive)
        }
        .accessibilityLabel("Sleep Timer")
        .accessibilityValue(state.sleepTimerActive ? "On" : "Off")
        .accessibilityIdentifier("nowPlaying.sleepTimer")
    }

    static func speedText(_ speed: Float) -> String {
        speed.formatted(.number.precision(.fractionLength(0...2))) + "×"
    }

    private var repeatValue: String {
        switch state.repeatMode {
        case .off: "Off"
        case .all: "All"
        case .one: "One"
        }
    }
}

private extension Image {
    /// A bottom-bar glyph: the accent while its mode is on, secondary otherwise, in a 44pt target.
    func modeGlyph(isOn: Bool) -> some View {
        font(.title3)
            .foregroundStyle(isOn ? AnyShapeStyle(.tint) : AnyShapeStyle(.s2SecondaryText))
            .frame(width: 44, height: 44)
            .contentShape(Rectangle())
    }
}

/// The position slider with elapsed and remaining times under it. Holds the dragged position locally and
/// seeks once, on release.
struct NowPlayingScrubber: View {
    let positionMs: Int
    let durationMs: Int
    let onSeek: (Int) -> Void

    @State private var isScrubbing = false
    @State private var scrubMs: Double = 0

    var body: some View {
        let current = isScrubbing ? Int(scrubMs) : positionMs
        VStack(spacing: Spacing.xsmall) {
            Slider(
                value: Binding(
                    get: { isScrubbing ? scrubMs : Double(positionMs) },
                    set: { newValue in
                        isScrubbing = true
                        scrubMs = newValue
                    }
                ),
                in: 0...Double(max(durationMs, 1)),
                onEditingChanged: { editing in
                    if !editing {
                        onSeek(Int(scrubMs))
                        isScrubbing = false
                    }
                }
            )
            .accessibilityLabel("Playback position")
            .accessibilityValue("\(Self.formatted(ms: current)) of \(Self.formatted(ms: durationMs))")
            // VoiceOver's swipe up/down adjusts the slider without `onEditingChanged`, so seek here.
            .accessibilityAdjustableAction { direction in
                if let target = Self.adjusted(positionMs: positionMs, durationMs: durationMs, direction) {
                    onSeek(target)
                }
            }

            HStack {
                Text(Self.formatted(ms: current))
                Spacer()
                Text("-" + Self.formatted(ms: max(0, durationMs - current)))
            }
            .font(.s2Time)
            .foregroundStyle(.s2SecondaryText)
            .accessibilityHidden(true)
        }
    }

    /// How far one VoiceOver adjustment seeks.
    static let accessibilityStepMs = 15_000

    /// Where one VoiceOver adjustment seeks to, clamped to the song.
    static func adjusted(positionMs: Int, durationMs: Int, _ direction: AccessibilityAdjustmentDirection) -> Int? {
        switch direction {
        case .increment: min(durationMs, positionMs + accessibilityStepMs)
        case .decrement: max(0, positionMs - accessibilityStepMs)
        @unknown default: nil
        }
    }

    static func formatted(ms: Int) -> String {
        let totalSeconds = max(0, ms) / 1000
        return String(format: "%d:%02d", totalSeconds / 60, totalSeconds % 60)
    }
}

/// Previous, play/pause and next; glyphs scale with Dynamic Type up to a cap, targets never under 44pt.
struct NowPlayingTransport: View {
    let isPlaying: Bool
    let actions: PlayerActions

    @ScaledMetric(relativeTo: .largeTitle) private var playSize: CGFloat = 64
    @ScaledMetric(relativeTo: .title) private var skipSize: CGFloat = 30

    var body: some View {
        HStack(spacing: Spacing.xlarge) {
            glyphButton("backward.fill", size: min(skipSize, 40), label: "Previous", action: actions.previous)
            glyphButton(
                isPlaying ? "pause.circle.fill" : "play.circle.fill",
                size: min(playSize, 84),
                label: isPlaying ? "Pause" : "Play",
                action: actions.playPause
            )
            .foregroundStyle(.tint)
            glyphButton("forward.fill", size: min(skipSize, 40), label: "Next", action: actions.next)
        }
    }

    private func glyphButton(_ systemImage: String, size: CGFloat, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.system(size: size))
                .frame(minWidth: 44, minHeight: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}

/// The queue, from Now Playing's queue button: every item with the current one marked, tap to skip to it. Edit
/// mode (the Edit/Done button) reorders by dragging; a swipe, or Remove from Queue in a row's context menu, takes an
/// item out (with Undo); Play Next moves it after the current song; Clear empties the queue (with Undo). Moves and
/// removals show at once and the player's queue replaces them when it catches up. Rows are keyed by the queue item's
/// uid, never its position.
struct NowPlayingQueueList: View {
    let queue: [NowPlayingQueueRow]
    var actions: PlayerActions = .none
    var notice: Binding<PlayerNotice?> = .constant(nil)

    @State private var rows: [NowPlayingQueueRow]
    @State private var editMode: EditMode = .inactive

    init(queue: [NowPlayingQueueRow], actions: PlayerActions = .none, notice: Binding<PlayerNotice?> = .constant(nil)) {
        self.queue = queue
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
                    List {
                        ForEach(rows) { item in
                            Button { actions.selectQueueItem(item.id) } label: {
                                row(item)
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel(item.isCurrent ? "\(item.title), now playing" : item.title)
                            .contextMenu {
                                NowPlayingQueueRowMenu(item: item, playNext: actions.playNext, remove: remove)
                            }
                        }
                        .onMove(perform: move)
                        .onDelete { offsets in
                            offsets.map { rows[$0].id }.forEach(remove)
                        }
                    }
                    .listStyle(.plain)
                }
            }
            .navigationTitle("Up Next")
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

    private func row(_ item: NowPlayingQueueRow) -> some View {
        HStack(spacing: Spacing.smallMedium) {
            Group {
                if let artwork = item.artwork {
                    RemoteArtwork(artwork, points: ArtworkSize.row)
                } else {
                    ArtworkPlaceholder()
                }
            }
            .artworkTile(ArtworkSize.row)

            VStack(alignment: .leading, spacing: Spacing.tiny) {
                Text(item.title)
                    .font(.body.weight(item.isCurrent ? .semibold : .regular))
                    .foregroundStyle(item.isCurrent ? AnyShapeStyle(.tint) : AnyShapeStyle(.primary))
                    .lineLimit(1)
                if let artist = item.artist {
                    Text(artist).font(.subheadline).foregroundStyle(.s2SecondaryText).lineLimit(1)
                }
            }
            Spacer(minLength: 0)
            if item.isCurrent {
                Image(systemName: "speaker.wave.2.fill")
                    .foregroundStyle(.tint)
                    .accessibilityHidden(true)
            }
        }
        .contentShape(Rectangle())
    }
}

/// A queue row's context menu: Play Next (not for the playing song) and Remove from Queue.
struct NowPlayingQueueRowMenu: View {
    let item: NowPlayingQueueRow
    let playNext: (Int64) -> Void
    let remove: (Int64) -> Void

    var body: some View {
        if !item.isCurrent {
            Button("Play Next", systemImage: "text.line.first.and.arrowtriangle.forward") { playNext(item.id) }
        }
        Button("Remove from Queue", systemImage: "minus.circle", role: .destructive) { remove(item.id) }
    }
}

#Preview("Playing") {
    NowPlayingContent(
        state: NowPlayingState(
            title: "Paranoid Android", artist: "Radiohead", album: "OK Computer", isPlaying: true,
            positionMs: 90_000, durationMs: 386_000,
            queue: [
                .init(id: 1, title: "Paranoid Android", artist: "Radiohead", isCurrent: true),
                .init(id: 2, title: "Hyperballad", artist: "Björk", isCurrent: false),
            ],
            shuffleOn: true, repeatMode: .one,
            isFavourite: true,
            songActions: NowPlayingSongAction.allCases,
            playlists: [.init(id: 1, name: "Road Trip")]
        ),
        notice: .constant(PlayerNotice(message: "1 song added to Road Trip"))
    )
}

#Preview("Nothing playing") {
    NowPlayingContent(state: .idle)
}

#Preview("Queue") {
    NowPlayingQueueList(
        queue: [
            .init(id: 1, title: "Paranoid Android", artist: "Radiohead", isCurrent: true),
            .init(id: 2, title: "Hyperballad", artist: "Björk", isCurrent: false),
        ]
    )
}

#Preview("Empty queue") {
    NowPlayingQueueList(queue: [])
}
