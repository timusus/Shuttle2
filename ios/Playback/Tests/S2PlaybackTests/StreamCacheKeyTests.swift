import XCTest
@testable import S2Playback

/// ``StreamCacheKey``: per-play session ids and tokens out, what names the stream kept (#822).
final class StreamCacheKeyTests: XCTestCase {

    /// The shape `JellyfinAuthenticationManager.buildJellyfinPath` makes.
    private func jellyfin(session: String, token: String, bitrate: Int? = nil) -> URL {
        URL(string: "https://jf.example.com/Audio/abc123/universal?UserId=u1&DeviceId=d1&PlaySessionId=\(session)"
            + "&Container=flac,mp3,opus&TranscodingContainer=ts&TranscodingProtocol=hls&EnableRedirection=true"
            + "&EnableRemoteMedia=true&AudioCodec=aac" + (bitrate.map { "&MaxStreamingBitrate=\($0)" } ?? "")
            + "&ApiKey=\(token)")!
    }

    /// The shape `EmbyAuthenticationManager.buildEmbyPath` makes.
    private func emby(session: String, token: String, startTicks: Int? = nil) -> URL {
        URL(string: "http://192.168.1.10:8096/emby/Audio/42/universal?UserId=u1&DeviceId=d1&PlaySessionId=\(session)"
            + "&Container=flac,mp3&MaxSampleRate=48000&AudioCodec=aac"
            + (startTicks.map { "&StartTimeTicks=\($0)" } ?? "") + "&api_key=\(token)")!
    }

    /// The shape `PlexAuthenticationManager.buildPlexPath` makes.
    private func plex(token: String) -> URL {
        URL(string: "https://10-0-0-5.abc.plex.direct:32400/library/parts/123/1690000000/file.flac"
            + "?X-Plex-Token=\(token)&X-Plex-Client-Identifier=c1&X-Plex-Device=iOS")!
    }

    func testJellyfinPlaysOfTheSameStreamShareAKey() {
        let key = StreamCacheKey.key(for: jellyfin(session: "a", token: "t1"))
        XCTAssertEqual(key, StreamCacheKey.key(for: jellyfin(session: "b", token: "t2")))
        XCTAssertFalse(key.contains("PlaySessionId") || key.contains("ApiKey"), key)
        XCTAssertTrue(key.contains("UserId=u1&DeviceId=d1&Container=flac,mp3,opus"), key)
    }

    func testEmbyPlaysOfTheSameStreamShareAKey() {
        let key = StreamCacheKey.key(for: emby(session: "a", token: "t1"))
        XCTAssertEqual(key, StreamCacheKey.key(for: emby(session: "b", token: "t2")))
        XCTAssertEqual(
            key,
            "http://192.168.1.10:8096/emby/Audio/42/universal?UserId=u1&DeviceId=d1&Container=flac,mp3"
                + "&MaxSampleRate=48000&AudioCodec=aac"
        )
    }

    func testPlexPlaysOfTheSameStreamShareAKey() {
        let key = StreamCacheKey.key(for: plex(token: "t1"))
        XCTAssertEqual(key, StreamCacheKey.key(for: plex(token: "t2")))
        XCTAssertEqual(
            key,
            "https://10-0-0-5.abc.plex.direct:32400/library/parts/123/1690000000/file.flac"
                + "?X-Plex-Client-Identifier=c1&X-Plex-Device=iOS"
        )
    }

    func testAPlexTranscodeSessionIdentifierIsDropped() {
        let url = URL(string: "https://plex.local/music/:/transcode/universal/start.m3u8?path=%2Flibrary%2Fmetadata%2F9"
            + "&musicBitrate=320&X-Plex-Session-Identifier=s1&X-Plex-Token=t")!
        XCTAssertEqual(
            StreamCacheKey.key(for: url),
            "https://plex.local/music/:/transcode/universal/start.m3u8?path=%2Flibrary%2Fmetadata%2F9&musicBitrate=320"
        )
    }

