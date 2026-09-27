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
        #expect((try? sut.inspect().find(text: "5 songs")) != nil)
        #expect((try? sut.inspect().find(text: "Pyramid Song")) != nil)
    }

    @Test func tappingASongPlaysFromItsIndex() throws {
        var played: Int?
        let state = SmartPlaylistDetailUiState(smartPlaylist: SmartPlaylistId.history.smartPlaylist, songs: TestSongs.demo, currentSong: nil, loading: false)
        let sut = SmartPlaylistDetailContent(state: state, onPlay: { played = $0 })
        try sut.inspect().find(button: "Unfinished Sympathy").tap()
        #expect(played == 3)
    }

    @Test func placeholders() throws {
        let loading = SmartPlaylistDetailUiState(smartPlaylist: nil, songs: [], currentSong: nil, loading: true)
        #expect((try? SmartPlaylistDetailContent(state: loading).inspect().find(ViewType.ProgressView.self)) != nil)
        let notFound = SmartPlaylistDetailUiState(smartPlaylist: nil, songs: [], currentSong: nil, loading: false)
        #expect((try? SmartPlaylistDetailContent(state: notFound).inspect().find(text: "Playlist Not Found")) != nil)
    }
}
