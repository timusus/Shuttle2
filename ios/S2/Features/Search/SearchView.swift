import os
import Shared
import SwiftUI

/// Search (#589): the shared `SearchViewModel` behind the system search field, focused when the tab opens with
/// nothing typed. Empty, it lists the recent searches (tap to search again, swipe to forget); typing shows the
/// results as Android's screen does: the top result, then Artists, Albums, Songs, Genres and Playlists, each capped
/// with a See All that expands it in place (`SearchResults.sections`). The type chips narrow the search and are kept
/// by the ViewModel. A tap opens the artist, album, genre or playlist, or plays every song result from the tapped
/// one (`SearchViewModel.playSong`); either keeps the query as a recent search. Long press plays or queues through
/// the shared `MediaAction`s.
struct SearchView: View {
    let navigator: Navigator

    @State private var query = ""
    @State private var isSearchPresented = false

    var body: some View {
        let models = ViewModelCache.shared.viewModel(AppTab.search.cacheKey) {
            SearchModels(graph: AppGraph.shared)
        }
        LibraryNowPlayingReader { nowPlaying in
            Observing(models.search.uiState, models.actions.uiState) { state, actions in
                SearchContentView(
                    state: state,
                    nowPlaying: nowPlaying,
                    onSelectRecent: { query = $0 },
                    onRemoveRecent: { models.search.onRemoveRecentSearch(query: $0) },
                    onSelectAll: { models.search.onSelectAll() },
                    onToggleCategory: { models.search.onToggleCategory(category: $0) },
                    onOpen: { route in
                        models.search.onResultChosen()
                        navigator.open(route)
                    },
                    onPlaySong: { index in
                        if let action = models.search.playSong(index: Int32(index)) { models.actions.send(action) }
                    },
                    onPlay: { models.actions.send(models.search.play(selection: $0)) },
                    onAction: { models.actions.send($0) }
                )
                .mediaActionResults(actions.events, handled: { models.actions.onEventHandled(id: $0) }, send: { models.actions.send($0) })
            }
        }
        .navigationTitle(AppTab.search.title)
        .searchable(text: $query, isPresented: $isSearchPresented, placement: .navigationBarDrawer(displayMode: .always), prompt: "Artists, Albums, Songs")
        .onSubmit(of: .search) { models.search.onSearch() }
        .onChange(of: query) { _, new in models.search.onQueryChange(query: new) }
        .onAppear {
            // The field and the ViewModel agree on the query, whichever outlived the other.
            models.search.onQueryChange(query: query)
            // Arriving with nothing typed means the user wants to type.
            if query.isEmpty { isSearchPresented = true }
        }
    }
}

/// The Search screen's ViewModels, cached together under its tab's key.
final class SearchModels: ViewModelGroup {
    let search: SearchViewModel
    let actions: MediaActionsViewModel

    init(graph: IosAppGraph) {
        search = graph.searchViewModel
        actions = graph.mediaActionsViewModel
    }

    var members: [Lifecycle_viewmodelViewModel] { [search, actions] }
}

/// Search from a `SearchUiState`: the type chips over the recent searches, the searching state, no results, or the
/// results.
struct SearchContentView: View {
    let state: SearchUiState
    var nowPlaying: LibraryNowPlaying = .none
    var onSelectRecent: (String) -> Void = { _ in }
    var onRemoveRecent: (String) -> Void = { _ in }
    var onSelectAll: () -> Void = {}
    var onToggleCategory: (SearchCategory) -> Void = { _ in }
    /// Opens a result's screen.
    var onOpen: (Route) -> Void = { _ in }
    /// Plays the song results from this index.
    var onPlaySong: (Int) -> Void = { _ in }
    /// Plays a top result that isn't a song, keeping the query as a recent search.
    let onPlay: (MediaSelection) -> Void
    /// Dispatches a long-press play or queue action.
    var onAction: (MediaAction) -> Void = { _ in }

    var body: some View {
        content
            .pinnedTopBar { typeChips }
    }

    /// The type chips pinned over the content. Their own property so tests can reach them: they can't see into
    /// iOS 26's `safeAreaBar`.
    @ViewBuilder
    var typeChips: some View {
        if showsChips {
            SearchCategoryChips(selected: state.categories, onSelectAll: onSelectAll, onToggle: onToggleCategory)
                .pinnedBarBackground()
        }
    }

