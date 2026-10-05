import CarPlay
import Shared
import UIKit

/// The CarPlay scene (#692, from Shuttle Podcasts): a tab bar of Home, Playlists and Library, drawn from the shared
/// view models the phone's screens use. Home is the phone's Home for the car: each shelf, in the phone's order, a row
/// of artwork whose images play (Jump Back In's resume) and whose row pushes the whole shelf; offline it shows only what
/// can play. Playlists lists the auto playlists and the user's own; Library pushes Artists, Albums, Songs and Genres.
/// Albums, artists, genres and playlists push their songs; a song plays its list from it, Shuffle shuffles the list.
/// Playback goes through `MediaActionsViewModel`, as the phone's does, and Now Playing's transport is the remote
/// commands `NowPlayingController` already registers.
@MainActor
final class CarPlaySceneDelegate: UIResponder, CPTemplateApplicationSceneDelegate, CPInterfaceControllerDelegate {
    private var interfaceController: CPInterfaceController?
    private var nowPlaying: CarPlayNowPlayingController?
    private var actions: MediaActionsViewModel?
    private var home: HomeViewModel?
    private var network: CarPlayNetworkMonitor?
    /// The tabs' view models and their observers, for the connection's life.
    private var viewModels: [Lifecycle_viewmodelViewModel] = []
    private var tasks: [Task<Void, Never>] = []
    /// What each pushed list observes, until it leaves the stack.
    private var pushed: [ObjectIdentifier: PushedList] = [:]
    private var lastRendered: [ObjectIdentifier: [CarPlaySectionModel]] = [:]

    private let homeTemplate = CPListTemplate(title: CarPlayText.home, sections: [])
    private let playlistsTemplate = CPListTemplate(title: CarPlayText.playlists, sections: [])
    private let libraryTemplate = CPListTemplate(title: CarPlayText.library, sections: [])
    // Library's lists, pushed from it; they follow their view models for the connection's life
    private let artistsTemplate = CPListTemplate(title: CarPlayText.artists, sections: [])
    private let albumsTemplate = CPListTemplate(title: CarPlayText.albums, sections: [])
    private let songsTemplate = CPListTemplate(title: CarPlayText.songs, sections: [])
    private let genresTemplate = CPListTemplate(title: CarPlayText.genres, sections: [])

    // The latest state of each list, which its rows' ids resolve against
    private var homeState: HomeUiState = HomeUiStateLoading.shared
    private var homeSections: [CarPlayHomeSection] = []
    private var homeItems: [String: HomeItem] = [:]
    /// The Home shelf pushed from its row, which follows Home's state while it's on the stack.
    private var openShelf: (id: String, template: CPListTemplate)?
    private var albums: [String: Album] = [:]
    private var artists: [String: AlbumArtist] = [:]
    private var genres: [String: Genre] = [:]
    private var playlists: [String: Playlist] = [:]
    private var smartPlaylists: [String: SmartPlaylist] = [:]
    private var songs: [Song] = []
    private var downloads: [String: OfflineDownload] = [:]
    /// What plays offline, built when Home is drawn offline and dropped when the library or the downloads change.
    private var offlineIndex: CarPlayOfflineIndex?
    /// The ids of the playlists with a song that plays offline, nil until known; restarted when the playlists or the downloads change.
    private var playablePlaylists: Set<Int64>?
    private var playablePlaylistsTask: Task<Void, Never>?
    private var playablePlaylistsInputs: PlayablePlaylistsInputs?

    private struct PlayablePlaylistsInputs: Equatable {
        let playlistIds: Set<Int64>
        let downloaded: Set<String>
    }

    private var isOffline: Bool { !(network?.isOnline ?? true) }

    private struct PushedList {
        let viewModel: Lifecycle_viewmodelViewModel
        let task: Task<Void, Never>
    }

    /// The songs a pushed list shows, and how it plays them.
    private struct SongList {
        let songs: [Song]
        let context: PlayContext
        let currentSongId: Int64?
        let loading: Bool
    }

    // MARK: - Scene lifecycle

