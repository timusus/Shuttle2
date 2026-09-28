import Shared
import SwiftUI

/// The frame every detail screen (album, album artist, genre, playlist, smart playlist) draws in, after Shuttle
/// Podcasts' `SeriesDetailView`: a hero, then the screen's rows, under a soft wash of the hero's own tint.
///
/// - The tint is local: `.artworkTint(from: tintSource)` extracts the hero cover's colour for this screen only (the
///   player's tint stays scoped to the player), and the Play/Shuffle capsules, the wash, the playing row and the
///   See All links follow it through `.tint`.
/// - Compact, or a regular container narrower than `AdaptiveLayout.twoColumnMinWidth`: one `List`, the hero its
///   first row (Android's "hero as list item", not a collapsing bar) with the wash as that row's background, reaching
///   up under the navigation bar. The bar takes over the title only once the hero's title has scrolled under it.
/// - Regular and wide from `twoColumnMinWidth`: the hero is a fixed leading column (its own scroll view, the wash
///   filling it) beside the list, with the larger `ArtworkSize.heroRegular` cover; the bar never shows the title,
///   since the hero is always on screen.
///
/// The screen's own modifiers (`toolbar`, `\.editMode`, alerts) go outside; `rows` are `List` content.
struct DetailScaffold<Hero: View, Rows: View>: View {
    let title: String
    let tintSource: ArtworkSource?
    @ViewBuilder let hero: (DetailHeroLayout) -> Hero
    @ViewBuilder let rows: () -> Rows

    var body: some View {
        DetailScaffoldBody(title: title, hero: hero, rows: rows)
            .artworkTint(from: tintSource)
    }
}

/// How a `DetailHero` lays out: centred over the list, or as the leading column of the two-column layout.
enum DetailHeroLayout: Equatable {
    case stacked
    case column(width: CGFloat)

    var artworkSize: CGFloat {
        switch self {
        case .stacked: ArtworkSize.hero
        case .column(let width): min(ArtworkSize.heroRegular, width)
        }
    }
}

/// One column (the hero the list's first row) or two (the hero a fixed leading column beside the list).
enum DetailColumns: Equatable {
    case single
    case two(heroColumnWidth: CGFloat)

    /// Two columns in a regular or wide container at least `AdaptiveLayout.twoColumnMinWidth` wide, the hero column
    /// a third of it but never narrower than the regular cover plus its margins; one otherwise.
    static func resolve(tier: LayoutTier, containerWidth: CGFloat) -> DetailColumns {
        guard tier != .compact, containerWidth >= AdaptiveLayout.twoColumnMinWidth else { return .single }
        let inset = AdaptiveLayout.contentInset(tier)
        return .two(heroColumnWidth: max(ArtworkSize.heroRegular + inset * 2, containerWidth / 3))
    }
}

/// `DetailScaffold` inside its tint, so it can read `\.artworkTint`.
private struct DetailScaffoldBody<Hero: View, Rows: View>: View {
    let title: String
    let hero: (DetailHeroLayout) -> Hero
    let rows: () -> Rows

    @Environment(\.layoutTier) private var layoutTier
    @Environment(\.artworkTint) private var tint
    @State private var heroVisible = true
    /// Where the navigation bar ends, in global coordinates: the single-column list's top plus the safe area it
    /// scrolls under. The hero's title hides under the bar above this line, and the wash reaches up past it.
    @State private var barBottom: CGFloat = 0

    var body: some View {
        Group {
            if layoutTier == .compact {
                singleColumn
            } else {
                GeometryReader { proxy in
                    switch DetailColumns.resolve(tier: layoutTier, containerWidth: proxy.size.width) {
                    case .single: singleColumn
                    case .two(let heroColumnWidth): twoColumn(columnWidth: heroColumnWidth)
                    }
                }
            }
        }
        .tint(tint)
        .navigationTitle(heroVisible ? "" : title)
        .navigationBarTitleDisplayMode(.inline)
    }

