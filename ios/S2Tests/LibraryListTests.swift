import Shared
import SwiftUI
import Testing
import ViewInspector
@testable import S2

/// Library > Songs and Albums from their UiStates: the loading, importing and empty placeholders, the rows, and
/// what a tap does.
@MainActor
struct LibraryListTests {
    private func songState(_ songs: [Song], _ loading: SongListUiState.LoadingState) -> SongListUiState {
        SongListUiState(
            songs: songs, selectedSongs: [], sortOrder: .songName, loadingState: loading, scanProgress: nil,
            letterIndex: LetterIndexKt.songLetterIndex(songs: songs, sortOrder: .songName)
        )
    }

    private func albumState(
        _ albums: [Album],
        _ loading: AlbumListUiState.LoadingState,
        viewMode: ViewMode = .list,
        events: [PendingEvent<any AlbumListEvent>] = []
    ) -> AlbumListUiState {
        AlbumListUiState(
            albums: albums, selectedAlbums: [], viewMode: viewMode, sortOrder: .albumName, loadingState: loading,
            scanProgress: nil, events: events, letterIndex: LetterIndexKt.albumLetterIndex(albums: albums, sortOrder: .albumName)
        )
    }

    private func album(_ name: String, artist: String, songs: Int32, year: Int32?) -> Album {
        Album(
            name: name, albumArtist: artist, artists: [artist], songCount: songs, duration: 0,
            year: year.map { KotlinInt(int: $0) }, playCount: 0, lastSongPlayed: nil, lastSongCompleted: nil,
            groupKey: AlbumGroupKey(key: name.lowercased(), albumArtistGroupKey: AlbumArtistGroupKey(key: artist.lowercased()), identity: nil),
            mediaProviders: [.jellyfin], artworkVersion: nil, dateAdded: nil, albumArtistKeys: []
        )
    }

    @Test func songsReadyListsEverySong() throws {
        let sut = SongListContent(state: songState(TestSongs.demo, .ready))
        for song in TestSongs.demo {
            #expect((try? sut.inspect().find(text: song.name!)) != nil, "missing \(song.name!)")
        }
        #expect((try? sut.inspect().find(text: "Radiohead · OK Computer")) != nil)
        #expect((try? sut.inspect().find(text: "6:26")) != nil)
    }

    /// The Downloaded filter (#851) is offered once a song is on the device, and flips its binding.
    @Test func theDownloadedFilterAppearsOnlyOnceASongIsDownloaded() throws {
        let state = songState(TestSongs.demo, .ready)
        let id = "songs.downloadedFilter"
        let without = SongListContent(state: state)
        #expect((try? without.inspect().find(ViewType.Toolbar.self).find(viewWithAccessibilityIdentifier: id)) == nil)
        let with = SongListContent(state: state).environment(\.downloadBadges, [TestSongs.demo[0].path: .downloaded])
        #expect((try? with.inspect().find(ViewType.Toolbar.self).find(viewWithAccessibilityIdentifier: id)) != nil)
        let downloading = SongListContent(state: state).environment(\.downloadBadges, [TestSongs.demo[0].path: .downloading])
        #expect((try? downloading.inspect().find(ViewType.Toolbar.self).find(viewWithAccessibilityIdentifier: id)) == nil)
    }

    @Test func theDownloadedFilterToggleFlipsItsBinding() throws {
        var isOn = false
        let toggle = DownloadedFilterToggle(isOn: Binding(get: { isOn }, set: { isOn = $0 }))
        try toggle.inspect().find(ViewType.Toggle.self).tap()
        #expect(isOn)
    }

    @Test func tappingASongPlaysTheListFromIt() throws {
        var played: Int?
        let sut = SongListContent(state: songState(TestSongs.demo, .ready), onPlay: { index, _ in played = index })
        try sut.inspect().find(button: "Teardrop").tap()
        #expect(played == 2)
    }