    func templateApplicationScene(
        _ templateApplicationScene: CPTemplateApplicationScene,
        didConnect interfaceController: CPInterfaceController
    ) {
        self.interfaceController = interfaceController
        interfaceController.delegate = self
        // The car can launch the app on its own, with no phone scene; S2App.init has built the graph by then, but
        // building it here too costs nothing (it's idempotent) and a CarPlay-only launch never sees an empty graph.
        AppGraph.initialize()
        let graph = AppGraph.shared

        let tabs: [(CPListTemplate, String, String)] = [
            (homeTemplate, CarPlayText.home, "house"),
            (playlistsTemplate, CarPlayText.playlists, "music.note.list"),
            (libraryTemplate, CarPlayText.library, "square.stack"),
        ]
        for (template, title, symbol) in tabs {
            template.tabTitle = title
            template.tabImage = UIImage(systemName: symbol)
        }
        for template in [homeTemplate, playlistsTemplate, artistsTemplate, albumsTemplate, songsTemplate, genresTemplate] {
            render(CarPlayCatalog.loading(), into: template)
        }
        render(CarPlayCatalog.library(), into: libraryTemplate)
        // The car decides how many tabs it draws, and CPTabBarTemplate throws, not truncates, past that. Now
        // Playing is never a tab: the car offers it itself, and it's pushed whenever something starts playing.
        let tabBar = CPTabBarTemplate(templates: Array(tabs.map(\.0).prefix(CPTabBarTemplate.maximumTabCount)))
        interfaceController.setRootTemplate(tabBar, animated: true, completion: nil)

        let nowPlaying = CarPlayNowPlayingController(binding: AppGraph.dependencies.playerBinding)
        nowPlaying.attach { [weak self] in self?.pushQueue() }
        self.nowPlaying = nowPlaying
        actions = graph.mediaActionsViewModel
        network = CarPlayNetworkMonitor { [weak self] _ in
            self?.observePlayablePlaylists()
            self?.renderHome()
        }

        observeHome(graph.homeViewModel)
        observePlaylists(graph.playlistListViewModel)
        observeAlbums(graph.albumListViewModel)
        observeArtists(graph.albumArtistListViewModel)
        observeGenres(graph.genreListViewModel)
        observeSongs(graph.songListViewModel)
        observeDownloads(graph.offlineDownloads)
    }

    func templateApplicationScene(
        _ templateApplicationScene: CPTemplateApplicationScene,
        didDisconnect interfaceController: CPInterfaceController
    ) {
        // Nothing runs once the car is gone: the phone keeps playing, and a collector per connection would pile
        // up. A reconnect calls didConnect again and fills the templates afresh.
        tasks.forEach { $0.cancel() }
        tasks.removeAll()
        playablePlaylistsTask?.cancel()
        playablePlaylistsTask = nil
        playablePlaylistsInputs = nil
        playablePlaylists = nil
        viewModels.forEach { $0.clear() }
        viewModels.removeAll()
        pushed.values.forEach { $0.task.cancel(); $0.viewModel.clear() }
        pushed.removeAll()
        actions?.clear()
        actions = nil
        home = nil
        network?.cancel()
        network = nil
        openShelf = nil
        offlineIndex = nil
        nowPlaying?.detach()
        nowPlaying = nil
        lastRendered.removeAll()
        self.interfaceController = nil
    }

    /// Home loads as it comes on screen, as the phone's does (`HomeViewModel.onVisibilityChanged`).
    nonisolated func templateDidAppear(_ aTemplate: CPTemplate, animated: Bool) {
        MainActor.assumeIsolated {
            if aTemplate === homeTemplate { home?.onVisibilityChanged(visible: true) }
        }
    }

    /// A template that left the stack forgets what it drew (its identifier can be reused by the next one, which must
    /// draw); a pushed list also stops observing and clears its view model. The tabs never leave.
    nonisolated func templateDidDisappear(_ aTemplate: CPTemplate, animated: Bool) {
        MainActor.assumeIsolated {
            if aTemplate === homeTemplate { home?.onVisibilityChanged(visible: false) }
            guard let controller = interfaceController,
                  ![homeTemplate, playlistsTemplate, libraryTemplate].contains(where: { $0 === aTemplate }),
                  !controller.templates.contains(where: { $0 === aTemplate }) else { return }
            let id = ObjectIdentifier(aTemplate)
            lastRendered[id] = nil
            if openShelf?.template === aTemplate { openShelf = nil }
            guard let list = pushed.removeValue(forKey: id) else { return }
            list.task.cancel()
            list.viewModel.clear()
        }
    }

