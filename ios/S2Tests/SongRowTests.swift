import Foundation
import Shared
import SwiftUI
import Testing
import ViewInspector

@testable import S2

/// The one song row (#702): its credits, its sort key, its glyphs, and the configurations each screen gives it.
@MainActor
struct SongRowTests {
    private let instant = KotlinInstant.companion.fromEpochMilliseconds(epochMilliseconds: 1_773_000_000_000)

    // MARK: - Credits

    @Test func aLibraryRowLeadsWithTheArtistThenTheAlbum() {
        #expect(SongRow.subtitle(TestSongs.demo[0]) == "Radiohead · OK Computer")
    }

    @Test func anArtistsOwnRowDropsTheirNameAndShowsTheAlbum() {
        #expect(SongRow.subtitle(TestSongs.demo[0], omittingArtist: "Radiohead") == "OK Computer")
        #expect(SongRow.subtitle(TestSongs.demo[0], omittingArtist: " radiohead ") == "OK Computer")
    }

    @Test func anArtistsRowShowsOtherCreditedArtistsWhenTheyDiffer() {
        let song = TestSongs.song(9, "Reckoner", artist: "Radiohead", album: "In Rainbows", durationMs: 1, artists: ["Radiohead", "Thom Yorke"])
        #expect(SongRow.credit(song, omitting: "Radiohead") == "Thom Yorke")
        #expect(SongRow.subtitle(song, omittingArtist: "Radiohead") == "Thom Yorke · In Rainbows")
    }

    @Test func aLeadingTheDoesNotMakeADifferentArtist() {
        let song = TestSongs.song(8, "Inertiatic ESP", artist: "The Mars Volta", album: "De-Loused", durationMs: 1, artists: ["The Mars Volta"])
        #expect(SongRow.credit(song, omitting: "Mars Volta") == nil)
    }

    @Test func anotherArtistsSongKeepsItsCredit() {
        #expect(SongRow.subtitle(TestSongs.demo[1], omittingArtist: "Radiohead") == "Björk · Post")
    }

    // MARK: - Sort key

    @Test func playsReadAsACount() {
        let one = TestSongs.song(1, "A", artist: "X", album: "Y", durationMs: 1, playCount: 1)
        let many = TestSongs.song(2, "B", artist: "X", album: "Y", durationMs: 1, playCount: 12)
        #expect(SongRowKey.plays.text(for: one) == "1 play")
        #expect(SongRowKey.plays.text(for: many) == "12 plays")
        #expect(SongRow.subtitle(many, key: .plays, omittingArtist: "X") == "Y · 12 plays")
    }

    @Test func aSongWithNoPlaysShowsNoPlayCount() {
        let none = TestSongs.song(3, "C", artist: "X", album: "Y", durationMs: 1, playCount: 0)
        #expect(SongRowKey.plays.text(for: none) == nil)
        #expect(SongRow.subtitle(none, key: .plays, omittingArtist: "X") == "Y")
    }

    @Test func lastPlayedReadsRelativeToNow() {
        let song = TestSongs.song(1, "A", artist: "X", album: "Y", durationMs: 1, lastPlayed: instant)
        let now = Date(timeIntervalSince1970: 1_773_000_000 + 3 * 86_400)
        #expect(SongRowKey.lastPlayed.text(for: song, now: now) == "3 days ago")
        #expect(SongRowKey.lastPlayed.text(for: TestSongs.demo[0]) == nil)
    }

    @Test func dateAddedReadsAsTheDate() {
        let song = TestSongs.song(1, "A", artist: "X", album: "Y", durationMs: 1, dateAdded: instant)
        #expect(SongRowKey.dateAdded.text(for: song) == libraryDateAdded(instant))
    }

    @Test func aMissingValueLeavesTheKeyOut() {
        #expect(SongRow.subtitle(TestSongs.demo[0], key: .dateAdded) == "Radiohead · OK Computer")
    }

    @Test func artistMostPlayedShowsPlaysAndOtherOrdersDoNot() {
        #expect(SongRowKey(artistSortOrder: .mostPlayed) == .plays)
        #expect(SongRowKey(artistSortOrder: .albumNewest) == nil)
        #expect(SongRowKey(artistSortOrder: .songTitle) == nil)
    }

    @Test func smartPlaylistsShowTheKeyTheyAreOrderedBy() {
        #expect(SongRowKey(smartPlaylist: .mostPlayed) == .plays)
        #expect(SongRowKey(smartPlaylist: .history) == .lastPlayed)
        #expect(SongRowKey(smartPlaylist: .recentlyAdded) == .dateAdded)
        #expect(SongRowKey(smartPlaylist: .favourites) == nil)
    }

    @Test func theLibrarySortsMapToTheirKeys() {
        #expect(SongRowKey(sortOrder: .playCount) == .plays)
        #expect(SongRowKey(sortOrder: .dateAdded) == .dateAdded)
        #expect(SongRowKey(sortOrder: .songName) == nil)
        #expect(SongRowKey(sortOrder: nil) == nil)
    }

    // MARK: - Glyphs

