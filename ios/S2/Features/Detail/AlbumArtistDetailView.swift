import Shared
import SwiftUI

/// Album artist detail (P5-7, polished in #624, sectioned in #631): a hero tinted from the artist's picture (the
/// artist's picture, name, albums · songs, Play/Shuffle, and Shuffle by Album in the toolbar's menu), the artist's
/// most played songs, a shelf of the artist's album tiles (each zooming into `Route.album`), then the artist's songs
/// in the chosen `ArtistSongSortOrder`: under one sticky, foldable header per album for the album orders, or as one
/// flat list. Tapping a song plays every song in the visible order from it, folded albums included. Modeled on
/// Android's `AlbumArtistDetailScreen.kt`.
struct AlbumArtistDetailView: View {
    let albumArtistKey: String?

    @Environment(Navigator.self) private var navigator: Navigator?

    var body: some View {
        let route = Route.albumArtist(albumArtistKey: albumArtistKey)
        let models = ViewModelCache.shared.viewModel(route.cacheKey) {
            AlbumArtistDetailModels(graph: AppGraph.shared, groupKey: AlbumArtistGroupKey(key: albumArtistKey))
        }
        Observing(models.artist.uiState, models.actions.uiState) { state, actions in
            AlbumArtistDetailContent(
                state: state,
                isPlaying: AppGraph.dependencies.playerBinding.isPlaying,
                onPlay: { songs, index, context in
                    models.actions.dispatch(action: MediaActionPlay(selection: MediaSelectionSongs(songs: songs), position: Int32(index), context: context))
                },
                onShuffle: { songs, context in
                    models.actions.dispatch(action: MediaActionShuffle(selection: MediaSelectionSongs(songs: songs), context: context))
                },
                onShuffleAlbums: { models.artist.onShuffleAlbums() },
                onPlayNext: { songs in
                    models.actions.dispatch(action: MediaActionPlayNext(selection: MediaSelectionSongs(songs: songs)))
                },
                onAddToQueue: { songs in
                    models.actions.dispatch(action: MediaActionAddToQueue(selection: MediaSelectionSongs(songs: songs)))
                },
                onAlbumTap: { navigator.openAsserting(.album($0)) },
                onToggleAlbum: { models.artist.onAlbumClick(album: $0) },
                onSortOrderSelected: { models.artist.onSortOrderSelected(order: $0) },
                onExpandAll: { models.artist.onExpandAll() },
                onCollapseAll: { models.artist.onCollapseAll() }
            )
            .mediaActionResults(actions.events, handled: { models.actions.onEventHandled(id: $0) })
            .albumArtistDetailEvents(state.events, handled: { models.artist.onEventHandled(id: $0) })
        }
    }
}

/// The Album Artist detail screen's ViewModels, cached together under its route's key.
final class AlbumArtistDetailModels: ViewModelGroup {
    let artist: AlbumArtistDetailViewModel
    let actions: MediaActionsViewModel

    init(graph: IosAppGraph, groupKey: AlbumArtistGroupKey) {
        artist = graph.albumArtistDetailViewModelFactory.create(groupKey: groupKey)
        actions = graph.mediaActionsViewModel
    }

    var members: [Lifecycle_viewmodelViewModel] { [artist, actions] }
}

/// The Album Artist detail screen from an `AlbumArtistDetailUiState`, in a `DetailScaffold` tinted from the artist's
/// picture. `onPlay` plays the given songs from an index: every song in the visible order for a song row, the top
/// songs for a top song, an album's songs for its header's Play.
struct AlbumArtistDetailContent: View {
    let state: AlbumArtistDetailUiState
    var isPlaying: Bool = false
    var onPlay: ([Song], Int, PlayContext) -> Void = { _, _, _ in }
    var onShuffle: ([Song], PlayContext) -> Void = { _, _ in }
    var onShuffleAlbums: () -> Void = {}
    var onPlayNext: ([Song]) -> Void = { _ in }
    var onAddToQueue: ([Song]) -> Void = { _ in }
    var onAlbumTap: (Album) -> Void = { _ in }
    var onToggleAlbum: (Album) -> Void = { _ in }
    var onSortOrderSelected: (ArtistSongSortOrder) -> Void = { _ in }
    var onExpandAll: () -> Void = {}
    var onCollapseAll: () -> Void = {}