    // MARK: - Tabs

    private func observe<State>(_ viewModel: Lifecycle_viewmodelViewModel, _ flow: SkieSwiftStateFlow<State>, _ update: @escaping (CarPlaySceneDelegate, State) -> Void) {
        viewModels.append(viewModel)
        update(self, flow.value)
        tasks.append(Task { [weak self] in
            for await state in flow {
                guard let self else { return }
                update(self, state)
            }
        })
    }

    private func observeHome(_ viewModel: HomeViewModel) {
        home = viewModel
        // Home is the tab the car opens on
        viewModel.onVisibilityChanged(visible: true)
        observe(viewModel, viewModel.uiState) { delegate, state in
            delegate.homeState = state
            delegate.renderHome()
        }
    }

    /// Draws Home, and the shelf pushed from it, from the latest state, offline only what can play.
    private func renderHome() {
        switch onEnum(of: homeState) {
        case .loading:
            render(CarPlayCatalog.loading(), into: homeTemplate)
        case .empty:
            homeSections = []
            homeItems = [:]
            render(CarPlayCatalog.message(CarPlayText.libraryEmpty, detail: CarPlayText.libraryEmptyDetail), into: homeTemplate)
        case .content(let content):
            homeSections = sections(content)
            let images = min(CarPlayCatalog.shelfImageLimit, CPMaximumNumberOfGridImages)
            render(CarPlayCatalog.home(homeSections, offline: isOffline, imageLimit: images, rowLimit: Self.rowLimit()), into: homeTemplate)
        }
        renderOpenShelf()
    }

    /// Home's shelves from its state, in its order; Shuffle All's prompt holds no items and isn't one.
    private func sections(_ content: HomeUiStateContent) -> [CarPlayHomeSection] {
        let index = isOffline ? currentOfflineIndex() : nil
        var items: [String: HomeItem] = [:]
        let sections = content.sections.filter { $0.id != .shuffleAll }.map { section in
            let resumes = section.id == .jumpBackIn
            let mixed = Set(section.items.map(\.typeLabel)).count > 1
            let entries = section.items.map { item in
                items[item.key] = item
                return CarPlayEntry(
                    id: item.key,
                    title: item.title,
                    subtitle: item.subtitle(mixed: mixed),
                    artwork: Self.artwork(item, covers: content.covers),
                    progress: resumes ? section.progress[item.key].map { Double($0.fraction) } : nil,
                    symbol: Self.symbol(item),
                    playableOffline: index?.isPlayable(item, playablePlaylists: playablePlaylists) ?? true
                )
            }
            return CarPlayHomeSection(id: "\(section.id)", header: HomeContent.title(section.title), entries: entries, resumes: resumes)
        }
        homeItems = items
        return sections
    }

    private func currentOfflineIndex() -> CarPlayOfflineIndex {
        if let offlineIndex { return offlineIndex }
        let index = CarPlayOfflineIndex(songs: songs, downloads: downloads)
        offlineIndex = index
        return index
    }

    private func renderOpenShelf() {
        guard let (id, template) = openShelf else { return }
        let section = homeSections.first { $0.id == id } ?? CarPlayHomeSection(id: id, header: template.title ?? "", entries: [])
        render(CarPlayCatalog.shelf(section, offline: isOffline, limit: Self.rowLimit()), into: template) { [weak self] in self?.performHome($0) }
    }

    private func observeDownloads(_ offlineDownloads: OfflineDownloads) {
        let flow = offlineDownloads.downloads
        downloads = flow.value
        tasks.append(Task { [weak self] in
            for await downloads in flow {
                guard let self else { return }
                self.downloads = downloads
                self.observePlayablePlaylists()
                self.libraryChanged()
            }
        })
    }