    private var singleColumn: some View {
        List {
            Section {
                hero(.stacked)
                    .environment(\.detailTitleProbe, DetailTitleProbe(barBottom: barBottom) { isVisible in
                        if heroVisible != isVisible { heroVisible = isVisible }
                    })
                    .padding(.horizontal, AdaptiveLayout.contentInset(layoutTier))
                    .padding(.top, Spacing.small)
                    .padding(.bottom, Spacing.large)
                    .listRowInsets(EdgeInsets())
                    // Up past the row's top under the navigation bar (and the status bar), so the bar sits on the
                    // wash rather than on a plain band above it.
                    .listRowBackground(DetailWash(style: .fading).padding(.top, -barBottom))
            }
            .listRowSeparator(.hidden)
            rows()
        }
        .listStyle(.plain)
        .onGeometryChange(for: CGFloat.self) { proxy in
            proxy.frame(in: .global).minY + proxy.safeAreaInsets.top
        } action: { barBottom = $0 }
    }

    private func twoColumn(columnWidth: CGFloat) -> some View {
        let inset = AdaptiveLayout.contentInset(layoutTier)
        return HStack(spacing: 0) {
            ScrollView {
                hero(.column(width: columnWidth - inset * 2))
                    .padding(.horizontal, inset)
                    .padding(.vertical, Spacing.large)
            }
            .frame(width: columnWidth)
            .background(DetailWash(style: .panel).ignoresSafeArea())

            List { rows() }
                .listStyle(.plain)
                .contentMargins(.trailing, inset, for: .scrollContent)
        }
        // The hero is always on screen here; clear a `false` left by a scrolled single column.
        .onAppear { heroVisible = true }
    }

}

/// How the stacked hero's title tells `DetailScaffold` whether it's still below the navigation bar, so the bar names
/// the screen only once the title has scrolled under it: whatever the hero's height, at every text size.
/// `onScrollGeometryChange` is iOS 18; this works on 17.
struct DetailTitleProbe {
    /// The bar's bottom edge in global coordinates.
    let barBottom: CGFloat
    let report: (_ isVisible: Bool) -> Void

    /// Whether a title whose bottom edge is at `titleMaxY` (global) still shows below the bar.
    static func isVisible(titleMaxY: CGFloat, barBottom: CGFloat) -> Bool {
        titleMaxY > barBottom
    }
}

extension EnvironmentValues {
    /// Set on the single-column hero only; the two-column hero is always on screen.
    @Entry var detailTitleProbe: DetailTitleProbe?
}

private struct DetailTitleProbeModifier: ViewModifier {
    @Environment(\.detailTitleProbe) private var probe

    func body(content: Content) -> some View {
        if let probe {
            content.onGeometryChange(for: Bool.self) { proxy in
                DetailTitleProbe.isVisible(titleMaxY: proxy.frame(in: .global).maxY, barBottom: probe.barBottom)
            } action: { probe.report($0) }
        } else {
            content
        }
    }
}

/// The soft wash of the screen's tint behind a hero: fading out into the list below a stacked hero, or a panel
/// that never quite fades behind the two-column hero, so the column reads as one tinted surface.
struct DetailWash: View {
    enum Style { case fading, panel }
    let style: Style

    @Environment(\.artworkTint) private var tint

    var body: some View {
        switch style {
        case .fading:
            LinearGradient(colors: [tint.opacity(0.22), tint.opacity(0.06), tint.opacity(0)], startPoint: .top, endPoint: .bottom)
        case .panel:
            LinearGradient(colors: [tint.opacity(0.18), tint.opacity(0.06)], startPoint: .top, endPoint: .bottom)
        }
    }
}

