import SwiftUI

/// Now Playing, after Shuttle Podcasts' player (#624): the cover over a blurred copy of itself (`ArtworkBackground`),
/// the title and artist in `MarqueeText` with the favourite and the song's actions trailing, a capsule scrubber and a
/// tint-filled play circle in the cover's tint, and shuffle, repeat, speed, the sleep timer, AirPlay and the queue in
/// one glass capsule along the bottom. The artwork tint (`\.artworkTint`) comes from above (`playerArtworkTint`, in
/// `ContentView`) and is the screen's `.tint`. Presented by `nowPlayingPresentation` (a full-screen cover in
/// `compact`, which a swipe down dismisses, a form sheet otherwise); the queue opens through `playerSheet` (a sheet
/// in `compact`, a popover otherwise). From `AdaptiveLayout.twoColumnMinWidth` (a phone on its side) the cover sits
/// left of the controls. Bound to the shared `PlayerViewModel` through `PlayerBinding` only.
///
/// The ViewModel's one-shot events are consumed here while Now Playing is up: a notice (`PlayerNotice`, with Undo
/// where the ViewModel offers it) or a screen to open, which closes Now Playing and pushes the route through `onOpen`.
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

/// The cover the mini player and Now Playing share (`nowPlayingMatchedGeometry`).
enum NowPlayingCover {
    static let matchedGeometryID = "nowPlaying.cover"
}

/// The screen's content: plain values in, so it previews and tests (ViewInspector) without Kotlin.
struct NowPlayingContent: View {
    let state: NowPlayingState
    var actions: PlayerActions = .none
    /// The notice showing, if any; the queue shows it instead while it's open.
    var notice: Binding<PlayerNotice?> = .constant(nil)
    var onClose: () -> Void = {}