    /// Re-asks which playlists have a song that plays offline (#925), for the current playlists and completed downloads.
    /// Only while offline, and only when the playlist ids or the completed download paths changed: `downloads` emits on
    /// every progress tick, and each restart re-reads every playlist's songs.
    private func observePlayablePlaylists() {
        guard isOffline else {
            playablePlaylistsTask?.cancel()
            playablePlaylistsTask = nil
            playablePlaylistsInputs = nil
            return
        }
        let downloaded = Set(downloads.compactMap { $0.value.state == .completed ? $0.key : nil })
        let inputs = PlayablePlaylistsInputs(playlistIds: Set(playlists.values.map(\.id)), downloaded: downloaded)
        guard inputs != playablePlaylistsInputs else { return }
        playablePlaylistsInputs = inputs
        playablePlaylistsTask?.cancel()
        let flow = AppGraph.shared.observePlayablePlaylists.invoke(playlists: Array(playlists.values), downloadedPaths: downloaded)
        playablePlaylistsTask = Task { [weak self] in
            for await ids in flow {
                guard let self else { return }
                self.playablePlaylists = Set(ids.map(\.int64Value))
                self.libraryChanged()
            }
        }
    }

    /// The library or the downloads changed: what plays offline may have too.
    private func libraryChanged() {
        offlineIndex = nil
        if isOffline { renderHome() }
    }

    private func observePlaylists(_ viewModel: PlaylistListViewModel) {
        observe(viewModel, viewModel.uiState) { $0.updatePlaylists($1) }
    }

    private func updatePlaylists(_ state: PlaylistListUiState) {
        playlists = Dictionary(state.playlists.map { ("\($0.id)", $0) }, uniquingKeysWith: { first, _ in first })
        smartPlaylists = Dictionary(state.smartPlaylists.map { (Self.smartKey($0), $0) }, uniquingKeysWith: { first, _ in first })
        observePlayablePlaylists()
        let smart = state.smartPlaylists.map { smartPlaylist in
            CarPlayEntry(id: Self.smartKey(smartPlaylist), title: smartPlaylist.id.title, subtitle: nil, symbol: smartPlaylist.id.symbol)
        }
        let own = state.playlists.map { playlist in
            // A playlist has no artwork of its own: its first song's, as its phone row's mosaic starts with
            CarPlayEntry(
                id: "\(playlist.id)",
                title: playlist.name,
                subtitle: nil,
                artwork: state.covers[KotlinLong(value: playlist.id)]?.first.map(ArtworkSource.song),
                symbol: "music.note.list"
            )
        }
        let loading = state.playlists.isEmpty && state.loadingState != .ready
        render(loading ? CarPlayCatalog.loading() : CarPlayCatalog.playlists(smart: smart, playlists: own, limit: Self.rowLimit()), into: playlistsTemplate)
    }

    private func observeAlbums(_ viewModel: AlbumListViewModel) {
        observe(viewModel, viewModel.uiState) { $0.updateAlbums($1) }
    }

    private func updateAlbums(_ state: AlbumListUiState) {
        albums = Dictionary(state.albums.map { ($0.stableId, $0) }, uniquingKeysWith: { first, _ in first })
        let entries = state.albums.map { album in
            CarPlayEntry(
                id: album.stableId,
                title: album.name ?? CarPlayText.unknown,
                subtitle: album.albumArtist ?? album.friendlyArtistName,
                artwork: .album(album)
            )
        }
        let loading = state.albums.isEmpty && (state.loadingState == .loading || state.loadingState == .scanning)
        render(loading ? CarPlayCatalog.loading() : CarPlayCatalog.entries(entries, empty: CarPlayText.noAlbums, limit: Self.rowLimit()), into: albumsTemplate)
    }

    private func observeArtists(_ viewModel: AlbumArtistListViewModel) {
        observe(viewModel, viewModel.uiState) { $0.updateArtists($1) }
    }

