import Shared
import SwiftUI

/// Home (#587, polished in #624, sections from #633): the suggestion sections from the shared `HomeViewModel`, with
/// no resume card: the mini player is that (#646). Jump Back In is a compact grid; the time of day, Heavy Rotation, Rediscover, Recently
/// Added and Genre Picks are shelves, each under a subtitle saying what it holds (#671). A tap opens the album, artist or playlist (zooming from the tile on iOS 18+), or
/// shuffles a Genre Picks tile; every tile's context menu and VoiceOver actions play, shuffle, queue or open it
/// through the shared `MediaAction`s. Before anything has been played (cold start) Home offers Shuffle All and says it
/// learns from listening. Before there's a library, the empty state, or the import's progress while one runs. Pull
/// to refresh re-imports and reloads Home, in every state.
///
/// Home reloads only as it comes on screen (it appears, or the app returns to the foreground), on pull to refresh or
/// when an import completes, and on the hour while it's off screen (#672), so nothing moves while it's being looked at.
struct HomeView: View {
    let navigator: Navigator

    @Environment(\.scenePhase) private var scenePhase
    @State private var appeared = false

    var body: some View {
        let models = ViewModelCache.shared.viewModel(AppTab.home.cacheKey) {
            HomeModels(graph: AppGraph.shared)
        }
        Observing(models.home.uiState, models.actions.uiState, models.importState) { state, actions, importState in
            HomeContent(
                state: state,
                importStatus: ImportStatus(importState),
                onShuffleAll: {
                    if let action = models.home.shuffleAll() { models.actions.dispatch(action: action) }
                },
                onOpen: { item in navigator.open(Self.route(item)) },
                onAction: { models.actions.dispatch(action: $0) }
            )
            .mediaActionResults(actions.events, handled: { models.actions.onEventHandled(id: $0) })
            .consumeEvents((state as? HomeUiStateContent)?.events ?? [], handled: { models.home.onEventHandled(id: $0) }) { _ in }
        }
        .refreshable {
            models.home.refresh()
            LibraryImport.refresh()
        }
        .onAppear {
            appeared = true
            models.home.onVisibilityChanged(visible: scenePhase != .background)
        }
        .onDisappear {
            appeared = false
            models.home.onVisibilityChanged(visible: false)
        }
        .onChange(of: scenePhase) { _, phase in
            models.home.onVisibilityChanged(visible: appeared && phase != .background)
        }
        .navigationTitle(AppTab.home.title)
    }

    /// The screen an item opens.
    static func route(_ item: HomeItem) -> Route {
        switch onEnum(of: item) {
        case .albumItem(let it): .album(it.album)
        case .artistItem(let it): .albumArtist(albumArtistKey: it.albumArtist.groupKey.key)
        case .playlistItem(let it): .playlist(id: it.playlist.id)
        case .smartPlaylistItem(let it): .smartPlaylist(id: it.smartPlaylistId.id)
        case .genreItem(let it): .genre(name: it.genre.name)
        }
    }
}

/// Home's shared ViewModels, cached together under its tab's key, plus the import's state for the empty state.
final class HomeModels: ViewModelGroup {
    let home: HomeViewModel
    let actions: MediaActionsViewModel
    let importState: SkieSwiftStateFlow<SongImportState>

    init(graph: IosAppGraph) {
        home = graph.homeViewModel
        actions = graph.mediaActionsViewModel
        importState = graph.songImportStateProvider.songImportState
    }

    var members: [Lifecycle_viewmodelViewModel] { [home, actions] }
}

/// Home from a `HomeUiState`. `showWhatsNew`/`HomeEvent.AnalyticsNowOn` have no iOS surface yet (no
/// changelog or analytics-consent screen until phase 7, #589), so their events are consumed and dropped.
///
/// A `ScrollView` of a `LazyVStack`, not a `List`: the grid and the shelves are full-bleed, and the
/// content is capped at `AdaptiveLayout.contentMaxWidth` and centred on an iPad.
struct HomeContent: View {
    let state: HomeUiState
    var importStatus: ImportStatus = .idle
    var onShuffleAll: () -> Void = {}
    /// Opens the item's screen.
    var onOpen: (HomeItem) -> Void = { _ in }
    /// Dispatches a play or queue action.
    var onAction: (MediaAction) -> Void = { _ in }

    @Environment(\.layoutTier) private var layoutTier
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// The tile the last tap came from, the one zoom source for its route. An album can be in more than one section,
    /// and a zoom from a tile the user didn't touch would be wrong.
    @State private var zoomSourceKey: String?