    /// A song's context menu queues it or excludes it through the shared action (#650).
    @Test func aSongsMenuPlaysNextQueuesAndExcludes() throws {
        var next: Song?
        var queued: Song?
        var excluded: Song?
        let menu = SongRowMenu(song: TestSongs.demo[1], onPlayNext: { next = $0 }, onAddToQueue: { queued = $0 }, onExclude: { excluded = $0 })
        try menu.inspect().find(button: "Play Next").tap()
        try menu.inspect().find(button: "Add to Queue").tap()
        try menu.inspect().find(button: "Exclude").tap()
        #expect(next?.id == TestSongs.demo[1].id)
        #expect(queued?.id == TestSongs.demo[1].id)
        #expect(excluded?.id == TestSongs.demo[1].id)
        #expect(try menu.inspect().find(button: "Exclude").role() == .destructive)
    }

    @Test func songsPlaceholders() throws {
        #expect((try? SongListContent(state: songState([], .empty)).inspect().find(text: "No Songs")) != nil)
        #expect((try? SongListContent(state: songState([], .scanning)).inspect().find(text: "Importing your library…")) != nil)
        #expect((try? SongListContent(state: songState([], .loading)).inspect().find(LibraryListSkeleton.self)) != nil)
        // The skeleton, not the list: no Shuffle row until there are songs to shuffle.
        #expect((try? SongListContent(state: songState([], .loading)).inspect().find(viewWithAccessibilityIdentifier: "songs.shuffle")) == nil)
    }

    /// A later import (pull to refresh) keeps showing what's already imported; only the first shows the placeholder (#623).
    @Test func anImportKeepsShowingTheSongsAndAlbumsAlreadyImported() throws {
        let songs = SongListContent(state: songState(TestSongs.demo, .scanning))
        #expect((try? songs.inspect().find(text: "Teardrop")) != nil)
        #expect((try? songs.inspect().find(text: "Importing your library…")) == nil)
        let albums = AlbumListContent(state: albumState([album("OK Computer", artist: "Radiohead", songs: 12, year: 1997)], .scanning))
        #expect((try? albums.inspect().find(text: "OK Computer")) != nil)
        #expect((try? albums.inspect().find(text: "Importing your library…")) == nil)
    }

    /// Launch imports only until an import has finished once; the saved library shows straight away after that (#623).
    @Test func launchImportsOnlyWhenNothingHasBeenImported() {
        var scans = 0
        LibraryImport.atLaunch(hasScanned: true, songTagsOutdated: false) { scans += 1 }
        #expect(scans == 0)
        LibraryImport.atLaunch(hasScanned: false, songTagsOutdated: true) { scans += 1 }
        #expect(scans == 1)
    }

    /// A library imported before this build's tags imports once more at launch (#637).
    @Test func launchImportsAgainWhenTheSongTagsAreOutdated() {
        var scans = 0
        LibraryImport.atLaunch(hasScanned: true, songTagsOutdated: true) { scans += 1 }
        #expect(scans == 1)
    }

    @Test func albumsReadyListsEachAlbumAsALinkToItsRoute() throws {
        let albums = [album("OK Computer", artist: "Radiohead", songs: 12, year: 1997), album("Post", artist: "Björk", songs: 1, year: nil)]
        let sut = AlbumListContent(state: albumState(albums, .ready))
        #expect((try? sut.inspect().find(text: "OK Computer")) != nil)
        #expect((try? sut.inspect().find(text: "Radiohead · 1997 · 12 songs")) != nil)
        #expect((try? sut.inspect().find(text: "Björk · 1 song")) != nil)
        #expect(try sut.inspect().findAll(ViewType.NavigationLink.self).count == 2)
        #expect(Route.album(albums[0]) == .album(albumKey: "ok computer", albumArtistKey: "radiohead"))
    }

