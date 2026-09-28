import Shared
import SwiftUI

/// The mini player: the song's cover, title and artist, play/pause ringed by the song's progress in the player's
/// artwork tint, and next; tapping opens Now Playing. Bound to the shared `PlayerViewModel` through `PlayerBinding`,
/// reading only its `miniPlayer` state (the progress ring reads the position in a view of its own).
///
/// Where it lives (#624, after Shuttle Podcasts' `MiniPlayerView`):
/// - iOS 26 on the phone's tab bar: the tab view's bottom accessory (`AppShell`), one bar in the Liquid Glass capsule
///   the system draws, laid out for its placement (`.expanded` above the tab bar, `.inline` beside the minimised one).
///   The per-screen insets then draw nothing (`\.miniPlayerInAccessory`).
/// - Otherwise (iOS 17-18, iOS 26.0, and the iPad sidebar): a floating rounded card inset at the bottom of every
///   screen (`miniPlayerInset`), `ArtworkCorner.tile` on `.thickMaterial` with a hairline edge and a soft shadow
///   (glass on iOS 26), inset `Spacing.small`.
///
/// Either way it shows only while there is a current song (`PlayerBinding.isMiniPlayerVisible`), sliding in and out
/// with `Motion.miniPlayerVisibility`; with nothing queued there is no bar and no inset.
struct MiniPlayerView: View {
    let binding: PlayerBinding
    let placement: MiniPlayerPlacement
    @Binding var showNowPlaying: Bool

    /// `binding` defaults to the app's single `PlayerBinding`, built once in `IosAppDependencies`: a view
    /// struct like this one is re-initialised on every parent body, so a fresh binding per init
    /// would restart its flows every time (`.claude/rules/ios.md`).
    init(
        showNowPlaying: Binding<Bool>,
        placement: MiniPlayerPlacement = .inset,
        binding: PlayerBinding = AppGraph.dependencies.playerBinding
    ) {
        self.binding = binding
        self.placement = placement
        self._showNowPlaying = showNowPlaying
    }

    var body: some View {
        let state = binding.miniPlayer
        let actions = binding.actions
        let binding = binding
        MiniPlayerBar(
            title: state.title ?? "",
            artist: state.artist,
            artwork: state.artwork,
            isPlaying: state.isPlaying,
            style: placement == .accessory ? .accessory : .floating,
            isCoverHidden: showNowPlaying,
            progress: { binding.progressFraction },
            onTap: { showNowPlaying = true },
            onPlayPause: actions.playPause,
            onNext: actions.next
        )
        .playerArtworkTint(binding)
    }
}

/// Where a `MiniPlayerView` is hosted.
enum MiniPlayerPlacement {
    /// A screen's bottom inset (`miniPlayerInset`).
    case inset
    /// The iOS 26 tab view bottom accessory.
    case accessory
}

extension EnvironmentValues {
    /// Set by `AppShell` when the tab view's bottom accessory hosts the mini player (iOS 26.1, tab bar), so the
    /// per-screen insets draw nothing.
    @Entry var miniPlayerInAccessory = false
}

extension PlayerBinding {
    /// How far through the current song the player is, 0...1: the mini player's progress ring.
    var progressFraction: Double {
        let duration = nowPlaying.durationMs
        guard duration > 0 else { return 0 }
        return min(1, max(0, Double(nowPlaying.positionMs) / Double(duration)))
    }
}

extension View {
    /// Provides the playing song's artwork tint (`\.artworkTint`) to the player surfaces: the mini player and Now
    /// Playing. Scoped to them rather than set above the whole shell, so a library placeholder isn't recoloured by
    /// whatever happens to be playing.
    func playerArtworkTint(_ binding: PlayerBinding = AppGraph.dependencies.playerBinding) -> some View {
        modifier(PlayerArtworkTintModifier(binding: binding))
    }
}

/// Reads only the current artwork, so a play/pause or progress tick doesn't re-run the extraction.
private struct PlayerArtworkTintModifier: ViewModifier {
    let binding: PlayerBinding

    func body(content: Content) -> some View {
        content.artworkTint(from: binding.miniPlayer.artwork)
    }
}

/// The mini player's content: plain values in, so it previews and tests (ViewInspector) without Kotlin.
struct MiniPlayerBar: View {
    /// Only drawn while a song is current (`PlayerBinding.isMiniPlayerVisible`), so there is always one.
    let title: String
    let artist: String?
    let artwork: ArtworkSource?
    let isPlaying: Bool
    var style: Style = .floating
    /// True while Now Playing is up: the cover hands its place to Now Playing's (matched geometry).
    var isCoverHidden = false
    /// The progress, 0...1, read inside the progress ring's own body so a tick redraws only the ring.
    var progress: () -> Double = { 0 }
    let onTap: () -> Void
    let onPlayPause: () -> Void
    let onNext: () -> Void

    enum Style {
        /// The floating rounded card (iOS 17-18, and the iPad sidebar).
        case floating
        /// Inside the iOS 26 tab view bottom accessory, whose capsule the system draws.
        case accessory
    }

