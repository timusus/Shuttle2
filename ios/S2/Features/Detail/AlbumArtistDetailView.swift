import Shared
import SwiftUI

/// Album artist detail (P5-7, polished in #624, sectioned in #631): a full-bleed hero behind the bars with the name over
/// it, showing and tinted from the image the shared `ArtistHeroArtwork` rule picks (#781: the artist's own, else their
/// top album's cover; `ArtistHeroPhoto`); albums · songs, Play/Shuffle, and Shuffle by Album in the toolbar's menu), a shelf of the
/// artist's album tiles (each zooming into `Route.album`) while the songs are flat, an Appears On shelf of others'
/// albums crediting them (#637, hidden when empty; a long press on any tile plays or queues it), then the artist's
/// songs in the chosen `ArtistSongSortOrder`: under one sticky, foldable header per album for the album orders, where
/// the headers stand in for the album shelf (#678), or as one flat list. Tapping a song plays every song in the
/// visible order from it, folded albums included. Modeled on Android's `AlbumArtistDetailScreen.kt`.
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
                    models.actions.send(MediaActionPlay(selection: MediaSelectionSongs(songs: songs), position: Int32(index), context: context))
                },
                onShuffle: { songs, context in
                    models.actions.send(MediaActionShuffle(selection: MediaSelectionSongs(songs: songs), context: context))
                },
                onShuffleAlbums: { models.artist.onShuffleAlbums() },
                onPlayNext: { songs in
                    models.actions.send(MediaActionPlayNext(selection: MediaSelectionSongs(songs: songs)))
                },
                onAddToQueue: { songs in
                    models.actions.send(MediaActionAddToQueue(selection: MediaSelectionSongs(songs: songs)))
                },
                onAlbumTap: { navigator.openAsserting(.album($0)) },
                onToggleAlbum: { models.artist.onToggleAlbum(album: $0) },
                onSortOrderSelected: { models.artist.onSortOrderSelected(order: $0) },
                onExpandAll: { models.artist.onExpandAll() },
                onCollapseAll: { models.artist.onCollapseAll() },
                albumActions: DetailAlbumActions(
                    onPlay: { models.actions.send(MediaActionPlay(selection: MediaSelectionAlbums(album: $0), position: 0)) },
                    onPlayNext: { models.actions.send(MediaActionPlayNext(selection: MediaSelectionAlbums(album: $0))) },
                    onAddToQueue: { models.actions.send(MediaActionAddToQueue(selection: MediaSelectionAlbums(album: $0))) }
                )
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
/// picture. `onPlay` plays the given songs from an index: every song in the visible order for a song row, an album's
/// songs for its header's Play.
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
    /// A shelf tile's long-press actions, on the whole album.
    var albumActions = DetailAlbumActions()
    /// The hero's image, already loaded; nil loads it from the artist's hero artwork (`ArtistHeroPhotoReader`). For
    /// tests and previews.
    var heroPhoto: ArtistHeroPhoto?

    @State private var songInfo: SongInfoTarget?

    @Environment(\.layoutTier) private var layoutTier
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

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
        // An artist only credited on others' albums (#637) has none of their own to count
        let subtitle = eyebrow(state.albums.isEmpty ? nil : pluralized(state.albums.count, "album"), pluralized(state.songs.count, "song"))
        // The shared rule's image (#781): the artist's own, the online one only on a confident match, else their top
        // album's cover. It fills the hero and seeds the tint, so the colours follow the image shown.
        let source = ArtworkSource.artistHero(state.hero ?? ArtistHeroArtwork(artist: artist, onlineLookup: false, fallbackAlbum: nil))
        return ArtistHeroPhotoReader(source: source, preset: heroPhoto) { photo in
            DetailScaffold(title: name, tintSource: source, backdrop: ArtistBackdrop(image: photo.image)) { layout in
                if case .bleed(let bleed) = layout {
                    ArtistBleedHero(
                        title: name,
                        subtitle: subtitle,
                        bleed: bleed,
                        onPlay: { onPlay(state.songs, 0, state.playContext) },
                        onShuffle: { onShuffle(state.songs, state.playContext) }
                    )
                }
            } rows: {
                if state.showAlbumsShelf {
                    DetailAlbumShelf(
                        title: "Albums",
                        albums: state.albums,
                        subtitle: { $0.year.map { String($0.intValue) } },
                        onAlbumTap: onAlbumTap,
                        albumActions: albumActions
                    )
                }
                // Grouped, the album sections are the artist's albums, and Appears On follows them (#788)
                if !state.hasAlbumSections {
                    appearsOnShelf
                }
                songs
            }
            .songInfoSheet($songInfo)
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

    /// Others' albums crediting the artist (#637), after the artist's own albums (#788).
    @ViewBuilder private var appearsOnShelf: some View {
        if !state.appearsOn.isEmpty {
            DetailAlbumShelf(
                title: "Appears On",
                albums: state.appearsOn,
                // Whose album it is: the album artist, not the track artists friendlyArtistName joins
                subtitle: { $0.albumArtist ?? $0.friendlyArtistName },
                onAlbumTap: onAlbumTap,
                albumActions: albumActions,
                tileIdentifier: "detailTile.appearsOn"
            )
        }
    }

    // MARK: - Songs

    @ViewBuilder private var songs: some View {
        let inset = AdaptiveLayout.contentInset(layoutTier)
        Section {
            SongsHeader(
                sortOrder: state.sortOrder,
                allExpanded: state.allAlbumsExpanded,
                hasAlbumSections: state.hasAlbumSections,
                onSortOrderSelected: onSortOrderSelected,
                onExpandAll: onExpandAll,
                onCollapseAll: onCollapseAll
            )
            .rowSeparator(.none)
            // The content inset, as the rows under it; trailing less the expand button's touch padding, so its glyph
            // lines up with the rows' chevrons.
            .listRowInsets(EdgeInsets(top: 0, leading: inset, bottom: 0, trailing: max(0, inset - Spacing.smallMedium)))
        }
        // No gap between the header and the first album or song under it: they read as one section.
        .listSectionSpacing(0)
        if state.sortOrder.groupsByAlbum {
            ForEach(indexedSections) { indexed in
                let section = indexed.section
                if let album = section.album {
                    albumSection(album, songs: section.songs, startIndex: indexed.startIndex)
                } else {
                    // Appears On sits between the artist's albums and the songs on none of them
                    if state.hasAlbumSections {
                        appearsOnShelf
                    }
                    Section {
                        songRows(section.songs, startIndex: indexed.startIndex, numbered: false)
                    } header: {
                        Text("Other Songs")
                            .font(.headline)
                            .foregroundStyle(Color.primary)
                            .textCase(nil)
                            .pinnedHeader()
                            .accessibilityAddTraits(.isHeader)
                    }
                }
            }
            // With no songs off the artist's albums, Appears On closes the page
            if state.hasAlbumSections && !state.sections.contains(where: { $0.album == nil }) {
                appearsOnShelf
            }
        } else {
            Section {
                songRows(state.songs, startIndex: 0, numbered: false)
            }
        }
    }

    /// [state.sections] paired with each section's starting offset in [state.songs] (sections flatten to it in
    /// order), so a tap plays from the tapped row's own position rather than a duplicate id's first occurrence.
    private struct IndexedSection: Identifiable {
        let section: AlbumArtistDetailUiState.SongSection
        let startIndex: Int
        var id: String { section.sectionId }
    }

    private var indexedSections: [IndexedSection] {
        var offset = 0
        return state.sections.map { section in
            let indexed = IndexedSection(section: section, startIndex: offset)
            offset += section.songs.count
            return indexed
        }
    }

    /// One album's section. Collapsed, it's only its header, so the headers stack as a run of compact rows (#678):
    /// a small constant gap after the section, and the header's own insets rather than the plain list's taller default.
    /// The gap is the same expanded or collapsed, so expanding never shifts what's above it.
    private func albumSection(_ album: Album, songs: [Song], startIndex: Int) -> some View {
        let expanded = isExpanded(album)
        let inset = AdaptiveLayout.contentInset(layoutTier)
        return Section {
            if expanded {
                songRows(songs, startIndex: startIndex, numbered: true)
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
            .pinnedHeader()
            .listRowInsets(EdgeInsets(top: Spacing.xsmall, leading: inset, bottom: Spacing.xsmall, trailing: inset))
        }
        .listSectionSpacing(Spacing.small)
    }

    /// [startIndex] is where [songs] begins in [state.songs] (its home section's offset), so tapping a row plays
    /// from that row's own position, not from the first song sharing its id.
    private func songRows(_ songs: [Song], startIndex: Int, numbered: Bool) -> some View {
        ForEach(Array(songs.enumerated()), id: \.element.id) { offset, song in
            Button { onPlay(state.songs, startIndex + offset, state.playContext) } label: {
                let playback = rowPlayback(song, current: state.currentSong, isPlaying: isPlaying)
                if numbered {
                    TrackRow(number: song.track.map { Int($0.intValue) }, title: song.name ?? "Unknown", durationMs: Int64(song.duration), playback: playback)
                } else {
                    SongRow(song: song, playback: playback, key: SongRowKey(artistSortOrder: state.sortOrder), omittingArtist: state.albumArtist?.name)
                }
            }
            .buttonStyle(.plain)
            .songContextMenu(song, onPlayNext: onPlayNext, onAddToQueue: onAddToQueue, onSongInfo: { songInfo = SongInfoTarget(songID: $0.id) })
            .rowSeparator(numbered ? .system : .none)
        }
    }

    private func isExpanded(_ album: Album) -> Bool {
        album.groupKey.map { state.expandedAlbums.contains($0) } ?? false
    }
}

/// The song list's section header: "Albums" while the album sections
/// stand in for the album shelf (#678), else "Songs"; then trailing, the sort menu, labelled with the current order
/// (a checkmark on it in the menu), and for the album orders an Expand All / Collapse All icon button.
struct SongsHeader: View {
    let sortOrder: ArtistSongSortOrder
    let allExpanded: Bool
    /// Whether the song list has at least one collapsible album section (#636): an album order with none, only
    /// "Other Songs", hides Expand All / Collapse All rather than showing a toggle with nothing to do.
    var hasAlbumSections: Bool = true
    var onSortOrderSelected: (ArtistSongSortOrder) -> Void = { _ in }
    var onExpandAll: () -> Void = {}
    var onCollapseAll: () -> Void = {}

    private var showsAlbums: Bool { sortOrder.groupsByAlbum && hasAlbumSections }

    var body: some View {
        HStack(alignment: .center, spacing: Spacing.smallMedium) {
            Text(showsAlbums ? "Albums" : "Songs")
                .font(.s2SectionTitle)
                .accessibilityAddTraits(.isHeader)
            Spacer(minLength: Spacing.small)
            Menu {
                Picker("Sort By", selection: Binding(get: { sortOrder }, set: onSortOrderSelected)) {
                    ForEach(ArtistSongSortOrder.menuOrder, id: \.self) { order in
                        Text(order.menuTitle).tag(order)
                    }
                }
            } label: {
                HStack(spacing: Spacing.xsmall) {
                    Text(sortOrder.shortTitle)
                    Image(systemName: "chevron.down")
                        .font(.caption.weight(.bold))
                        .imageScale(.small)
                }
                .font(.subheadline.weight(.medium))
                .foregroundStyle(.tint)
                .contentShape(Rectangle())
            }
            .accessibilityLabel("Sort Songs")
            .accessibilityValue(sortOrder.menuTitle)
            .accessibilityIdentifier("artistDetail.sortMenu")
            if showsAlbums {
                Button(action: allExpanded ? onCollapseAll : onExpandAll) {
                    Label(
                        allExpanded ? "Collapse All" : "Expand All",
                        systemImage: allExpanded ? "rectangle.compress.vertical" : "rectangle.expand.vertical"
                    )
                    .labelStyle(.iconOnly)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.tint)
                    .frame(minWidth: TouchTarget.minimum, minHeight: TouchTarget.minimum)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(allExpanded ? "Collapse All" : "Expand All")
                .accessibilityIdentifier("artistDetail.expandAll")
            }
        }
    }
}

// MARK: - Hero

/// The hero's image (#781): `pending` until it has loaded, then the image, or nil when there's nothing to show. Either
/// way the hero runs full bleed, over a neutral fill until (or unless) an image arrives, so the name and Play/Shuffle
/// show at once.
enum ArtistHeroPhoto: Equatable {
    case loaded(UIImage?)
    case pending

    /// The longest side the image is decoded at, in points: past the widest iPhone and the iPad hero column, without
    /// decoding a server's full-size original.
    static let decodePoints: CGFloat = 520

    /// The image, nil while pending or when nothing loaded.
    var image: UIImage? {
        if case .loaded(let image) = self { image } else { nil }
    }
}

/// Loads the hero's `ArtistHeroPhoto` for `source`, then draws `content` with it. An image already in memory shows in the
/// first frame; otherwise `content` draws `.pending` until the load finishes.
struct ArtistHeroPhotoReader<Content: View>: View {
    let source: ArtworkSource
    var preset: ArtistHeroPhoto?
    @ViewBuilder let content: (ArtistHeroPhoto) -> Content

    @Environment(\.displayScale) private var displayScale
    /// The last load's result, under the source's cache key: the hero's source changes when the artist's albums arrive.
    @State private var loaded: (key: String, photo: ArtistHeroPhoto)?

    private var maxPixelSize: Int { Int((ArtistHeroPhoto.decodePoints * displayScale).rounded(.up)) }

    /// The image without waiting: given, already loaded, or already decoded.
    private var known: ArtistHeroPhoto? {
        if let preset { return preset }
        if let loaded, loaded.key == source.cacheKey { return loaded.photo }
        return ArtworkLoader.shared.cached(source, maxPixelSize: maxPixelSize).map { .loaded($0) }
    }

    var body: some View {
        content(known ?? .pending)
            .task(id: "\(source.cacheKey)@\(maxPixelSize)") {
                guard known == nil else { return }
                let key = source.cacheKey
                let image = await ArtworkLoader.shared.image(for: source, maxPixelSize: maxPixelSize)
                loaded = (key, .loaded(image))
            }
    }
}

/// The hero's backdrop: the image filling the frame, anchored to its top so a portrait photo keeps the faces (a
/// neutral fill while it's pending or when there's none), under a scrim at the top for the status bar and the bar buttons and one
/// at the bottom for the name. Its last `fadeHeight` fades out into whatever is behind it, so the photo melts into the
/// page rather than ending in a hard line; the name sits above the fade.
struct ArtistBackdrop: View {
    /// The photo; nil draws the placeholder.
    let image: UIImage?

    /// The height of the fade at the bottom, which the hero keeps its name above.
    static let fadeHeight: CGFloat = Spacing.xlarge
    /// The top scrim's height: past the status and navigation bars on every iPhone.
    static let topScrimHeight: CGFloat = 120

    var body: some View {
        Color.clear
            .overlay(alignment: .top) {
                if let image {
                    Image(uiImage: image)
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                } else {
                    Color(uiColor: .systemGray3)
                }
            }
            .clipped()
            .overlay(alignment: .top) {
                LinearGradient(
                    stops: [.init(color: .black.opacity(0.35), location: 0), .init(color: .black.opacity(0.15), location: 0.5), .init(color: .black.opacity(0), location: 1)],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .frame(height: Self.topScrimHeight)
            }
            .overlay {
                LinearGradient(
                    stops: [.init(color: .black.opacity(0), location: 0.45), .init(color: .black.opacity(0.7), location: 1)],
                    startPoint: .top,
                    endPoint: .bottom
                )
            }
            .mask {
                VStack(spacing: 0) {
                    Rectangle()
                    LinearGradient(
                        stops: [.init(color: .black, location: 0), .init(color: .black.opacity(0.45), location: 0.5), .init(color: .black.opacity(0), location: 1)],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                    .frame(height: Self.fadeHeight)
                }
            }
            .accessibilityHidden(true)
    }
}

/// The full-bleed hero's foreground: the artist's name, large and leading, and the albums · songs line, in white over
/// the bottom of the backdrop's scrim; then Play/Shuffle under the photo, on the page.
struct ArtistBleedHero: View {
    let title: String
    let subtitle: String?
    let bleed: DetailBleed
    var onPlay: () -> Void = {}
    var onShuffle: () -> Void = {}

    @Environment(\.layoutTier) private var layoutTier
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        let inset = AdaptiveLayout.contentInset(layoutTier)
        VStack(alignment: .leading, spacing: Spacing.medium) {
            VStack(alignment: .leading, spacing: Spacing.xsmall) {
                Text(title)
                    .font(.s2LargeTitle)
                    .foregroundStyle(.white)
                    .lineLimit(dynamicTypeSize.isAccessibilitySize ? nil : 2)
                    .minimumScaleFactor(0.75)
                    .accessibilityAddTraits(.isHeader)
                    .detailTitleProbe()
                if let subtitle, !subtitle.isEmpty {
                    Text(subtitle)
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(.white.opacity(0.85))
                }
            }
            .shadow(color: .black.opacity(0.3), radius: 6, y: 1)
            .padding(.horizontal, inset)
            // Clear of the backdrop's fade, so the name stays on the darkest part of the scrim.
            .padding(.bottom, ArtistBackdrop.fadeHeight)
            .frame(maxWidth: .infinity, minHeight: bleed.height, alignment: .bottomLeading)
            HeroActions(onPlay: onPlay, onShuffle: onShuffle)
                .padding(.horizontal, inset)
                .frame(maxWidth: bleed.isColumn || layoutTier == .compact ? .infinity : ArtworkSize.heroRegular + Spacing.xlarge + inset * 2, alignment: .leading)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private extension AlbumArtistDetailUiState.SongSection {
    /// A stable id for the section: its album's, or the one trailing section without an album.
    var sectionId: String { album.map { "album|\($0.stableId)" } ?? "other" }
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

/// A shelf tile's long-press actions on its whole album, as the Albums list offers them. Nil actions leave the tile
/// without a menu.
struct DetailAlbumActions {
    var onPlay: ((Album) -> Void)?
    var onPlayNext: ((Album) -> Void)?
    var onAddToQueue: ((Album) -> Void)?
}

/// A detail screen's shelf of album tiles under a `SectionHeader`, as one `List` section: a tap opens the tile's
/// album (`onAlbumTap`), and the tapped tile is the zoom source for it. Only the tapped one, as on Home
/// (`ZoomSourceSelection`): the album can also be on a Home shelf or another screen's shelf still live under this
/// screen in the stack. A long press opens
/// `albumActions`, when given.
struct DetailAlbumShelf: View {
    let title: String
    let albums: [Album]
    let subtitle: (Album) -> String?
    let onAlbumTap: (Album) -> Void
    var albumActions = DetailAlbumActions()
    var tileIdentifier = "detailTile.album"

    @Environment(\.layoutTier) private var layoutTier
    @Environment(\.zoomTiles) private var zoomTiles

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
                            zoomTiles?.select(tileKey)
                            onAlbumTap(album)
                        } label: {
                            AlbumTileLabel(album: album, subtitle: subtitle(album))
                        }
                        .buttonStyle(.pressScale)
                        .zoomSource(id: Route.album(album).cacheKey, tileKey: tileKey)
                        .modifier(DetailAlbumMenu(album: album, subtitle: subtitle(album), actions: albumActions))
                        .accessibilityIdentifier(tileIdentifier)
                    }
                }
            }
            .padding(.vertical, Spacing.small)
            .listRowInsets(EdgeInsets())
            .listRowBackground(Color.clear)
        }
        .rowSeparator(.none)
    }
}

