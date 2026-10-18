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
    @Environment(\.appServices) private var appServices
    private var services: AppServices { appServices.required }

    var body: some View {
        let route = Route.albumArtist(albumArtistKey: albumArtistKey)
        let models = services.viewModels.viewModel(route.cacheKey) {
            AlbumArtistDetailModels(graph: services.graph, groupKey: AlbumArtistGroupKey(key: albumArtistKey))
        }
        Observing(models.artist.uiState, models.actions.uiState) { state, actions in
            AlbumArtistDetailContent(
                state: state,
                isPlaying: services.playerBinding.isPlaying,
                onPlay: { songs, index, context in
                    models.actions.send(MediaActionPlay(selection: MediaSelectionSongs(songs: songs), position: Int32(index), context: context), intent: services.playIntent)
                },
                onShuffle: { songs, context in
                    models.actions.send(MediaActionShuffle(selection: MediaSelectionSongs(songs: songs), context: context), intent: services.playIntent)
                },
                onShuffleAlbums: { models.artist.onShuffleAlbums() },
                onPlayNext: { songs in
                    models.actions.send(MediaActionPlayNext(selection: MediaSelectionSongs(songs: songs)), intent: services.playIntent)
                },
                onAddToQueue: { songs in
                    models.actions.send(MediaActionAddToQueue(selection: MediaSelectionSongs(songs: songs)), intent: services.playIntent)
                },
                onAlbumTap: { navigator.openAsserting(.album($0)) },
                onToggleAlbum: { models.artist.onToggleAlbum(album: $0) },
                onSortOrderSelected: { models.artist.onSortOrderSelected(order: $0) },
                onExpandAll: { models.artist.onExpandAll() },
                onCollapseAll: { models.artist.onCollapseAll() },
                albumActions: DetailAlbumActions(
                    onPlay: { models.actions.send(MediaActionPlay(selection: MediaSelectionAlbums(album: $0), position: 0), intent: services.playIntent) },
                    onPlayNext: { models.actions.send(MediaActionPlayNext(selection: MediaSelectionAlbums(album: $0)), intent: services.playIntent) },
                    onAddToQueue: { models.actions.send(MediaActionAddToQueue(selection: MediaSelectionAlbums(album: $0)), intent: services.playIntent) }
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
        let subtitle = eyebrow(state.albums.isEmpty ? nil : pluralized(state.albums.count, .album), pluralized(state.songs.count, .song))
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
                        SectionHeader("Other Songs")
                            .textCase(nil)
                            .pinnedHeader()
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