    @Test func albumsGridShowsEachAlbumAsATileLinkingToItsRoute() throws {
        let albums = [album("OK Computer", artist: "Radiohead", songs: 12, year: 1997), album("Post", artist: "Björk", songs: 1, year: nil)]
        let sut = AlbumListContent(state: albumState(albums, .ready, viewMode: .grid))
        #expect((try? sut.inspect().find(text: "OK Computer")) != nil)
        #expect(try sut.inspect().findAll(ViewType.NavigationLink.self).count == 2)
        #expect((try? sut.inspect().find(text: "Radiohead")) != nil)
        #expect((try? sut.inspect().find(ViewType.List.self)) == nil)
    }

    /// Albums and Artists open with the same toolbar Shuffle button as Songs, in list and grid alike (#676).
    @Test func albumsOpenWithAToolbarShuffleButtonInListAndGrid() throws {
        let albums = [album("OK Computer", artist: "Radiohead", songs: 12, year: 1997), album("Post", artist: "Björk", songs: 1, year: nil)]
        for viewMode in [ViewMode.list, .grid] {
            var shuffled = false
            let sut = AlbumListContent(state: albumState(albums, .ready, viewMode: viewMode), onShuffle: { shuffled = true })
            let button = try sut.inspect().find(ViewType.Toolbar.self).find(viewWithAccessibilityIdentifier: "albums.shuffle")
            #expect((try? button.find(text: "2 albums")) == nil, "\(viewMode)")
            try button.find(ViewType.Button.self).tap()
            #expect(shuffled, "\(viewMode)")
        }
        for loadingState in [AlbumListUiState.LoadingState.loading, .empty] {
            #expect((try? AlbumListContent(state: albumState([], loadingState)).inspect().find(viewWithAccessibilityIdentifier: "albums.shuffle")) == nil)
        }
    }

    @Test func artistsOpenWithAToolbarShuffleButtonInListAndGrid() throws {
        let artists = ["Air", "Björk", "Radiohead"].map { name in
            AlbumArtist(
                name: name, artists: [name], albumCount: 1, songCount: 1, playCount: 0,
                groupKey: AlbumArtistGroupKey(key: name.lowercased()), mediaProviders: [.jellyfin], artworkVersion: nil, appearsOnCount: 0
            )
        }
        for viewMode in [ViewMode.list, .grid] {
            var shuffled = false
            let state = AlbumArtistListUiState(
                albumArtists: artists, selectedArtists: [], viewMode: viewMode, sortOrder: .`default`, loadingState: .ready, scanProgress: nil,
                letterIndex: LetterIndexKt.albumArtistLetterIndex(albumArtists: artists, sortOrder: .`default`)
            )
            let button = try AlbumArtistListContent(state: state, onShuffle: { shuffled = true }).inspect()
                .find(ViewType.Toolbar.self).find(viewWithAccessibilityIdentifier: "albumArtists.shuffle")
            #expect((try? button.find(text: "3 artists")) == nil, "\(viewMode)")
            try button.find(ViewType.Button.self).tap()
            #expect(shuffled, "\(viewMode)")
        }
    }

    @Test func theLayoutMenuPicksEachViewModeThroughTheViewModel() throws {
        var chosen: ViewMode?
        let menu = try ViewModeToggle(mode: .grid) { chosen = $0 }.inspect()
        #expect((try? menu.find(text: "Layout: Grid")) != nil)
        try menu.find(button: "List").tap()
        #expect(chosen == .list)
        try menu.find(button: "Compact Grid").tap()
        #expect(chosen == .compactGrid)
        try ViewModeToggle(mode: .compactGrid) { chosen = $0 }.inspect().find(button: "Grid").tap()
        #expect(chosen == .grid)
    }

    @Test func theLayoutMenuChecksTheCurrentMode() throws {
        let menu = try ViewModeToggle(mode: .compactGrid) { _ in }.inspect()
        #expect(try menu.find(button: "Compact Grid").find(ViewType.Image.self).actualImage().name() == "checkmark")
        #expect((try? menu.find(button: "Grid").find(ViewType.Image.self)) == nil)
    }

