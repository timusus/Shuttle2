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

    // MARK: - Grouping, sort and reorder

    private func album(_ name: String) -> Album {
        Album(
            name: name, albumArtist: "Radiohead", artists: ["Radiohead"], songCount: 1, duration: 0,
            year: nil, playCount: 0, lastSongPlayed: nil, lastSongCompleted: nil,
            groupKey: AlbumGroupKey(key: name.lowercased(), albumArtistGroupKey: AlbumArtistGroupKey(key: "radiohead"), identity: nil),
            mediaProviders: [.jellyfin], artworkVersion: nil, dateAdded: nil, albumArtistKeys: [AlbumArtistGroupKey(key: "radiohead")]
        )
    }

    private func state(sort: SongListSort, sections: [SongSection], expanded: [Album] = []) -> PlaylistDetailUiState {
        let songs = sections.flatMap(\.songs).enumerated().map { entry(Int64($0.offset + 10), $0.element, sortOrder: Int64($0.offset)) }
        return PlaylistDetailUiState(
            playlist: playlist(), songs: songs, selectedIds: [], currentSong: nil, loading: false, events: [],
            sort: sort, sections: sections, expandedAlbums: Set(expanded.compactMap(\.groupKey))
        )
    }

    @Test func groupedShowsAlbumHeadersAndPlaysTheDisplayedOrder() throws {
        var played: Int?
        let ok = album("OK Computer")
        let kidA = album("Kid A")
        let sections = [SongSection(album: kidA, songs: [TestSongs.demo[1]]), SongSection(album: ok, songs: [TestSongs.demo[0]])]
        let sut = PlaylistDetailContent(state: state(sort: .albumTitle, sections: sections, expanded: [ok]), onPlay: { played = $0 })
        #expect((try? sut.inspect().find(text: "Albums")) != nil)
        #expect((try? sut.inspect().find(text: "Kid A")) != nil)
        // Only the unfolded album lists its song, and it plays from its place in the displayed order
        #expect((try? sut.inspect().find(text: "Hyperballad")) == nil)
        try sut.inspect().find(button: TestSongs.demo[0].name ?? "").tap()
        #expect(played == 1)
    }

    @Test func customOrderIsFlatReorderableWithNoHint() throws {
        let flat = state(sort: .playlistOrder, sections: [SongSection(album: nil, songs: Array(TestSongs.demo.prefix(2)))])
        #expect(flat.canReorder)
        let sut = PlaylistDetailContent(state: flat)
        #expect((try? sut.inspect().find(text: "Songs")) != nil)
        #expect((try? sut.inspect().find(viewWithAccessibilityIdentifier: "playlistDetail.reorderHint")) == nil)
    }

    @Test func otherSortsExplainHowToReorder() throws {
        let sorted = state(sort: .songTitle, sections: [SongSection(album: nil, songs: Array(TestSongs.demo.prefix(2)))])
        #expect(!sorted.canReorder)
        let sut = PlaylistDetailContent(state: sorted)
        #expect((try? sut.inspect().find(text: "Sorted by Song Title. Choose Custom to reorder.")) != nil)
    }

    @Test func sortMenuOffersTheUserPlaylistSortsAndSelectsOne() throws {
        var selected: SongListSort?
        let sorted = state(sort: .playlistOrder, sections: [SongSection(album: nil, songs: Array(TestSongs.demo.prefix(2)))])
        let sut = PlaylistDetailContent(state: sorted, onSortSelected: { selected = $0 })
        let picker = try sut.inspect().find(ViewType.Picker.self)
        for choice in PlaylistKind.user.sortChoices {
            #expect((try? picker.find(text: choice.title)) != nil)
        }
        try picker.select(value: SongListSort.albumTitle)
        #expect(selected == .albumTitle)
    }

    @Test func placeholders() throws {
        let loading = PlaylistDetailUiState(playlist: nil, songs: [], selectedIds: [], currentSong: nil, loading: true, events: [])
        #expect((try? PlaylistDetailContent(state: loading).inspect().find(ViewType.ProgressView.self)) != nil)
        let notFound = PlaylistDetailUiState(playlist: nil, songs: [], selectedIds: [], currentSong: nil, loading: false, events: [])
        #expect((try? PlaylistDetailContent(state: notFound).inspect().find(text: "Playlist Not Found")) != nil)
    }
}