/// A shelf tile's context menu from its `DetailAlbumActions`: only the actions given, and none at all without any.
private struct DetailAlbumMenu: ViewModifier {
    let album: Album
    let subtitle: String?
    let actions: DetailAlbumActions

    func body(content: Content) -> some View {
        if actions.onPlay == nil && actions.onPlayNext == nil && actions.onAddToQueue == nil {
            content
        } else {
            // The tile itself as the preview: inside a List the default lifts the whole shelf row.
            content.contextMenu {
                DetailAlbumMenuItems(album: album, actions: actions)
            } preview: {
                AlbumTileLabel(album: album, subtitle: subtitle)
                    .padding(Spacing.smallMedium)
            }
        }
    }
}

/// The items of a shelf tile's context menu, a view of its own so tests can inspect it (ViewInspector can't open a
/// `.contextMenu`).
struct DetailAlbumMenuItems: View {
    let album: Album
    let actions: DetailAlbumActions

    var body: some View {
        if let onPlay = actions.onPlay {
            Button("Play", systemImage: "play") { onPlay(album) }
        }
        if let onPlayNext = actions.onPlayNext {
            Button("Play Next", systemImage: "text.line.first.and.arrowtriangle.forward") { onPlayNext(album) }
        }
        if let onAddToQueue = actions.onAddToQueue {
            Button("Add to Queue", systemImage: "text.append") { onAddToQueue(album) }
        }
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