    @Environment(\.layoutTier) private var layoutTier
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// The Songs header's row id, which Top Songs' See All scrolls to.
    static let songsHeaderId = "artistDetail.songsHeader"

    var body: some View {
        switch state.loadingState {
        case .loading:
            ProgressView()
        case .empty:
            EmptyState("Artist Not Found", systemImage: "person.2")
        case .ready:
            if let artist = state.albumArtist {
                ready(artist)
            } else {
                EmptyState("Artist Not Found", systemImage: "person.2")
            }
        }
    }

    private func ready(_ artist: AlbumArtist) -> some View {
        let name = artist.name ?? artist.friendlyArtistName ?? "Unknown Artist"
        return ScrollViewReader { proxy in
            DetailScaffold(title: name, tintSource: .albumArtist(artist)) { layout in
                DetailHero(
                    title: name,
                    subtitle: eyebrow(pluralized(state.albums.count, "album"), pluralized(state.songs.count, "song")),
                    layout: layout,
                    onPlay: { onPlay(state.songs, 0, state.playContext) },
                    onShuffle: { onShuffle(state.songs, state.playContext) }
                ) { points in
                    RemoteArtwork(.albumArtist(artist), points: points) {
                        ArtworkPlaceholder(symbol: "person.fill")
                    }
                    .artworkTile(points, cornerRadius: ArtworkCorner.hero)
                }
            } rows: {
                if !state.topSongs.isEmpty {
                    topSongs {
                        onSortOrderSelected(.mostPlayed)
                        withAnimation(Motion.disclosure.reduced(reduceMotion)) {
                            proxy.scrollTo(Self.songsHeaderId, anchor: .top)
                        }
                    }
                }
                if !state.albums.isEmpty {
                    DetailAlbumShelf(title: "Albums", albums: state.albums, subtitle: { $0.year.map { String($0.intValue) } }, onAlbumTap: onAlbumTap)
                }
                songs
            }
            .animation(Motion.disclosure.reduced(reduceMotion), value: state.expandedAlbums)
            .toolbar {
                Menu {
                    Button("Shuffle by Album", systemImage: "square.stack", action: onShuffleAlbums)
                } label: {
                    Label("More", systemImage: "ellipsis.circle")
                }
                .accessibilityIdentifier("artistDetail.more")
            }
        }
    }

    // MARK: - Top Songs

    /// How many top songs show before See All: five on compact, all of them (the ViewModel keeps ten) on regular.
    static func topSongsLimit(_ tier: LayoutTier) -> Int {
        tier == .compact ? 5 : Int(AlbumArtistDetailUiState.companion.TOP_SONGS_LIMIT)
    }

    private func topSongs(seeAll: @escaping () -> Void) -> some View {
        let limit = Self.topSongsLimit(layoutTier)
        let shown = Array(state.topSongs.prefix(limit))
        return Section {
            Group {
                if state.topSongs.count > limit {
                    SectionHeader("Top Songs", seeAllAction: seeAll)
                } else {
                    SectionHeader("Top Songs")
                }
            }
            .listRowSeparator(.hidden)
            ForEach(Array(shown.enumerated()), id: \.element.id) { index, song in
                Button { onPlay(state.topSongs, index, state.playContext) } label: {
                    DetailSongRow(song: song, playback: rowPlayback(song, current: state.currentSong, isPlaying: isPlaying))
                }
                .buttonStyle(.plain)
                .songContextMenu(song, onPlayNext: onPlayNext, onAddToQueue: onAddToQueue)
                .accessibilityIdentifier("artistDetail.topSong")
            }
        }
    }

    // MARK: - Songs