    var body: some View {
        switch onEnum(of: state) {
        case .loading:
            ProgressView()
        case .empty:
            // A scroll view even here, so pull to refresh re-imports from the empty state too.
            ScrollView {
                emptyState
                    .containerRelativeFrame(.vertical)
            }
        case .content(let content):
            let coldStart = content.sections.contains { $0.id == .shuffleAll }
            ScrollView {
                LazyVStack(alignment: .leading, spacing: Spacing.large) {
                    // Cold start's Shuffle All leads, above the shelves: the one sure thing to do with a new library.
                    if coldStart {
                        ColdStartCard(onShuffleAll: onShuffleAll)
                            .padding(.horizontal, inset)
                    }
                    ForEach(content.sections.filter { $0.id != .shuffleAll && !$0.items.isEmpty }, id: \.id) { section in
                        sectionView(section)
                    }
                }
                .padding(.top, Spacing.small)
                .padding(.bottom, Spacing.large)
                // A reload moves, adds and removes sections and tiles by their ids rather than replacing them (#672)
                .animation(reduceMotion ? nil : .default, value: Self.identity(content.sections))
                .frame(maxWidth: AdaptiveLayout.contentMaxWidth)
                .frame(maxWidth: .infinity)
            }
            .environment(\.homeCovers, content.covers)
            .toolbar {
                // Cold start has its own, larger Shuffle All.
                if !coldStart {
                    Button("Shuffle", systemImage: "shuffle", action: onShuffleAll)
                }
            }
        }
    }

    private var inset: CGFloat { AdaptiveLayout.contentInset(layoutTier) }

    /// "No Music" is only true once nothing is importing: during the first import the library is empty because it
    /// hasn't arrived yet, so Home says that instead.
    @ViewBuilder
    private var emptyState: some View {
        switch importStatus {
        case .importing(let provider, let message, _):
            EmptyState("Importing Your Music", systemImage: "arrow.down.circle", message: message ?? "Reading your library from \(provider).") {
                ProgressView()
            }
        case .idle, .failed:
            // Plex isn't offered on iOS until its provider is in :shared (`MediaProviderType.signInTypes`).
            EmptyState("No Music", systemImage: "house", message: "Connect a Jellyfin or Emby server to stream your music.") {
                NavigationLink("Add a Source", value: Route.sources)
                    .accessibilityIdentifier("homeEmpty.addSource")
            }
        }
    }

    /// A section under its header: Jump Back In as a grid, the others as shelves.
    private func sectionView(_ section: HomeSection) -> some View {
        VStack(alignment: .leading, spacing: Spacing.smallMedium) {
            header(section)
                .padding(.horizontal, inset)
            if section.id == .jumpBackIn {
                JumpBackInGrid(
                    items: section.items,
                    progress: section.progress,
                    perform: onAction,
                    open: onOpen,
                    zoomSourceKey: zoomSourceKey,
                    onTapped: { zoomSourceKey = $0 }
                )
                .padding(.horizontal, inset)
            } else {
                let mixed = Set(section.items.map(\.typeLabel)).count > 1
                Shelf(inset: inset) {
                    ForEach(section.items, id: \.key) { item in
                        shelfTile(item, mixed: mixed, tileKey: "\(section.id)|\(item.key)")
                    }
                }
            }
        }
    }

    /// The Podcasts-style header, with See All only where a screen holds the whole of what the section samples.
    @ViewBuilder
    private func header(_ section: HomeSection) -> some View {
        let title = Self.title(section.title)
        let subtitle = section.subtitle?.localized()
        switch section.id {
        case .recentlyAdded: SectionHeader(title, subtitle: subtitle, seeAll: .smartPlaylist(id: "recently-added"))
        case .genrePicks: SectionHeader(title, subtitle: subtitle, seeAll: .libraryCategory(.genres))
        default: SectionHeader(title, subtitle: subtitle)
        }
    }

    /// What a reload animates between: each section's id and its items' keys, in order.
    static func identity(_ sections: [HomeSection]) -> [String] {
        sections.map { section in "\(section.id)|" + section.items.map(\.key).joined(separator: ",") }
    }

