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
        SongListUiState(songs: songs, selectedSongs: [], sortOrder: .songName, loadingState: loading, scanProgress: nil)
    }

    private func albumState(_ albums: [Album], _ loading: AlbumListUiState.LoadingState, events: [PendingEvent<any AlbumListEvent>] = []) -> AlbumListUiState {
        AlbumListUiState(
            albums: albums, selectedAlbums: [], viewMode: .list, sortOrder: .albumName, loadingState: loading,
            scanProgress: nil, events: events
        )
    }

    private func album(_ name: String, artist: String, songs: Int32, year: Int32?) -> Album {
        Album(
            name: name, albumArtist: artist, artists: [artist], songCount: songs, duration: 0,
            year: year.map { KotlinInt(int: $0) }, playCount: 0, lastSongPlayed: nil, lastSongCompleted: nil,
            groupKey: AlbumGroupKey(key: name.lowercased(), albumArtistGroupKey: AlbumArtistGroupKey(key: artist.lowercased())),
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
        #expect((try? SongListContent(state: songState([], .loading)).inspect().find(ViewType.ProgressView.self)) != nil)
        #expect((try? SongListContent(state: songState([], .loading)).inspect().find(ViewType.List.self)) == nil)
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