    private func updateArtists(_ state: AlbumArtistListUiState) {
        artists = Dictionary(state.albumArtists.map { ($0.stableId, $0) }, uniquingKeysWith: { first, _ in first })
        let entries = state.albumArtists.map { artist in
            CarPlayEntry(
                id: artist.stableId,
                title: artist.name ?? artist.friendlyArtistName ?? CarPlayText.unknown,
                subtitle: nil,
                artwork: .albumArtist(artist)
            )
        }
        let loading = state.albumArtists.isEmpty && (state.loadingState == .loading || state.loadingState == .scanning)
        render(loading ? CarPlayCatalog.loading() : CarPlayCatalog.entries(entries, empty: CarPlayText.noArtists, limit: Self.rowLimit()), into: artistsTemplate)
    }

    private func observeGenres(_ viewModel: GenreListViewModel) {
        observe(viewModel, viewModel.uiState) { $0.updateGenres($1) }
    }

    private func updateGenres(_ state: GenreListUiState) {
        genres = Dictionary(state.genres.map { ($0.name, $0) }, uniquingKeysWith: { first, _ in first })
        let entries = state.genres.map { genre in
            CarPlayEntry(id: genre.name, title: genre.name, subtitle: HomeItemGenreItem(genre: genre).detail, symbol: "guitars")
        }
        let loading = state.genres.isEmpty && (state.loadingState == .loading || state.loadingState == .scanning)
        render(loading ? CarPlayCatalog.loading() : CarPlayCatalog.entries(entries, empty: CarPlayText.noGenres, limit: Self.rowLimit()), into: genresTemplate)
    }

    private func observeSongs(_ viewModel: SongListViewModel) {
        observe(viewModel, viewModel.uiState) { $0.updateSongs($1) }
    }

    private func updateSongs(_ state: SongListUiState) {
        if state.songs != songs {
            songs = state.songs
            libraryChanged()
        }
        let sections: [CarPlaySectionModel] = switch state.loadingState {
        case .empty: CarPlayCatalog.message(CarPlayText.libraryEmpty, detail: CarPlayText.libraryEmptyDetail)
        case .loading where state.songs.isEmpty, .scanning where state.songs.isEmpty: CarPlayCatalog.loading()
        default: CarPlayCatalog.songs(state.songs.map(Self.song), shuffleTitle: CarPlayText.shuffleAll, limit: Self.rowLimit())
        }
        render(sections, into: songsTemplate)
    }

    // MARK: - Actions

    /// A shelf image (or a pushed shelf's row) plays its item, as the phone's tile does, and Jump Back In's carries on
    /// where it was left; a shelf's row pushes the whole shelf.
    private func performHome(_ action: CarPlayRowAction) {
        switch action {
        case .shuffle:
            play(AppGraph.shared.appIntentLibrary.shuffleAll())
        case .play(let key):
            guard let item = homeItems[key] else { return }
            play(item.playAction(), key: key)
        case .resume(let key):
            guard let item = homeItems[key] else { return }
            play(item.resumeAction(), key: key)
        case .openShelf(let id):
            pushShelf(id)
        default:
            break
        }
    }

    private func pushShelf(_ id: String) {
        guard let controller = interfaceController, openShelf == nil,
              let section = homeSections.first(where: { $0.id == id }) else { return }
        let template = CPListTemplate(title: section.header, sections: [])
        openShelf = (id, template)
        renderOpenShelf()
        controller.pushTemplate(template, animated: true, completion: nil)
    }

    private func performLibrary(_ action: CarPlayRowAction) {
        guard case .open(let id) = action, let category = CarPlayLibraryCategory(rawValue: id),
              let controller = interfaceController else { return }
        let template = switch category {
        case .artists: artistsTemplate
        case .albums: albumsTemplate
        case .songs: songsTemplate
        case .genres: genresTemplate
        }
        // A second tap while the first push is under way would push it twice, which CarPlay refuses
        guard !controller.templates.contains(where: { $0 === template }) else { return }
        controller.pushTemplate(template, animated: true, completion: nil)
    }

