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
/// - Given a `backdrop` (an artist's photo), the hero runs full bleed: the list (or the hero column) runs up under the
///   status and navigation bars, the backdrop is drawn edge to edge from the top of the screen,
///   `DetailBleed.backdropHeight` tall, and the hero is handed `.bleed` to lay its text over the backdrop's bottom.
///
/// The screen's own modifiers (`toolbar`, `\.editMode`, alerts) go outside; `rows` are `List` content.
struct DetailScaffold<Hero: View, Backdrop: View, Rows: View>: View {
    let title: String
    let tintSource: ArtworkSource?
    let backdrop: Backdrop?
    @ViewBuilder let hero: (DetailHeroLayout) -> Hero
    @ViewBuilder let rows: () -> Rows

    init(
        title: String,
        tintSource: ArtworkSource?,
        backdrop: Backdrop?,
        @ViewBuilder hero: @escaping (DetailHeroLayout) -> Hero,
        @ViewBuilder rows: @escaping () -> Rows
    ) {
        self.title = title
        self.tintSource = tintSource
        self.backdrop = backdrop
        self.hero = hero
        self.rows = rows
    }

    var body: some View {
        DetailScaffoldBody(title: title, backdrop: backdrop, hero: hero, rows: rows)
            .artworkTint(from: tintSource)
    }
}

extension DetailScaffold where Backdrop == EmptyView {
    /// The usual inset hero, under the wash.
    init(title: String, tintSource: ArtworkSource?, @ViewBuilder hero: @escaping (DetailHeroLayout) -> Hero, @ViewBuilder rows: @escaping () -> Rows) {
        self.init(title: title, tintSource: tintSource, backdrop: nil, hero: hero, rows: rows)
    }
}

/// How a `DetailHero` lays out: centred over the list, as the leading column of the two-column layout, or over a
/// full-bleed backdrop (`DetailScaffold`'s `backdrop`), in either.
enum DetailHeroLayout: Equatable {
    case stacked
    case column(width: CGFloat)
    case bleed(DetailBleed)

    var artworkSize: CGFloat {
        switch self {
        case .stacked, .bleed: ArtworkSize.hero
        case .column(let width): min(ArtworkSize.heroRegular, width)
        }
    }

    /// The cover at the accessibility text sizes: `accessibilityArtworkSize` at most, so the title, the eyebrow lines
    /// and the Play/Shuffle row, all much taller there, still land on the first screen above the mini player
    /// rather than below it (#782). The other sizes keep `artworkSize`.
    func artworkSize(isAccessibilitySize: Bool) -> CGFloat {
        isAccessibilitySize ? min(artworkSize, Self.accessibilityArtworkSize) : artworkSize
    }

    /// Half the compact hero.
    static let accessibilityArtworkSize = ArtworkSize.hero / 2
}

/// A full-bleed hero's geometry: the backdrop's height from the top of the screen, along whose bottom the hero's title
/// sits, and whether the hero is the two-column layout's leading column (whose width it fills) or the list's first row.
struct DetailBleed: Equatable {
    let height: CGFloat
    let isColumn: Bool