    @Environment(\.layoutTier) private var tier
    @Environment(\.artworkTint) private var artworkTint
    @Environment(\.artworkTintInk) private var artworkTintInk
    @Environment(\.isArtworkTinted) private var isArtworkTinted
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.colorSchemeContrast) private var colorSchemeContrast
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    @State private var showQueue = false
    @State private var showNewPlaylist = false
    @State private var newPlaylistName = ""
    @State private var isScrubbing = false
    @State private var containerWidth: CGFloat = 0
    // The swipe-down dismiss (full-screen cover only), after Podcasts' player.
    @State private var dragOffset: CGFloat = 0
    @State private var isDragging = false
    /// Set when a drag opened sideways (the scrubber, a marquee line) and held until it ends, so its vertical
    /// drift doesn't pull the player down.
    @State private var dragIsSideways = false

    /// How far a released pull, or its predicted end, must travel to dismiss.
    static let dismissDistance: CGFloat = 150
    static let dismissPredictedDistance: CGFloat = 300
    /// The player's scale while it's being pulled down.
    static let draggingScale: CGFloat = 0.95

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
        .background {
            // In a clear overlay so the backdrop's fill-scaled image can't widen the layout (Podcasts' fix).
            Color.clear
                .overlay { ArtworkBackground(source: state.artwork) }
                .ignoresSafeArea()
        }
        .onGeometryChange(for: CGFloat.self) { proxy in
            proxy.size.width
        } action: { width in
            containerWidth = width
        }
        .offset(y: dragOffset)
        .scaleEffect(isDragging ? Self.draggingScale : 1, anchor: .top)
        .animation(Motion.press.reduced(reduceMotion), value: isDragging)
        .simultaneousGesture(dismissDrag, including: dismissesByDragging ? .all : .subviews)
        // On a player surface the artwork tint is the accent.
        .tint(artworkTint)
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

    // MARK: - Layout

    private var closeRow: some View {
        HStack {
            Button(action: onClose) {
                // On a material disc: the backdrop's top edge can be as dark as the cover, whatever the scheme.
                Image(systemName: "chevron.down")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(.primary)
                    .frame(width: 36, height: 36)
                    .background(.ultraThinMaterial, in: Circle())
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Close")
            .accessibilityIdentifier("nowPlaying.close")
            Spacer()
        }
        .padding(.horizontal, Spacing.small)
    }

    private var isTwoColumn: Bool {
        containerWidth >= AdaptiveLayout.twoColumnMinWidth
    }

    @ViewBuilder
    private var player: some View {
        if isTwoColumn {
            HStack(spacing: Spacing.xlarge) {
                cover
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                controls(spacing: Spacing.medium)
                    .frame(maxWidth: ArtworkSize.playerMaximum)
                    .frame(maxHeight: .infinity)
            }
            .padding(.horizontal, Spacing.xlarge)
            .padding(.bottom, Spacing.medium)
        } else {
            VStack(spacing: Spacing.large) {
                cover
                    .frame(maxHeight: .infinity)
                controls(spacing: Spacing.large)
            }
            .padding(.horizontal, Spacing.large)
            .padding(.bottom, Spacing.medium)
        }
    }

    private func controls(spacing: CGFloat) -> some View {
        VStack(spacing: spacing) {
            titleRow
            NowPlayingScrubber(
                positionMs: state.positionMs, durationMs: state.durationMs, isScrubbing: $isScrubbing, onSeek: actions.seek
            )
            NowPlayingTransport(isPlaying: state.isPlaying, actions: actions)
            bottomCapsule
        }
    }

    /// The cover: square, as large as the space left allows up to `ArtworkSize.playerMaximum`, easing back to
    /// `Motion.pausedCoverScale` while paused.
    private var cover: some View {
        Group {
            if let source = state.artwork {
                RemoteArtwork(source, points: ArtworkSize.playerMaximum)
            } else {
                ArtworkPlaceholder()
            }
        }
        .aspectRatio(1, contentMode: .fit)
        .artworkStyle(cornerRadius: ArtworkCorner.player)
        .artworkShadow(.player)
        .nowPlayingMatchedGeometry(id: NowPlayingCover.matchedGeometryID)
        .scaleEffect(state.isPlaying ? 1 : Motion.pausedCoverScale)
        .animation(Motion.coverScale.reduced(reduceMotion), value: state.isPlaying)
        .frame(maxWidth: ArtworkSize.playerMaximum, maxHeight: ArtworkSize.playerMaximum)
        .accessibilityHidden(true)
    }

    private var titleRow: some View {
        HStack(spacing: Spacing.xsmall) {
            VStack(alignment: .leading, spacing: Spacing.tiny) {
                MarqueeText(state.title ?? "")
                    .font(.s2PlayerTitle)
                    .foregroundStyle(.primary)
                    .accessibilityAddTraits(.isHeader)
                    .accessibilityIdentifier("nowPlaying.title")
                if let subtitle {
                    MarqueeText(subtitle)
                        .font(.title3)
                        .foregroundStyle(secondaryInk)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            favouriteButton
            songMenu
        }
    }

    private var subtitle: String? {
        let parts = [state.artist, state.album].compactMap { $0 }
        return parts.isEmpty ? nil : parts.joined(separator: " · ")
    }

    // MARK: - Ink

    /// Captions on the backdrop: the AA-safe grey, measured on the scrimmed ground; plain `.primary` under Increase
    /// Contrast.
    private var secondaryInk: Color {
        colorSchemeContrast == .increased ? .primary : TintedChromeInk.secondaryInk(isDarkScheme: colorScheme == .dark)
    }

    /// A control's glyph: the tint while it has something to report (a mode on), neutral otherwise.
    private func chromeInk(isOn: Bool) -> Color {
        TintedChromeInk.foreground(
            tint: artworkTint,
            isTinted: isOn,
            increasedContrast: colorSchemeContrast == .increased,
            isDarkScheme: colorScheme == .dark
        )
    }

    // MARK: - Bottom capsule

    /// Shuffle, repeat, speed, the sleep timer, AirPlay and the queue in one capsule: Liquid Glass on iOS 26,
    /// `.ultraThinMaterial` below. Neutral, each glyph taking the tint only while its mode is on.
    private var bottomCapsule: some View {
        HStack(spacing: 0) {
            Button(action: actions.toggleShuffle) {
                Image(systemName: "shuffle")
                    .capsuleGlyph(chromeInk(isOn: state.shuffleOn))
            }
            .buttonStyle(.pressScale)
            .accessibilityLabel("Shuffle")
            .accessibilityValue(state.shuffleOn ? "On" : "Off")
            .accessibilityIdentifier("nowPlaying.shuffle")
            .frame(maxWidth: .infinity)

            Button(action: actions.toggleRepeat) {
                Image(systemName: state.repeatMode == .one ? "repeat.1" : "repeat")
                    .capsuleGlyph(chromeInk(isOn: state.repeatMode != .off))
                    .contentTransition(.symbolEffect(.replace))
            }
            .buttonStyle(.pressScale)
            .accessibilityLabel("Repeat")
            .accessibilityValue(repeatValue)
            .accessibilityIdentifier("nowPlaying.repeat")
            .frame(maxWidth: .infinity)

            speedMenu
                .frame(maxWidth: .infinity)

            sleepTimerMenu
                .frame(maxWidth: .infinity)

            AirPlayButton(activeTint: artworkTint, inactiveTint: chromeInk(isOn: false))
                .frame(width: 44, height: 44)
                .accessibilityIdentifier("nowPlaying.airPlay")
                .frame(maxWidth: .infinity)

            Button { showQueue = true } label: {
                Image(systemName: "list.bullet")
                    .capsuleGlyph(chromeInk(isOn: false))
            }
            .buttonStyle(.pressScale)
            .disabled(state.queue.isEmpty)
            .accessibilityLabel("Queue")
            .accessibilityIdentifier("nowPlaying.queue")
            .playerSheet(isPresented: $showQueue, tier: tier) {
                NowPlayingQueueList(queue: state.queue, isPlaying: state.isPlaying, actions: actions, notice: notice)
                    // A presentation sits outside this screen's environment: hand the player's tint on.
                    .environment(\.artworkTint, artworkTint)
                    .environment(\.artworkTintInk, artworkTintInk)
                    .environment(\.isArtworkTinted, isArtworkTinted)
                    .tint(artworkTint)
            }
            .frame(maxWidth: .infinity)
        }
        .padding(.horizontal, Spacing.small)
        .padding(.vertical, Spacing.xsmall)
        .modifier(GlassCapsule())
        // Six 44 pt targets fit the narrowest phone; past the first accessibility size the glyphs would outgrow them.
        .dynamicTypeSize(...DynamicTypeSize.accessibility1)
    }

    private var favouriteButton: some View {
        Button(action: actions.toggleFavourite) {
            Image(systemName: state.isFavourite ? "heart.fill" : "heart")
                .font(.title3)
                .foregroundStyle(state.isFavourite ? chromeInk(isOn: true) : secondaryInk)
                .contentTransition(.symbolEffect(.replace))
                .frame(width: 44, height: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(.pressScale)
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
                .font(.title3)
                .foregroundStyle(secondaryInk)
                .frame(width: 44, height: 44)
                .contentShape(Rectangle())
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
                .capsuleGlyph(chromeInk(isOn: state.playbackSpeed != 1))
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
                .capsuleGlyph(chromeInk(isOn: state.sleepTimerActive))
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

    // MARK: - Swipe down to dismiss

    /// Only the full-screen cover: the form sheet has the system's own.
    private var dismissesByDragging: Bool {
        NowPlayingPresentationStyle.resolve(for: tier) == .fullScreenCover
    }

    private var dismissDrag: some Gesture {
        DragGesture()
            .onChanged { value in
                guard !isScrubbing else { return }
                // Decided by the first movement and held, so a pull that wanders sideways later still dismisses.
                if dragOffset == 0, abs(value.translation.width) > abs(value.translation.height) {
                    dragIsSideways = true
                }
                guard !dragIsSideways else { return }
                let translation = max(value.translation.height, 0)
                isDragging = translation > 0
                dragOffset = translation
            }
            .onEnded { value in
                defer { dragIsSideways = false }
                guard !dragIsSideways, !isScrubbing else { return }
                let translation = max(value.translation.height, 0)
                if translation > Self.dismissDistance || value.predictedEndTranslation.height > Self.dismissPredictedDistance {
                    onClose()
                } else {
                    withAnimation(Motion.coverScale.reduced(reduceMotion)) {
                        dragOffset = 0
                        isDragging = false
                    }
                }
            }
    }
}

private extension Image {
    /// A glyph in the bottom capsule, in a 44 pt target.
    func capsuleGlyph(_ ink: Color) -> some View {
        font(.title3)
            .foregroundStyle(ink)
            .frame(width: 44, height: 44)
            .contentShape(Rectangle())
    }
}

/// The bottom controls' ground: a Liquid Glass capsule on iOS 26, `.ultraThinMaterial` below.
private struct GlassCapsule: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 26, *) {
            content.glassEffect(.regular, in: Capsule())
        } else {
            content.background(.ultraThinMaterial, in: Capsule())
        }
    }
}

// MARK: - Scrubber

/// The position scrubber: a capsule track filled in the tint to the position, 6 pt at rest and 10 pt while dragged,
/// with the elapsed and remaining times under it. A drag anywhere on it holds the dragged position locally and seeks
/// once, on release. VoiceOver reads it as one adjustable element ("1:30 of 6:26"), each swipe seeking
/// `accessibilityStepMs`.
struct NowPlayingScrubber: View {
    let positionMs: Int
    let durationMs: Int
    /// True while a finger is on the track: Now Playing's swipe-down dismiss stands aside.
    var isScrubbing: Binding<Bool> = .constant(false)
    let onSeek: (Int) -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.colorSchemeContrast) private var colorSchemeContrast
    @State private var dragging = false
    @State private var scrubMs: Double = 0

    /// The track's height at rest and while dragged.
    static let trackHeight: CGFloat = 6
    static let draggingTrackHeight: CGFloat = 10

    init(positionMs: Int, durationMs: Int, isScrubbing: Binding<Bool> = .constant(false), onSeek: @escaping (Int) -> Void) {
        self.positionMs = positionMs
        self.durationMs = durationMs
        self.isScrubbing = isScrubbing
        self.onSeek = onSeek
    }

    var body: some View {
        let current = dragging ? Int(scrubMs) : positionMs
        let fraction = durationMs > 0 ? min(1, max(0, Double(current) / Double(durationMs))) : 0
        VStack(spacing: Spacing.xsmall) {
            GeometryReader { proxy in
                let width = proxy.size.width
                ZStack(alignment: .leading) {
                    Capsule()
                        .fill(Color.primary.opacity(0.15))
                    Capsule()
                        .fill(.tint)
                        .frame(width: width * fraction)
                }
                .frame(height: dragging ? Self.draggingTrackHeight : Self.trackHeight)
                .frame(maxHeight: .infinity)
                .contentShape(Rectangle())
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { value in
                            if !dragging {
                                dragging = true
                                isScrubbing.wrappedValue = true
                            }
                            let x = min(max(value.location.x, 0), width)
                            scrubMs = width > 0 ? Double(x / width) * Double(durationMs) : 0
                        }
                        .onEnded { _ in
                            onSeek(Int(scrubMs))
                            dragging = false
                            isScrubbing.wrappedValue = false
                        }
                )
            }
            .frame(height: 44 / 2 + Self.draggingTrackHeight)
            .animation(Motion.press.reduced(reduceMotion), value: dragging)
            .accessibilityElement()
            .accessibilityLabel("Playback position")
            .accessibilityValue("\(Self.formatted(ms: current)) of \(Self.formatted(ms: durationMs))")
            .accessibilityAdjustableAction { direction in
                if let target = Self.adjusted(positionMs: positionMs, durationMs: durationMs, direction) {
                    onSeek(target)
                }
            }
            .accessibilityIdentifier("nowPlaying.scrubber")

            HStack {
                Text(Self.formatted(ms: current))
                Spacer()
                Text("-" + Self.formatted(ms: max(0, durationMs - current)))
            }
            .font(.s2Time)
            .foregroundStyle(colorSchemeContrast == .increased ? .primary : TintedChromeInk.secondaryInk(isDarkScheme: colorScheme == .dark))
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

// MARK: - Transport

/// Previous, play/pause and next: a 72 pt tint-filled play circle whose glyph swaps with a replace transition, 34 pt
/// skip glyphs, all scaling with Dynamic Type up to a cap and pressing down on touch; targets never under 44 pt.
struct NowPlayingTransport: View {
    let isPlaying: Bool
    let actions: PlayerActions

    @Environment(\.artworkTint) private var tint
    @Environment(\.artworkTintInk) private var tintInk
    @ScaledMetric(relativeTo: .largeTitle) private var playDiameter: CGFloat = 72
    @ScaledMetric(relativeTo: .title) private var skipSize: CGFloat = 34
    @State private var playTrigger = false
    @State private var skipTrigger = false

    static let maxPlayDiameter: CGFloat = 88
    static let maxSkipSize: CGFloat = 44

    var body: some View {
        let diameter = min(playDiameter, Self.maxPlayDiameter)
        let skip = min(skipSize, Self.maxSkipSize)
        HStack(spacing: Spacing.xlarge) {
            skipButton("backward.fill", size: skip, label: "Previous", action: actions.previous)

            Button {
                actions.playPause()
                playTrigger.toggle()
            } label: {
                Image(systemName: isPlaying ? "pause.fill" : "play.fill")
                    .font(.system(size: diameter * 0.4, weight: .semibold))
                    .foregroundStyle(tintInk)
                    .contentTransition(.symbolEffect(.replace))
                    .frame(width: diameter, height: diameter)
                    .background(Circle().fill(tint))
                    .contentShape(Circle())
            }
            .buttonStyle(.pressScale)
            .accessibilityLabel(isPlaying ? "Pause" : "Play")
            .accessibilityIdentifier("nowPlaying.playPause")

            skipButton("forward.fill", size: skip, label: "Next", action: actions.next)
        }
        .sensoryFeedback(.impact(weight: .medium), trigger: playTrigger)
        .sensoryFeedback(.impact(weight: .light), trigger: skipTrigger)
    }

    private func skipButton(_ systemImage: String, size: CGFloat, label: String, action: @escaping () -> Void) -> some View {
        Button {
            action()
            skipTrigger.toggle()
        } label: {
            Image(systemName: systemImage)
                .font(.system(size: size))
                .foregroundStyle(.primary)
                .frame(minWidth: 44, minHeight: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(.pressScale)
        .accessibilityLabel(label)
    }
}

// MARK: - Queue

/// The queue, from Now Playing's queue button, in the player's tint: the playing song as a card pinned at the top
/// with "Up Next" under it (a plain list's section header stays put while the rest scrolls), the songs after it, and
/// the songs already played below those. Tap a row to skip to it. Edit mode (the Edit/Done button) reorders Up
/// Next by dragging; a swipe, or Remove from Queue in a row's context menu, takes an item out (with Undo); Play Next
/// moves it after the current song; Clear empties the queue (with Undo). Moves and removals show at once and the
/// player's queue replaces them when it catches up. Rows are keyed by the queue item's uid, never its position.
struct NowPlayingQueueList: View {
    let queue: [NowPlayingQueueRow]
    var isPlaying = false
    var actions: PlayerActions = .none
    var notice: Binding<PlayerNotice?> = .constant(nil)

    @State private var rows: [NowPlayingQueueRow]
    @State private var editMode: EditMode = .inactive
    @Environment(\.artworkTint) private var tint

    init(
        queue: [NowPlayingQueueRow],
        isPlaying: Bool = false,
        actions: PlayerActions = .none,
        notice: Binding<PlayerNotice?> = .constant(nil)
    ) {
        self.queue = queue
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
            Section {
                if upNext.isEmpty {
                    Text("Nothing up next")
                        .font(.subheadline)
                        .foregroundStyle(.s2SecondaryText)
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
                VStack(alignment: .leading, spacing: Spacing.smallMedium) {
                    if let currentIndex {
                        nowPlayingCard(rows[currentIndex])
                    }
                    SectionHeader("Up Next")
                }
                .textCase(nil)
                .padding(.vertical, Spacing.small)
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
                    SectionHeader("Played")
                        .textCase(nil)
                        .padding(.vertical, Spacing.small)
                }
            }
        }
        .listStyle(.plain)
    }

    /// The playing song, pinned above Up Next on a wash of the tint, its cover carrying the playing indicator.
    private func nowPlayingCard(_ item: NowPlayingQueueRow) -> some View {
        Button { actions.selectQueueItem(item.id) } label: {
            VStack(alignment: .leading, spacing: Spacing.small) {
                Text("Now Playing")
                    .font(.s2Eyebrow)
                    .foregroundStyle(.s2SecondaryText)
                    .accessibilityHidden(true)
                MediaRow(
                    item.title,
                    subtitle: item.artist,
                    artwork: item.artwork,
                    artworkSize: ArtworkSize.albumRow,
                    playback: isPlaying ? .playing : .paused
                )
            }
            .padding(Spacing.smallMedium)
            .background(tint.opacity(0.12), in: RoundedRectangle(cornerRadius: ArtworkCorner.tile, style: .continuous))
            .contentShape(RoundedRectangle(cornerRadius: ArtworkCorner.tile, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(item.title), now playing")
        .accessibilityAddTraits(.isButton)
        .accessibilityIdentifier("queue.nowPlaying")
        .contextMenu {
            NowPlayingQueueRowMenu(item: item, playNext: actions.playNext, remove: remove)
        }
    }

    private func row(_ item: NowPlayingQueueRow) -> some View {
        Button { actions.selectQueueItem(item.id) } label: {
            MediaRow(item.title, subtitle: item.artist, artwork: item.artwork)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(item.title)
        .contextMenu {
            NowPlayingQueueRowMenu(item: item, playNext: actions.playNext, remove: remove)
        }
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
            .init(id: 0, title: "Airbag", artist: "Radiohead", isCurrent: false),
            .init(id: 1, title: "Paranoid Android", artist: "Radiohead", isCurrent: true),
            .init(id: 2, title: "Hyperballad", artist: "Björk", isCurrent: false),
        ],
        isPlaying: true
    )
}

#Preview("Empty queue") {
    NowPlayingQueueList(queue: [])
}
