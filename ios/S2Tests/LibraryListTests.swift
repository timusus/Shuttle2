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
            mediaProviders: [.jellyfin], artworkVersion: nil
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

    @Test func tappingASongPlaysTheListFromIt() throws {
        var played: Int?
        let sut = SongListContent(state: songState(TestSongs.demo, .ready), onPlay: { played = $0 })
        try sut.inspect().find(button: "Teardrop").tap()
        #expect(played == 2)
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

    @Test func theToolbarTogglesTheViewModeThroughTheViewModel() throws {
        var chosen: ViewMode?
        let toggle = ViewModeToggle(mode: .grid) { chosen = $0 }
        try toggle.inspect().find(button: "Show as List").tap()
        #expect(chosen == .list)
        try ViewModeToggle(mode: .list) { chosen = $0 }.inspect().find(button: "Show as Grid").tap()
        #expect(chosen == .grid)
    }

    @Test func theLoadingSkeletonFollowsTheViewMode() throws {
        #expect((try? AlbumListContent(state: albumState([], .loading, viewMode: .grid)).inspect().find(LibraryGridSkeleton.self)) != nil)
        #expect((try? AlbumListContent(state: albumState([], .loading)).inspect().find(LibraryListSkeleton.self)) != nil)
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

    @Test func albumsPlaceholders() throws {
        #expect((try? AlbumListContent(state: albumState([], .empty)).inspect().find(text: "No Albums")) != nil)
        #expect((try? AlbumListContent(state: albumState([], .scanning)).inspect().find(text: "Importing your library…")) != nil)
    }

    @Test func songAndAlbumRowsDrawTheirArtwork() throws {
        let songRow = SongRow(song: TestSongs.demo[0])
        #expect((try? songRow.inspect().find(RemoteArtwork<ArtworkPlaceholder>.self)) != nil)
        let albumRow = AlbumRow(album: album("OK Computer", artist: "Radiohead", songs: 12, year: 1997))
        #expect((try? albumRow.inspect().find(RemoteArtwork<ArtworkPlaceholder>.self)) != nil)
        #expect(ArtworkSource.album(albumRow.album) == ArtworkSource(id: "ok computer") { nil })
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