    /// The type chips narrow a query, so they're shown once there is one.
    private var showsChips: Bool {
        !(state.content is SearchContentRecent)
    }

    @ViewBuilder
    private var content: some View {
        switch onEnum(of: state.content) {
        case .recent(let recent):
            if recent.searches.isEmpty {
                EmptyState("Search Your Library", systemImage: "magnifyingglass", message: "Find artists, albums, songs, genres and playlists.")
            } else {
                SearchRecentList(searches: recent.searches, onSelect: onSelectRecent, onRemove: onRemoveRecent)
            }
        case .searching:
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .accessibilityIdentifier("search.searching")
        case .noResults(let noResults):
            ContentUnavailableView.search(text: noResults.query)
                .accessibilityIdentifier("search.noResults")
        case .results(let results):
            SearchResultList(
                query: results.query,
                results: results.results,
                nowPlaying: nowPlaying,
                onOpen: onOpen,
                onPlaySong: onPlaySong,
                onPlay: onPlay,
                onAction: onAction
            )
        }
    }
}

/// The recent searches, newest first: a tap searches again, a swipe forgets one.
struct SearchRecentList: View {
    let searches: [String]
    let onSelect: (String) -> Void
    let onRemove: (String) -> Void

    var body: some View {
        List {
            Section {
                ForEach(searches, id: \.self) { search in
                    Button { onSelect(search) } label: {
                        HStack(spacing: Spacing.small) {
                            Image(systemName: "clock.arrow.circlepath")
                                .frame(width: IconSize.large)
                            // A text-only row: its separator starts at the title.
                            Text(search)
                                .rowSeparator(.insetToTitle)
                        }
                        .foregroundStyle(.primary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("search.recent")
                    .swipeActions(edge: .trailing) {
                        Button(role: .destructive) { onRemove(search) } label: {
                            Label("Remove", systemImage: "trash")
                        }
                    }
                }
            } header: {
                SectionHeader("Recent Searches")
                    .textCase(nil)
                    .pinnedHeader()
            }
        }
        .listStyle(.plain)
    }
}

/// The All chip, then one per type; All is on when no type is.
struct SearchCategoryChips: View {
    let selected: Set<SearchCategory>
    let onSelectAll: () -> Void
    let onToggle: (SearchCategory) -> Void

    @Environment(\.layoutTier) private var layoutTier

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Spacing.small) {
                FilterChip(title: "All", isSelected: selected.isEmpty, identifier: "searchChip.all", action: onSelectAll)
                ForEach(SearchCategory.allCases, id: \.self) { category in
                    FilterChip(title: category.title, isSelected: selected.contains(category), identifier: "searchChip.\(category.title.lowercased())") {
                        onToggle(category)
                    }
                }
            }
            .font(.subheadline.weight(.semibold))
            .padding(.horizontal, AdaptiveLayout.contentInset(layoutTier))
        }
        .scrollBounceBehavior(.basedOnSize, axes: .horizontal)
        .padding(.vertical, Spacing.small)
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Result Types")
    }
}

extension SearchCategory {
    /// What one result of the group is, for the top result's label: "Artist", "Album", "Song", "Genre" or "Playlist".
    var kindLabel: String {
        switch self {
        case .artists: "Artist"
        case .albums: "Album"
        case .songs: "Song"
        case .genres: "Genre"
        case .playlists: "Playlist"
        }
    }

    var title: String {
        switch self {
        case .artists: "Artists"
        case .albums: "Albums"
        case .songs: "Songs"
        case .genres: "Genres"
        case .playlists: "Playlists"
        }
    }
}

/// The results: the best match of all first, lifted out of its group, then each group as `SearchResults.sections`
/// lays it out. See All expands a group in place, until the query changes.
struct SearchResultList: View {
    let query: String
    let results: SearchResults
    var nowPlaying: LibraryNowPlaying
    let onOpen: (Route) -> Void
    let onPlaySong: (Int) -> Void
    /// Plays a top result that isn't a song, which keeps the query as a recent search (`SearchViewModel.play`).
    let onPlay: (MediaSelection) -> Void
    let onAction: (MediaAction) -> Void
    /// The rows' items, read across the bridge once for these results.
    private let items: SearchResultItems

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @State private var expanded: SearchCategory?
    @State private var songInfo: SongInfoTarget?

