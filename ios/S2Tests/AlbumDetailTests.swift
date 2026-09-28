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

    private func album(songCount: Int32 = 2, year: Int32? = 1997) -> Album {
        Album(
            name: "OK Computer", albumArtist: "Radiohead", artists: ["Radiohead"], songCount: songCount, duration: 0,
            year: year.map { KotlinInt(int: $0) }, playCount: 0, lastSongPlayed: nil, lastSongCompleted: nil,
            groupKey: AlbumGroupKey(key: "ok computer", albumArtistGroupKey: AlbumArtistGroupKey(key: "radiohead"), identity: nil),
            mediaProviders: [.jellyfin], artworkVersion: nil
        )
    }

    @Test func readyShowsHeroAndSongs() throws {
        let songs = [song(1, "Airbag", track: 1, disc: 1), song(2, "Paranoid Android", track: 2, disc: 1)]
        let state = AlbumDetailUiState(album: album(), songs: songs, currentSong: nil, loadingState: .ready, seed: ArtworkSeedNone.shared)
        let sut = AlbumDetailContent(state: state)
        #expect((try? sut.inspect().find(text: "OK Computer")) != nil)
        #expect((try? sut.inspect().find(ViewType.Text.self, where: { try $0.string().hasPrefix("Radiohead · 1997 · 2 songs · ") })) != nil)
        #expect((try? sut.inspect().find(text: "Airbag")) != nil)
        #expect((try? sut.inspect().find(text: "Paranoid Android")) != nil)
    }

    @Test func multiDiscAlbumsGetDiscSections() throws {
        let songs = [song(1, "Track A", track: 1, disc: 1), song(2, "Track B", track: 1, disc: 2)]
        let state = AlbumDetailUiState(album: album(songCount: 2), songs: songs, currentSong: nil, loadingState: .ready, seed: ArtworkSeedNone.shared)
        let sut = AlbumDetailContent(state: state)
        #expect((try? sut.inspect().find(text: "Disc 1")) != nil)
        #expect((try? sut.inspect().find(text: "Disc 2")) != nil)
    }

    @Test func singleDiscAlbumsGetNoDiscSectionTitle() throws {
        let songs = [song(1, "Airbag", track: 1, disc: 1)]
        let state = AlbumDetailUiState(album: album(songCount: 1), songs: songs, currentSong: nil, loadingState: .ready, seed: ArtworkSeedNone.shared)
        let sut = AlbumDetailContent(state: state)
        #expect((try? sut.inspect().find(text: "Disc 1")) == nil)
    }

    @Test func tappingATrackPlaysFromItsIndex() throws {
        var played: Int?
        let songs = [song(1, "Airbag", track: 1, disc: 1), song(2, "Paranoid Android", track: 2, disc: 1)]
        let state = AlbumDetailUiState(album: album(), songs: songs, currentSong: nil, loadingState: .ready, seed: ArtworkSeedNone.shared)
        let sut = AlbumDetailContent(state: state, onPlay: { played = $0 })
        try sut.inspect().find(button: "Paranoid Android").tap()
        #expect(played == 1)
    }

    @Test func placeholders() throws {
        let empty = AlbumDetailUiState(album: nil, songs: [], currentSong: nil, loadingState: .loading, seed: ArtworkSeedNone.shared)
        #expect((try? AlbumDetailContent(state: empty).inspect().find(ViewType.ProgressView.self)) != nil)
        let notFound = AlbumDetailUiState(album: nil, songs: [], currentSong: nil, loadingState: .empty, seed: ArtworkSeedNone.shared)
        #expect((try? AlbumDetailContent(state: notFound).inspect().find(text: "Album Not Found")) != nil)
    }
}
