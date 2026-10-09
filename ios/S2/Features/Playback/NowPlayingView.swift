import Shared
import SwiftUI

/// Now Playing, after Shuttle Podcasts' player and Apple Music (#624, #644): the cover over a ground in its own colour
/// with a blurred copy of it glowing through the top (`ArtworkBackground`), close and the favourite heart along the
/// top, the title with the artist and the album under it in `MarqueeText` (tapping either opens its screen; a long
/// press of the cover or the title opens the song's menu), a capsule scrubber, previous / play-pause / next in the
/// cover's tint between shuffle and repeat, and Audio (speed, then the equalizer and playback settings), the sleep timer, AirPlay and the
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
        NowPlayingContent(state: binding.nowPlaying, actions: actions, notice: $notice, onClose: { dismiss() })
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

    /// The binding's actions, with screens opened from inside Now Playing closing it first.
    private var actions: PlayerActions {
        var actions = binding.actions
        actions.openRoute = { route in
            dismiss()
            onOpen(route)
        }
        return actions
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
    @State private var showSleepTimer = false
    /// VoiceOver's Add to Playlist action asks where in a dialog; the context menu has its own submenu.
    @State private var showPlaylistChoices = false
    @State private var showNewPlaylist = false
    @State private var songInfo: SongInfoTarget?
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

    /// Close on the leading edge and the favourite heart and More menu on the trailing one, each on a material disc: the backdrop's
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
                HStack(spacing: Spacing.small) {
                    favouriteButton
                    moreButton
                }
            }
        }
        .padding(.horizontal, Spacing.small)
        // Capped like the transport: past the first accessibility size the discs crowd the bar (`TopBarGlyph`).
        .dynamicTypeSize(...DynamicTypeSize.accessibility1)
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
                NowPlayingTransport(isPlaying: state.isPlaying, isLoading: state.isLoading, actions: actions)
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
            if let badge = state.qualityBadge {
                Text(badge)
                    .font(.caption.weight(.medium))
                    .foregroundStyle(secondaryInk)
                    .padding(.top, Spacing.tiny)
                    .accessibilityLabel("Audio quality, \(state.spokenQualityBadge ?? badge)")
                    .accessibilityIdentifier("nowPlaying.quality")
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
        // At the accessibility sizes the line wraps to two lines instead of scrolling; a name longer than that is
        // truncated at the end of the second line (VoiceOver still reads it whole).
        let line = Group {
            if dynamicTypeSize.isAccessibilitySize {
                Text(text)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
                    .frame(maxWidth: .infinity, alignment: .leading)
            } else {
                MarqueeText(text)
            }
        }
        .font(font)
        .foregroundStyle(secondaryInk)
        if state.songActions.contains(action) {
            Button { perform(action) } label: {
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

    /// Song Info is this view's sheet; every other action goes to the ViewModel.
    private func perform(_ action: NowPlayingSongAction) {
        if action == .songInfo {
            songInfo = state.songID.map { SongInfoTarget(songID: $0) }
        } else {
            actions.songAction(action)
        }
    }

    /// The song's menu: the top bar's More button, and a long press of the cover or the title.
    private var songMenu: some View {
        NowPlayingSongMenu(
            songActions: state.songActions,
            playlists: state.playlists,
            onAction: perform,
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
                Button(action.title) { perform(action) }
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

    /// Audio (speed, then the equalizer and playback settings), the sleep timer, AirPlay and the queue in one capsule: Liquid Glass on
    /// iOS 26, `.ultraThinMaterial` below. Neutral, each glyph taking the tint only while its mode is on.
    private var bottomCapsule: some View {
        HStack(spacing: 0) {
            audioButton
                .frame(maxWidth: .infinity)

            sleepTimerButton
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
            .playerSheet(isPresented: $showQueue, tier: tier, keepsPlayerTappable: true) {
                NowPlayingQueueList(
                    queue: state.queue,
                    source: state.queueSource?.item,
                    isPlaying: state.isPlaying,
                    actions: actions,
                    notice: notice
                )
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

    /// The song's menu behind a visible ellipsis; the long press on the cover or title stays as a shortcut.
    private var moreButton: some View {
        Menu {
            songMenu
        } label: {
            Image(systemName: "ellipsis")
                .topBarGlyph(.primary)
        }
        .accessibilityLabel("More")
        .accessibilityIdentifier("nowPlaying.more")
        // Song Info opens from the menu, so the iPad popover anchors to this button.
        .playerSheet(item: $songInfo, tier: tier) { target in
            SongInfoSheet(songID: target.songID)
        }
    }

    /// Opens the Audio sheet: the playback speed, and the Equalizer & Playback Settings screen it pushes. Tinted while
    /// the speed isn't normal.
    private var audioButton: some View {
        Button { showAudio = true } label: {
            Image(systemName: "slider.vertical.3")
                .capsuleGlyph(chromeInk(isOn: state.playbackSpeed != 1))
        }
        .buttonStyle(.pressScale)
        .accessibilityLabel("Audio")
        .accessibilityValue("Speed \(NowPlayingAudioSheet.format(state.playbackSpeed))")
        .accessibilityIdentifier("nowPlaying.audio")
        .playerSheet(isPresented: $showAudio, tier: tier) {
            NowPlayingAudioSheet(speed: state.playbackSpeed, setSpeed: actions.setSpeed)
                .playerTinted(artworkTint, ink: artworkTintInk, isTinted: isArtworkTinted)
        }
    }

    /// Opens the sleep timer sheet. Tinted while the timer is running.
    private var sleepTimerButton: some View {
        Button { showSleepTimer = true } label: {
            Image(systemName: state.sleepTimerActive ? "moon.zzz.fill" : "moon.zzz")
                .capsuleGlyph(chromeInk(isOn: state.sleepTimerActive))
                // The button is the one element; the symbol's own label ("Snooze") would surface beside it (#650).
                .accessibilityHidden(true)
        }
        .buttonStyle(.pressScale)
        .accessibilityLabel("Sleep Timer")
        .accessibilityValue(state.sleepTimerActive ? "On" : "Off")
        .accessibilityIdentifier("nowPlaying.sleepTimer")
        .playerSheet(isPresented: $showSleepTimer, tier: tier) {
            NowPlayingSleepTimerSheet(
                isActive: state.sleepTimerActive,
                playToEnd: state.sleepTimerPlayToEnd,
                startTimer: actions.startSleepTimer,
                stopTimer: actions.stopSleepTimer,
                remaining: actions.sleepTimerRemaining
            )
            .playerTinted(artworkTint, ink: artworkTintInk, isTinted: isArtworkTinted)
        }
    }

    // MARK: - Swipe down to dismiss

    /// Only the full-screen cover (the form sheet has the system's own), and not behind a player sheet, whose
    /// exposed upper half would otherwise close the whole player (#684).
    private var dismissesByDragging: Bool {
        NowPlayingPresentationStyle.resolve(for: tier) == .fullScreenCover && !(showQueue || showAudio || showSleepTimer || songInfo != nil)
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

    /// A glyph in the top bar (close, favourite, more): on a material disc in a target at least 44 pt.
    func topBarGlyph(_ ink: Color) -> some View {
        modifier(TopBarGlyph(ink: ink))
    }

    /// Hands the player's tint on to a sheet it presents, which sits outside the screen's environment.
    func playerTinted(_ tint: Color, ink: Color, isTinted: Bool) -> some View {
        environment(\.artworkTint, tint)
            .environment(\.artworkTintInk, ink)
            .environment(\.isArtworkTinted, isTinted)
            .tint(tint)
    }
}

/// `topBarGlyph`: the disc scales with the text size, so a larger glyph never overflows it, and the touch target
/// grows with it, so the bar lays the discs out at the size they draw rather than overlapping. The top bar caps the
/// text size at `accessibility1`, which bounds both.
private struct TopBarGlyph: ViewModifier {
    let ink: Color

    @ScaledMetric(relativeTo: .body) private var disc = TouchTarget.disc

    func body(content: Content) -> some View {
        content
            .font(.body.weight(.semibold))
            .foregroundStyle(ink)
            .frame(width: disc, height: disc)
            .glassSurface(in: Circle(), fallback: .disc)
            .touchTarget(disc)
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