    init(
        query: String,
        results: SearchResults,
        nowPlaying: LibraryNowPlaying = .none,
        onOpen: @escaping (Route) -> Void,
        onPlaySong: @escaping (Int) -> Void,
        onPlay: @escaping (MediaSelection) -> Void,
        onAction: @escaping (MediaAction) -> Void
    ) {
        self.query = query
        self.results = results
        self.nowPlaying = nowPlaying
        self.onOpen = onOpen
        self.onPlaySong = onPlaySong
        self.onPlay = onPlay
        self.onAction = onAction
        items = SearchResultItems(results)
    }

    var body: some View {
        List {
            if let top = results.top {
                Section {
                    topResultCard(top)
                        .rowSeparator(.none)
                } header: {
                    SectionHeader("Top Result").textCase(nil).pinnedHeader()
                }
            }
            ForEach(results.sections(expanded: expanded), id: \.category) { section in
                Section {
                    ForEach(Int(section.from)..<Int(section.until), id: \.self) { index in
                        row(section.category, index: index).rowSeparator(.none)
                    }
                } header: {
                    header(section).textCase(nil).pinnedHeader()
                }
            }
        }
        .listStyle(.plain)
        .onChange(of: query) { expanded = nil }
        .songInfoSheet($songInfo)
        .accessibilityIdentifier("search.results")
    }

    /// The best match as a card: its kind in the tint, a larger row, and a Play button that plays it without opening it.
    private func topResultCard(_ top: SearchCategory) -> some View {
        VStack(alignment: .leading, spacing: Spacing.small) {
            Text(top.kindLabel)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.tint)
                .accessibilityIdentifier("search.topResult.kind")
            let layout = dynamicTypeSize.isAccessibilitySize
                ? AnyLayout(VStackLayout(alignment: .leading, spacing: Spacing.small))
                : AnyLayout(HStackLayout(spacing: Spacing.small))
            layout {
                row(top, index: 0, artworkSize: Self.topResultArtwork)
                Button { playTop(top) } label: {
                    Label("Play \(topTitle(top))", systemImage: "play.fill").labelStyle(.iconOnly)
                }
                .buttonStyle(.borderedProminent)
                .buttonBorderShape(.circle)
                .controlSize(.large)
                .accessibilityIdentifier("search.topResult.play")
            }
        }
        .padding(Spacing.smallMedium)
        .background(.quaternary, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }

    private static let topResultArtwork: CGFloat = 96

    /// The top result's title, for its Play button's label.
    private func topTitle(_ category: SearchCategory) -> String {
        switch category {
        case .artists: AlbumArtistRow.title(items.artists[0])
        case .albums: items.albums[0].name ?? "Unknown"
        case .songs: items.songs[0].name ?? "Unknown"
        case .genres: items.genres[0].name.trimmingCharacters(in: .whitespacesAndNewlines)
        case .playlists: items.playlists[0].name
        }
    }

    private func playTop(_ category: SearchCategory) {
        switch category {
        case .artists: onPlay(MediaSelectionAlbumArtists(albumArtist: items.artists[0]))
        case .albums: onPlay(MediaSelectionAlbums(album: items.albums[0]))
        case .songs: onPlaySong(0)
        case .genres: onPlay(MediaSelectionGenres(genre: items.genres[0]))
        case .playlists: onPlay(MediaSelectionPlaylists(playlist: items.playlists[0]))
        }
    }

    @ViewBuilder
    private func header(_ section: SearchSection) -> some View {
        if section.hasMore {
            SectionHeader(section.category.title) { expanded = section.category }
        } else {
            SectionHeader(section.category.title)
        }
    }