    @ViewBuilder private var songs: some View {
        let indexById = Dictionary(state.songs.enumerated().map { ($0.element.id, $0.offset) }, uniquingKeysWith: { first, _ in first })
        Section {
            SongsHeader(
                sortOrder: state.sortOrder,
                allExpanded: allExpanded,
                onSortOrderSelected: onSortOrderSelected,
                onExpandAll: onExpandAll,
                onCollapseAll: onCollapseAll
            )
            .listRowSeparator(.hidden)
            .id(Self.songsHeaderId)
        }
        if state.sortOrder.groupsByAlbum {
            ForEach(state.sections, id: \.sectionId) { section in
                if let album = section.album {
                    albumSection(album, songs: section.songs, indexById: indexById)
                } else {
                    Section {
                        songRows(section.songs, indexById: indexById, numbered: false)
                    } header: {
                        Text("Other Songs")
                            .font(.headline)
                            .foregroundStyle(Color.primary)
                            .textCase(nil)
                            .accessibilityAddTraits(.isHeader)
                    }
                }
            }
        } else {
            Section {
                songRows(state.songs, indexById: indexById, numbered: false)
            }
        }
    }

    private func albumSection(_ album: Album, songs: [Song], indexById: [Int64: Int]) -> some View {
        let expanded = isExpanded(album)
        return Section {
            if expanded {
                songRows(songs, indexById: indexById, numbered: true)
            }
        } header: {
            AlbumSectionHeader(
                album: album,
                songCount: songs.count,
                isExpanded: expanded,
                onToggle: { onToggleAlbum(album) },
                onPlay: { onPlay(songs, 0, album.playContext) },
                onShuffle: { onShuffle(songs, album.playContext) },
                onPlayNext: { onPlayNext(songs) },
                onAddToQueue: { onAddToQueue(songs) },
                onOpenAlbum: { onAlbumTap(album) }
            )
        }
    }

    private func songRows(_ songs: [Song], indexById: [Int64: Int], numbered: Bool) -> some View {
        ForEach(songs, id: \.id) { song in
            Button { onPlay(state.songs, indexById[song.id] ?? 0, state.playContext) } label: {
                let playback = rowPlayback(song, current: state.currentSong, isPlaying: isPlaying)
                if numbered {
                    TrackRow(number: song.track.map { Int($0.intValue) }, title: song.name ?? "Unknown", durationMs: Int64(song.duration), playback: playback)
                } else {
                    DetailSongRow(song: song, playback: playback)
                }
            }
            .buttonStyle(.plain)
            .songContextMenu(song, onPlayNext: onPlayNext, onAddToQueue: onAddToQueue)
        }
    }

    private func isExpanded(_ album: Album) -> Bool {
        album.groupKey.map { state.expandedAlbums.contains($0) } ?? false
    }

    /// Whether every album section is unfolded, which turns the header's Expand All into Collapse All.
    private var allExpanded: Bool {
        let albums = state.sections.compactMap(\.album)
        return !albums.isEmpty && albums.allSatisfy(isExpanded)
    }
}

/// The Songs header: the title, the sort menu (a checkmark on the current order) and, for the album orders, Expand
/// All or Collapse All.
struct SongsHeader: View {
    let sortOrder: ArtistSongSortOrder
    let allExpanded: Bool
    var onSortOrderSelected: (ArtistSongSortOrder) -> Void = { _ in }
    var onExpandAll: () -> Void = {}
    var onCollapseAll: () -> Void = {}

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: Spacing.medium) {
            Text("Songs")
                .font(.s2SectionTitle)
                .accessibilityAddTraits(.isHeader)
            Spacer(minLength: Spacing.small)
            if sortOrder.groupsByAlbum {
                Button(allExpanded ? "Collapse All" : "Expand All", action: allExpanded ? onCollapseAll : onExpandAll)
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(.tint)
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("artistDetail.expandAll")
            }
            Menu {
                Picker("Sort By", selection: Binding(get: { sortOrder }, set: onSortOrderSelected)) {
                    ForEach(ArtistSongSortOrder.menuOrder, id: \.self) { order in
                        Text(order.menuTitle).tag(order)
                    }
                }
            } label: {
                Label("Sort", systemImage: "arrow.up.arrow.down")
                    .labelStyle(.iconOnly)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.tint)
                    .contentShape(Rectangle())
            }
            .accessibilityLabel("Sort Songs")
            .accessibilityValue(sortOrder.menuTitle)
            .accessibilityIdentifier("artistDetail.sortMenu")
        }
    }
}

