import Shared
import SwiftUI
import Testing
import ViewInspector
@testable import S2

/// Playlist detail from its UiState: the hero, the song list, what tapping a song plays, and the not-found/
/// loading placeholders. Reorder and the rename/delete menu are wired the same way `PlaylistListView`'s
/// create/rename/delete are, so per that file's note ViewInspector 0.10.3 can't drive them end-to-end here;
/// `PlaylistDetailViewModel`'s own `onMove`/`onRename`/`onDelete` are exercised by its own ViewModel tests.
@MainActor
struct PlaylistDetailTests {
    private func entry(_ id: Int64, _ song: Song, sortOrder: Int64) -> PlaylistSong {
        PlaylistSong(id: id, sortOrder: sortOrder, song: song)
    }

    private func playlist(sortOrder: PlaylistSongSortOrder = .position, sortDescending: Bool = false) -> Playlist {
        Playlist(id: 1, name: "Road Trip", songCount: 2, duration: 0, sortOrder: sortOrder, sortDescending: sortDescending, mediaProvider: .shuttle, externalId: nil)
    }

    @Test func readyShowsHeroAndSongs() throws {
        let songs = [entry(10, TestSongs.demo[0], sortOrder: 0), entry(11, TestSongs.demo[1], sortOrder: 1)]
        let state = PlaylistDetailUiState(playlist: playlist(), songs: songs, selectedIds: [], currentSong: nil, loading: false, events: [])
        let sut = PlaylistDetailContent(state: state)
        #expect((try? sut.inspect().find(text: "Road Trip")) != nil)
        #expect((try? sut.inspect().find(ViewType.Text.self, where: { try $0.string().hasPrefix("2 songs · ") })) != nil)
        #expect((try? sut.inspect().find(text: "Paranoid Android")) != nil)
        #expect((try? sut.inspect().find(text: "Hyperballad")) != nil)
    }

    @Test func tappingASongPlaysFromItsIndex() throws {
        var played: Int?
        let songs = [entry(10, TestSongs.demo[0], sortOrder: 0), entry(11, TestSongs.demo[1], sortOrder: 1)]
        let state = PlaylistDetailUiState(playlist: playlist(), songs: songs, selectedIds: [], currentSong: nil, loading: false, events: [])
        let sut = PlaylistDetailContent(state: state, onPlay: { played = $0 })
        try sut.inspect().find(button: "Hyperballad").tap()
        #expect(played == 1)
    }

    /// The hero draws the playlist's cover mosaic from the ViewModel's covers, generated artwork without them (#652).
    @Test func theHeroIsThePlaylistsCoverMosaic() throws {
        let songs = [entry(10, TestSongs.demo[0], sortOrder: 0), entry(11, TestSongs.demo[1], sortOrder: 1)]
        let state = PlaylistDetailUiState(playlist: playlist(), songs: songs, selectedIds: [], currentSong: nil, loading: false, events: [])
        let mosaic = try PlaylistDetailContent(state: state, covers: Array(TestSongs.demo.prefix(4))).inspect().find(CoverMosaic.self).actualView()
        #expect(mosaic.isMosaic)
        let one = try PlaylistDetailContent(state: state, covers: [TestSongs.demo[0]]).inspect().find(CoverMosaic.self).actualView()
        #expect(!one.isMosaic)
        #expect(one.covers.count == 1)
        let generated = try PlaylistDetailContent(state: state).inspect().find(CoverMosaic.self).actualView()
        #expect(generated.covers.isEmpty)
        #expect(generated.symbol == GeneratedArtwork.playlistSymbol)
    }

    @Test func placeholders() throws {
        let loading = PlaylistDetailUiState(playlist: nil, songs: [], selectedIds: [], currentSong: nil, loading: true, events: [])
        #expect((try? PlaylistDetailContent(state: loading).inspect().find(ViewType.ProgressView.self)) != nil)
        let notFound = PlaylistDetailUiState(playlist: nil, songs: [], selectedIds: [], currentSong: nil, loading: false, events: [])
        #expect((try? PlaylistDetailContent(state: notFound).inspect().find(text: "Playlist Not Found")) != nil)
    }
}
