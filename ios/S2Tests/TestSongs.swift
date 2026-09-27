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

    static func song(_ id: Int64, _ name: String, artist: String, album: String, durationMs: Int32) -> Song {
        Song(
            id: id, name: name, albumArtist: artist, artists: [artist], album: album, track: nil, disc: nil,
            duration: durationMs, date: nil, genres: [], path: "demo://\(id)", size: 0, mimeType: "audio/flac",
            lastModified: nil, lastPlayed: nil, lastCompleted: nil, playCount: 0, playbackPosition: 0,
            blacklisted: false, externalId: nil, mediaProvider: .shuttle, replayGainTrack: nil,
            replayGainAlbum: nil, lyrics: nil, grouping: nil, bitRate: nil, bitDepth: nil, sampleRate: nil,
            channelCount: nil, audioCodec: nil, artworkVersion: nil, dateAdded: nil, favouritedAt: nil
        )
    }
}