/// A detail screen's hero: the cover (with the hero shadow), a two-line title, the eyebrow line (artist · year ·
/// N songs · duration) and the Play/Shuffle capsules in the screen's tint. Centred when stacked, leading in the
/// two-column layout's hero column. `artwork` draws the cover at the size it's given, already clipped.
struct DetailHero<Artwork: View>: View {
    let title: String
    let subtitle: String?
    var layout: DetailHeroLayout = .stacked
    var onPlay: () -> Void = {}
    var onShuffle: () -> Void = {}
    @ViewBuilder let artwork: (CGFloat) -> Artwork

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    private var alignment: HorizontalAlignment { layout == .stacked ? .center : .leading }
    private var textAlignment: TextAlignment { layout == .stacked ? .center : .leading }

    var body: some View {
        VStack(alignment: alignment, spacing: Spacing.medium) {
            artwork(layout.artworkSize)
                .artworkShadow(.hero)
                .padding(.bottom, Spacing.xsmall)
            VStack(alignment: alignment, spacing: Spacing.xsmall) {
                Text(title)
                    .font(.s2Title2)
                    .multilineTextAlignment(textAlignment)
                    .lineLimit(dynamicTypeSize.isAccessibilitySize ? nil : 2)
                    .accessibilityAddTraits(.isHeader)
                    .modifier(DetailTitleProbeModifier())
                if let subtitle, !subtitle.isEmpty {
                    Text(subtitle)
                        .font(.s2Eyebrow)
                        .foregroundStyle(.s2SecondaryText)
                        .multilineTextAlignment(textAlignment)
                }
            }
            HeroActions(onPlay: onPlay, onShuffle: onShuffle)
                .frame(maxWidth: layout == .stacked ? ArtworkSize.heroRegular + Spacing.xlarge : .infinity)
        }
        .frame(maxWidth: .infinity, alignment: layout == .stacked ? .center : .leading)
    }
}

/// Play and Shuffle as two capsules sharing the width, in the tint in scope: glass on iOS 26, bordered below.
/// Stacked at the accessibility sizes, where side by side they'd truncate.
struct HeroActions: View {
    let onPlay: () -> Void
    let onShuffle: () -> Void

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        let layout = dynamicTypeSize.isAccessibilitySize
            ? AnyLayout(VStackLayout(spacing: Spacing.smallMedium))
            : AnyLayout(HStackLayout(spacing: Spacing.smallMedium))
        layout {
            Button(action: onPlay) {
                Label("Play", systemImage: "play.fill").frame(maxWidth: .infinity)
            }
            .capsuleButton(prominent: true)
            .accessibilityLabel("Play")
            Button(action: onShuffle) {
                Label("Shuffle", systemImage: "shuffle").frame(maxWidth: .infinity)
            }
            .capsuleButton(prominent: false)
        }
        .fontWeight(.semibold)
        .controlSize(.large)
    }
}

extension View {
    /// A capsule button in the tint in scope: `.glassProminent` / `.glass` on iOS 26, `.borderedProminent` /
    /// `.bordered` in a capsule below. A prominent button's label takes `\.artworkTintInk`, which clears AA on the
    /// tint fill whatever the cover.
    func capsuleButton(prominent: Bool) -> some View {
        modifier(CapsuleButtonModifier(prominent: prominent))
    }
}

private struct CapsuleButtonModifier: ViewModifier {
    let prominent: Bool
    @Environment(\.artworkTintInk) private var ink

    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            if prominent {
                content.foregroundStyle(ink).buttonStyle(.glassProminent)
            } else {
                content.buttonStyle(.glass)
            }
        } else {
            if prominent {
                content.foregroundStyle(ink).buttonStyle(.borderedProminent).buttonBorderShape(.capsule)
            } else {
                content.buttonStyle(.bordered).buttonBorderShape(.capsule)
            }
        }
    }
}

/// The hero cover for screens without artwork of their own (genre, smart playlist, an empty playlist): the
/// tinted `ArtworkPlaceholder` at hero size with the hero corner.
struct DetailPlaceholderArtwork: View {
    let systemImage: String
    let points: CGFloat

    var body: some View {
        ArtworkPlaceholder(symbol: systemImage)
            .artworkTile(points, cornerRadius: ArtworkCorner.hero)
    }
}

