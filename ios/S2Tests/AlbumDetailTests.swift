import Shared
import SwiftUI
import Testing
import ViewInspector
@testable import S2

/// Album detail from its UiState: the loading/not-found placeholders, the hero's title/subtitle, disc grouping,
/// and what tapping a track plays.
@MainActor
struct AlbumDetailTests {
    private func song(_ id: Int64, _ name: String, track: Int32?, disc: Int32?) -> Song {
        Song(
            id: id, name: name, albumArtist: "Radiohead", artists: ["Radiohead"], album: "OK Computer",
            track: track.map { KotlinInt(int: $0) }, disc: disc.map { KotlinInt(int: $0) }, duration: 240_000,
            date: nil, genres: [], path: "demo://\(id)", size: 0, mimeType: "audio/flac", lastModified: nil,
            lastPlayed: nil, lastCompleted: nil, playCount: 0, playbackPosition: 0, blacklisted: false,
            externalId: nil, mediaProvider: .shuttle, replayGainTrack: nil, replayGainAlbum: nil, lyrics: nil,
            grouping: nil, bitRate: nil, bitDepth: nil, sampleRate: nil, channelCount: nil, audioCodec: nil,
            artworkVersion: nil, dateAdded: nil, favouritedAt: nil, albumArtists: nil, artistsTag: nil,
            artistDisplay: nil, compilation: nil, mbTrackId: nil, mbAlbumId: nil, mbReleaseGroupId: nil,
            mbArtistIds: nil, mbAlbumArtistIds: nil, serverAlbumId: nil, serverArtistIds: nil,
            serverAlbumArtistIds: nil, albumIdentity: nil
        )
    }

    private func album(songCount: Int32 = 2, year: Int32? = 1997, albumArtist: String? = "Radiohead", artists: [String] = ["Radiohead"], artistKey: String? = "radiohead", albumArtistKeys: [String]? = nil) -> Album {
        Album(
            name: "OK Computer", albumArtist: albumArtist, artists: artists, songCount: songCount, duration: 0,
            year: year.map { KotlinInt(int: $0) }, playCount: 0, lastSongPlayed: nil, lastSongCompleted: nil,
            groupKey: AlbumGroupKey(key: "ok computer", albumArtistGroupKey: AlbumArtistGroupKey(key: artistKey), identity: nil),
            mediaProviders: [.jellyfin], artworkVersion: nil, dateAdded: nil,
            albumArtistKeys: (albumArtistKeys ?? artistKey.map { [$0] } ?? []).map { AlbumArtistGroupKey(key: $0) }
        )
    }

    @Test func readyShowsHeroAndSongs() throws {
        let songs = [song(1, "Airbag", track: 1, disc: 1), song(2, "Paranoid Android", track: 2, disc: 1)]
        let state = AlbumDetailUiState(album: album(), songs: songs, currentSong: nil, loadingState: .ready, seed: ArtworkSeedNone.shared, moreByArtist: [])
        let sut = AlbumDetailContent(state: state)
        #expect((try? sut.inspect().find(text: "OK Computer")) != nil)
        #expect((try? sut.inspect().find(ViewType.Text.self, where: { try $0.string().hasPrefix("Radiohead · 1997 · 2 songs · ") })) != nil)
        #expect((try? sut.inspect().find(text: "Airbag")) != nil)
        #expect((try? sut.inspect().find(text: "Paranoid Android")) != nil)
    }

    @Test func multiDiscAlbumsGetDiscSections() throws {
        let songs = [song(1, "Track A", track: 1, disc: 1), song(2, "Track B", track: 1, disc: 2)]
        let state = AlbumDetailUiState(album: album(songCount: 2), songs: songs, currentSong: nil, loadingState: .ready, seed: ArtworkSeedNone.shared, moreByArtist: [])
        let sut = AlbumDetailContent(state: state)
        #expect((try? sut.inspect().find(text: "Disc 1")) != nil)
        #expect((try? sut.inspect().find(text: "Disc 2")) != nil)
    }