private extension AlbumArtistDetailUiState.SongSection {
    /// A stable id for the section: its album's, or the one trailing section without an album.
    var sectionId: String { album.map { "album|\($0.stableId)" } ?? "other" }
}

private extension View {
    func songContextMenu(_ song: Song, onPlayNext: @escaping ([Song]) -> Void, onAddToQueue: @escaping ([Song]) -> Void) -> some View {
        contextMenu {
            Button("Play Next", systemImage: "text.line.first.and.arrowtriangle.forward") { onPlayNext([song]) }
            Button("Add to Queue", systemImage: "text.append") { onAddToQueue([song]) }
        }
    }
}

extension View {
    /// `AlbumArtistDetailViewModel`'s own events: a Shuffle by Album that couldn't start.
    func albumArtistDetailEvents(_ events: [PendingEvent<any AlbumArtistDetailEvent>], handled: @escaping (Int64) -> Void) -> some View {
        modifier(AlbumArtistDetailEventsModifier(events: events, handled: handled))
    }
}

private struct AlbumArtistDetailEventsModifier: ViewModifier {
    let events: [PendingEvent<any AlbumArtistDetailEvent>]
    let handled: (Int64) -> Void
    @State private var alert: String?

    func body(content: Content) -> some View {
        content
            .consumeEvents(events, handled: handled) { event in
                if let failed = event as? AlbumArtistDetailEventShuffleAlbumsFailed {
                    alert = failed.reason.map { "Couldn't shuffle: \($0)" } ?? "Couldn't shuffle."
                }
            }
            .alert(alert ?? "", isPresented: Binding(get: { alert != nil }, set: { if !$0 { alert = nil } })) {
                Button("OK", role: .cancel) {}
            }
    }
}

/// A detail screen's shelf of album tiles under a `SectionHeader`, as one `List` section: a tap opens the tile's
/// album (`onAlbumTap`), and the tapped tile is the zoom source for it. Only the tapped one, as on Home
/// (`ZoomTile`): the album can also be on a Home shelf still live under this screen in the stack.
struct DetailAlbumShelf: View {
    let title: String
    let albums: [Album]
    let subtitle: (Album) -> String?
    let onAlbumTap: (Album) -> Void

    @Environment(\.layoutTier) private var layoutTier
    @State private var zoomSourceKey: String?

    var body: some View {
        let inset = AdaptiveLayout.contentInset(layoutTier)
        Section {
            VStack(alignment: .leading, spacing: Spacing.smallMedium) {
                SectionHeader(title)
                    .padding(.horizontal, inset)
                Shelf(inset: inset) {
                    ForEach(albums, id: \.stableId) { album in
                        let tileKey = "detailShelf|\(album.stableId)"
                        Button {
                            zoomSourceKey = tileKey
                            onAlbumTap(album)
                        } label: {
                            AlbumTileLabel(album: album, subtitle: subtitle(album))
                        }
                        .buttonStyle(.pressScale)
                        .zoomSource(id: Route.album(album).cacheKey, tileKey: tileKey, activeKey: zoomSourceKey)
                        .accessibilityIdentifier("detailTile.album")
                    }
                }
            }
            .padding(.vertical, Spacing.small)
            .listRowInsets(EdgeInsets())
            .listRowBackground(Color.clear)
        }
        .listRowSeparator(.hidden)
    }
}

extension Optional where Wrapped == Navigator {
    /// Opens `route` on the environment's `Navigator`. The app always provides one (`ContentView`), so a missing one
    /// is a wiring bug: a debug build stops on it rather than a tap silently doing nothing.
    @MainActor
    func openAsserting(_ route: Route) {
        guard let navigator = self else {
            assertionFailure("No Navigator in the environment to open \(route)")
            return
        }
        navigator.open(route)
    }
}