    private func performAlbums(_ action: CarPlayRowAction) {
        guard case .open(let id) = action, let album = albums[id] else { return }
        let title = album.name ?? CarPlayText.unknown
        guard let groupKey = album.groupKey else {
            // An album with no key can't be looked up; play it rather than open an empty list
            play(MediaActionPlay(selection: MediaSelectionAlbums(album: album), position: 0))
            return
        }
        let viewModel = AppGraph.shared.albumDetailViewModelFactory.create(groupKey: groupKey)
        pushSongs(title: title, viewModel: viewModel, flow: viewModel.uiState) { state in
            SongList(songs: state.songs, context: state.playContext, currentSongId: state.currentSong?.id, loading: state.loadingState == .loading)
        }
    }

    private func performArtists(_ action: CarPlayRowAction) {
        guard case .open(let id) = action, let artist = artists[id] else { return }
        let viewModel = AppGraph.shared.albumArtistDetailViewModelFactory.create(groupKey: artist.groupKey)
        pushSongs(title: artist.name ?? artist.friendlyArtistName ?? CarPlayText.unknown, viewModel: viewModel, flow: viewModel.uiState) { state in
            SongList(songs: state.songs, context: state.playContext, currentSongId: state.currentSong?.id, loading: state.loadingState == .loading)
        }
    }

    private func performGenres(_ action: CarPlayRowAction) {
        guard case .open(let id) = action, let genre = genres[id] else { return }
        let viewModel = AppGraph.shared.genreDetailViewModelFactory.create(genreName: genre.name)
        pushSongs(title: genre.name, viewModel: viewModel, flow: viewModel.uiState) { state in
            SongList(songs: state.songs, context: state.playContext, currentSongId: state.currentSong?.id, loading: state.loading)
        }
    }

    private func performPlaylists(_ action: CarPlayRowAction) {
        guard case .open(let id) = action else { return }
        if let smartPlaylist = smartPlaylists[id] {
            let viewModel = AppGraph.shared.smartPlaylistDetailViewModelFactory.create(smartPlaylistId: smartPlaylist.id.id)
            pushSongs(title: smartPlaylist.id.title, viewModel: viewModel, flow: viewModel.uiState) { state in
                SongList(songs: state.songs, context: state.playContext, currentSongId: state.currentSong?.id, loading: state.loading)
            }
            return
        }
        guard let playlist = playlists[id] else { return }
        let viewModel = AppGraph.shared.playlistDetailViewModelFactory.create(playlistId: playlist.id)
        pushSongs(title: playlist.name, viewModel: viewModel, flow: viewModel.uiState) { state in
            SongList(songs: state.songs.map(\.song), context: state.playContext, currentSongId: state.currentSong?.id, loading: state.loading)
        }
    }

    private func performSongs(_ action: CarPlayRowAction) {
        let selection = MediaSelectionSongs(songs: songs)
        switch action {
        case .shuffle: play(MediaActionShuffle(selection: selection))
        case .playSong(let index): play(MediaActionPlay(selection: selection, position: Int32(index)))
        default: break
        }
    }

    /// Pushes a list of songs at once and fills it in behind: a car screen that waits before it opens reads as a
    /// dropped tap, and the driver presses again.
    private func pushSongs<State>(
        title: String,
        viewModel: Lifecycle_viewmodelViewModel,
        flow: SkieSwiftStateFlow<State>,
        list: @escaping (State) -> SongList
    ) {
        guard let controller = interfaceController else {
            viewModel.clear()
            return
        }
        let template = CPListTemplate(title: title, sections: [])
        if #available(iOS 18.4, *) { template.showsSpinnerWhileEmpty = true }
        var current = list(flow.value)
        let update: (CarPlaySceneDelegate, SongList) -> Void = { delegate, songs in
            current = songs
            let sections = songs.loading && songs.songs.isEmpty
                ? CarPlayCatalog.loading()
                : CarPlayCatalog.songs(songs.songs.map(Self.song), playingId: songs.currentSongId, limit: Self.rowLimit())
            delegate.render(sections, into: template) { [weak delegate] action in
                let selection = MediaSelectionSongs(songs: current.songs)
                switch action {
                case .shuffle: delegate?.play(MediaActionShuffle(selection: selection, context: current.context))
                case .playSong(let index): delegate?.play(MediaActionPlay(selection: selection, position: Int32(index), context: current.context))
                default: break
                }
            }
        }
        update(self, current)
        let task = Task { [weak self] in
            for await state in flow {
                guard let self else { return }
                update(self, list(state))
            }
        }
        pushed[ObjectIdentifier(template)] = PushedList(viewModel: viewModel, task: task)
        controller.pushTemplate(template, animated: true, completion: nil)
    }