    func testAPlexTranscodesBareSessionIsDroppedButNotElsewhere() {
        func transcode(_ session: String) -> URL {
            URL(string: "https://plex.local/music/:/transcode/universal/start?path=%2Flibrary%2Fmetadata%2F9&offset=30"
                + "&session=\(session)&X-Plex-Session-Identifier=s2-9&X-Plex-Token=t")!
        }
        XCTAssertEqual(StreamCacheKey.key(for: transcode("u1")), StreamCacheKey.key(for: transcode("u2")))
        XCTAssertEqual(
            StreamCacheKey.key(for: transcode("u1")),
            "https://plex.local/music/:/transcode/universal/start?path=%2Flibrary%2Fmetadata%2F9&offset=30"
        )
        let other = URL(string: "https://example.com/stream?session=a&id=1")!
        XCTAssertEqual(StreamCacheKey.key(for: other), other.absoluteString)
    }

    func testSessionIdsAndTokensAreDroppedWhateverTheirCase() {
        let url = URL(string: "http://jf.local:8096/Audio/1/universal?UserId=u&PLAYSESSIONID=s&AudioCodec=mp3&APIKEY=t"
            + "&Api_Key=t&X-Emby-Token=t&x-plex-token=t&X-MediaBrowser-Token=t&StartTimeTicks=10")!
        XCTAssertEqual(
            StreamCacheKey.key(for: url),
            "http://jf.local:8096/Audio/1/universal?UserId=u&AudioCodec=mp3&StartTimeTicks=10"
        )
    }

    func testADifferentlyServedStreamKeepsAKeyOfItsOwn() {
        XCTAssertNotEqual(
            StreamCacheKey.key(for: jellyfin(session: "a", token: "t", bitrate: 320_000)),
            StreamCacheKey.key(for: jellyfin(session: "a", token: "t", bitrate: 128_000))
        )
        XCTAssertNotEqual(
            StreamCacheKey.key(for: emby(session: "a", token: "t")),
            StreamCacheKey.key(for: emby(session: "a", token: "t", startTicks: 300_000_000))
        )
    }

    func testSubsonicPlaysOfTheSameStreamShareAKey() {
        func subsonic(id: String, token: String, salt: String) -> URL {
            URL(string: "https://sub.example.com/rest/stream?id=\(id)&format=raw&u=me&t=\(token)&s=\(salt)&v=1.16.1&c=Shuttle")!
        }
        let key = StreamCacheKey.key(for: subsonic(id: "42", token: "aaa", salt: "x1"))
        XCTAssertEqual(key, StreamCacheKey.key(for: subsonic(id: "42", token: "bbb", salt: "x2")))
        XCTAssertEqual(key, "https://sub.example.com/rest/stream?id=42&format=raw&u=me&v=1.16.1&c=Shuttle")
        XCTAssertNotEqual(key, StreamCacheKey.key(for: subsonic(id: "43", token: "aaa", salt: "x1")))
    }

    func testAURLWithNothingToDropIsKeptAsGiven() {
        let url = URL(string: "http://host/a.mp3?b=1&a=%2F")!
        XCTAssertEqual(StreamCacheKey.key(for: url), url.absoluteString)
        let plain = URL(string: "http://host/a.mp3")!
        XCTAssertEqual(StreamCacheKey.key(for: plain), plain.absoluteString)
        let tokenOnly = URL(string: "http://host/emby/Audio/1/stream?static=true&api_key=t")!
        XCTAssertEqual(StreamCacheKey.key(for: tokenOnly), "http://host/emby/Audio/1/stream?static=true")
        let onlyAToken = URL(string: "http://host/Audio/1/stream?ApiKey=t")!
        XCTAssertEqual(StreamCacheKey.key(for: onlyAToken), "http://host/Audio/1/stream")
    }
}