/// A numbered track: the track number in `.s2Time` (the playing indicator in its place while it's the current
/// song), the title and an optional subtitle, the duration trailing. The album screen's row, where every track
/// shares the cover so a thumbnail would say nothing.
struct TrackRow: View {
    let number: Int?
    let title: String
    var subtitle: String?
    let durationMs: Int64
    var playback: MediaRowPlayback = .none

    @Environment(\.artworkTint) private var tint
    @ScaledMetric(relativeTo: .caption) private var numberWidth = Spacing.large

    var body: some View {
        HStack(spacing: Spacing.smallMedium) {
            Group {
                if playback != .none {
                    NowPlayingIndicator(isAnimating: playback == .playing)
                        .foregroundStyle(tint)
                        .frame(width: numberWidth * 0.7, height: numberWidth * 0.7)
                } else if let number {
                    Text("\(number)")
                        .font(.s2Time)
                        .foregroundStyle(.s2SecondaryText)
                }
            }
            .frame(width: numberWidth, alignment: .center)
            VStack(alignment: .leading, spacing: Spacing.tiny) {
                Text(title)
                    .lineLimit(1)
                    .foregroundStyle(playback == .none ? AnyShapeStyle(.primary) : AnyShapeStyle(tint))
                    .fontWeight(playback == .none ? nil : .semibold)
                if let subtitle, !subtitle.isEmpty {
                    Text(subtitle)
                        .font(.subheadline)
                        .foregroundStyle(.s2SecondaryText)
                        .lineLimit(1)
                }
            }
            Spacer(minLength: Spacing.small)
            SongDurationText(durationMs: durationMs)
        }
        .contentShape(Rectangle())
    }
}

/// A song's duration as a row's trailing time.
struct SongDurationText: View {
    let durationMs: Int64

    var body: some View {
        Text(Duration.milliseconds(durationMs).formatted(.time(pattern: .minuteSecond)))
            .font(.s2RowTime)
            .foregroundStyle(.s2SecondaryText)
    }
}

/// A song as a detail screen's row with its own cover (artist, genre and playlist screens, whose songs come from
/// many albums): `MediaRow` with the artist and album as the subtitle and the duration trailing.
struct DetailSongRow: View {
    let song: Song
    var playback: MediaRowPlayback = .none

    var body: some View {
        MediaRow(
            song.name ?? "Unknown",
            subtitle: [song.friendlyArtistName ?? song.albumArtist, song.album].compactMap { $0 }.joined(separator: " · "),
            artwork: .song(song),
            playback: playback
        ) {
            SongDurationText(durationMs: Int64(song.duration))
        }
    }
}

/// Whether `song` is the one playing, as a row shows it: the `currentSong` a detail ViewModel reports, and whether
/// the player is playing (`PlayerBinding`).
func rowPlayback(_ song: Song, current: Song?, isPlaying: Bool) -> MediaRowPlayback {
    guard let current, current.id == song.id else { return .none }
    return isPlaying ? .playing : .paused
}

/// A hero's eyebrow line: its parts joined with " · ", skipping empty ones.
func eyebrow(_ parts: String?...) -> String {
    parts.compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " · ")
}

/// The total running time of `songs`, "43 min" or "1 hr 3 min"; nil when unknown (zero).
func totalDuration(_ songs: [Song]) -> String? {
    let ms = songs.reduce(Int64(0)) { $0 + Int64($1.duration) }
    guard ms >= 60_000 else { return nil }
    return Duration.milliseconds(ms).formatted(.units(allowed: [.hours, .minutes], width: .abbreviated))
}

/// "1 song" / "N songs" (or any other noun), the pluralisation every detail hero's subtitle repeats.
func pluralized(_ count: Int, _ noun: String) -> String { count == 1 ? "1 \(noun)" : "\(count) \(noun)s" }

extension PlayerBinding {
    /// Whether the player is playing, for a detail screen's playing row. Reads only the mini player's state, so a
    /// progress tick doesn't redraw the screen.
    var isPlaying: Bool { miniPlayer.isPlaying }
}