    /// The `index`th hit of `category`'s group. Ids are the category and index, stable for a given result set.
    @ViewBuilder
    private func row(_ category: SearchCategory, index: Int, artworkSize: CGFloat? = nil) -> some View {
        switch category {
        case .artists:
            let artist = items.artists[index]
            resultLink(.albumArtist(artist), identifier: "search.result.artist") {
                MediaRow(
                    AlbumArtistRow.title(artist),
                    subtitle: AlbumArtistRow.subtitle(artist),
                    artwork: .albumArtist(artist),
                    artworkSize: artworkSize ?? ArtworkSize.row,
                    placeholderSymbol: "music.mic",
                    playback: nowPlaying.playback(albumArtist: artist)
                )
            }
            .contextMenu { menu(MediaSelectionAlbumArtists(albumArtist: artist)) }
        case .albums:
            let album = items.albums[index]
            resultLink(.album(album), identifier: "search.result.album") {
                AlbumRow(album: album, playback: nowPlaying.playback(album: album), artworkSize: artworkSize ?? ArtworkSize.albumRow)
            }
                .contextMenu { menu(MediaSelectionAlbums(album: album)) }
        case .songs:
            let song = items.songs[index]
            Button { onPlaySong(index) } label: {
                SongRow(song: song, playback: nowPlaying.playback(song: song), artworkSize: artworkSize ?? ArtworkSize.row)
            }
                .buttonStyle(.pressScale)
                .accessibilityIdentifier("search.result.song")
                .contextMenu {
                    SongRowMenu(
                        song: song,
                        onPlayNext: { onAction(MediaActionPlayNext(selection: MediaSelectionSongs(song: $0))) },
                        onAddToQueue: { onAction(MediaActionAddToQueue(selection: MediaSelectionSongs(song: $0))) },
                        onExclude: { onAction(MediaActionExclude(selection: MediaSelectionSongs(song: $0))) },
                        onSongInfo: { songInfo = SongInfoTarget(songID: $0.id) }
                    )
                }
        case .genres:
            let genre = items.genres[index]
            resultLink(.genre(genre), identifier: "search.result.genre") { GenreRow(genre: genre, artworkSize: artworkSize ?? ArtworkSize.row) }
                .contextMenu { menu(MediaSelectionGenres(genre: genre)) }
        case .playlists:
            let playlist = items.playlists[index]
            resultLink(.playlist(playlist), identifier: "search.result.playlist") { PlaylistRow(playlist: playlist, artworkSize: artworkSize ?? ArtworkSize.row) }
                .contextMenu { menu(MediaSelectionPlaylists(playlist: playlist)) }
        }
    }

    /// A row that opens `route` through `onOpen`, so the query is kept as a recent search before the push.
    private func resultLink<Label: View>(_ route: Route, identifier: String, @ViewBuilder label: () -> Label) -> some View {
        Button { onOpen(route) } label: {
            label()
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
        }
        .buttonStyle(.pressScale)
        .accessibilityIdentifier(identifier)
    }

    @ViewBuilder
    private func menu(_ selection: MediaSelection) -> some View {
        Button("Play", systemImage: "play") { onAction(MediaActionPlay(selection: selection, position: 0)) }
        Button("Play Next", systemImage: "text.line.first.and.arrowtriangle.forward") { onAction(MediaActionPlayNext(selection: selection)) }
        Button("Add to Queue", systemImage: "text.append") { onAction(MediaActionAddToQueue(selection: selection)) }
    }
}

/// A `SearchResults`' hits as Swift arrays. Every read of one of its Kotlin lists from Swift copies the whole list, so
/// reading them per row made laying out N rows cost N copies (#677); the rows read these instead, copied once. The
/// "Search results" signpost interval times it in Instruments.
struct SearchResultItems {
    static let signposter = OSSignposter(subsystem: "com.simplecityapps.shuttle", category: "Search")

    let artists: [AlbumArtist]
    let albums: [Album]
    let songs: [Song]
    let genres: [Genre]
    let playlists: [Playlist]

    init(_ results: SearchResults) {
        let interval = Self.signposter.beginInterval("Search results")
        defer { Self.signposter.endInterval("Search results", interval) }
        artists = results.artists.map { $0.item! }
        albums = results.albums.map { $0.item! }
        songs = results.songs.map { $0.item! }
        genres = results.genres.map { $0.item! }
        playlists = results.playlists.map { $0.item! }
    }
}
