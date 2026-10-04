import Foundation
import Shared
import Testing
@testable import S2

/// The audio badge label and Song Info's sections: what shows, in what order, and what is left out.
struct SongInfoTests {
    private let en = Locale(identifier: "en_US")

    private func song(
        name: String? = "Teardrop", mimeType: String = "audio/flac", codec: String? = nil, bitDepth: Int32? = nil,
        sampleRate: Int32? = nil, bitRate: Int32? = nil, path: String = "/Music/Teardrop.flac", size: Int64 = 0,
        playCount: Int32 = 0, provider: MediaProviderType = .shuttle, track: Int32? = nil, disc: Int32? = nil,
        channels: Int32? = nil
    ) -> Song {
        Song(
            id: 1, name: name, albumArtist: nil, artists: ["Massive Attack"], album: "Mezzanine", track: track.map { KotlinInt(int: $0) }, disc: disc.map { KotlinInt(int: $0) },
            duration: 330_000, date: nil, genres: [], path: path, size: size, mimeType: mimeType,
            lastModified: nil, lastPlayed: nil, lastCompleted: nil, playCount: playCount, playbackPosition: 0,
            blacklisted: false, externalId: nil, mediaProvider: provider, replayGainTrack: nil,
            replayGainAlbum: nil, lyrics: nil, grouping: nil, bitRate: bitRate.map { KotlinInt(int: $0) },
            bitDepth: bitDepth.map { KotlinInt(int: $0) }, sampleRate: sampleRate.map { KotlinInt(int: $0) },
            channelCount: channels.map { KotlinInt(int: $0) }, audioCodec: codec, artworkVersion: nil, dateAdded: nil, favouritedAt: nil,
            albumArtists: nil, artistsTag: nil, artistDisplay: nil, compilation: nil, mbTrackId: nil, mbAlbumId: nil,
            mbReleaseGroupId: nil, mbArtistIds: nil, mbAlbumArtistIds: nil, serverAlbumId: nil, serverArtistIds: nil,
            serverAlbumArtistIds: nil, albumIdentity: nil
        )
    }

    // MARK: - AudioQuality

    @Test func anAlbumBadgeShowsOnlyWhenEverySongSharesAFormat() {
        let flac = { (id: Int64) in TestSongs.song(id, "S", artist: "A", album: "B", durationMs: 1, mimeType: "audio/flac", bitDepth: 24, sampleRate: 96_000) }
        #expect(AudioQuality.sharedBadge(of: [flac(1), flac(2)], locale: Locale(identifier: "en_US")) == "FLAC · 24/96 kHz")
        let mp3 = TestSongs.song(3, "S", artist: "A", album: "B", durationMs: 1, mimeType: "audio/mpeg", bitRate: 320)
        #expect(AudioQuality.sharedBadge(of: [flac(1), mp3]) == nil)
        #expect(AudioQuality.sharedBadge(of: [mp3], locale: Locale(identifier: "en_US")) == "MP3 · 320 kbps")
        let unknown = TestSongs.song(4, "S", artist: "A", album: "B", durationMs: 1, mimeType: "")
        #expect(AudioQuality.sharedBadge(of: [unknown]) == nil)
        #expect(AudioQuality.sharedBadge(of: [flac(1), unknown]) == nil)
        let wildcard = TestSongs.song(5, "S", artist: "A", album: "B", durationMs: 1, mimeType: "audio/*")
        #expect(AudioQuality.sharedBadge(of: [wildcard]) == nil)
        #expect(AudioQuality.sharedBadge(of: []) == nil)
    }

    @Test func codecWinsOverContainer() {
        #expect(AudioQuality(codec: "alac", mimeType: "audio/mp4").format == "ALAC")
        #expect(AudioQuality(mimeType: "audio/mp4").format == "M4A")
        #expect(AudioQuality(mimeType: "audio/x-ogg; codecs=vorbis").format == "OGG")
    }

    @Test func badgeIsDepthOverKilohertzForLosslessAndBitRateForLossy() {
        #expect(AudioQuality(mimeType: "audio/flac", bitDepth: 24, sampleRate: 96_000).badge(locale: en) == "FLAC · 24/96 kHz")
        #expect(AudioQuality(mimeType: "audio/flac", bitDepth: 16, sampleRate: 44_100).badge(locale: en) == "FLAC · 16/44.1 kHz")
        #expect(AudioQuality(codec: "ALAC", mimeType: "audio/mp4", bitDepth: 24, sampleRate: 48_000, bitRate: 1_400).badge(locale: en) == "ALAC · 24/48 kHz")
        #expect(AudioQuality(mimeType: "audio/mpeg", bitRate: 320).badge(locale: en) == "MP3 · 320 kbps")
        #expect(AudioQuality(mimeType: "audio/mpeg").badge(locale: en) == "MP3")
    }