    @Test func aFavouriteShowsAHeartAndOthersDoNot() throws {
        let heart = TestSongs.song(1, "A", artist: "X", album: "Y", durationMs: 1, favourite: true)
        #expect((try? SongRow(song: heart).inspect().find(viewWithAccessibilityIdentifier: "songRow.favourite")) != nil)
        #expect((try? SongRow(song: TestSongs.demo[0]).inspect().find(viewWithAccessibilityIdentifier: "songRow.favourite")) == nil)
    }

    @Test func aServerSongShowsNoServerGlyph() throws {
        let remote = TestSongs.song(1, "A", artist: "X", album: "Y", durationMs: 1, provider: .plex)
        #expect((try? SongRow(song: remote).inspect().find(viewWithAccessibilityIdentifier: "songRow.server")) == nil)
    }

    // MARK: - Menu

    @Test func theMenuOffersSongInfoOnlyWhereTheScreenPresentsIt() throws {
        var info: Song?
        let with = SongRowMenu(song: TestSongs.demo[1], onPlayNext: { _ in }, onAddToQueue: { _ in }, onExclude: { _ in }, onSongInfo: { info = $0 })
        try with.inspect().find(button: "Song Info").tap()
        #expect(info?.id == TestSongs.demo[1].id)
        let without = SongRowMenu(song: TestSongs.demo[1], onPlayNext: { _ in }, onAddToQueue: { _ in }, onExclude: { _ in })
        #expect((try? without.inspect().find(button: "Song Info")) == nil)
    }

    // MARK: - Screens

    @Test func artistDetailRowsDropTheArtistsNameAndShowPlaysUnderMostPlayed() throws {
        let song = TestSongs.song(1, "Reckoner", artist: "Radiohead", album: "In Rainbows", durationMs: 1, playCount: 12)
        let state = AlbumArtistDetailUiState(
            albumArtist: AlbumArtist(
                name: "Radiohead", artists: ["Radiohead"], albumCount: 1, songCount: 1, playCount: 12,
                groupKey: AlbumArtistGroupKey(key: "radiohead"), mediaProviders: [.shuttle], artworkVersion: nil, appearsOnCount: 0
            ),
            albums: [], appearsOn: [], songs: [song], sortOrder: .mostPlayed, sections: [.init(album: nil, songs: [song])],
            currentSong: nil, expandedAlbums: [], loadingState: .ready, events: [], hero: nil, seed: ArtworkSeedNone.shared
        )
        let sut = AlbumArtistDetailContent(state: state, heroPhoto: .loaded(nil))
        #expect((try? sut.inspect().find(text: "In Rainbows · 12 plays")) != nil)
    }

    @Test func aSmartPlaylistShowsItsKeyOnItsRows() throws {
        let song = TestSongs.song(1, "Reckoner", artist: "Radiohead", album: "In Rainbows", durationMs: 1, playCount: 7)
        let state = SmartPlaylistDetailUiState(smartPlaylist: SmartPlaylistId.mostPlayed.smartPlaylist, songs: [song], currentSong: nil, loading: false)
        let sut = SmartPlaylistDetailContent(state: state)
        #expect((try? sut.inspect().find(text: "Radiohead · In Rainbows · 7 plays")) != nil)
    }
}

/// The rest of #702's consistency work: one artist wording, playlist runtime, the Search top result's kind.
@MainActor
struct RowTextTests {
    private func artist(albums: Int32, songs: Int32) -> AlbumArtist {
        AlbumArtist(
            name: "X", artists: ["X"], albumCount: albums, songCount: songs, playCount: 0,
            groupKey: AlbumArtistGroupKey(key: "x"), mediaProviders: [.shuttle], artworkVersion: nil, appearsOnCount: 0
        )
    }

    @Test func artistSubtitleIsTheSameInLibraryAndSearch() {
        let sut = artist(albums: 2, songs: 14)
        #expect(AlbumArtistRow.subtitle(sut) == "2 albums · 14 songs")
        #expect(AlbumArtistRow.subtitle(artist(albums: 1, songs: 1)) == "1 album · 1 song")
    }

    @Test func playlistSubtitleCarriesTheRuntimeFromAMinute() {
        func playlist(songs: Int32, ms: Int32) -> Playlist {
            Playlist(id: 1, name: "P", songCount: songs, duration: ms, sortOrder: .position, sortDescending: false, mediaProvider: .shuttle, externalId: nil)
        }
        // The abbreviation's punctuation ("min" / "min.") follows the locale, so only the prefix is pinned.
        #expect(PlaylistRow.subtitle(playlist(songs: 12, ms: 43 * 60_000)).hasPrefix("12 songs · 43 min"))
        #expect(PlaylistRow.subtitle(playlist(songs: 1, ms: 20_000)) == "1 song")
    }

    @Test func searchLabelsTheTopResultByKind() {
        #expect(SearchCategory.artists.kindLabel == "Artist")
        #expect(SearchCategory.albums.kindLabel == "Album")
        #expect(SearchCategory.songs.kindLabel == "Song")
        #expect(SearchCategory.playlists.kindLabel == "Playlist")
        #expect(SearchCategory.genres.kindLabel == "Genre")
    }
}
