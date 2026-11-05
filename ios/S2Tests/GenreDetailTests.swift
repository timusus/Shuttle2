import Shared
import SwiftUI
import Testing
import ViewInspector
@testable import S2

/// Genre detail from its UiState: the hero (its cover mosaic, as its Library row draws), the songs grouped by album or flat in the chosen sort, and
/// what tapping a song plays.
@MainActor
struct GenreDetailTests {
    private func album(_ name: String) -> Album {
        Album(
            name: name, albumArtist: "Massive Attack", artists: ["Massive Attack"], songCount: 2, duration: 0,
            year: nil, playCount: 0, lastSongPlayed: nil, lastSongCompleted: nil,
            groupKey: AlbumGroupKey(key: name.lowercased(), albumArtistGroupKey: AlbumArtistGroupKey(key: "massive attack"), identity: nil),
            mediaProviders: [.jellyfin], artworkVersion: nil, dateAdded: nil, albumArtistKeys: []
        )
    }

    private func genre() -> Genre { Genre(name: "Trip Hop", songCount: 2, duration: 0, mediaProviders: [.jellyfin]) }

    @Test func readyShowsHeroAndSongs() throws {
        let state = GenreDetailUiState(genre: genre(), songs: TestSongs.demo, currentSong: nil, loading: false)
        let sut = GenreDetailContent(state: state)
        #expect((try? sut.inspect().find(text: "Trip Hop")) != nil)
        #expect((try? sut.inspect().find(ViewType.Text.self, where: { try $0.string().hasPrefix("2 songs · ") })) != nil)
        #expect((try? sut.inspect().find(text: "Teardrop")) != nil)
    }

    @Test func tappingASongPlaysFromItsIndex() throws {
        var played: Int?
        let state = GenreDetailUiState(genre: genre(), songs: TestSongs.demo, currentSong: nil, loading: false)
        let sut = GenreDetailContent(state: state, onPlay: { played = $0 })
        try sut.inspect().find(button: "Hyperballad").tap()
        #expect(played == 1)
    }

    private func grouped(expanded: [Album] = []) -> GenreDetailUiState {
        let mezzanine = album("Mezzanine")
        let dummy = album("Dummy")
        let songs = TestSongs.demo
        return GenreDetailUiState(
            genre: genre(), songs: songs, currentSong: nil, loading: false, sort: .albumTitle,
            sections: [SongSection(album: dummy, songs: Array(songs[0..<2])), SongSection(album: mezzanine, songs: Array(songs[2..<4]))],
            expandedAlbums: Set(expanded.compactMap(\.groupKey)), events: []
        )
    }

    @Test func groupedShowsFoldedAlbumHeaders() throws {
        let sut = GenreDetailContent(state: grouped())
        #expect((try? sut.inspect().find(text: "Mezzanine")) != nil)
        #expect((try? sut.inspect().find(text: "Dummy")) != nil)
        #expect((try? sut.inspect().find(text: TestSongs.demo[2].name ?? "")) == nil)
    }

    @Test func expandedAlbumListsItsSongsAndTapsPlayTheDisplayedOrder() throws {
        var played: Int?
        let sut = GenreDetailContent(state: grouped(expanded: [album("Mezzanine")]), onPlay: { played = $0 })
        try sut.inspect().find(button: TestSongs.demo[3].name ?? "").tap()
        #expect(played == 3)
    }

    @Test func togglingAnAlbumHeaderReportsTheAlbum() throws {
        var toggled: Album?
        let sut = GenreDetailContent(state: grouped(), onToggleAlbum: { toggled = $0 })
        try sut.inspect().find(AlbumSectionHeader.self).find(ViewType.Button.self, where: { try $0.accessibilityIdentifier() == "artistDetail.albumHeader" }).tap()
        #expect(toggled?.name == "Dummy")
    }

    @Test func sortMenuListsGenreSortsAndSelectsOne() throws {
        var selected: SongListSort?
        let sut = GenreDetailContent(state: grouped(), onSortSelected: { selected = $0 })
        let picker = try sut.inspect().find(ViewType.Picker.self)
        for choice in PlaylistKind.genre.sortChoices {
            #expect((try? picker.find(text: choice.title)) != nil)
        }
        try picker.select(value: SongListSort.songTitle)
        #expect(selected == .songTitle)
    }

    /// The hero draws the genre's cover mosaic from the ViewModel's covers, generated artwork without them (#652).
    @Test func theHeroIsTheGenresCoverMosaic() throws {
        let state = GenreDetailUiState(genre: genre(), songs: TestSongs.demo, currentSong: nil, loading: false)
        let mosaic = try GenreDetailContent(state: state, covers: Array(TestSongs.demo.prefix(4))).inspect().find(CoverMosaic.self).actualView()
        #expect(mosaic.isMosaic)
        #expect(mosaic.seed == "Trip Hop")
        let generated = try GenreDetailContent(state: state).inspect().find(CoverMosaic.self).actualView()
        #expect(generated.covers.isEmpty)
        #expect(generated.symbol == GeneratedArtwork.genreSymbol)
    }

    @Test func placeholders() throws {
        let loading = GenreDetailUiState(genre: nil, songs: [], currentSong: nil, loading: true)
        #expect((try? GenreDetailContent(state: loading).inspect().find(ViewType.ProgressView.self)) != nil)
        let notFound = GenreDetailUiState(genre: nil, songs: [], currentSong: nil, loading: false)
        #expect((try? GenreDetailContent(state: notFound).inspect().find(text: "Genre Not Found")) != nil)
    }
}
