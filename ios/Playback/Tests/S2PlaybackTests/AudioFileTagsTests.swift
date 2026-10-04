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

    // Each container's tags arrive raw, as libavformat names them; the Kotlin FfmpegTagsTest maps these same tags.

    func testID3InMP3() throws {
        let tags = try read("tagged", "mp3")
        XCTAssertEqual(tags.value("title"), "Tagged Chirp")
        XCTAssertEqual(tags.value("artist"), "Artist A; Artist B")
        XCTAssertEqual(tags.value("album_artist"), "The Album Artist")
        XCTAssertEqual(tags.value("track"), "3/12")
        XCTAssertEqual(tags.value("date"), "1997-05-21")
        XCTAssertEqual(tags.value("REPLAYGAIN_TRACK_GAIN"), "-6.50 dB")
        XCTAssertEqual(tags.value("compilation"), "1")
        XCTAssertEqual(tags.codec, "mp3")
        XCTAssertEqual(tags.sampleRate, 44100)
        XCTAssertNotNil(tags.durationMs)
        XCTAssertNotNil(tags.bitRateKbps)
    }

    func testVorbisCommentsInFLAC() throws {
        let tags = try read("tagged", "flac")
        XCTAssertEqual(tags.value("title"), "Tagged Tone")
        XCTAssertEqual(tags.value("DATE"), "2004")
        XCTAssertEqual(tags.value("REPLAYGAIN_ALBUM_GAIN"), "+1.25 dB")
        XCTAssertEqual(tags.value("MUSICBRAINZ_TRACKID"), "11111111-2222-3333-4444-555555555555")
        XCTAssertEqual(tags.codec, "flac")
        XCTAssertEqual(tags.bitDepth, 16)
        XCTAssertEqual(tags.channelCount, 2)
    }

    func testStreamTagsInOpus() throws {
        let tags = try read("tagged", "opus")
        XCTAssertEqual(tags.value("title"), "Tagged Opus")
        XCTAssertEqual(tags.value("track"), "1/9")
        XCTAssertEqual(tags.codec, "opus")
        XCTAssertNotNil(tags.durationMs)
    }

    func testAtomsInMP4() throws {
        let tags = try read("tagged-alac", "m4a")
        XCTAssertEqual(tags.value("title"), "Tagged Alac")
        XCTAssertEqual(tags.value("album_artist"), "Alac Album Artist")
        XCTAssertEqual(tags.value("track"), "4/10")
        XCTAssertEqual(tags.value("date"), "2011")
        XCTAssertEqual(tags.codec, "alac")
    }

    func testOriginalReleaseDateInID3() throws {
        // TDRC=2017 (the remaster) with TDOR=2002, as ffmpeg wrote them: TDOR comes through under its frame id.
        let tags = try read("reissue", "mp3")
        XCTAssertEqual(tags.value("date"), "2017")
        XCTAssertEqual(tags.value("TDOR"), "2002")
    }

    func testUntaggedFileHasPropertiesOnly() throws {
        let tags = try read("tone-44k", "aiff")
        XCTAssertEqual(tags.tags, [])
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
}