    @Test func singleDiscAlbumsGetNoDiscSectionTitle() throws {
        let songs = [song(1, "Airbag", track: 1, disc: 1)]
        let state = AlbumDetailUiState(album: album(songCount: 1), songs: songs, currentSong: nil, loadingState: .ready, seed: ArtworkSeedNone.shared, moreByArtist: [])
        let sut = AlbumDetailContent(state: state)
        #expect((try? sut.inspect().find(text: "Disc 1")) == nil)
    }

    private func moreByState(_ others: [Album]) -> AlbumDetailUiState {
        AlbumDetailUiState(album: album(songCount: 1), songs: [song(1, "Airbag", track: 1, disc: 1)], currentSong: nil, loadingState: .ready, seed: ArtworkSeedNone.shared, moreByArtist: others)
    }

    @Test func moreByShelfShowsTheArtistsOtherAlbums() throws {
        let sut = AlbumDetailContent(state: moreByState([album(year: 2000)]))
        #expect((try? sut.inspect().find(text: "More by Radiohead")) != nil)
        #expect(try sut.inspect().findAll(ViewType.Button.self, where: { (try? $0.accessibilityIdentifier()) == "detailTile.moreBy" }).count == 1)
    }

    @Test func moreByShelfIsHiddenWithoutAlbums() throws {
        let sut = AlbumDetailContent(state: moreByState([]))
        #expect((try? sut.inspect().find(text: "More by Radiohead")) == nil)
    }

    @Test func tappingAMoreByTileOpensItsAlbum() throws {
        var opened: Album?
        let other = album(year: 2000)
        let sut = AlbumDetailContent(state: moreByState([other]), onAlbumTap: { opened = $0 })
        try sut.inspect().find(ViewType.Button.self, where: { (try? $0.accessibilityIdentifier()) == "detailTile.moreBy" }).tap()
        #expect(opened?.stableId == other.stableId)
    }

    @Test func tappingATrackPlaysFromItsIndex() throws {
        var played: Int?
        let songs = [song(1, "Airbag", track: 1, disc: 1), song(2, "Paranoid Android", track: 2, disc: 1)]
        let state = AlbumDetailUiState(album: album(), songs: songs, currentSong: nil, loadingState: .ready, seed: ArtworkSeedNone.shared, moreByArtist: [])
        let sut = AlbumDetailContent(state: state, onPlay: { played = $0 })
        try sut.inspect().find(button: "Paranoid Android").tap()
        #expect(played == 1)
    }

    @Test func artistLineGoesToTheArtist() throws {
        var opened = 0
        let songs = [song(1, "Airbag", track: 1, disc: 1)]
        let state = AlbumDetailUiState(album: album(songCount: 1), songs: songs, currentSong: nil, loadingState: .ready, seed: ArtworkSeedNone.shared, moreByArtist: [])
        let sut = AlbumDetailContent(state: state, onGoToArtist: { opened += 1 })
        try sut.inspect().find(ViewType.Button.self, where: { (try? $0.accessibilityLabel().string()) == "Go to Radiohead" }).tap()
        #expect(opened == 1)
        // The eyebrow no longer repeats the artist.
        #expect((try? sut.inspect().find(ViewType.Text.self, where: { try $0.string().hasPrefix("1997 · 1 song") })) != nil)
    }

    private func artistButton(_ sut: AlbumDetailContent, _ label: String) -> InspectableView<ViewType.Button>? {
        try? sut.inspect().find(ViewType.Button.self, where: { (try? $0.accessibilityLabel().string()) == label })
    }

    @Test func noArtistKeyMeansNoArtistButton() throws {
        let songs = [song(1, "Airbag", track: 1, disc: 1)]
        let state = AlbumDetailUiState(album: album(songCount: 1, artistKey: nil), songs: songs, currentSong: nil, loadingState: .ready, seed: ArtworkSeedNone.shared, moreByArtist: [])
        let sut = AlbumDetailContent(state: state, onGoToArtist: {})
        #expect(artistButton(sut, "Go to Radiohead") == nil)
        // The artist stays in the eyebrow.
        #expect((try? sut.inspect().find(ViewType.Text.self, where: { try $0.string().hasPrefix("Radiohead · 1997") })) != nil)
    }