    @Test func theLoadingSkeletonFollowsTheViewMode() throws {
        let grid = try AlbumListContent(state: albumState([], .loading, viewMode: .grid)).inspect().find(LibraryGridSkeleton.self)
        #expect(try !grid.actualView().small)
        let compact = try AlbumListContent(state: albumState([], .loading, viewMode: .compactGrid)).inspect().find(LibraryGridSkeleton.self)
        #expect(try compact.actualView().small)
        #expect((try? AlbumListContent(state: albumState([], .loading)).inspect().find(LibraryListSkeleton.self)) != nil)
    }

    // 375 pt up: an iPhone in portrait, beside the letter index; 320 pt (an SE) keeps two.
    @Test(arguments: [375, 393, 430] as [CGFloat])
    func theCompactGridHasThreeColumnsOnAnIPhone(width: CGFloat) {
        #expect(LibraryGrid<EmptyView>.columnCount(width: width, tier: .compact, small: true) == 3)
        #expect(LibraryGrid<EmptyView>.columnCount(width: width, tier: .compact, indexWidth: 0, small: true) == 3)
        #expect(LibraryGrid<EmptyView>.columnCount(width: width, tier: .compact) == 2)
    }

    @Test func theCompactGridHasMoreColumnsThanTheGridOnAnIPad() {
        #expect(LibraryGrid<EmptyView>.columnCount(width: 834, tier: .regular, small: true) > LibraryGrid<EmptyView>.columnCount(width: 834, tier: .regular))
    }

    @Test func theCompactGridTakesFewerColumnsAtAccessibilityTextSizes() {
        #expect(LibraryGrid<EmptyView>.columnCount(width: 393, tier: .compact, small: true, accessibilitySize: true) == 1)
    }

    @Test func thePlayingSongAndAlbumAreMarked() {
        let song = TestSongs.demo[0]
        let playing = LibraryNowPlaying(songId: song.id, albumKey: "ok computer", albumArtistKey: "radiohead", isPlaying: true)
        #expect(playing.playback(song: song) == .playing)
        #expect(playing.playback(song: TestSongs.demo[1]) == .none)
        #expect(playing.playback(album: album("OK Computer", artist: "Radiohead", songs: 12, year: 1997)) == .playing)
        #expect(playing.playback(album: album("Post", artist: "Björk", songs: 1, year: nil)) == .none)
        var paused = playing
        paused.isPlaying = false
        #expect(paused.playback(song: song) == .paused)
        #expect(LibraryNowPlaying.none.playback(song: song) == .none)
    }

    @Test func eachAlbumArtistOfThePlayingSongsAlbumIsMarked() {
        let playing = LibraryNowPlaying(albumKey: "split", albumArtistKey: "radiohead, thom yorke", albumArtistKeys: ["radiohead", "thom yorke"], isPlaying: true)
        let artist = { (key: String) in
            AlbumArtist(name: key, artists: [key], albumCount: 1, songCount: 1, playCount: 0, groupKey: AlbumArtistGroupKey(key: key), mediaProviders: [.jellyfin], artworkVersion: nil, appearsOnCount: 0)
        }
        #expect(playing.playback(albumArtist: artist("radiohead")) == .playing)
        #expect(playing.playback(albumArtist: artist("thom yorke")) == .playing)
        #expect(playing.playback(albumArtist: artist("radiohead, thom yorke")) == .none)
        #expect(LibraryNowPlaying.none.playback(albumArtist: artist("radiohead")) == .none)
    }

    @Test func albumsPlaceholders() throws {
        #expect((try? AlbumListContent(state: albumState([], .empty)).inspect().find(text: "No Albums")) != nil)
        #expect((try? AlbumListContent(state: albumState([], .scanning)).inspect().find(text: "Importing your library…")) != nil)
    }

