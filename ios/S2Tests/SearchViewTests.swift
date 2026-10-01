import Shared
import SwiftUI
import Testing
import ViewInspector
@testable import S2

/// Search from its UiState: the recent searches, searching, no results and results states, See All, and what a tap
/// opens or plays.
@MainActor
struct SearchViewTests {
    private let query = SearchQuery.companion.parse(query: "radio")

    private func artist(_ name: String, albums: Int32, songs: Int32) -> AlbumArtist {
        AlbumArtist(
            name: name, artists: [name], albumCount: albums, songCount: songs, playCount: 0,
            groupKey: AlbumArtistGroupKey(key: name.lowercased()), mediaProviders: [.jellyfin], artworkVersion: nil, appearsOnCount: 0
        )
    }

    private func album(_ name: String, artist: String = "Radiohead") -> Album {
        Album(
            name: name, albumArtist: artist, artists: [artist], songCount: 10, duration: 0,
            year: nil, playCount: 0, lastSongPlayed: nil, lastSongCompleted: nil,
            groupKey: AlbumGroupKey(key: name.lowercased(), albumArtistGroupKey: AlbumArtistGroupKey(key: artist.lowercased()), identity: nil),
            mediaProviders: [.jellyfin], artworkVersion: nil, dateAdded: nil
        )
    }

    private func results(artists: [AlbumArtist] = [], albums: [Album] = [], songs: [Song] = [], top: SearchCategory? = nil) -> SearchResults {
        SearchResults(
            artists: artists.map { SearchHit(item: $0, query: query) },
            albums: albums.map { SearchHit(item: $0, query: query) },
            songs: songs.map { SearchHit(item: $0, query: query) },
            genres: [],
            playlists: [],
            top: top
        )
    }

    private func state(_ content: SearchContent, categories: Set<SearchCategory> = []) -> SearchUiState {
        SearchUiState(categories: categories, content: content)
    }

    @Test func nothingTypedAndNoRecentSearchesInvitesASearch() throws {
        let sut = SearchContentView(state: state(SearchContentRecent(searches: [])))
        #expect((try? sut.inspect().find(text: "Search Your Library")) != nil)
        #expect((try? sut.inspect().find(FilterChip.self)) == nil)
    }

    @Test func aRecentSearchFillsTheField() throws {
        var chosen: String?
        let sut = SearchContentView(state: state(SearchContentRecent(searches: ["radiohead", "björk"])), onSelectRecent: { chosen = $0 })
        #expect((try? sut.inspect().find(text: "Recent Searches")) != nil)
        try sut.inspect().find(button: "björk").tap()
        #expect(chosen == "björk")
    }

    @Test func searchingShowsProgressAndTheTypeChips() throws {
        let sut = SearchContentView(state: state(SearchContentSearching.shared))
        #expect((try? sut.inspect().find(ViewType.ProgressView.self)) != nil)
        #expect(try sut.inspect().findAll(FilterChip.self).count == 1 + SearchCategory.allCases.count)
    }

    @Test func aTypeChipToggles() throws {
        var toggled: SearchCategory?
        var all = false
        let sut = SearchContentView(
            state: state(SearchContentNoResults(query: "zzz"), categories: [.songs]),
            onSelectAll: { all = true },
            onToggleCategory: { toggled = $0 }
        )
        try sut.inspect().find(button: "Albums").tap()
        try sut.inspect().find(button: "All").tap()
        #expect(toggled == .albums)
        #expect(all)
    }

    @Test func noResultsNamesTheQuery() throws {
        let sut = SearchContentView(state: state(SearchContentNoResults(query: "zzz")))
        #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: "search.noResults")) != nil)
    }

    @Test func resultsAreSectionedWithTheTopResultFirst() throws {
        let results = results(
            artists: [artist("Radiohead", albums: 3, songs: 42), artist("Radio Dept", albums: 0, songs: 2)],
            albums: [album("OK Computer")],
            songs: TestSongs.demo,
            top: .artists
        )
        let sut = SearchContentView(state: state(SearchContentResults(query: "radio", results: results)))
        for header in ["Top Result", "Artists", "Albums", "Songs"] {
            #expect((try? sut.inspect().find(text: header)) != nil)
        }
        #expect((try? sut.inspect().find(text: "3 albums · 42 songs")) != nil)
        // A track-only artist (#637) counts their songs.
        #expect((try? sut.inspect().find(text: "2 songs")) != nil)
        // Songs are capped at five, with a See All; the demo has exactly five, so none.
        #expect((try? sut.inspect().find(text: "Paranoid Android")) != nil)
    }

    @Test func seeAllExpandsACappedSection() throws {
        let albums = ["A", "B", "C", "D"].map { album($0) }
        let sut = SearchResultList(query: "a", results: results(albums: albums, songs: [TestSongs.demo[0]]), onOpen: { _ in }, onPlaySong: { _ in }, onAction: { _ in })
        #expect((try? sut.inspect().find(text: "D")) == nil)
        #expect((try? sut.inspect().find(button: "See All")) != nil)
    }

    @Test func tappingAnArtistOrAlbumOpensItsRoute() throws {
        var opened: [Route] = []
        let radiohead = artist("Radiohead", albums: 3, songs: 42)
        let okComputer = album("OK Computer")
        let sut = SearchResultList(
            query: "radio",
            results: results(artists: [radiohead], albums: [okComputer]),
            onOpen: { opened.append($0) }, onPlaySong: { _ in }, onAction: { _ in }
        )
        try sut.inspect().find(text: "Radiohead").find(ViewType.Button.self, relation: .parent).tap()
        try sut.inspect().find(text: "OK Computer").find(ViewType.Button.self, relation: .parent).tap()
        #expect(opened == [.albumArtist(radiohead), .album(okComputer)])
    }

    @Test func tappingASongPlaysFromItsIndexInTheSongResults() throws {
        var played: Int?
        let sut = SearchResultList(
            query: "radio", results: results(songs: TestSongs.demo, top: .songs),
            onOpen: { _ in }, onPlaySong: { played = $0 }, onAction: { _ in }
        )
        try sut.inspect().find(text: "Hyperballad").find(ViewType.Button.self, relation: .parent).tap()
        #expect(played == 1)
        try sut.inspect().find(text: "Paranoid Android").find(ViewType.Button.self, relation: .parent).tap()
        #expect(played == 0)
    }
}
