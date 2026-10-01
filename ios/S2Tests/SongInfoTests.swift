import Foundation
import Shared
import Testing
@testable import S2

/// The audio-quality labels and Song Info's sections: what shows, in what order, and what is left out.
struct SongInfoTests {
    private let en = Locale(identifier: "en_US")

    private func song(
        name: String? = "Teardrop", mimeType: String = "audio/flac", codec: String? = nil, bitDepth: Int32? = nil,
        sampleRate: Int32? = nil, bitRate: Int32? = nil, path: String = "/Music/Teardrop.flac", size: Int64 = 0,
        playCount: Int32 = 0, provider: MediaProviderType = .shuttle
    ) -> Song {
        Song(
            id: 1, name: name, albumArtist: nil, artists: ["Massive Attack"], album: "Mezzanine", track: nil, disc: nil,
            duration: 330_000, date: nil, genres: [], path: path, size: size, mimeType: mimeType,
            lastModified: nil, lastPlayed: nil, lastCompleted: nil, playCount: playCount, playbackPosition: 0,
            blacklisted: false, externalId: nil, mediaProvider: provider, replayGainTrack: nil,
            replayGainAlbum: nil, lyrics: nil, grouping: nil, bitRate: bitRate.map { KotlinInt(int: $0) },
            bitDepth: bitDepth.map { KotlinInt(int: $0) }, sampleRate: sampleRate.map { KotlinInt(int: $0) },
            channelCount: nil, audioCodec: codec, artworkVersion: nil, dateAdded: nil, favouritedAt: nil,
            albumArtists: nil, artistsTag: nil, artistDisplay: nil, compilation: nil, mbTrackId: nil, mbAlbumId: nil,
            mbReleaseGroupId: nil, mbArtistIds: nil, mbAlbumArtistIds: nil, serverAlbumId: nil, serverArtistIds: nil,
            serverAlbumArtistIds: nil, albumIdentity: nil
        )
    }

    // MARK: - AudioQuality

    @Test func summaryJoinsFormatResolutionAndBitRate() {
        let quality = AudioQuality(mimeType: "audio/flac", bitDepth: 24, sampleRate: 96_000, bitRate: 2_304)
        #expect(quality.summary(locale: en) == "FLAC · 24-bit / 96 kHz · 2,304 kbps")
    }

    @Test func summaryKeepsFractionalKilohertzAndSkipsMissingParts() {
        #expect(AudioQuality(mimeType: "audio/mpeg", sampleRate: 44_100, bitRate: 320).summary(locale: en) == "MP3 · 44.1 kHz · 320 kbps")
        #expect(AudioQuality(mimeType: "audio/flac").summary(locale: en) == "FLAC")
        #expect(AudioQuality(mimeType: "").summary(locale: en) == nil)
    }

    @Test func codecWinsOverContainer() {
        #expect(AudioQuality(codec: "alac", mimeType: "audio/mp4").format == "ALAC")
        #expect(AudioQuality(mimeType: "audio/mp4").format == "M4A")
        #expect(AudioQuality(mimeType: "audio/x-ogg; codecs=vorbis").format == "OGG")
    }

    @Test func badgeIsDepthOverKilohertzOrBitRate() {
        #expect(AudioQuality(mimeType: "audio/flac", bitDepth: 24, sampleRate: 96_000).badge(locale: en) == "FLAC 24/96")
        #expect(AudioQuality(mimeType: "audio/flac", bitDepth: 16, sampleRate: 44_100).badge(locale: en) == "FLAC 16/44.1")
        #expect(AudioQuality(mimeType: "audio/mpeg", bitRate: 320).badge(locale: en) == "MP3 320")
        #expect(AudioQuality(mimeType: "audio/mpeg").badge(locale: en) == "MP3")
    }

    @Test func zeroValuesCountAsMissing() {
        #expect(AudioQuality(mimeType: "audio/flac", bitDepth: 0, sampleRate: 0, bitRate: 0).summary(locale: en) == "FLAC")
    }

    // MARK: - Sections

    @Test func sectionsAreTrackAudioLibraryThenSource() {
        let sections = SongInfoSections.make(for: song(bitDepth: 24, sampleRate: 96_000, bitRate: 2_304, size: 5_000_000), locale: en)
        #expect(sections.map(\.title) == ["Track", "Audio", "Library", "Source"])
        #expect(sections[0].rows.map(\.label) == ["Title", "Artist", "Album", "Duration"])
        #expect(sections[1].rows.first?.value == "FLAC · 24-bit / 96 kHz · 2,304 kbps")
        #expect(sections[0].rows.last?.value == "5:30")
    }

    @Test func emptyFieldsAreHidden() {
        let sections = SongInfoSections.make(for: song(name: nil, mimeType: ""), locale: en)
        let labels = sections.flatMap(\.rows).map(\.label)
        #expect(!labels.contains("Title"))
        #expect(!labels.contains("Format"))
        #expect(!labels.contains("Last Played"))
        #expect(!labels.contains("Date Added"))
        #expect(!sections.map(\.title).contains("Audio"))
    }

    @Test func aLocalSongShowsItsPathAndARemoteOneItsServer() {
        let local = SongInfoSections.make(for: song(), locale: en).last
        #expect(local?.rows == [SongInfoRow(label: "Path", value: "/Music/Teardrop.flac")])
        let remote = SongInfoSections.make(for: song(provider: .jellyfin), locale: en).last
        #expect(remote?.rows == [SongInfoRow(label: "Server", value: "Jellyfin")])
    }

    @Test func aDocumentURIShowsThePathInsideItsVolume() {
        let uri = "content://com.android.externalstorage.documents/document/primary%3AMusic%2FAlbum%2F01%20Song.flac"
        #expect(SongInfoSections.displayPath(uri) == "Music/Album/01 Song.flac")
        #expect(SongInfoSections.displayPath("") == nil)
    }

    @Test func playCountAlwaysShows() {
        let library = SongInfoSections.make(for: song(playCount: 0), locale: en).first { $0.title == "Library" }
        #expect(library?.rows == [SongInfoRow(label: "Plays", value: "0")])
    }
}
