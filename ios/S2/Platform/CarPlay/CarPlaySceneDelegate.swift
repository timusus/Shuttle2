import CarPlay
import Shared
import UIKit

/// The CarPlay scene (#692, from Shuttle Podcasts): a tab bar of Home, Albums, Artists, Playlists and Songs, each a
/// list drawn from the shared view models the phone's screens use. Albums, artists and playlists push their songs;
/// a song plays its list from it, Shuffle shuffles the list, and a Home item plays (Jump Back In resumes). Playback
/// goes through `MediaActionsViewModel`, as the phone's does, and Now Playing's transport is the remote commands
/// `NowPlayingController` already registers.
@MainActor
final class CarPlaySceneDelegate: UIResponder, CPTemplateApplicationSceneDelegate, CPInterfaceControllerDelegate {
    private var interfaceController: CPInterfaceController?
    private var nowPlaying: CarPlayNowPlayingController?
    private var actions: MediaActionsViewModel?
    /// The tabs' view models and their observers, for the connection's life.
    private var viewModels: [Lifecycle_viewmodelViewModel] = []
    private var tasks: [Task<Void, Never>] = []
    /// What each pushed list observes, until it leaves the stack.
    private var pushed: [ObjectIdentifier: PushedList] = [:]
    private var lastRendered: [ObjectIdentifier: [CarPlaySectionModel]] = [:]

    private let homeTemplate = CPListTemplate(title: CarPlayText.home, sections: [])
    private let albumsTemplate = CPListTemplate(title: CarPlayText.albums, sections: [])
    private let artistsTemplate = CPListTemplate(title: CarPlayText.artists, sections: [])
    private let playlistsTemplate = CPListTemplate(title: CarPlayText.playlists, sections: [])
    private let songsTemplate = CPListTemplate(title: CarPlayText.songs, sections: [])

