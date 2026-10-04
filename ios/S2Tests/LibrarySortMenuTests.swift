import Shared
import SwiftUI
import Testing
import ViewInspector
@testable import S2

/// The Library tabs' sort menus (#702): each lists the orders its tab offers, chooses through the ViewModel's callback,
/// and a row's subtitle follows the sort key.
@MainActor
struct LibrarySortMenuTests {
    private func album(added: KotlinInstant?) -> Album {
        Album(
            name: "Kid A", albumArtist: "Radiohead", artists: ["Radiohead"], songCount: 10, duration: 0,
            year: KotlinInt(int: 2000), playCount: 0, lastSongPlayed: nil, lastSongCompleted: nil,
            groupKey: AlbumGroupKey(key: "kid a", albumArtistGroupKey: AlbumArtistGroupKey(key: "radiohead"), identity: nil),
            mediaProviders: [.jellyfin], artworkVersion: nil, dateAdded: added
        )
    }

    private func albumState(_ albums: [Album], sortOrder: AlbumSortOrder) -> AlbumListUiState {
        AlbumListUiState(
            albums: albums, selectedAlbums: [], viewMode: .list, sortOrder: sortOrder, loadingState: .ready,
            scanProgress: nil, events: [], letterIndex: LetterIndexKt.albumLetterIndex(albums: albums, sortOrder: sortOrder)
        )
    }

    @Test func albumsSortMenuListsItsOrdersAndSelectsOne() throws {
        var selected: AlbumSortOrder?
        let sut = LibrarySortMenu(
            identifier: "albums.sortMenu", orders: AlbumSortOrder.libraryMenuOrder, sortOrder: .albumName,
            title: \.libraryMenuTitle, onSelect: { selected = $0 }
        )
        let picker = try sut.inspect().find(ViewType.Picker.self)
        for title in ["Title", "Artist", "Year", "Date Added"] {
            #expect((try? picker.find(text: title)) != nil, "missing \(title)")
        }
        try picker.select(value: AlbumSortOrder.year)
        #expect(selected == .year)
    }

    @Test func everyTabsMenuOffersWhatTheDomainSorts() {
        #expect(SongSortOrder.libraryMenuOrder.map(\.libraryMenuTitle) == ["Title", "Artist", "Album", "Date Added", "Play Count", "Year"])
        #expect(AlbumSortOrder.libraryMenuOrder.map(\.libraryMenuTitle) == ["Title", "Artist", "Year", "Date Added"])
        #expect(AlbumArtistSortOrder.libraryMenuOrder.map(\.libraryMenuTitle) == ["Name", "Album Count"])
        #expect(PlaylistSortOrder.libraryMenuOrder.map(\.libraryMenuTitle) == ["Name", "Date Created"])
        #expect(GenreSortOrder.libraryMenuOrder.map(\.libraryMenuTitle) == ["Name", "Song Count"])
    }

    @Test func theAlbumsToolbarCarriesTheSortMenuOnlyOnceThereAreAlbums() throws {
        let shown = AlbumListContent(state: albumState([album(added: nil)], sortOrder: .year))
        #expect((try? shown.inspect().find(viewWithAccessibilityIdentifier: "albums.sortMenu")) != nil)
    }

    @Test func anAlbumRowShowsTheDateAddedUnderThatSort() throws {
        let added = KotlinInstant.companion.fromEpochMilliseconds(epochMilliseconds: 1_773_000_000_000)
        let byDate = try AlbumRow(album: album(added: added), sortOrder: .dateAdded).inspect().find(ViewType.Text.self) { try $0.string().contains("songs") }
        let day = try #require(libraryDateAdded(added))
        #expect(try byDate.string().hasSuffix(day))
        let byName = try AlbumRow(album: album(added: added), sortOrder: .albumName).inspect().find(ViewType.Text.self) { try $0.string().contains("songs") }
        #expect(try !byName.string().contains(day))
    }

    @Test func aSongRowShowsItsPlayCountUnderThatSort() throws {
        let song = TestSongs.demo[0]
        let played = TestSongs.song(1, "Paranoid Android", artist: "Radiohead", album: "OK Computer", durationMs: 386_000, playCount: 12)
        #expect((try? SongRow(song: played, sortOrder: .playCount).inspect().find(text: "Radiohead · OK Computer · 12 plays")) != nil)
        #expect((try? SongRow(song: played, sortOrder: .songName).inspect().find(text: "Radiohead · OK Computer")) != nil)
        // A song never played has no count to show (#702).
        #expect((try? SongRow(song: song, sortOrder: .playCount).inspect().find(text: "Radiohead · OK Computer")) != nil)
    }
}