    /// Up Next on Now Playing: the queue from the current song, which plays the tapped one.
    private func pushQueue() {
        guard let controller = interfaceController else { return }
        let binding = AppGraph.dependencies.playerBinding
        let queue = binding.nowPlaying.queue
        let songs = queue.map { CarPlaySong(id: $0.id, title: $0.title, subtitle: $0.artist, artwork: $0.artwork) }
        let template = CPListTemplate(title: CarPlayText.upNext, sections: [])
        render(CarPlayCatalog.queue(songs, currentIndex: queue.firstIndex(where: \.isCurrent), limit: Self.rowLimit()), into: template) { action in
            guard case .playSong(let index) = action, queue.indices.contains(index) else { return }
            binding.actions.selectQueueItem(queue[index].id)
            controller.popTemplate(animated: true, completion: nil)
        }
        controller.pushTemplate(template, animated: true, completion: nil)
    }

    private func play(_ action: any MediaAction, key: String? = nil) {
        actions?.send(action, key: key)
        showNowPlaying()
    }

    private func showNowPlaying() {
        guard let controller = interfaceController, controller.topTemplate !== CPNowPlayingTemplate.shared else { return }
        controller.pushTemplate(CPNowPlayingTemplate.shared, animated: true, completion: nil)
    }

    // MARK: - Rendering

    /// Draws `sections` into `template`, unless they're what it already shows: a redraw resets the car's scroll.
    private func render(_ sections: [CarPlaySectionModel], into template: CPListTemplate, perform: ((CarPlayRowAction) -> Void)? = nil) {
        let id = ObjectIdentifier(template)
        guard lastRendered[id] != sections else { return }
        lastRendered[id] = sections
        let perform = perform ?? { [weak self, weak template] action in
            guard let self, let template else { return }
            self.perform(action, from: template)
        }
        template.updateSections(CarPlayListRenderer.sections(sections, perform: perform))
    }

    private func perform(_ action: CarPlayRowAction, from template: CPListTemplate) {
        switch template {
        case homeTemplate: performHome(action)
        case playlistsTemplate: performPlaylists(action)
        case libraryTemplate: performLibrary(action)
        case albumsTemplate: performAlbums(action)
        case artistsTemplate: performArtists(action)
        case songsTemplate: performSongs(action)
        case genresTemplate: performGenres(action)
        default: break
        }
    }

    // MARK: - Mapping

    private static func rowLimit() -> Int {
        min(CarPlayCatalog.listRowLimit, CPListTemplate.maximumItemCount)
    }

    /// A smart playlist's row id, kept apart from a playlist's numeric one.
    private static func smartKey(_ smartPlaylist: SmartPlaylist) -> String {
        "smart:\(smartPlaylist.id.id)"
    }

    private static func song(_ song: Song) -> CarPlaySong {
        CarPlaySong(
            id: song.id,
            title: song.name ?? CarPlayText.unknown,
            subtitle: song.friendlyArtistName ?? song.albumArtist,
            artwork: .song(song)
        )
    }

    private static func artwork(_ item: HomeItem, covers: [String: [Song]]) -> ArtworkSource? {
        switch onEnum(of: item) {
        case .albumItem(let it): .album(it.album)
        case .artistItem(let it): .albumArtist(it.albumArtist)
        default: covers[item.key]?.first.map(ArtworkSource.song)
        }
    }

    /// What a Home item shows until its artwork arrives, or with none.
    private static func symbol(_ item: HomeItem) -> String {
        switch onEnum(of: item) {
        case .albumItem: "square.stack"
        case .artistItem: "music.mic"
        case .playlistItem: "music.note.list"
        case .smartPlaylistItem(let it): it.smartPlaylistId.symbol
        case .genreItem: "guitars"
        }
    }
}