    /// A shelf tile. A tap opens the item, or for a genre shuffles it (a Genre Pick is something to put on).
    private func shelfTile(_ item: HomeItem, mixed: Bool, tileKey: String) -> some View {
        let open: (HomeItem) -> Void = { item in
            zoomSourceKey = tileKey
            onOpen(item)
        }
        return Button {
            if item is HomeItemGenreItem {
                onAction(item.playAction())
            } else {
                open(item)
            }
        } label: {
            HomeShelfTileLabel(item: item, mixed: mixed)
        }
        .buttonStyle(.pressScale)
        .zoomSource(for: item, tileKey: tileKey, activeKey: zoomSourceKey)
        .accessibilityIdentifier(item.tileIdentifier)
        .homeItemActions(HomeItemActions(item: item, perform: onAction, open: open))
    }

    static func title(_ title: HomeSectionTitle) -> String {
        switch title {
        case .jumpBackIn: "Jump Back In"
        case .thisMorning: "This Morning"
        case .thisAfternoon: "This Afternoon"
        case .tonight: "Tonight"
        case .heavyRotation: "Heavy Rotation"
        case .rediscover: "Rediscover"
        case .recentlyAdded: "Recently Added"
        case .genrePicks: "Genre Picks"
        case .shuffleAll: "Shuffle All"
        }
    }
}

/// Cold start: nothing played yet, so nothing to suggest from. A full-width Shuffle All and a line on how Home fills in.
private struct ColdStartCard: View {
    let onShuffleAll: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.smallMedium) {
            Button(action: onShuffleAll) {
                Label("Shuffle All", systemImage: "shuffle")
                    .font(.s2Headline)
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .buttonBorderShape(.capsule)
            .controlSize(.large)
            .accessibilityIdentifier("home.shuffleAll")
            Label("Home learns from what you play: your albums, artists and genres show up here as you listen.", systemImage: "sparkles")
                .font(.footnote)
                .foregroundStyle(.s2SecondaryText)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityIdentifier("home.coldStartHint")
        }
    }
}

/// A horizontal shelf: tiles in a lazy row that snaps to them, inset to the screen's margin.
struct Shelf<Content: View>: View {
    let inset: CGFloat
    @ViewBuilder let content: () -> Content

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            LazyHStack(alignment: .top, spacing: Spacing.smallMedium) {
                content()
            }
            .scrollTargetLayout()
        }
        .scrollTargetBehavior(.viewAligned)
        .contentMargins(.horizontal, inset, for: .scrollContent)
    }
}

/// An album tile's face: the cover at `ArtworkSize.shelf(tier)` with the tile corner and hairline, its title and a
/// subtitle. The label of whatever says what a tap does (a `NavigationLink` on an artist's screen).
struct AlbumTileLabel: View {
    let album: Album
    let subtitle: String?

    @Environment(\.layoutTier) private var layoutTier

    var body: some View {
        let size = ArtworkSize.shelf(layoutTier)
        VStack(alignment: .leading, spacing: Spacing.xsmall) {
            RemoteArtwork(.album(album), points: size) {
                ArtworkPlaceholder(symbol: "square.stack")
            }
            .artworkTile(size, cornerRadius: ArtworkCorner.tile)
            .padding(.bottom, Spacing.xsmall)
            Text(album.name ?? "Unknown")
                .font(.subheadline.weight(.medium))
                .foregroundStyle(.primary)
                .lineLimit(1)
            if let subtitle {
                Text(subtitle)
                    .font(.caption)
                    .foregroundStyle(.s2SecondaryText)
                    .lineLimit(1)
            }
        }
        .frame(width: size, alignment: .leading)
        .contentShape(Rectangle())
    }
}

/// Which of several tiles showing one item is the zoom source for its screen. An item can be in more than one Home
/// section (or an artist's albums pushed over Home), and a zoom from a tile the user didn't touch would be wrong, as
/// would two live sources sharing one id.
enum ZoomTile {
    /// `id` for the tile last tapped (`activeKey`); otherwise an id of the tile's own, which no screen zooms to.
    static func sourceID(_ id: String, tileKey: String, activeKey: String?) -> String {
        activeKey == tileKey ? id : "tile|\(tileKey)"
    }
}

extension View {
    /// The zoom source for `id` while this tile (`tileKey`) is the last one tapped (`activeKey`), per
    /// `ZoomTile.sourceID`. The id changes rather than the modifier coming and going, so the tile keeps its identity
    /// (and its loaded cover) when it's tapped.
    func zoomSource(id: String, tileKey: String, activeKey: String?) -> some View {
        zoomSource(id: ZoomTile.sourceID(id, tileKey: tileKey, activeKey: activeKey))
    }
}