    @Test func songAndAlbumRowsDrawTheirArtwork() throws {
        let songRow = SongRow(song: TestSongs.demo[0])
        #expect((try? songRow.inspect().find(RemoteArtwork<ArtworkPlaceholder>.self)) != nil)
        let albumRow = AlbumRow(album: album("OK Computer", artist: "Radiohead", songs: 12, year: 1997))
        #expect((try? albumRow.inspect().find(RemoteArtwork<ArtworkPlaceholder>.self)) != nil)
        // Keyed by the album's whole group key (#693), so a same-named album by another artist draws its own cover.
        #expect(ArtworkSource.album(albumRow.album) == ArtworkSource(id: albumRow.album.stableId) { nil })
        #expect(albumRow.album.stableId != album("OK Computer", artist: "Someone Else", songs: 12, year: 1997).stableId)
    }

    @Test func emptyStatesUseTheSharedEmptyState() throws {
        let songs = try SongListContent(state: songState([], .empty)).inspect()
        #expect((try? songs.find(EmptyState<EmptyView>.self)) != nil)
        #expect((try? songs.find(text: "Pull to refresh to import.")) != nil)
        let albums = try AlbumListContent(state: albumState([], .empty)).inspect()
        #expect((try? albums.find(EmptyState<EmptyView>.self)) != nil)
    }

    @Test func playbackMessagesBecomeAlerts() {
        #expect(MediaActionText.alert(for: MediaActionMessagePlaybackFailed(reason: "offline")) == "Couldn't play: offline")
        #expect(MediaActionText.alert(for: MediaActionMessageNoSongs.shared) == "There's nothing to play.")
        #expect(MediaActionText.alert(for: MediaActionMessageNotFound.shared) == nil)
    }
}

@MainActor
struct LibrarySkeletonLayoutTests {
    private func artistState(_ viewMode: ViewMode) -> AlbumArtistListUiState {
        AlbumArtistListUiState(
            albumArtists: [], selectedArtists: [], viewMode: viewMode, sortOrder: .`default`, loadingState: .loading, scanProgress: nil, letterIndex: nil
        )
    }

    @Test func theArtistSkeletonFollowsTheViewMode() throws {
        let grid = try AlbumArtistListContent(state: artistState(.grid)).inspect()
        #expect((try? grid.find(LibraryGridSkeleton.self)) != nil)
        #expect((try? grid.find(LibraryListSkeleton.self)) == nil)
        let list = try AlbumArtistListContent(state: artistState(.list)).inspect()
        #expect((try? list.find(LibraryListSkeleton.self)) != nil)
        #expect((try? list.find(LibraryGridSkeleton.self)) == nil)
    }

    @Test func theAlbumSkeletonStartsOnTheSavedViewMode() {
        // Android's default for albums is the grid; a saved list must show list rows from the first frame.
        for viewMode in [ViewMode.list, .grid] {
            let state = AlbumListUiState(
                albums: [], selectedAlbums: [], viewMode: viewMode, sortOrder: .albumName, loadingState: .loading,
                scanProgress: nil, events: [], letterIndex: nil
            )
            let sut = AlbumListContent(state: state)
            let showsGrid = (try? sut.inspect().find(LibraryGridSkeleton.self)) != nil
            let showsList = (try? sut.inspect().find(LibraryListSkeleton.self)) != nil
            #expect(showsGrid == (viewMode == .grid), "\(viewMode)")
            #expect(showsList == (viewMode == .list), "\(viewMode)")
        }
    }

    @Test func theGridSkeletonUsesTheLoadedIndexedGridsColumns() throws {
        let skeleton = try LibraryGridSkeleton().inspect().find(ViewType.LazyVGrid.self).columns()
        let loaded = try LibraryGrid(index: []) { Color.clear }.inspect().find(ViewType.LazyVGrid.self).columns()
        #expect(skeleton.map { "\($0)" } == loaded.map { "\($0)" })
    }

    @Test func withoutACategoryTheRootShowsNoSkeleton() {
        let sut = LibraryRootContent(categories: [], availability: .loading, importStatus: .idle, selection: .constant(nil)) { Text($0.title) }
        #expect((try? sut.inspect().find(LibraryListSkeleton.self)) == nil)
        #expect((try? sut.inspect().find(LibraryGridSkeleton.self)) == nil)
    }
}
