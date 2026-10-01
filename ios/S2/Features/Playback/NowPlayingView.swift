import SwiftUI

/// Now Playing, after Shuttle Podcasts' player and Apple Music (#624, #644): the cover over a ground in its own colour
/// with a blurred copy of it glowing through the top (`ArtworkBackground`), close and the favourite heart along the
/// top, the title with the artist and the album under it in `MarqueeText` (tapping either opens its screen; a long
/// press of the cover or the title opens the song's menu), a capsule scrubber, previous / play-pause / next in the
/// cover's tint between shuffle and repeat, and Audio (speed and the equalizer), the sleep timer, AirPlay and the
/// queue in one glass capsule along the bottom. The cover's colour (`\.artworkTintSource`) comes from above
/// (`playerArtworkTint`, in `ContentView`); the screen derives
/// its ground, tint and captions from it (`PlayerPalette`), so they are measured on the ground they sit on. Presented by `nowPlayingPresentation` (a full-screen cover in
/// `compact`, which a swipe down dismisses, a form sheet otherwise); the queue and Audio open through `playerSheet` (a sheet
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
    @Environment(\.artworkTintSource) private var artworkTintSource
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.colorSchemeContrast) private var colorSchemeContrast
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    @State private var showQueue = false
    @State private var showAudio = false
    /// VoiceOver's Add to Playlist action asks where in a dialog; the context menu has its own submenu.
    @State private var showPlaylistChoices = false
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
        let palette = self.palette
        VStack(spacing: 0) {
            topBar
            if state.title == nil {
                EmptyState("Nothing Playing", systemImage: "play.circle", message: "Play a song from your library.")
                    .frame(maxHeight: .infinity)
            } else {
                player
            }
        }
        // The player's own tint for everything inside: measured on its ground, not the scheme's.
        .environment(\.artworkTint, ContrastSafeTint.color(palette.tint))
        .environment(\.artworkTintInk, ContrastSafeTint.color(palette.onTint))
        .background {
            // In a clear overlay so the backdrop's fill-scaled image can't widen the layout (Podcasts' fix).
            Color.clear
                .overlay {
                    ArtworkBackground(source: state.artwork, palette: palette, layout: isTwoColumn ? .sideBySide : .stacked)
                }
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
        .tint(playerTint)
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

    /// Close on the leading edge and the favourite heart on the trailing one, each on a material disc: the backdrop's
    /// top edge can be as dark as the cover, whatever the scheme.
    private var topBar: some View {
        HStack {
            Button(action: onClose) {
                Image(systemName: "chevron.down")
                    .topBarGlyph(.primary)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Close")
            .accessibilityIdentifier("nowPlaying.close")
            Spacer()
            if state.title != nil {
                favouriteButton
            }
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
            titleBlock
            NowPlayingScrubber(
                positionMs: state.positionMs, durationMs: state.durationMs, isScrubbing: $isScrubbing, timeInk: secondaryInk,
                onSeek: actions.seek
            )
            HStack(spacing: 0) {
                shuffleButton
                    .frame(maxWidth: .infinity)
                NowPlayingTransport(isPlaying: state.isPlaying, actions: actions)
                repeatButton
                    .frame(maxWidth: .infinity)
            }
            bottomCapsule
        }
    }

    /// The cover: square, as large as the space left allows up to `ArtworkSize.playerMaximum`, easing back to
    /// `Motion.pausedCoverScale` while paused. A long press opens the song's menu.
    private var cover: some View {
        Group {
            if let source = state.artwork {
                RemoteArtwork(source, points: ArtworkSize.playerMaximum)
            } else {
                ArtworkPlaceholder()
            }
        }
        .aspectRatio(1, contentMode: .fit)
        .artworkStyle(.artworkPlayer)
        .contextMenu { songMenu }
        .artworkShadow(.player)
        .nowPlayingMatchedGeometry(id: NowPlayingCover.matchedGeometryID)
        .scaleEffect(state.isPlaying ? 1 : Motion.pausedCoverScale)
        .animation(Motion.coverScale.reduced(reduceMotion), value: state.isPlaying)
        .frame(maxWidth: ArtworkSize.playerMaximum, maxHeight: ArtworkSize.playerMaximum)
        .accessibilityHidden(true)
    }

    /// The title, then the artist and the album on lines of their own: tapping either opens its screen, when the
    /// ViewModel offers that for the song. A long press opens the song's menu; VoiceOver has the same actions on the
    /// title.
    private var titleBlock: some View {
        VStack(alignment: .leading, spacing: Spacing.tiny) {
            MarqueeText(state.title ?? "")
                .font(.s2HeroTitle)
                .foregroundStyle(.primary)
                .accessibilityAddTraits(.isHeader)
                .accessibilityActions { songAccessibilityActions }
                .accessibilityIdentifier("nowPlaying.title")
            if let artist = state.artist {
                detailLine(artist, font: .title3, opens: .goToArtist, id: "nowPlaying.artist")
            }
            if let album = state.album {
                detailLine(album, font: .body, opens: .goToAlbum, id: "nowPlaying.album")
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .contentShape(Rectangle())
        .contextMenu { songMenu }
        .confirmationDialog("Add to Playlist", isPresented: $showPlaylistChoices, titleVisibility: .visible) {
            NowPlayingPlaylistChoices(
                playlists: state.playlists, onNewPlaylist: { showNewPlaylist = true }, onChoose: actions.addToPlaylist
            )
        }
    }

    /// An artist or album line: a button to its screen when the song offers `action`, plain text otherwise.
    @ViewBuilder
    private func detailLine(_ text: String, font: Font, opens action: NowPlayingSongAction, id: String) -> some View {
        let line = MarqueeText(text)
            .font(font)
            .foregroundStyle(secondaryInk)
        if state.songActions.contains(action) {
            Button { actions.songAction(action) } label: {
                line.contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(text)
            .accessibilityHint(action == .goToArtist ? "Opens the artist" : "Opens the album")
            .accessibilityIdentifier(id)
        } else {
            line
                .accessibilityIdentifier(id)
        }
    }

    /// The song's menu, on a long press of the cover or the title.
    private var songMenu: some View {
        NowPlayingSongMenu(
            songActions: state.songActions,
            playlists: state.playlists,
            onAction: actions.songAction,
            onNewPlaylist: { showNewPlaylist = true },
            onAddToPlaylist: actions.addToPlaylist
        )
    }

    /// The song's menu as VoiceOver actions on the title; Add to Playlist asks where.
    @ViewBuilder
    private var songAccessibilityActions: some View {
        ForEach(state.songActions, id: \.self) { action in
            if action == .addToPlaylist {
                Button(action.title) { showPlaylistChoices = true }
            } else {
                Button(action.title) { actions.songAction(action) }
            }
        }
    }

    // MARK: - Ink

    /// The ground, tint and captions for this cover and scheme.
    private var palette: PlayerPalette {
        .resolve(extracted: artworkTintSource, isDarkScheme: colorScheme == .dark)
    }

    private var playerTint: Color { ContrastSafeTint.color(palette.tint) }

    /// Captions on the backdrop: the label faded toward the ground as far as AA allows (`PlayerPalette`); plain
    /// `.primary` under Increase Contrast.
    private var secondaryInk: Color {
        colorSchemeContrast == .increased ? .primary : ContrastSafeTint.color(palette.secondaryInk)
    }

    /// A control's glyph: the tint while it has something to report (a mode on), the caption ink otherwise.
    private func chromeInk(isOn: Bool) -> Color {
        if colorSchemeContrast == .increased { return .primary }
        return isOn ? playerTint : secondaryInk
    }

    // MARK: - Shuffle and repeat

    /// How strongly a mode that's on washes its button's disc with the tint.
    static let modeWashOpacity = 0.18

    /// The repeat glyph for each mode: `repeat.1` for one, `repeat` otherwise (all is told from off by its tint and disc).
    static func repeatSymbol(_ mode: NowPlayingRepeat) -> String {
        mode == .one ? "repeat.1" : "repeat"
    }

    private var shuffleButton: some View {
        modeButton("shuffle", isOn: state.shuffleOn, action: actions.toggleShuffle)
            .accessibilityLabel("Shuffle")
            .accessibilityValue(state.shuffleOn ? "On" : "Off")
            .accessibilityIdentifier("nowPlaying.shuffle")
    }

    private var repeatButton: some View {
        modeButton(Self.repeatSymbol(state.repeatMode), isOn: state.repeatMode != .off, action: actions.toggleRepeat)
            .accessibilityLabel("Repeat")
            .accessibilityValue(repeatValue)
            .accessibilityIdentifier("nowPlaying.repeat")
    }

    /// Shuffle or repeat either side of the transport: while on, the glyph takes the tint on a disc washed with it, so
    /// the mode reads without colour too (Increase Contrast inks both states alike).
    private func modeButton(_ systemImage: String, isOn: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.title3.weight(NowPlayingTransport.weight))
                .foregroundStyle(chromeInk(isOn: isOn))
                .contentTransition(.symbolEffect(.replace))
                .frame(width: TouchTarget.minimum, height: TouchTarget.minimum)
                .background {
                    Circle()
                        .fill(playerTint.opacity(isOn ? Self.modeWashOpacity : 0))
                }
                .contentShape(Circle())
                .animation(Motion.press.reduced(reduceMotion), value: isOn)
        }
        .buttonStyle(.pressScale)
        // Capped with the capsule: past the first accessibility size the glyph would outgrow its disc.
        .dynamicTypeSize(...DynamicTypeSize.accessibility1)
    }

    private var repeatValue: String {
        switch state.repeatMode {
        case .off: "Off"
        case .all: "All"
        case .one: "One"
        }
    }

    // MARK: - Bottom capsule

    /// Audio (speed and the equalizer), the sleep timer, AirPlay and the queue in one capsule: Liquid Glass on
    /// iOS 26, `.ultraThinMaterial` below. Neutral, each glyph taking the tint only while its mode is on.
    private var bottomCapsule: some View {
        HStack(spacing: 0) {
            audioButton
                .frame(maxWidth: .infinity)

            sleepTimerMenu
                .frame(maxWidth: .infinity)

            AirPlayButton(activeTint: playerTint, inactiveTint: chromeInk(isOn: false))
                .touchTarget()
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
                    .playerTinted(artworkTint, ink: artworkTintInk, isTinted: isArtworkTinted)
            }
            .frame(maxWidth: .infinity)
        }
        .padding(.horizontal, Spacing.small)
        .padding(.vertical, Spacing.xsmall)
        .glassSurface(in: S2Shape.capsule)
        // A container, so its own id doesn't override its controls'.
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("nowPlaying.bottomBar")
        // Past the first accessibility size the glyphs would outgrow their 44 pt targets.
        .dynamicTypeSize(...DynamicTypeSize.accessibility1)
    }

    private var favouriteButton: some View {
        Button(action: actions.toggleFavourite) {
            Image(systemName: state.isFavourite ? "heart.fill" : "heart")
                .contentTransition(.symbolEffect(.replace))
                .topBarGlyph(state.isFavourite ? chromeInk(isOn: true) : .primary)
        }
        .buttonStyle(.pressScale)
        .accessibilityLabel("Favorite")
        .accessibilityValue(state.isFavourite ? "On" : "Off")
        .accessibilityIdentifier("nowPlaying.favourite")
    }

    /// Opens the Audio sheet: the playback speed and the equalizer. Tinted while the speed isn't normal.
    private var audioButton: some View {
        Button { showAudio = true } label: {
            Image(systemName: "slider.vertical.3")
                .capsuleGlyph(chromeInk(isOn: state.playbackSpeed != 1))
        }
        .buttonStyle(.pressScale)
        .accessibilityLabel("Audio")
        .accessibilityValue("Speed \(Self.speedText(state.playbackSpeed))")
        .accessibilityIdentifier("nowPlaying.audio")
        .playerSheet(isPresented: $showAudio, tier: tier) {
            NowPlayingAudioSheet(speed: state.playbackSpeed, setSpeed: actions.setSpeed)
                .playerTinted(artworkTint, ink: artworkTintInk, isTinted: isArtworkTinted)
        }
    }

    /// The playback speeds offered, 1 being normal.
    static let speeds: [Float] = [0.5, 0.75, 1, 1.25, 1.5, 2]

    /// The sleep timer's durations, in minutes.
    static let sleepTimerMinutes = [15, 30, 45, 60]

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
                // The menu is the one element; the symbol's own label ("Snooze") would surface beside it (#650).
                .accessibilityHidden(true)
        }
        .accessibilityLabel("Sleep Timer")
        .accessibilityValue(state.sleepTimerActive ? "On" : "Off")
        .accessibilityIdentifier("nowPlaying.sleepTimer")
    }

    static func speedText(_ speed: Float) -> String {
        speed.formatted(.number.precision(.fractionLength(0...2))) + "×"
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

private extension View {
    /// A glyph in the bottom capsule, in a 44 pt target.
    func capsuleGlyph(_ ink: Color) -> some View {
        font(.title3)
            .foregroundStyle(ink)
            .touchTarget()
    }

    /// A glyph in the top bar (close, favourite): on a material disc in a 44 pt target.
    func topBarGlyph(_ ink: Color) -> some View {
        font(.body.weight(.semibold))
            .foregroundStyle(ink)
            .frame(width: TouchTarget.disc, height: TouchTarget.disc)
            .glassSurface(in: Circle(), fallback: .disc)
            .touchTarget()
    }

    /// Hands the player's tint on to a sheet it presents, which sits outside the screen's environment.
    func playerTinted(_ tint: Color, ink: Color, isTinted: Bool) -> some View {
        environment(\.artworkTint, tint)
            .environment(\.artworkTintInk, ink)
            .environment(\.isArtworkTinted, isTinted)
            .tint(tint)
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
    /// The elapsed and remaining times' colour: the player's caption ink.
    var timeInk: Color = .secondary
    let onSeek: (Int) -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var dragging = false
    @State private var scrubMs: Double = 0

    /// The track's height at rest and while dragged.
    static let trackHeight: CGFloat = 6
    static let draggingTrackHeight: CGFloat = 10

    init(
        positionMs: Int,
        durationMs: Int,
        isScrubbing: Binding<Bool> = .constant(false),
        timeInk: Color = .secondary,
        onSeek: @escaping (Int) -> Void
    ) {
        self.positionMs = positionMs
        self.durationMs = durationMs
        self.isScrubbing = isScrubbing
        self.timeInk = timeInk
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
            .frame(height: TouchTarget.minimum / 2 + Self.draggingTrackHeight)
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
            .foregroundStyle(timeInk)
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

/// Previous, play/pause and next as one family: a 72 pt tint-filled play circle whose glyph swaps with a replace
/// transition, and previous and next as glyphs of the same weight in the same tint, each in a 60 pt target. All
/// scale with Dynamic Type up to a cap and press down on touch.
struct NowPlayingTransport: View {
    let isPlaying: Bool
    let actions: PlayerActions

    @Environment(\.artworkTint) private var tint
    @Environment(\.artworkTintInk) private var tintInk
    @ScaledMetric(relativeTo: .largeTitle) private var playDiameter: CGFloat = 72
    @ScaledMetric(relativeTo: .title) private var skipSize: CGFloat = 30
    @State private var playTrigger = false
    @State private var skipTrigger = false

    static let maxPlayDiameter: CGFloat = 88
    static let maxSkipSize: CGFloat = 38
    /// The play glyph's size as a share of the circle, and the transport's weight.
    static let playGlyphRatio: CGFloat = 0.4
    static let weight: Font.Weight = .semibold
    /// The skip buttons' target: larger than the 44 pt minimum, as the screen's primary controls.
    static let skipTarget: CGFloat = 60

    var body: some View {
        let diameter = min(playDiameter, Self.maxPlayDiameter)
        let skip = min(skipSize, Self.maxSkipSize)
        HStack(spacing: Spacing.large) {
            skipButton("backward.fill", size: skip, label: "Previous", action: actions.previous)

            Button {
                actions.playPause()
                playTrigger.toggle()
            } label: {
                Image(systemName: isPlaying ? "pause.fill" : "play.fill")
                    .font(.s2ScaledGlyph(diameter * Self.playGlyphRatio, weight: Self.weight))
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
                .font(.s2ScaledGlyph(size, weight: Self.weight))
                .foregroundStyle(tint)
                .frame(minWidth: Self.skipTarget, minHeight: Self.skipTarget)
                .contentShape(Rectangle())
        }
        .buttonStyle(.pressScale)
        .accessibilityLabel(label)
    }
}

// MARK: - Queue

/// The queue, from Now Playing's queue button, in the player's tint: one plain list in three sections under the same
/// headers, as Apple Music lays out its queue: Now Playing (the playing song, its cover carrying the playing
/// indicator), Up Next (the songs after it), and Played (those before it). Every song is the same `MediaRow`. Tap a row
/// to skip to it. Edit mode (the Edit/Done button) reorders Up Next by dragging; a swipe, or Remove from Queue in a
/// row's context menu, takes an item out (with Undo); Play Next moves it after the current song; Clear empties the
/// queue (with Undo). Moves and removals show at once and the player's queue replaces them when it catches up. Rows
/// are keyed by the queue item's uid, never its position.
struct NowPlayingQueueList: View {
    let queue: [NowPlayingQueueRow]
    var isPlaying = false
    var actions: PlayerActions = .none
    var notice: Binding<PlayerNotice?> = .constant(nil)

    @State private var rows: [NowPlayingQueueRow]
    @State private var editMode: EditMode = .inactive

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
                sectionHeader("Up Next")
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
    }

    private func sectionHeader(_ title: String) -> some View {
        SectionHeader(title)
            .textCase(nil)
            .padding(.vertical, Spacing.xsmall)
            .pinnedHeader()
    }

    /// A queue row; the playing song's cover carries the playing indicator and VoiceOver says it's playing.
    private func row(_ item: NowPlayingQueueRow) -> some View {
        Button { actions.selectQueueItem(item.id) } label: {
            MediaRow(
                item.title,
                subtitle: item.artist,
                artwork: item.artwork,
                playback: item.isCurrent ? (isPlaying ? .playing : .paused) : .none
            )
        }
        .buttonStyle(.plain)
        .rowSeparator(.none)
        .accessibilityLabel(item.isCurrent ? "\(item.title), now playing" : item.title)
        .accessibilityIdentifier(item.isCurrent ? "queue.nowPlaying" : "queue.row")
        .contextMenu {
            NowPlayingQueueRowMenu(item: item, playNext: actions.playNext, remove: remove, exclude: actions.excludeQueueItem)
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

// MARK: - Song menu

/// The playing song's menu, on a long press of Now Playing's cover or title: the actions the ViewModel offers it that
/// iOS has a screen for, in its order, with Add to Playlist as a submenu and Exclude marked destructive.
struct NowPlayingSongMenu: View {
    let songActions: [NowPlayingSongAction]
    let playlists: [PlaylistOption]
    let onAction: (NowPlayingSongAction) -> Void
    let onNewPlaylist: () -> Void
    let onAddToPlaylist: (PlaylistChoice) -> Void

    var body: some View {
        ForEach(songActions, id: \.self) { action in
            switch action {
            case .addToPlaylist:
                Menu {
                    NowPlayingPlaylistChoices(playlists: playlists, onNewPlaylist: onNewPlaylist, onChoose: onAddToPlaylist)
                } label: {
                    Label(action.title, systemImage: action.systemImage)
                }
            case .exclude:
                Button(action.title, systemImage: action.systemImage, role: .destructive) { onAction(action) }
            default:
                Button(action.title, systemImage: action.systemImage) { onAction(action) }
            }
        }
    }
}

/// Where Add to Playlist can put the song: a new playlist, Favorites, or one of `playlists`.
struct NowPlayingPlaylistChoices: View {
    let playlists: [PlaylistOption]
    let onNewPlaylist: () -> Void
    let onChoose: (PlaylistChoice) -> Void

    var body: some View {
        Button("New Playlist…", systemImage: "plus", action: onNewPlaylist)
        Button("Favorites", systemImage: "heart") { onChoose(.favourites) }
        ForEach(playlists) { playlist in
            Button(playlist.name) { onChoose(.playlist(id: playlist.id)) }
        }
    }
}

// MARK: - Audio

/// The Audio sheet, from Now Playing's Audio button: the playback speed as a checked list, and the Equalizer, pushed
/// inside the sheet (the same `EqualizerView` Settings pushes). One grouped form, as iOS lays out settings.
struct NowPlayingAudioSheet: View {
    let speed: Float
    let setSpeed: (Float) -> Void

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section("Playback Speed") {
                    ForEach(NowPlayingContent.speeds, id: \.self) { option in
                        speedRow(option)
                    }
                }
                Section {
                    NavigationLink {
                        EqualizerView()
                    } label: {
                        Label("Equalizer", systemImage: "slider.vertical.3")
                    }
                    .accessibilityIdentifier("audio.equalizer")
                }
            }
            .navigationTitle("Audio")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }

    private func speedRow(_ option: Float) -> some View {
        let isSelected = option == speed
        let text = NowPlayingContent.speedText(option)
        return Button { setSpeed(option) } label: {
            HStack {
                // The label's own colour, not the tint a form gives a button's label: only the checkmark marks the choice.
                Text(option == 1 ? "\(text) (Normal)" : text)
                    .foregroundStyle(Color(uiColor: .label))
                Spacer()
                if isSelected {
                    Image(systemName: "checkmark")
                        .font(.body.weight(.semibold))
                        .foregroundStyle(.tint)
                }
            }
            .contentShape(Rectangle())
        }
        .accessibilityLabel(text)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
        .accessibilityIdentifier("audio.speed.\(text)")
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

#Preview("Audio") {
    NowPlayingAudioSheet(speed: 1.25) { _ in }
}

#Preview("Empty queue") {
    NowPlayingQueueList(queue: [])
}