    /// The backdrop's height for a `width`-wide hero in a `containerHeight`-tall app (`\.rootContainerSize`): square
    /// on compact, 4:3 on regular (a wide hero gets a landscape frame rather than one taller than the screen), and
    /// never more than half the app's height, so the first rows always show under it (an iPhone in landscape, an iPad
    /// in a short window).
    static func backdropHeight(width: CGFloat, containerHeight: CGFloat, tier: LayoutTier) -> CGFloat {
        let aspect: CGFloat = tier == .compact ? 1 : 0.75
        return max(0, min(width * aspect, containerHeight * 0.5))
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
private struct DetailScaffoldBody<Hero: View, Backdrop: View, Rows: View>: View {
    let title: String
    let backdrop: Backdrop?
    let hero: (DetailHeroLayout) -> Hero
    let rows: () -> Rows

    @Environment(\.layoutTier) private var layoutTier
    @Environment(\.artworkTint) private var tint
    @Environment(\.rootContainerSize) private var rootContainerSize
    @State private var heroVisible = true
    /// Where the navigation bar ends, in global coordinates. The hero's title hides under the bar above this line,
    /// and the inset hero's wash reaches up past it. It never sizes a row: see `singleColumn`.
    @State private var barBottom: CGFloat = 0
    /// The single-column list's width, which a full-bleed backdrop's height follows.
    @State private var listWidth: CGFloat = 0

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

    private var titleProbe: DetailTitleProbe {
        DetailTitleProbe(barBottom: barBottom) { isVisible in
            if heroVisible != isVisible { heroVisible = isVisible }
        }
    }

    private var singleColumn: some View {
        List {
            Section {
                stackedHero
            }
            .rowSeparator(.none)
            rows()
        }
        .listStyle(.plain)
        // A full-bleed list runs up under the bars itself, so its first row starts at the top of the screen and the
        // hero is exactly as tall as the backdrop. No row's height may follow the screen's own geometry (#700): the
        // zoom push lays the screen out in a transition container of its own, without the bars' safe area and in
        // shifted global coordinates (the bar's bottom read -426 on an iPhone 16), and a hero row sized from that
        // stayed oversized in its cell after the push, its title and Play row 286 pt down the page. So the backdrop's
        // height comes from the list's width and the app's height, neither of which a push changes.
        .modifier(UnderTheBars(isActive: backdrop != nil))
        // The bar's bottom, on a view laid out inside the list's safe area: the title probe's line and how far the
        // inset hero's wash reaches up. Its own minY, not the list's minY plus its top inset, which counted the bar
        // twice (#700).
        .overlay(alignment: .top) {
            Color.clear
                .frame(height: 0)
                .onGeometryChange(for: CGFloat.self) { proxy in
                    proxy.frame(in: .global).minY
                } action: { barBottom = $0 }
        }
        .onGeometryChange(for: CGFloat.self) { proxy in
            proxy.size.width
        } action: { listWidth = $0 }
        .modifier(BleedNavigationBar(isActive: backdrop != nil, isOverBackdrop: heroVisible))
    }

    @ViewBuilder private var stackedHero: some View {
        if let backdrop {
            let height = DetailBleed.backdropHeight(width: listWidth, containerHeight: rootContainerSize.height, tier: layoutTier)
            hero(.bleed(DetailBleed(height: height, isColumn: false)))
                .environment(\.detailTitleProbe, titleProbe)
                .padding(.bottom, Spacing.small)
                .listRowInsets(EdgeInsets())
                // The row starts at the top of the screen (`UnderTheBars`), so the photo runs behind the status and
                // navigation bars from there.
                .listRowBackground(
                    VStack(spacing: 0) {
                        backdrop
                            .frame(height: height)
                            .clipped()
                        Spacer(minLength: 0)
                    }
                )
        } else {
            hero(.stacked)
                .environment(\.detailTitleProbe, titleProbe)
                .padding(.horizontal, AdaptiveLayout.contentInset(layoutTier))
                .padding(.top, Spacing.small)
                .padding(.bottom, Spacing.large)
                .listRowInsets(EdgeInsets())
                // Up past the row's top under the navigation bar (and the status bar), so the bar sits on the
                // wash rather than on a plain band above it.
                .listRowBackground(DetailWash(style: .fading).padding(.top, -max(barBottom, 0)))
        }
    }

    private func twoColumn(columnWidth: CGFloat) -> some View {
        let inset = AdaptiveLayout.contentInset(layoutTier)
        return HStack(spacing: 0) {
            Group {
                if let backdrop {
                    // The column's scroll view runs up under the bars, so the backdrop starts at the top of the screen.
                    let height = DetailBleed.backdropHeight(width: columnWidth, containerHeight: rootContainerSize.height, tier: .compact)
                    ScrollView {
                        hero(.bleed(DetailBleed(height: height, isColumn: true)))
                            .padding(.bottom, Spacing.large)
                            .background(alignment: .top) {
                                backdrop
                                    .frame(height: height)
                                    .clipped()
                            }
                    }
                    .ignoresSafeArea(edges: .top)
                } else {
                    ScrollView {
                        hero(.column(width: columnWidth - inset * 2))
                            .padding(.horizontal, inset)
                            .padding(.vertical, Spacing.large)
                    }
                }
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

/// A full-bleed list's safe area: it ignores the top one, running up under the status and navigation bars so its
/// first row (the hero, the backdrop its background) starts at the top of the screen.
private struct UnderTheBars: ViewModifier {
    let isActive: Bool

    func body(content: Content) -> some View {
        if isActive {
            content.ignoresSafeArea(edges: .top)
        } else {
            content
        }
    }
}

/// Light bar chrome over a full-bleed backdrop: while the hero's title is still below the bar, the navigation bar
/// draws no background of its own and runs in the dark scheme, so the status bar, the back button and the bar
/// buttons are white over the photo in both appearances; once the title has scrolled under the bar, the bar takes
/// back its usual material and the screen's own scheme. `toolbarColorScheme` only takes on a bar whose background is
/// visible, hence the clear one rather than a hidden one, and a visible material bar after it: handed back to
/// `.automatic` and a nil scheme, iOS 26 kept the dark one, and the inline title stayed white over light rows (#635).
/// Inactive (no backdrop), the bar is left alone.
private struct BleedNavigationBar: ViewModifier {
    let isActive: Bool
    let isOverBackdrop: Bool

    @Environment(\.colorScheme) private var colorScheme

    func body(content: Content) -> some View {
        if isActive {
            content
                .toolbarBackground(isOverBackdrop ? AnyShapeStyle(Color.clear) : AnyShapeStyle(.bar), for: .navigationBar)
                .toolbarBackground(.visible, for: .navigationBar)
                .toolbarColorScheme(isOverBackdrop ? .dark : colorScheme, for: .navigationBar)
        } else {
            content
        }
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

extension View {
    /// Marks the hero's title for `DetailScaffold`, which names the screen in the navigation bar once it has scrolled
    /// under the bar.
    func detailTitleProbe() -> some View {
        modifier(DetailTitleProbeModifier())
    }
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
    /// An artist line above the subtitle that opens the artist (a text button in the tint); nil leaves it out.
    var artist: String? = nil
    var onArtist: (() -> Void)? = nil
    var layout: DetailHeroLayout = .stacked
    var onPlay: () -> Void = {}
    var onShuffle: () -> Void = {}
    /// Whether the screen's songs are downloaded or downloading (#759), shown under the subtitle.
    var downloadStatus: DownloadSummary.Status = .notDownloaded
    @ViewBuilder let artwork: (CGFloat) -> Artwork

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.artworkTint) private var tint

    private var alignment: HorizontalAlignment { layout == .stacked ? .center : .leading }
    private var textAlignment: TextAlignment { layout == .stacked ? .center : .leading }

    var body: some View {
        VStack(alignment: alignment, spacing: Spacing.medium) {
            artwork(layout.artworkSize(isAccessibilitySize: dynamicTypeSize.isAccessibilitySize))
                .artworkShadow(.hero)
                .padding(.bottom, Spacing.xsmall)
            VStack(alignment: alignment, spacing: Spacing.xsmall) {
                Text(title)
                    .font(.s2HeroTitle)
                    .multilineTextAlignment(textAlignment)
                    .lineLimit(dynamicTypeSize.isAccessibilitySize ? nil : 2)
                    .accessibilityAddTraits(.isHeader)
                    .detailTitleProbe()
                if let artist, let onArtist {
                    Button(action: onArtist) {
                        Text(artist)
                            .font(.s2Eyebrow)
                            .foregroundStyle(tint)
                            .multilineTextAlignment(textAlignment)
                            .padding(.vertical, 12)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    // The label's padding grows the hit area toward 44 pt; this takes it back out of the layout.
                    .padding(.vertical, -12)
                    .accessibilityLabel("Go to \(artist)")
                    .accessibilityHint("Opens the artist")
                }
                if let subtitle, !subtitle.isEmpty {
                    Text(subtitle)
                        .font(.s2Eyebrow)
                        .foregroundStyle(.s2TextSecondary)
                        .multilineTextAlignment(textAlignment)
                }
                DownloadStatusLabel(status: downloadStatus)
            }
            HeroActions(onPlay: onPlay, onShuffle: onShuffle)
                .frame(maxWidth: layout == .stacked ? ArtworkSize.heroRegular + Spacing.xlarge : .infinity)
        }
        .frame(maxWidth: .infinity, alignment: layout == .stacked ? .center : .leading)
    }
}

/// Play and Shuffle as two capsules sharing the width, in the tint in scope: Play filled with the tint, Shuffle a
/// tonal capsule (a light wash of the tint); glass on iOS 26, bordered below. At the accessibility sizes the
/// capsules show their glyphs only (the titles stay their accessibility labels): side by side the titles truncate,
/// and stacked the second capsule fell below the first screen, under the mini player (#782).
struct HeroActions: View {
    let onPlay: () -> Void
    let onShuffle: () -> Void

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        HStack(spacing: Spacing.smallMedium) {
            Button(action: onPlay) {
                Label("Play", systemImage: "play.fill").frame(maxWidth: .infinity)
            }
            .capsuleButton(prominent: true)
            .accessibilityLabel("Play")
            Button(action: onShuffle) {
                Label("Shuffle", systemImage: "shuffle").frame(maxWidth: .infinity)
            }
            .capsuleButton(prominent: false)
            .accessibilityLabel("Shuffle")
        }
        .labelStyle(GlyphOnlyAtAccessibilitySizes(isAccessibilitySize: dynamicTypeSize.isAccessibilitySize))
        .fontWeight(.semibold)
        .controlSize(.large)
    }
}

/// `HeroActions`' labels: the glyph alone at the accessibility sizes, glyph and title otherwise.
private struct GlyphOnlyAtAccessibilitySizes: LabelStyle {
    let isAccessibilitySize: Bool

    func makeBody(configuration: Configuration) -> some View {
        if isAccessibilitySize {
            configuration.icon
        } else {
            Label(configuration)
        }
    }
}

extension View {
    /// A capsule button in the tint in scope: `.glassProminent` on iOS 26, `.borderedProminent` in a capsule below.
    /// A prominent button is filled with the tint, its label in `\.artworkTintInk`, which clears AA on the tint fill
    /// whatever the cover; the other is tonal, a light wash of the tint with the label in the tint itself, so it
    /// stands off the page in either scheme (a white or grey capsule all but vanished on the light page).
    func capsuleButton(prominent: Bool) -> some View {
        modifier(CapsuleButtonModifier(prominent: prominent))
    }
}

private struct CapsuleButtonModifier: ViewModifier {
    let prominent: Bool
    @Environment(\.artworkTint) private var tint
    @Environment(\.artworkTintInk) private var ink
    @Environment(\.colorScheme) private var colorScheme

    /// The tonal capsule's fill: the tint over the page, a little stronger in dark mode, where a faint wash reads as grey.
    private var tonalFill: Color { tint.opacity(colorScheme == .dark ? 0.26 : 0.16) }

    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            if prominent {
                content.foregroundStyle(ink).buttonStyle(.glassProminent)
            } else {
                content.foregroundStyle(tint).buttonStyle(.glassProminent).tint(tonalFill)
            }
        } else {
            if prominent {
                content.foregroundStyle(ink).buttonStyle(.borderedProminent).buttonBorderShape(.capsule)
            } else {
                content.foregroundStyle(tint).buttonStyle(.borderedProminent).buttonBorderShape(.capsule).tint(tonalFill)
            }
        }
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
                        .foregroundStyle(.s2TextSecondary)
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
                        .foregroundStyle(.s2TextSecondary)
                        .lineLimit(1)
                }
            }
            // A text-only row: its separator starts at the title.
            .rowSeparator(.insetToTitle)
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
            .font(.s2RowMeta)
            .foregroundStyle(.s2TextSecondary)
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
func totalDuration(_ songs: [Song], locale: Locale = .current) -> String? {
    runtime(ms: songs.reduce(Int64(0)) { $0 + Int64($1.duration) }, locale: locale)
}

/// A running time of `ms` milliseconds as "43 min" or "1 hr 3 min"; nil under a minute (unknown or negligible).
func runtime(ms: Int64, locale: Locale = .current) -> String? {
    guard ms >= 60_000 else { return nil }
    return Duration.milliseconds(ms).formatted(.units(allowed: [.hours, .minutes], width: .abbreviated).locale(locale))
}

/// A song's length as VoiceOver says it: "3 minutes, 20 seconds".
func spokenDuration(ms: Int64, locale: Locale = .current) -> String {
    Duration.milliseconds(ms).formatted(.units(allowed: [.hours, .minutes, .seconds], width: .wide).locale(locale))
}

/// "1 song" / "N songs" (or any other noun), the pluralisation every detail hero's subtitle repeats.
func pluralized(_ count: Int, _ noun: String) -> String { count == 1 ? "1 \(noun)" : "\(count) \(noun)s" }

extension PlayerBinding {
    /// Whether the player is playing, for a detail screen's playing row. Reads only the mini player's state, so a
    /// progress tick doesn't redraw the screen.
    var isPlaying: Bool { miniPlayer.isPlaying }
}

extension View {
    /// The long-press menu on a song row: Play Next, Add to Queue and, when the screen presents it, Song Info for that
    /// one song.
    func songContextMenu(
        _ song: Song,
        onPlayNext: @escaping ([Song]) -> Void,
        onAddToQueue: @escaping ([Song]) -> Void,
        onSongInfo: ((Song) -> Void)? = nil,
        downloads: DetailDownloads = DetailDownloads()
    ) -> some View {
        contextMenu {
            Button("Play Next", systemImage: "text.line.first.and.arrowtriangle.forward") { onPlayNext([song]) }
            Button("Add to Queue", systemImage: "text.append") { onAddToQueue([song]) }
            if let onSongInfo {
                Button("Song Info", systemImage: "info.circle") { onSongInfo(song) }
            }
            DownloadMenuItems(songs: [song], downloads: downloads)
        }
    }
}

/// A detail screen's offline downloads (#759, docs/architecture/downloads.md): how far some songs' downloads have got
/// (`OfflineDownloads.summary`, the shared actions' rule) and the shared Download and Remove Download actions. The
/// default has no `summary`, which hides the items, as for a screen that doesn't offer them.
struct DetailDownloads {
    var summary: (([Song]) -> DownloadSummary)? = nil
    var onDownload: ([Song]) -> Void = { _ in }
    var onRemove: ([Song]) -> Void = { _ in }
}

extension DetailDownloads {
    /// Reads the graph's downloads and sends the actions through `actions`. Its screen observes
    /// `OfflineDownloads.downloads`, so it's redrawn, and the summary read again, as a download moves on.
    @MainActor
    init(actions: MediaActionsViewModel, downloads: OfflineDownloads = AppGraph.shared.offlineDownloads) {
        summary = { downloads.summary(songs: $0) }
        onDownload = { actions.send(MediaActionDownload(selection: MediaSelectionSongs(songs: $0))) }
        onRemove = { actions.send(MediaActionRemoveDownload(selection: MediaSelectionSongs(songs: $0))) }
    }
}

/// Download, for songs not yet all on the device, and Remove Download, for any that are or are on their way.
struct DownloadMenuItems: View {
    let songs: [Song]
    let downloads: DetailDownloads

    var body: some View {
        if let summary = downloads.summary?(songs) {
            if summary.canDownload {
                Button("Download", systemImage: "arrow.down.circle") { downloads.onDownload(songs) }
            }
            if summary.canRemove {
                Button("Remove Download", systemImage: "xmark.circle", role: .destructive) { downloads.onRemove(songs) }
            }
        }
    }
}

/// "Downloaded" once every song of the screen is on the device, "Downloading" while any is on its way; nothing otherwise.
struct DownloadStatusLabel: View {
    let status: DownloadSummary.Status

    var body: some View {
        switch status {
        case .downloaded:
            Label("Downloaded", systemImage: "arrow.down.circle.fill")
                .labelStyle(DownloadStatusLabelStyle())
                .accessibilityIdentifier("detailHero.downloaded")
        case .downloading:
            Label("Downloading", systemImage: "arrow.down.circle.dotted")
                .labelStyle(DownloadStatusLabelStyle())
                .accessibilityIdentifier("detailHero.downloading")
        default:
            EmptyView()
        }
    }
}

private struct DownloadStatusLabelStyle: LabelStyle {
    func makeBody(configuration: Configuration) -> some View {
        HStack(spacing: Spacing.xsmall) {
            configuration.icon
            configuration.title
        }
        .font(.s2Eyebrow)
        .foregroundStyle(.s2TextSecondary)
        .accessibilityElement(children: .combine)
    }
}
