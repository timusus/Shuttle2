import Shared
import SwiftUI
import Testing
import ViewInspector
@testable import S2

/// Smart playlist detail from its UiState: the hero titled from its slug, the song list, and what tapping a
/// song plays.
@MainActor
struct SmartPlaylistDetailTests {
    @Test func readyShowsHeroAndSongs() throws {
        let state = SmartPlaylistDetailUiState(smartPlaylist: SmartPlaylistId.favourites.smartPlaylist, songs: TestSongs.demo, currentSong: nil, loading: false)
        let sut = SmartPlaylistDetailContent(state: state)
        #expect((try? sut.inspect().find(text: "Favourites")) != nil)
        #expect((try? sut.inspect().find(ViewType.Text.self, where: { try $0.string().hasPrefix("5 songs · ") })) != nil)
        #expect((try? sut.inspect().find(text: "Pyramid Song")) != nil)
    }

    @Test func tappingASongPlaysFromItsIndex() throws {
        var played: Int?
        let state = SmartPlaylistDetailUiState(smartPlaylist: SmartPlaylistId.history.smartPlaylist, songs: TestSongs.demo, currentSong: nil, loading: false)
        let sut = SmartPlaylistDetailContent(state: state, onPlay: { played = $0 })
        try sut.inspect().find(button: "Unfinished Sympathy").tap()
        #expect(played == 3)
    }

    /// The hero draws the generated artwork its Library row draws: a smart playlist has no cover songs (#652).
    @Test func theHeroIsItsGeneratedArtwork() throws {
        let playlist = SmartPlaylistId.favourites.smartPlaylist
        let state = SmartPlaylistDetailUiState(smartPlaylist: playlist, songs: TestSongs.demo, currentSong: nil, loading: false)
        let mosaic = try SmartPlaylistDetailContent(state: state).inspect().find(CoverMosaic.self).actualView()
        #expect(mosaic.covers.isEmpty)
        #expect(mosaic.seed == playlist.id.title)
        #expect(mosaic.symbol == playlist.id.symbol)
    }

    @Test func placeholders() throws {
        let loading = SmartPlaylistDetailUiState(smartPlaylist: nil, songs: [], currentSong: nil, loading: true)
        #expect((try? SmartPlaylistDetailContent(state: loading).inspect().find(ViewType.ProgressView.self)) != nil)
        let notFound = SmartPlaylistDetailUiState(smartPlaylist: nil, songs: [], currentSong: nil, loading: false)
        #expect((try? SmartPlaylistDetailContent(state: notFound).inspect().find(text: "Playlist Not Found")) != nil)
    }
}
