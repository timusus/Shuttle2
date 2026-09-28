import Shared
import SwiftUI
import Testing
import ViewInspector
@testable import S2

/// Album artist detail from its UiState: the hero, the album shelf's links, the flat song list, and what
/// tapping a song plays.
@MainActor
struct AlbumArtistDetailTests {
    private func album(_ name: String) -> Album {
        Album(
            name: name, albumArtist: "Radiohead", artists: ["Radiohead"], songCount: 1, duration: 0,
            year: nil, playCount: 0, lastSongPlayed: nil, lastSongCompleted: nil,
            groupKey: AlbumGroupKey(key: name.lowercased(), albumArtistGroupKey: AlbumArtistGroupKey(key: "radiohead")),
            mediaProviders: [.jellyfin], artworkVersion: nil
        )
    }

    private func artist() -> AlbumArtist {
        AlbumArtist(
            name: "Radiohead", artists: ["Radiohead"], albumCount: 2, songCount: 2, playCount: 0,
            groupKey: AlbumArtistGroupKey(key: "radiohead"), mediaProviders: [.jellyfin], artworkVersion: nil
        )
    }

    @Test func readyShowsHeroShelfAndSongs() throws {
        let state = AlbumArtistDetailUiState(
            albumArtist: artist(), albums: [album("OK Computer"), album("Kid A")], songs: TestSongs.demo,
            currentSong: nil, expandedAlbums: [], loadingState: .ready, events: [], seed: ArtworkSeedNone.shared
        )
        let sut = AlbumArtistDetailContent(state: state)
        #expect((try? sut.inspect().find(text: "Radiohead")) != nil)
        #expect((try? sut.inspect().find(text: "2 albums · 5 songs")) != nil)
        #expect((try? sut.inspect().find(text: "OK Computer")) != nil)
        #expect((try? sut.inspect().find(text: "Kid A")) != nil)
        #expect((try? sut.inspect().find(text: "Paranoid Android")) != nil)
    }

    @Test func tappingAnAlbumTileOpensIt() throws {
        var opened: Album?
        let state = AlbumArtistDetailUiState(
            albumArtist: artist(), albums: [album("OK Computer"), album("Kid A")], songs: TestSongs.demo,
            currentSong: nil, expandedAlbums: [], loadingState: .ready, events: [], seed: ArtworkSeedNone.shared
        )
        let sut = AlbumArtistDetailContent(state: state, onAlbumTap: { opened = $0 })
        try sut.inspect().find(button: "OK Computer").tap()
        #expect(opened?.name == "OK Computer")
    }

    @Test func tappingASongPlaysFromItsIndex() throws {
        var played: Int?
        let state = AlbumArtistDetailUiState(
            albumArtist: artist(), albums: [], songs: TestSongs.demo, currentSong: nil, expandedAlbums: [],
            loadingState: .ready, events: [], seed: ArtworkSeedNone.shared
        )
        let sut = AlbumArtistDetailContent(state: state, onPlay: { played = $0 })
        try sut.inspect().find(button: "Teardrop").tap()
        #expect(played == 2)
    }

    @Test func placeholders() throws {
        let loading = AlbumArtistDetailUiState(
            albumArtist: nil, albums: [], songs: [], currentSong: nil, expandedAlbums: [], loadingState: .loading,
            events: [], seed: ArtworkSeedNone.shared
        )
        #expect((try? AlbumArtistDetailContent(state: loading).inspect().find(ViewType.ProgressView.self)) != nil)
        let notFound = AlbumArtistDetailUiState(
            albumArtist: nil, albums: [], songs: [], currentSong: nil, expandedAlbums: [], loadingState: .empty,
            events: [], seed: ArtworkSeedNone.shared
        )
        #expect((try? AlbumArtistDetailContent(state: notFound).inspect().find(text: "Artist Not Found")) != nil)
    }
}
