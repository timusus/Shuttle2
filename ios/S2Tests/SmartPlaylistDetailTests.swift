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

    // MARK: - Grouping and sort

    private func album(_ name: String, year: Int32) -> Album {
        Album(
            name: name, albumArtist: "Radiohead", artists: ["Radiohead"], songCount: 2, duration: 0,
            year: KotlinInt(int: year), playCount: 0, lastSongPlayed: nil, lastSongCompleted: nil,
            groupKey: AlbumGroupKey(key: name.lowercased(), albumArtistGroupKey: AlbumArtistGroupKey(key: "radiohead"), identity: nil),
            mediaProviders: [.jellyfin], artworkVersion: nil, dateAdded: nil, albumArtistKeys: [AlbumArtistGroupKey(key: "radiohead")]
        )
    }

    /// Recently Added grouped by album: OK Computer (the first two demo songs), Kid A (the next two), then the last song on none.
    private func grouped(_ sort: SongListSort = .albumNewestAdded, expanded: [Album] = []) -> SmartPlaylistDetailUiState {
        let ok = album("OK Computer", year: 1997)
        let kidA = album("Kid A", year: 2000)
        let songs = TestSongs.demo
        let sections = [
            SongSection(album: ok, songs: Array(songs[0..<2])),
            SongSection(album: kidA, songs: Array(songs[2..<4])),
            SongSection(album: nil, songs: [songs[4]])
        ]
        return SmartPlaylistDetailUiState(
            smartPlaylist: SmartPlaylistId.recentlyAdded.smartPlaylist, songs: songs, currentSong: nil, loading: false,
            sort: sort, sections: sections, expandedAlbums: Set(expanded.compactMap(\.groupKey)), events: []
        )
    }

    @Test func groupedShowsFoldedAlbumHeadersAndOtherSongs() throws {
        let sut = SmartPlaylistDetailContent(state: grouped())
        #expect((try? sut.inspect().find(text: "Albums")) != nil)
        #expect((try? sut.inspect().find(text: "OK Computer")) != nil)
        #expect((try? sut.inspect().find(text: "Kid A")) != nil)
        #expect((try? sut.inspect().find(text: "Other Songs")) != nil)
        // Folded: only the unattached song's row is listed
        #expect((try? sut.inspect().find(text: "Pyramid Song")) != nil)
        #expect((try? sut.inspect().find(text: "Paranoid Android")) == nil)
    }

    @Test func expandedAlbumListsItsSongsAndTapsPlayTheDisplayedOrder() throws {
        var played: Int?
        let kidA = album("Kid A", year: 2000)
        let sut = SmartPlaylistDetailContent(state: grouped(expanded: [kidA]), onPlay: { played = $0 })
        #expect((try? sut.inspect().find(text: "Paranoid Android")) == nil)
        try sut.inspect().find(button: TestSongs.demo[3].name ?? "").tap()
        #expect(played == 3)
    }

    @Test func togglingAlbumHeaderReportsTheAlbum() throws {
        var toggled: Album?
        let sut = SmartPlaylistDetailContent(state: grouped(), onToggleAlbum: { toggled = $0 })
        try sut.inspect().find(AlbumSectionHeader.self).find(ViewType.Button.self, where: { try $0.accessibilityIdentifier() == "artistDetail.albumHeader" }).tap()
        #expect(toggled?.name == "OK Computer")
    }

    @Test func flatSortHasNoAlbumSections() throws {
        let state = SmartPlaylistDetailUiState(
            smartPlaylist: SmartPlaylistId.recentlyAdded.smartPlaylist, songs: TestSongs.demo, currentSong: nil, loading: false,
            sort: .newestAdded, sections: [SongSection(album: nil, songs: TestSongs.demo)], expandedAlbums: [], events: []
        )
        let sut = SmartPlaylistDetailContent(state: state)
        #expect((try? sut.inspect().find(text: "Songs")) != nil)
        #expect((try? sut.inspect().find(text: "Albums")) == nil)
        #expect((try? sut.inspect().find(AlbumSectionHeader.self)) == nil)
        #expect((try? sut.inspect().find(text: "Other Songs")) == nil)
        #expect((try? sut.inspect().find(text: "Paranoid Android")) != nil)
    }

    @Test func sortMenuListsTheKindsSortsAndSelectsOne() throws {
        var selected: SongListSort?
        let sut = SmartPlaylistDetailContent(state: grouped(), onSortSelected: { selected = $0 })
        let picker = try sut.inspect().find(ViewType.Picker.self)
        for choice in PlaylistKind.recentlyAdded.sortChoices {
            #expect((try? picker.find(text: choice.title)) != nil)
        }
        #expect((try? sut.inspect().find(text: "New Albums")) != nil)
        try picker.select(value: SongListSort.songTitle)
        #expect(selected == .songTitle)
    }

    @Test func recentlyAddedGroupsDownloadedAndHistoryDoesNot() {
        #expect(PlaylistKind.recentlyAdded.defaultSort == .albumNewestAdded)
        #expect(PlaylistKind.history.sorts.allSatisfy { !$0.groupsByAlbum })
    }

    @Test func placeholders() throws {
        let loading = SmartPlaylistDetailUiState(smartPlaylist: nil, songs: [], currentSong: nil, loading: true)
        #expect((try? SmartPlaylistDetailContent(state: loading).inspect().find(ViewType.ProgressView.self)) != nil)
        let notFound = SmartPlaylistDetailUiState(smartPlaylist: nil, songs: [], currentSong: nil, loading: false)
        #expect((try? SmartPlaylistDetailContent(state: notFound).inspect().find(text: "Playlist Not Found")) != nil)
    }
}
