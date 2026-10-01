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
            groupKey: AlbumGroupKey(key: key, albumArtistGroupKey: AlbumArtistGroupKey(key: "artist"), identity: nil),
            mediaProviders: [.jellyfin], artworkVersion: nil, dateAdded: nil
        )
    }

    private func albumState(_ albums: [Album], sortOrder: AlbumSortOrder, viewMode: ViewMode) -> AlbumListUiState {
        AlbumListUiState(
            albums: albums, selectedAlbums: [], viewMode: viewMode, sortOrder: sortOrder, loadingState: .ready, scanProgress: nil, events: [],
            letterIndex: LetterIndexKt.albumLetterIndex(albums: albums, sortOrder: sortOrder)
        )
    }

    private func songState(_ songs: [Song], sortOrder: SongSortOrder) -> SongListUiState {
        SongListUiState(
            songs: songs, selectedSongs: [], sortOrder: sortOrder, loadingState: .ready, scanProgress: nil,
            letterIndex: LetterIndexKt.songLetterIndex(songs: songs, sortOrder: sortOrder)
        )
    }

    private func genreState(_ genres: [Genre], sortOrder: GenreSortOrder) -> GenreListUiState {
        GenreListUiState(
            genres: genres, loadingState: .ready, scanProgress: nil, sortOrder: sortOrder,
            letterIndex: LetterIndexKt.genreLetterIndex(genres: genres, sortOrder: sortOrder)
        )
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
        #expect(genreState([], sortOrder: .songCount).letterIndex == nil)
        #expect(LetterIndex.sections(songState(songs, sortOrder: .dateAdded).letterIndex, items: songs, id: \.id) == nil)
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
        // The strip on every version, as in the grids, so the letters sit at the same x in both (#643).
        #expect(hasStrip(byName))

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
                groupKey: AlbumArtistGroupKey(key: name.lowercased()), mediaProviders: [.jellyfin], artworkVersion: nil, appearsOnCount: 0
            )
        }
        let state = AlbumArtistListUiState(
            albumArtists: artists, selectedArtists: [], viewMode: .list, sortOrder: .`default`, loadingState: .ready, scanProgress: nil,
            letterIndex: LetterIndexKt.albumArtistLetterIndex(albumArtists: artists, sortOrder: .`default`)
        )
        #expect(try AlbumArtistListContent(state: state).inspect().findAll(ViewType.Section.self).count == 2)
    }

    @Test func theGenresListIsIndexedByNameNotBySongCount() throws {
        let genres = [Genre(name: "Ambient", songCount: 1, duration: 0, mediaProviders: []), Genre(name: "Rock", songCount: 9, duration: 0, mediaProviders: [])]
        let byName = GenreListContent(state: genreState(genres, sortOrder: .default))
        #expect(try byName.inspect().findAll(ViewType.Section.self).count == 2)
        let byCount = GenreListContent(state: genreState(genres.reversed(), sortOrder: .songCount))
        #expect(try byCount.inspect().findAll(ViewType.Section.self).isEmpty)
    }

    @Test func theSongsListOpensWithAToolbarShuffleButton() throws {
        var shuffled = false
        let songs = songs(sortedNames)
        for sortOrder in [SongSortOrder.songName, .year] {
            let sut = SongListContent(state: songState(songs, sortOrder: sortOrder), onShuffle: { shuffled = true })
            let button = try sut.inspect().find(ViewType.Toolbar.self).find(viewWithAccessibilityIdentifier: "songs.shuffle")
            #expect((try? button.find(text: "7 songs")) == nil)
            try button.find(ViewType.Button.self).tap()
        }
        #expect(shuffled)
        #expect((try? SongListContent(state: songState([], sortOrder: .songName)).inspect().find(viewWithAccessibilityIdentifier: "songs.shuffle")) == nil)
    }

    @Test func theStripIsOneAdjustableControlForVoiceOver() throws {
        let sections = try #require(LetterIndex.sections([LetterSection(letter: "A", firstIndex: 0)], items: ["a"], id: \.self))
        let strip = try LetterIndexStrip(sections: sections, onSelect: { _ in }).inspect().vStack()
        #expect(try strip.accessibilityLabel().string() == "Section index")
        #expect(try strip.accessibilityIdentifier() == "library.sectionIndex")
    }

    private let alphabet = ["#"] + (UnicodeScalar("A").value ... UnicodeScalar("Z").value).map { String(UnicodeScalar($0)!) }

    @Test func everyLetterShowsWhenTheyFit() {
        let entries = LetterIndexEntry.entries(alphabet, slots: 27)
        #expect(entries.map(\.label) == alphabet)
        #expect(entries.map(\.sections) == (0 ..< 27).map { $0 ..< $0 + 1 })
        #expect(LetterIndexEntry.entries(alphabet, slots: .max).count == 27)
    }

    /// Too little height for every letter (landscape, an SE, a large text size): evenly spaced letters from the first
    /// to the last with a dot for the ones between, as UIKit does, rather than letters too small to hit.
    @Test func lettersThatDontFitAreThinnedToEveryOtherWithDots() {
        let entries = LetterIndexEntry.entries(alphabet, slots: 19)
        #expect(entries.count == 19)
        #expect(entries.first?.label == "#")
        #expect(entries.last?.label == "Z")
        #expect(entries.enumerated().allSatisfy { ($0.offset.isMultiple(of: 2)) == ($0.element.label != LetterIndexEntry.skipped) })
        // Together the rows stand for every section once, in order.
        #expect(entries.flatMap { Array($0.sections) } == Array(0 ..< 27))
        // An even number of slots leaves one empty rather than ending on a dot.
        #expect(LetterIndexEntry.entries(alphabet, slots: 20) == entries)
    }

    @Test func thinningNeverGoesBelowFirstDotLast() {
        let entries = LetterIndexEntry.entries(alphabet, slots: 1)
        #expect(entries.map(\.label) == ["#", LetterIndexEntry.skipped, "Z"])
        #expect(entries.map(\.sections) == [0 ..< 1, 1 ..< 26, 26 ..< 27])
        #expect(LetterIndexEntry.entries(["A", "B"], slots: 0).map(\.label) == ["A", "B"])
        #expect(LetterIndexEntry.entries([], slots: 0).isEmpty)
    }

    @Test func slotsKeepEachRowAtLeastTheMinimumHeight() {
        #expect(LetterIndexStrip.slots(height: 0) == .max)
        let height: CGFloat = 200
        let slots = LetterIndexStrip.slots(height: height)
        #expect((height - Spacing.small * 2) / CGFloat(slots) >= LetterIndexStrip.minimumLetterHeight)
        #expect((height - Spacing.small * 2) / CGFloat(slots + 1) < LetterIndexStrip.minimumLetterHeight)
    }

    /// A drag over a dot passes through each letter it stands for.
    @Test func aDragAcrossADotPassesThroughTheLettersItSkips() {
        let entries = LetterIndexEntry.entries(["A", "B", "C", "D", "E"], slots: 3)
        #expect(entries.map(\.label) == ["A", LetterIndexEntry.skipped, "E"])
        let positions = stride(from: 0.0, through: 1.0, by: 0.01).compactMap { LetterIndexEntry.section(at: $0, in: entries) }
        #expect(Array(Set(positions)).sorted() == [0, 1, 2, 3, 4])
        #expect(positions == positions.sorted())
        #expect(LetterIndexEntry.section(at: -1, in: entries) == 0)
        #expect(LetterIndexEntry.section(at: 2, in: entries) == 4)
        #expect(LetterIndexEntry.section(at: 0.5, in: []) == nil)
    }

    /// The strip's room below: the tallest bottom inset while holding, so the tab bar minimising (a smaller inset)
    /// doesn't move it; the inset as it is while following, so the mini player going reserves nothing.
    @Test func theClearanceHoldsTheTallestInsetUntilToldToFollow() {
        var clearance = LetterIndexClearance()
        clearance.measure(83)
        #expect(clearance.value == 83)
        clearance.measure(150)
        clearance.hold()
        clearance.measure(100)
        #expect(clearance.value == 150)
        clearance.measure(160)
        #expect(clearance.value == 160)
        clearance.follow()
        #expect(clearance.value == 160)
        clearance.measure(83)
        #expect(clearance.value == 83)
        clearance.hold()
        clearance.measure(49)
        #expect(clearance.value == 83)
        clearance.follow()
        #expect(clearance.value == 49)
    }

    private func hasStrip(_ view: InspectableView<ViewType.ClassifiedView>) -> Bool {
        (try? view.find(LetterIndexStrip.self)) != nil
    }
}