    // The latest state of each tab, which its rows' ids resolve against
    private var homeItems: [String: HomeItem] = [:]
    private var jumpBackInKeys: Set<String> = []
    private var albums: [String: Album] = [:]
    private var artists: [String: AlbumArtist] = [:]
    private var playlists: [String: Playlist] = [:]
    private var songs: [Song] = []

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
            (albumsTemplate, CarPlayText.albums, "square.stack"),
            (artistsTemplate, CarPlayText.artists, "music.mic"),
            (playlistsTemplate, CarPlayText.playlists, "music.note.list"),
            (songsTemplate, CarPlayText.songs, "music.note"),
        ]
        for (template, title, symbol) in tabs {
            template.tabTitle = title
            template.tabImage = UIImage(systemName: symbol)
            render(CarPlayCatalog.loading(), into: template)
        }
        // The car decides how many tabs it draws, and CPTabBarTemplate throws, not truncates, past that. Now
        // Playing is never a tab: the car offers it itself, and it's pushed whenever something starts playing.
        let tabBar = CPTabBarTemplate(templates: Array(tabs.map(\.0).prefix(CPTabBarTemplate.maximumTabCount)))
        interfaceController.setRootTemplate(tabBar, animated: true, completion: nil)

        let nowPlaying = CarPlayNowPlayingController(binding: AppGraph.dependencies.playerBinding)
        nowPlaying.attach { [weak self] in self?.pushQueue() }
        self.nowPlaying = nowPlaying
        actions = graph.mediaActionsViewModel

        observeHome(graph.homeViewModel)
        observeAlbums(graph.albumListViewModel)
        observeArtists(graph.albumArtistListViewModel)
        observePlaylists(graph.playlistListViewModel)
        observeSongs(graph.songListViewModel)
    }

    func templateApplicationScene(
        _ templateApplicationScene: CPTemplateApplicationScene,
        didDisconnect interfaceController: CPInterfaceController
    ) {
        // Nothing runs once the car is gone: the phone keeps playing, and a collector per connection would pile
        // up. A reconnect calls didConnect again and fills the templates afresh.
        tasks.forEach { $0.cancel() }
        tasks.removeAll()
        viewModels.forEach { $0.clear() }
        viewModels.removeAll()
        pushed.values.forEach { $0.task.cancel(); $0.viewModel.clear() }
        pushed.removeAll()
        actions?.clear()
        actions = nil
        nowPlaying?.detach()
        nowPlaying = nil
        lastRendered.removeAll()
        self.interfaceController = nil
    }

    /// A template that left the stack forgets what it drew (its identifier can be reused by the next one, which must
    /// draw); a pushed list also stops observing and clears its view model.
    nonisolated func templateDidDisappear(_ aTemplate: CPTemplate, animated: Bool) {
        MainActor.assumeIsolated {
            guard let controller = interfaceController,
                  !controller.templates.contains(where: { $0 === aTemplate }) else { return }
            let id = ObjectIdentifier(aTemplate)
            lastRendered[id] = nil
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
        observe(viewModel, viewModel.uiState) { $0.updateHome($1) }
    }

    private func updateHome(_ state: HomeUiState) {
        switch onEnum(of: state) {
        case .loading:
            render(CarPlayCatalog.loading(), into: homeTemplate)
        case .empty:
            homeItems = [:]
            render(CarPlayCatalog.home([], budget: Self.budget()), into: homeTemplate)
        case .content(let content):
            var items: [String: HomeItem] = [:]
            var jumpBackIn: Set<String> = []
            let sections = content.sections.filter { $0.id != .shuffleAll }.map { section in
                let resumes = section.id == .jumpBackIn
                let entries = section.items.map { item in
                    items[item.key] = item
                    if resumes { jumpBackIn.insert(item.key) }
                    return CarPlayEntry(
                        id: item.key,
                        title: item.title,
                        subtitle: item.subtitle(mixed: true),
                        artwork: Self.artwork(item, covers: content.covers),
                        progress: resumes ? section.progress[item.key].map { Double($0.fraction) } : nil
                    )
                }
                return CarPlayHomeSection(id: "\(section.id)", header: HomeContent.title(section.title), entries: entries, keepsAll: resumes)
            }
            homeItems = items
            jumpBackInKeys = jumpBackIn
            render(CarPlayCatalog.home(sections, budget: Self.budget()), into: homeTemplate)
        }
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

    private func observePlaylists(_ viewModel: PlaylistListViewModel) {
        observe(viewModel, viewModel.uiState) { $0.updatePlaylists($1) }
    }

    private func updatePlaylists(_ state: PlaylistListUiState) {
        playlists = Dictionary(state.playlists.map { ("\($0.id)", $0) }, uniquingKeysWith: { first, _ in first })
        let entries = state.playlists.map { playlist in
            // A playlist has no artwork of its own: its first song's, as its phone row's mosaic starts with
            CarPlayEntry(
                id: "\(playlist.id)",
                title: playlist.name,
                subtitle: nil,
                artwork: state.covers[KotlinLong(value: playlist.id)]?.first.map(ArtworkSource.song)
            )
        }
        let loading = state.playlists.isEmpty && state.loadingState != .ready
        render(loading ? CarPlayCatalog.loading() : CarPlayCatalog.entries(entries, empty: CarPlayText.noPlaylists, limit: Self.rowLimit()), into: playlistsTemplate)
    }

    private func observeSongs(_ viewModel: SongListViewModel) {
        observe(viewModel, viewModel.uiState) { $0.updateSongs($1) }
    }

    private func updateSongs(_ state: SongListUiState) {
        songs = state.songs
        let sections: [CarPlaySectionModel] = switch state.loadingState {
        case .empty: CarPlayCatalog.message(CarPlayText.libraryEmpty, detail: CarPlayText.libraryEmptyDetail)
        case .loading where state.songs.isEmpty, .scanning where state.songs.isEmpty: CarPlayCatalog.loading()
        default: CarPlayCatalog.songs(state.songs.map(Self.song), shuffleTitle: CarPlayText.shuffleAll, limit: Self.rowLimit())
        }
        render(sections, into: songsTemplate)
    }

    // MARK: - Actions

    private func performHome(_ action: CarPlayRowAction) {
        switch action {
        case .shuffle:
            play(AppGraph.shared.appIntentLibrary.shuffleAll())
        case .play(let key):
            guard let item = homeItems[key] else { return }
            play(jumpBackInKeys.contains(key) ? item.resumeAction() : item.playAction(), key: key)
        default:
            break
        }
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

    private func performPlaylists(_ action: CarPlayRowAction) {
        guard case .open(let id) = action, let playlist = playlists[id] else { return }
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
        case albumsTemplate: performAlbums(action)
        case artistsTemplate: performArtists(action)
        case playlistsTemplate: performPlaylists(action)
        case songsTemplate: performSongs(action)
        default: break
        }
    }

    // MARK: - Mapping

    private static func rowLimit() -> Int {
        min(CarPlayCatalog.listRowLimit, CPListTemplate.maximumItemCount)
    }

    private static func budget() -> CarPlayBudget {
        CarPlayBudget(maxItems: CPListTemplate.maximumItemCount, maxSections: CPListTemplate.maximumSectionCount)
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
}
