import Shared
import SwiftUI

/// The frame every detail screen (album, album artist, genre, playlist, smart playlist) draws in, after Shuttle
/// Podcasts' `SeriesDetailView`: a hero, then the screen's rows, under a soft wash of the hero's own tint.
///
/// - The tint is local: `.artworkTint(from: tintSource)` extracts the hero cover's colour for this screen only (the
///   player's tint stays scoped to the player), and the Play/Shuffle capsules, the wash, the playing row and the
///   See All links follow it through `.tint`. In the light scheme the hero's wash and tint come from the cover's
///   `DetailPalette`, a vivid wash with a tint measured on it, rather than a wash of the darkened tint.
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
            .modifier(DetailPaletteProvider())
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