    @Test func compilationLinkNamesTheAlbumArtist() throws {
        let songs = [song(1, "Airbag", track: 1, disc: 1)]
        let compilation = album(songCount: 1, albumArtist: "Various Artists", artists: ["A", "B", "C"], artistKey: "various artists")
        let state = AlbumDetailUiState(album: compilation, songs: songs, currentSong: nil, loadingState: .ready, seed: ArtworkSeedNone.shared, moreByArtist: [])
        let sut = AlbumDetailContent(state: state, onGoToArtist: {})
        #expect(artistButton(sut, "Go to Various Artists") != nil)
        #expect(artistButton(sut, "Go to A, B, C") == nil)
    }

    @Test func linkMapping() {
        let normal = AlbumArtistLink(name: "Radiohead", key: "radiohead")
        #expect(normal?.name == "Radiohead")
        #expect(normal?.route == .albumArtist(albumArtistKey: "radiohead"))
        let compilation = AlbumArtistLink(album(albumArtist: "Various Artists", artists: ["A", "B"], artistKey: "various artists"))
        #expect(compilation?.name == "Various Artists")
        #expect(compilation?.route == .albumArtist(albumArtistKey: "various artists"))
        // An album of several album artists, or one featuring another, opens its primary artist, not their joint key
        let several = AlbumArtistLink(album(albumArtist: "Radiohead, Thom Yorke", artistKey: "radiohead, thom yorke", albumArtistKeys: ["radiohead", "thom yorke"]))
        #expect(several?.route == .albumArtist(albumArtistKey: "radiohead"))
        let featuring = AlbumArtistLink(album(albumArtist: "Radiohead feat. Björk", artistKey: "radiohead feat. björk", albumArtistKeys: ["radiohead"]))
        #expect(featuring?.route == .albumArtist(albumArtistKey: "radiohead"))
        #expect(AlbumArtistLink(name: "Radiohead", key: nil) == nil)
        #expect(AlbumArtistLink(album(artistKey: nil)) == nil)
        #expect(AlbumArtistLink(name: nil, key: "radiohead") == nil)
        #expect(AlbumArtistLink(name: "  ", key: "radiohead") == nil)
        #expect(AlbumArtistLink(album(albumArtist: "")) == nil)
    }

    @Test func moreMenuActsOnTheWholeAlbum() throws {
        var next: String?
        var queued: String?
        var artist = 0
        let songs = [song(1, "Airbag", track: 1, disc: 1)]
        let state = AlbumDetailUiState(album: album(songCount: 1), songs: songs, currentSong: nil, loadingState: .ready, seed: ArtworkSeedNone.shared, moreByArtist: [])
        let sut = AlbumDetailContent(
            state: state,
            onPlayAlbumNext: { next = $0.name },
            onAddAlbumToQueue: { queued = $0.name },
            onGoToArtist: { artist += 1 }
        )
        let menu = try sut.inspect().find(ViewType.Menu.self)
        try menu.find(button: "Play Next").tap()
        try menu.find(button: "Add to Queue").tap()
        try menu.find(button: "Go to Artist").tap()
        #expect(next == "OK Computer")
        #expect(queued == "OK Computer")
        #expect(artist == 1)
    }

    @Test func moreMenuHidesGoToArtistWithoutAKey() throws {
        let songs = [song(1, "Airbag", track: 1, disc: 1)]
        let state = AlbumDetailUiState(album: album(songCount: 1, artistKey: nil), songs: songs, currentSong: nil, loadingState: .ready, seed: ArtworkSeedNone.shared, moreByArtist: [])
        let menu = try AlbumDetailContent(state: state, onGoToArtist: {}).inspect().find(ViewType.Menu.self)
        #expect((try? menu.find(button: "Go to Artist")) == nil)
    }

    @Test func placeholders() throws {
        let empty = AlbumDetailUiState(album: nil, songs: [], currentSong: nil, loadingState: .loading, seed: ArtworkSeedNone.shared, moreByArtist: [])
        #expect((try? AlbumDetailContent(state: empty).inspect().find(ViewType.ProgressView.self)) != nil)
        let notFound = AlbumDetailUiState(album: nil, songs: [], currentSong: nil, loadingState: .empty, seed: ArtworkSeedNone.shared, moreByArtist: [])
        #expect((try? AlbumDetailContent(state: notFound).inspect().find(text: "Album Not Found")) != nil)
    }
}
