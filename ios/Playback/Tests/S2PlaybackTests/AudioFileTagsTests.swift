import XCTest
@testable import S2Playback
import S2PlaybackTestSupport

/// The local library's tag reader (#590): the `tagged*` fixtures were written by the ffmpeg CLI, each in the
/// spelling its container uses (ID3 frames, MP4 atoms, Vorbis comments on FLAC's header and on Opus's stream).
final class AudioFileTagsTests: XCTestCase {
    private func read(_ name: String, _ ext: String) throws -> AudioFileTags {
        let url = try XCTUnwrap(LoopbackMediaServer.fixtureURL(name, withExtension: ext))
        return try XCTUnwrap(AudioFileTags.read(fileAt: url))
    }

    func testID3InMP3() throws {
        let tags = try read("tagged", "mp3")
        XCTAssertEqual(tags.title, "Tagged Chirp")
        XCTAssertEqual(tags.artists, ["Artist A", "Artist B"])
        XCTAssertEqual(tags.artistDisplay, "Artist A; Artist B")
        XCTAssertEqual(tags.albumArtist, "The Album Artist")
        XCTAssertEqual(tags.album, "The Album")
        XCTAssertEqual(tags.track, 3)
        XCTAssertEqual(tags.trackTotal, 12)
        XCTAssertEqual(tags.disc, 1)
        XCTAssertEqual(tags.discTotal, 2)
        XCTAssertEqual(tags.year, 1997)
        XCTAssertEqual(tags.genres, ["Rock", "Pop"])
        XCTAssertEqual(tags.replayGainTrack, -6.5)
        XCTAssertEqual(tags.compilation, true)
        XCTAssertEqual(tags.codec, "mp3")
        XCTAssertEqual(tags.sampleRate, 44100)
        XCTAssertNotNil(tags.durationMs)
        XCTAssertNotNil(tags.bitRateKbps)
    }

    func testVorbisCommentsInFLAC() throws {
        let tags = try read("tagged", "flac")
        XCTAssertEqual(tags.title, "Tagged Tone")
        XCTAssertEqual(tags.artists, ["Flac Artist"])
        XCTAssertEqual(tags.albumArtist, "Flac Album Artist")
        XCTAssertEqual(tags.album, "Flac Album")
        XCTAssertEqual(tags.track, 7)
        XCTAssertNil(tags.trackTotal)
        XCTAssertEqual(tags.disc, 2)
        XCTAssertEqual(tags.year, 2004)
        XCTAssertEqual(tags.genres, ["Jazz"])
        XCTAssertEqual(tags.replayGainAlbum, 1.25)
        XCTAssertEqual(tags.mbTrackId, "11111111-2222-3333-4444-555555555555")
        XCTAssertEqual(tags.codec, "flac")
        XCTAssertEqual(tags.bitDepth, 16)
        XCTAssertEqual(tags.channelCount, 2)
    }

    func testStreamTagsInOpus() throws {
        let tags = try read("tagged", "opus")
        XCTAssertEqual(tags.title, "Tagged Opus")
        XCTAssertEqual(tags.artists, ["Opus Artist"])
        XCTAssertEqual(tags.album, "Opus Album")
        XCTAssertEqual(tags.track, 1)
        XCTAssertEqual(tags.trackTotal, 9)
        XCTAssertEqual(tags.codec, "opus")
        XCTAssertNotNil(tags.durationMs)
    }

    func testAtomsInMP4() throws {
        let tags = try read("tagged-alac", "m4a")
        XCTAssertEqual(tags.title, "Tagged Alac")
        XCTAssertEqual(tags.albumArtist, "Alac Album Artist")
        XCTAssertEqual(tags.track, 4)
        XCTAssertEqual(tags.trackTotal, 10)
        XCTAssertEqual(tags.year, 2011)
        XCTAssertEqual(tags.codec, "alac")
    }

    func testUntaggedFileHasPropertiesOnly() throws {
        let tags = try read("tone-44k", "aiff")
        XCTAssertNil(tags.title)
        XCTAssertEqual(tags.artists, [])
        XCTAssertEqual(tags.bitDepth, 16)
        XCTAssertEqual(tags.sampleRate, 44100)
    }

    func testEmbeddedPictures() throws {
        for (name, ext) in [("tagged", "mp3"), ("tagged", "flac"), ("tagged-alac", "m4a")] {
            let url = try XCTUnwrap(LoopbackMediaServer.fixtureURL(name, withExtension: ext))
            let picture = try XCTUnwrap(AudioFileTags.embeddedPicture(fileAt: url), "\(name).\(ext)")
            XCTAssertEqual(Array(picture.prefix(4)), [0x89, 0x50, 0x4E, 0x47], "\(name).\(ext) is the PNG cover")
        }
        let untagged = try XCTUnwrap(LoopbackMediaServer.fixtureURL("tagged", withExtension: "opus"))
        XCTAssertNil(AudioFileTags.embeddedPicture(fileAt: untagged))
    }

    func testNotAudio() {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("not-audio-\(UUID()).mp3")
        try? Data("hello".utf8).write(to: url)
        defer { try? FileManager.default.removeItem(at: url) }
        XCTAssertNil(AudioFileTags.read(fileAt: url))
        XCTAssertNil(AudioFileTags.read(fileAt: URL(fileURLWithPath: "/nonexistent/file.flac")))
    }

    func testMappingSpellings() {
        let tags = AudioFileTags(tags: [
            ("ALBUMARTIST", "AA"),
            ("TRACKNUMBER", "05"),
            ("ORIGINALDATE", "1980-01-01"),
            ("MusicBrainz Album Artist Id", "a1/a2"),
            ("lyrics-eng", "la la"),
            ("title", "From the container"),
            ("TITLE", "From the stream"),
            ("GENRE", "Rock, Indie; Pop"),
            ("compilation", "0"),
        ])
        XCTAssertEqual(tags.albumArtist, "AA")
        XCTAssertEqual(tags.track, 5)
        XCTAssertEqual(tags.year, 1980)
        XCTAssertEqual(tags.mbAlbumArtistIds, ["a1", "a2"])
        XCTAssertEqual(tags.lyrics, "la la")
        XCTAssertEqual(tags.title, "From the container")
        XCTAssertEqual(tags.genres, ["Rock", "Indie", "Pop"])
        XCTAssertEqual(tags.compilation, false)
        XCTAssertNil(AudioFileTags(tags: [("date", "unknown")]).year)
    }

    func testOriginalDateBeatsReissueDate() {
        XCTAssertEqual(AudioFileTags(tags: [("date", "2017-06-30"), ("originaldate", "2002-01-28")]).year, 2002)
        XCTAssertEqual(AudioFileTags(tags: [("date", "2017"), ("year", "2002")]).year, 2017)
    }
}