    @Test func aLossyFormatIgnoresTagLibsBitDepth() {
        #expect(AudioQuality(codec: "AAC", mimeType: "audio/mp4", bitDepth: 16, sampleRate: 44_100, bitRate: 256).badge(locale: en) == "AAC · 256 kbps")
        #expect(AudioQuality(mimeType: "audio/mp4", bitDepth: 16, sampleRate: 44_100, bitRate: 256).badge(locale: en) == "M4A · 256 kbps")
        #expect(!AudioQuality(mimeType: "audio/mpeg", bitDepth: 16).isLossless)
    }

    @Test func losslessFormatsAreRecognised() {
        for mime in ["audio/flac", "audio/x-wav", "audio/x-aiff", "audio/x-ape", "audio/x-wavpack"] {
            #expect(AudioQuality(mimeType: mime).isLossless, "\(mime)")
        }
        #expect(AudioQuality(codec: "DSD64").isLossless)
    }

    @Test func kilohertzKeepsTwoDecimalsAndFollowsTheLocale() {
        #expect(AudioQuality.kilohertz(22_050, locale: en) == "22.05")
        #expect(AudioQuality.kilohertz(44_100, locale: en) == "44.1")
        #expect(AudioQuality.kilohertz(96_000, locale: en) == "96")
        #expect(AudioQuality.kilohertz(44_100, locale: Locale(identifier: "de_DE")) == "44,1")
        #expect(AudioQuality(mimeType: "audio/flac", bitDepth: 16, sampleRate: 44_100).badge(locale: Locale(identifier: "de_DE")) == "FLAC · 16/44,1 kHz")
    }

    @Test func zeroValuesCountAsMissing() {
        #expect(AudioQuality(mimeType: "audio/flac", bitDepth: 0, sampleRate: 0, bitRate: 0).badge(locale: en) == "FLAC")
    }

    // MARK: - Sections (the shared infoSections(), localised)

    @Test func sectionsAreTheSharedTagsFileThenPlayback() {
        let sections = SongInfoSections.make(for: song(bitDepth: 24, sampleRate: 96_000, bitRate: 2_304, size: 5 * 1024 * 1024))
        #expect(sections.map(\.title) == ["Tags", "File", "Playback"])
        #expect(sections[0].rows.map(\.label) == ["Title", "Artists", "Album"])
        #expect(sections[1].rows.map(\.label) == ["Source", "Path", "MIME Type", "Size", "Duration", "Bit rate", "Bit depth", "Sample rate"])
        #expect(sections[1].rows.map(\.value) == ["This device", "/Music/Teardrop.flac", "audio/flac", "5.00 MB", "5:30", "2304 kb/s", "24-bit", "96 kHz"])
        #expect(sections[2].rows == [SongInfoRow(label: "Play count", value: "0")])
    }

    @Test func emptyFieldsAreHidden() {
        let labels = SongInfoSections.make(for: song(name: nil, mimeType: "")).flatMap(\.rows).map(\.label)
        #expect(!labels.contains("Title"))
        #expect(!labels.contains("MIME Type"))
        #expect(!labels.contains("Bit rate"))
    }

    @Test func zeroTrackDiscAndChannelsAreHidden() {
        let labels = SongInfoSections.make(for: song(track: 0, disc: 0, channels: 0)).flatMap(\.rows).map(\.label)
        #expect(!labels.contains("Track #"))
        #expect(!labels.contains("Disc"))
        #expect(!labels.contains("Channel count"))
        let shown = SongInfoSections.make(for: song(track: 3, disc: 1, channels: 2)).flatMap(\.rows)
        #expect(shown.first { $0.label == "Track #" }?.value == "3")
        #expect(shown.first { $0.label == "Channel count" }?.value == "2")
    }

    @Test func aDocumentURIShowsThePathInsideItsVolume() {
        let uri = "content://com.android.externalstorage.documents/document/primary%3AMusic%2FAlbum%2F01%20Song.flac"
        let path = SongInfoSections.make(for: song(path: uri)).flatMap(\.rows).first { $0.label == "Path" }
        #expect(path?.value == "Music/Album/01 Song.flac")
    }

    @Test func aFractionalSampleRateShowsTwoDecimals() {
        let rate = SongInfoSections.make(for: song(sampleRate: 22_050)).flatMap(\.rows).first { $0.label == "Sample rate" }
        #expect(rate?.value == "22.05 kHz")
    }
}
