import Shared

/// A few Kotlin songs for the tests, on `demo://N` paths: the fake engine takes any URL, and the stream
/// resolver passes these through as they are.
enum TestSongs {
    static let demo: [Song] = [
        song(1, "Paranoid Android", artist: "Radiohead", album: "OK Computer", durationMs: 386_000),
        song(2, "Hyperballad", artist: "Björk", album: "Post", durationMs: 321_000),
        song(3, "Teardrop", artist: "Massive Attack", album: "Mezzanine", durationMs: 330_000),
        song(4, "Unfinished Sympathy", artist: "Massive Attack", album: "Blue Lines", durationMs: 308_000),
        song(5, "Pyramid Song", artist: "Radiohead", album: "Amnesiac", durationMs: 289_000),
    ]

    static func song(
        _ id: Int64, _ name: String, artist: String, album: String, durationMs: Int32,
        artists: [String]? = nil, playCount: Int32 = 0, lastPlayed: KotlinInstant? = nil, dateAdded: KotlinInstant? = nil,
        favourite: Bool = false, provider: MediaProviderType = .shuttle,
        mimeType: String = "audio/flac", bitRate: Int32? = nil, bitDepth: Int32? = nil, sampleRate: Int32? = nil
    ) -> Song {
        Song(
            id: id, name: name, albumArtist: artist, artists: artists ?? [artist], album: album, track: nil, disc: nil,
            duration: durationMs, date: nil, genres: [], path: "demo://\(id)", size: 0, mimeType: mimeType,
            lastModified: nil, lastPlayed: lastPlayed, lastCompleted: nil, playCount: playCount, playbackPosition: 0,
            blacklisted: false, externalId: nil, mediaProvider: provider, replayGainTrack: nil,
            replayGainAlbum: nil, lyrics: nil, grouping: nil, bitRate: bitRate.map { KotlinInt(int: $0) }, bitDepth: bitDepth.map { KotlinInt(int: $0) }, sampleRate: sampleRate.map { KotlinInt(int: $0) },
            channelCount: nil, audioCodec: nil, artworkVersion: nil, dateAdded: dateAdded, favouritedAt: favourite ? KotlinInstant.companion.fromEpochMilliseconds(epochMilliseconds: 1_773_000_000_000) : nil,
            albumArtists: nil, artistsTag: nil, artistDisplay: nil, compilation: nil, mbTrackId: nil, mbAlbumId: nil,
            mbReleaseGroupId: nil, mbArtistIds: nil, mbAlbumArtistIds: nil, serverAlbumId: nil, serverArtistIds: nil,
            serverAlbumArtistIds: nil, albumIdentity: nil
        )
    }
}
