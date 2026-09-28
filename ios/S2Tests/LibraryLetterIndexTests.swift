import Shared
import SwiftUI
import Testing
import ViewInspector
@testable import S2

/// The Library lists' A–Z index (#627): its sections come from the shared UiStates' `letterIndex`, which keys a name
/// sort the way the shared sort compares it, and it's shown only for a sort by name.
@MainActor
struct LibraryLetterIndexTests {
    private func songs(_ names: [String]) -> [Song] {
        names.enumerated().map { TestSongs.song(Int64($0.offset + 1), $0.element, artist: "Artist", album: "Album", durationMs: 1000) }
    }

    private func album(_ name: String) -> Album {
        let key = name.lowercased()
        return Album(
            name: name, albumArtist: "Artist", artists: ["Artist"], songCount: 1, duration: 0, year: nil, playCount: 0,
            lastSongPlayed: nil, lastSongCompleted: nil,
            groupKey: AlbumGroupKey(key: key, albumArtistGroupKey: AlbumArtistGroupKey(key: "artist")),
            mediaProviders: [.jellyfin], artworkVersion: nil
        )
    }

    private func albumState(_ albums: [Album], sortOrder: AlbumSortOrder, viewMode: ViewMode) -> AlbumListUiState {
        AlbumListUiState(albums: albums, selectedAlbums: [], viewMode: viewMode, sortOrder: sortOrder, loadingState: .ready, scanProgress: nil, events: [])
    }

    private func songState(_ songs: [Song], sortOrder: SongSortOrder) -> SongListUiState {
        SongListUiState(songs: songs, selectedSongs: [], sortOrder: sortOrder, loadingState: .ready, scanProgress: nil)
    }

    /// Rows already in the shared name order: digits under '#', accents under their base letter.
    private let sortedNames = ["10cc", "Ágape", "apple", "Beta", "élan", "Émilie", "zebra"]

    @Test func aNameSortsSectionsAreTheSharedLettersOverTheRows() throws {
        let songs = songs(sortedNames)
        let sections = try #require(LetterIndex.sections(songState(songs, sortOrder: .songName).letterIndex, items: songs, id: \.id))

        #expect(sections.map(\.letter) == ["#", "A", "B", "E", "Z"])
        #expect(sections.map(\.rows) == [0 ..< 1, 1 ..< 3, 3 ..< 4, 4 ..< 6, 6 ..< 7])
        #expect(sections.map(\.anchor) == [1, 2, 4, 5, 7].map { AnyHashable(Int64($0)) })
        #expect(sections.allSatisfy { $0.isIndexed })
    }

    @Test func aSortThatIsntByNameHasNoSections() {
        let songs = songs(sortedNames)
        #expect(LetterIndex.sections(songState(songs, sortOrder: .year).letterIndex, items: songs, id: \.id) == nil)
        #expect(LetterIndex.sections(songState(songs, sortOrder: .playCount).letterIndex, items: songs, id: \.id) == nil)
        #expect(albumState([album("Post")], sortOrder: .year, viewMode: .list).letterIndex == nil)
        #expect(GenreListUiState(genres: [], loadingState: .ready, scanProgress: nil, sortOrder: .songCount).letterIndex == nil)
    }

    /// A collation that splits a letter (ideographs' '#' after Z, digits' before A) indexes only its first run.
    @Test func aLetterThatComesBackIsIndexedOnce() throws {
        let items = ["1", "a", "坂"]
        let shared = [LetterSection(letter: "#", firstIndex: 0), LetterSection(letter: "A", firstIndex: 1), LetterSection(letter: "#", firstIndex: 2)]
        let sections = try #require(LetterIndex.sections(shared, items: items, id: \.self))
        #expect(sections.map(\.isIndexed) == [true, true, false])
        #expect(sections.map(\.anchor) == ["1", "a", "坂"])
    }

    @Test func theSongsListIsSectionedOnlyWhenSortedByName() throws {
        let songs = songs(sortedNames)
        let byName = try SongListContent(state: songState(songs, sortOrder: .songName)).inspect()
        #expect(byName.findAll(ViewType.Section.self).count == 5)
        #expect(hasStrip(byName) == !usesNativeIndex)

        let byYear = try SongListContent(state: songState(songs, sortOrder: .year)).inspect()
        #expect(byYear.findAll(ViewType.Section.self).isEmpty)
        #expect(!hasStrip(byYear))
        #expect((try? byYear.find(text: "Beta")) != nil)
    }

    @Test func aSectionedSongStillPlaysTheListFromItsOwnPosition() throws {
        var played: Int?
        let sut = SongListContent(state: songState(songs(sortedNames), sortOrder: .songName), onPlay: { played = $0 })
        try sut.inspect().find(button: "Émilie").tap()
        #expect(played == 5)
    }

    /// Grids have no native index on any version, so they always use the strip.
    @Test func theAlbumGridHasTheStripOnlyWhenSortedByName() throws {
        let albums = ["Abbey Road", "Blue Lines", "Post"].map(album)
        #expect(try hasStrip(AlbumListContent(state: albumState(albums, sortOrder: .albumName, viewMode: .grid)).inspect()))
        #expect(try !hasStrip(AlbumListContent(state: albumState(albums, sortOrder: .year, viewMode: .grid)).inspect()))
        #expect(try AlbumListContent(state: albumState(albums, sortOrder: .albumName, viewMode: .list)).inspect().findAll(ViewType.Section.self).count == 3)
    }

    @Test func theArtistsListIsAlwaysIndexed() throws {
        let artists = ["Air", "Björk"].map { name in
            AlbumArtist(
                name: name, artists: [name], albumCount: 1, songCount: 1, playCount: 0,
                groupKey: AlbumArtistGroupKey(key: name.lowercased()), mediaProviders: [.jellyfin], artworkVersion: nil
            )
        }
        let state = AlbumArtistListUiState(albumArtists: artists, selectedArtists: [], viewMode: .list, loadingState: .ready, scanProgress: nil)
        #expect(try AlbumArtistListContent(state: state).inspect().findAll(ViewType.Section.self).count == 2)
    }

    @Test func theGenresListIsIndexedByNameNotBySongCount() throws {
        let genres = [Genre(name: "Ambient", songCount: 1, duration: 0, mediaProviders: []), Genre(name: "Rock", songCount: 9, duration: 0, mediaProviders: [])]
        let byName = GenreListContent(state: GenreListUiState(genres: genres, loadingState: .ready, scanProgress: nil, sortOrder: .default))
        #expect(try byName.inspect().findAll(ViewType.Section.self).count == 2)
        let byCount = GenreListContent(state: GenreListUiState(genres: genres.reversed(), loadingState: .ready, scanProgress: nil, sortOrder: .songCount))
        #expect(try byCount.inspect().findAll(ViewType.Section.self).isEmpty)
    }

    @Test func theStripIsOneAdjustableControlForVoiceOver() throws {
        let sections = try #require(LetterIndex.sections([LetterSection(letter: "A", firstIndex: 0)], items: ["a"], id: \.self))
        let strip = try LetterIndexStrip(sections: sections, onSelect: { _ in }).inspect().vStack()
        #expect(try strip.accessibilityLabel().string() == "Section index")
        #expect(try strip.accessibilityIdentifier() == "library.sectionIndex")
    }

    private var usesNativeIndex: Bool {
        if #available(iOS 26, *) { true } else { false }
    }

    private func hasStrip(_ view: InspectableView<ViewType.ClassifiedView>) -> Bool {
        (try? view.find(LetterIndexStrip.self)) != nil
    }
}