    /// The accessory's capsule is about 48 pt tall; its cover sits inside it with room to spare.
    static let accessoryCover: CGFloat = 32
    /// The floating card's lift off the content.
    static let floatingShadow = ArtworkShadow(opacity: 0.18, radius: 18, y: 6)
    /// The progress ring's diameter and stroke, around the play/pause glyph inside its 44 pt target.
    static let progressRingDiameter: CGFloat = 36
    static let progressRingWidth: CGFloat = 2.5

    @Environment(\.artworkTint) private var tint
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    init(
        title: String,
        artist: String?,
        artwork: ArtworkSource?,
        isPlaying: Bool,
        style: Style = .floating,
        isCoverHidden: Bool = false,
        progress: @escaping () -> Double = { 0 },
        onTap: @escaping () -> Void,
        onPlayPause: @escaping () -> Void,
        onNext: @escaping () -> Void
    ) {
        self.title = title
        self.artist = artist
        self.artwork = artwork
        self.isPlaying = isPlaying
        self.style = style
        self.isCoverHidden = isCoverHidden
        self.progress = progress
        self.onTap = onTap
        self.onPlayPause = onPlayPause
        self.onNext = onNext
    }

    var body: some View {
        Group {
            switch style {
            case .floating:
                floatingCard
            case .accessory:
                if #available(iOS 26, *) {
                    MiniPlayerAccessoryContent(bar: self)
                } else {
                    floatingCard
                }
            }
        }
        .tint(tint)
    }

    // MARK: - Floating card

    private var floatingCard: some View {
        let shape = RoundedRectangle(cornerRadius: ArtworkCorner.tile, style: .continuous)
        return row(coverSize: ArtworkSize.row, showsArtist: true, showsNext: true)
            .padding(.leading, Spacing.small)
            .padding(.trailing, Spacing.xsmall)
            .padding(.vertical, Spacing.small)
            .modifier(FloatingCardBackground(shape: shape))
            .clipShape(shape)
            .artworkShadow(Self.floatingShadow)
            .padding(.horizontal, Spacing.small)
            .padding(.bottom, Spacing.small)
    }

    // MARK: - Shared row

    /// The cover, title (and artist), play/pause (and next).
    func row(coverSize: CGFloat, showsArtist: Bool, showsNext: Bool) -> some View {
        HStack(spacing: Spacing.small) {
            Button(action: onTap) {
                HStack(spacing: Spacing.smallMedium) {
                    cover
                        .artworkTile(coverSize)
                        .nowPlayingMatchedGeometry(id: NowPlayingCover.matchedGeometryID, isSource: !isCoverHidden)
                        .opacity(isCoverHidden ? 0 : 1)
                    VStack(alignment: .leading, spacing: 0) {
                        Text(title)
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(.primary)
                            .lineLimit(1)
                            .accessibilityIdentifier("miniPlayer.title")
                        if showsArtist, let artist {
                            Text(artist)
                                .font(.footnote)
                                // In the accessory, the glass's own vibrant secondary, which follows the light or
                                // dark appearance the glass takes over what scrolls under it.
                                .foregroundStyle(style == .accessory ? AnyShapeStyle(.secondary) : AnyShapeStyle(.s2SecondaryText))
                                .lineLimit(1)
                        }
                    }
                    Spacer(minLength: 0)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(accessibilityLabel)
            .accessibilityValue(isPlaying ? "Playing" : "Paused")
            .accessibilityHint("Opens Now Playing")
            .accessibilityIdentifier("miniPlayer.open")

            Button(action: onPlayPause) {
                Image(systemName: isPlaying ? "pause.fill" : "play.fill")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(.primary)
                    .contentTransition(.symbolEffect(.replace))
                    .background { MiniPlayerProgressRing(progress: progress) }
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.pressScale)
            .accessibilityLabel(isPlaying ? "Pause" : "Play")
            .accessibilityIdentifier("miniPlayer.playPause")

            if showsNext {
                Button(action: onNext) {
                    Image(systemName: "forward.fill")
                        .font(.body.weight(.semibold))
                        .foregroundStyle(.primary)
                        .frame(width: 44, height: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.pressScale)
                .accessibilityLabel("Next")
                .accessibilityIdentifier("miniPlayer.next")
            }
        }
    }

    /// The song's cover, or the placeholder tile when the song has none.
    @ViewBuilder
    private var cover: some View {
        if let artwork {
            RemoteArtwork(artwork, points: ArtworkSize.row)
        } else {
            ArtworkPlaceholder()
        }
    }

    private var accessibilityLabel: String {
        [title, artist].compactMap { $0 }.joined(separator: ", ")
    }
}

/// The iOS 26 accessory's content, laid out for where the system put it: the full row (cover, title, artist,
/// play/pause, next) above the tab bar, and cover, title and play/pause beside the minimised tab bar.
@available(iOS 26, *)
private struct MiniPlayerAccessoryContent: View {
    let bar: MiniPlayerBar
    @Environment(\.tabViewBottomAccessoryPlacement) private var placement

    var body: some View {
        let isInline = placement == .inline
        bar.row(coverSize: MiniPlayerBar.accessoryCover, showsArtist: !isInline, showsNext: !isInline)
            .padding(.leading, Spacing.small)
            .padding(.trailing, Spacing.xsmall)
    }
}

/// The floating card's ground: Liquid Glass on iOS 26; below, `.thickMaterial`, so the list scrolling behind reads
/// as colour rather than as text, edged with a hairline.
private struct FloatingCardBackground: ViewModifier {
    let shape: RoundedRectangle

    func body(content: Content) -> some View {
        if #available(iOS 26, *) {
            content.glassEffect(.regular, in: shape)
        } else {
            content
                .background(.thickMaterial, in: shape)
                .overlay { shape.strokeBorder(Color.primary.opacity(0.08), lineWidth: Spacing.hairline) }
        }
    }
}

/// The song's progress as a ring around the mini player's play/pause glyph, in the player's tint over a faint track,
/// starting at 12 o'clock. It reads the position itself (`progress`), so the bar around it isn't redrawn on every
/// tick.
struct MiniPlayerProgressRing: View {
    let progress: () -> Double

    var body: some View {
        let fraction = progress()
        ZStack {
            Circle()
                .stroke(.tint.opacity(0.2), lineWidth: MiniPlayerBar.progressRingWidth)
            Circle()
                .trim(from: 0, to: fraction)
                .stroke(.tint, style: StrokeStyle(lineWidth: MiniPlayerBar.progressRingWidth, lineCap: .round))
                .rotationEffect(.degrees(-90))
        }
        .frame(width: MiniPlayerBar.progressRingDiameter, height: MiniPlayerBar.progressRingDiameter)
        .accessibilityHidden(true)
    }
}

extension View {
    /// Insets the mini player at the bottom of this screen. Apply it INSIDE the `NavigationStack`, to the root screen
    /// and to every pushed one (`routeDestinations`): attached to the stack itself (Shuttle Podcasts found) the bar
    /// draws but reserves no safe area and receives no touches. Where the iOS 26 tab view accessory hosts the mini
    /// player (`\.miniPlayerInAccessory`), the inset is empty.
    func miniPlayerInset(showNowPlaying: Binding<Bool>) -> some View {
        modifier(MiniPlayerInsetModifier(showNowPlaying: showNowPlaying))
    }

    /// Insets `bar` at the bottom of this screen, stretching the screen to fill first: `safeAreaInset` sizes to the
    /// view it's attached to, so on content that doesn't fill (a `ProgressView`, an empty state, the importing
    /// placeholder) the bar sat just under it, mid-screen above an empty band, instead of docking above the tab bar
    /// (#623).
    func dockedAtBottom<Bar: View>(@ViewBuilder _ bar: () -> Bar) -> some View {
        frame(maxWidth: .infinity, maxHeight: .infinity)
            .safeAreaInset(edge: .bottom, spacing: 0, content: bar)
    }
}

/// `miniPlayerInset`: the floating bar while there is a current song, sliding up from the bottom edge; otherwise, or
/// when the accessory hosts it, nothing, and the inset collapses with it so lists keep no gap.
private struct MiniPlayerInsetModifier: ViewModifier {
    @Binding var showNowPlaying: Bool
    var binding: PlayerBinding = AppGraph.dependencies.playerBinding

    @Environment(\.miniPlayerInAccessory) private var inAccessory
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func body(content: Content) -> some View {
        let isShown = binding.isMiniPlayerVisible && !inAccessory
        content
            .dockedAtBottom {
                if isShown {
                    MiniPlayerView(showNowPlaying: $showNowPlaying, binding: binding)
                        .transition(AnyTransition.move(edge: .bottom).combined(with: .opacity).reduced(reduceMotion))
                }
            }
            .animation(Motion.miniPlayerVisibility.reduced(reduceMotion), value: isShown)
    }
}

/// The iOS 26.1 tab view bottom accessory hosting the mini player, enabled only while there is a current song, so
/// no empty capsule sits over the tab bar. (26.0 has no `isEnabled`; there the screens' floating inset is used.)
@available(iOS 26.1, *)
struct MiniPlayerAccessoryModifier: ViewModifier {
    @Binding var showNowPlaying: Bool
    var binding: PlayerBinding = AppGraph.dependencies.playerBinding

    // The system animates the accessory in and out itself; an `.animation` here would animate the whole tab view.
    func body(content: Content) -> some View {
        content.tabViewBottomAccessory(isEnabled: binding.isMiniPlayerVisible) {
            MiniPlayerView(showNowPlaying: $showNowPlaying, placement: .accessory, binding: binding)
        }
    }
}

#Preview("Playing") {
    VStack {
        Spacer()
        MiniPlayerBar(
            title: "Paranoid Android", artist: "Radiohead", artwork: nil, isPlaying: true, progress: { 0.3 },
            onTap: {}, onPlayPause: {}, onNext: {}
        )
    }
}
