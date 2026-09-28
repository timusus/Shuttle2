import Shared
import SwiftUI
import Testing
import ViewInspector
@testable import S2

/// Genre detail from its UiState: the hero (icon placeholder, no artwork), the album shelf, the song list, and
/// what tapping a song plays.
@MainActor
struct GenreDetailTests {
    private func album(_ name: String) -> Album {
        Album(
            name: name, albumArtist: "Massive Attack", artists: ["Massive Attack"], songCount: 1, duration: 0,
            year: nil, playCount: 0, lastSongPlayed: nil, lastSongCompleted: nil,
            groupKey: AlbumGroupKey(key: name.lowercased(), albumArtistGroupKey: AlbumArtistGroupKey(key: "massive attack")),
            mediaProviders: [.jellyfin], artworkVersion: nil
        )
    }

    private func genre() -> Genre { Genre(name: "Trip Hop", songCount: 2, duration: 0, mediaProviders: [.jellyfin]) }

    @Test func readyShowsHeroShelfAndSongs() throws {
        let state = GenreDetailUiState(genre: genre(), albums: [album("Mezzanine")], songs: TestSongs.demo, currentSong: nil, loading: false)
        let sut = GenreDetailContent(state: state)
        #expect((try? sut.inspect().find(text: "Trip Hop")) != nil)
        #expect((try? sut.inspect().find(ViewType.Text.self, where: { try $0.string().hasPrefix("2 songs · ") })) != nil)
        #expect((try? sut.inspect().find(text: "Mezzanine")) != nil)
        #expect((try? sut.inspect().find(text: "Teardrop")) != nil)
    }

    @Test func tappingASongPlaysFromItsIndex() throws {
        var played: Int?
        let state = GenreDetailUiState(genre: genre(), albums: [], songs: TestSongs.demo, currentSong: nil, loading: false)
        let sut = GenreDetailContent(state: state, onPlay: { played = $0 })
        try sut.inspect().find(button: "Hyperballad").tap()
        #expect(played == 1)
    }

    @Test func tappingAnAlbumTileOpensIt() throws {
        var opened: Album?
        let state = GenreDetailUiState(
            genre: genre(), albums: [album("Mezzanine"), album("Dummy")], songs: TestSongs.demo, currentSong: nil, loading: false
        )
        let sut = GenreDetailContent(state: state, onAlbumTap: { opened = $0 })
        try sut.inspect().find(viewWithAccessibilityIdentifier: "detailTile.album").button().tap()
        #expect(opened?.name == "Mezzanine")
        try sut.inspect().find(button: "Dummy").tap()
        #expect(opened?.name == "Dummy")
    }

    @Test func placeholders() throws {
        let loading = GenreDetailUiState(genre: nil, albums: [], songs: [], currentSong: nil, loading: true)
        #expect((try? GenreDetailContent(state: loading).inspect().find(ViewType.ProgressView.self)) != nil)
        let notFound = GenreDetailUiState(genre: nil, albums: [], songs: [], currentSong: nil, loading: false)
        #expect((try? GenreDetailContent(state: notFound).inspect().find(text: "Genre Not Found")) != nil)
    }
}
