import PlaybackDecode
import PlaybackStreaming
import PlaybackStreamingTestSupport
import S2PlaybackTestSupport
import XCTest
@testable import S2Playback

/// A download is kept for the next play only when it is the whole song: a transcode cut short, or a time-offset
/// re-open's tail, must never be found later as the song.
final class StreamStoreTests: XCTestCase {

    private var server: PlaybackStreamingTestSupport.LoopbackMediaServer?
    private var readers: [StreamByteReader] = []

    override func tearDown() {
        readers.forEach { $0.cancel() }
        readers = []
        server?.stop()
        server = nil
        super.tearDown()
    }

    private func started() throws -> PlaybackStreamingTestSupport.LoopbackMediaServer {
        let tone = try XCTUnwrap(S2PlaybackTestSupport.LoopbackMediaServer.fixtureURL("tone", withExtension: "mp3"))
        let made = try PlaybackStreamingTestSupport.LoopbackMediaServer(body: Data(contentsOf: tone), mimeType: "audio/mpeg")
        server = made
        return made
    }

    /// Reads `url` through `store` as the player does, until it ends or fails; the bytes read.
    @discardableResult
    private func readToEnd(_ url: URL, store: GrowingFileStore) throws -> Int {
        let reader = try StreamStore.reader(url: url, headers: [:], store: store)
        readers.append(reader)
        var total = 0
        var buffer = [UInt8](repeating: 0, count: 16 * 1024)
        while true {
            let read: Int
            do {
                read = try buffer.withUnsafeMutableBytes { try reader.read(into: $0.baseAddress!, maxLength: $0.count) }
            } catch {
                return total
            }
            if read <= 0 { return total }
            total += read
        }
    }

    private func kept(_ url: URL, in store: GrowingFileStore) -> URL? {
        store.completedFile(for: StreamCacheKey.stableURL(for: url))
    }

    /// Waits for the download that just ended to land in the cache, so the absent cases below are not vacuous.
    private func waitUntilKept(_ url: URL, in store: GrowingFileStore) -> URL? {
        let deadline = Date().addingTimeInterval(5)
        while Date() < deadline {
            if let file = kept(url, in: store) { return file }
            Thread.sleep(forTimeInterval: 0.02)
        }
        return nil
    }

    func testAWholeDownloadIsKeptForTheNextPlay() throws {
        let server = try started()
        let store = GrowingFileStore.temporary()

        XCTAssertEqual(try readToEnd(server.url, store: store), server.body.count)

        let file = try XCTUnwrap(waitUntilKept(server.url, in: store))
        XCTAssertEqual(try Data(contentsOf: file), server.body)
    }

    /// A transcode's origin states no length, so a body that closes early reads as one that ended.
    func testATranscodeThatClosesBeforeItsEndIsNotKept() throws {
        let server = try started()
        server.streamsWithoutLength = true
        server.closesAfterBodyBytes = server.body.count / 2
        let store = GrowingFileStore.temporary()

        XCTAssertLessThan(try readToEnd(server.url, store: store), server.body.count)
        readers.forEach { $0.cancel() }

        XCTAssertNil(kept(server.url, in: store))
        XCTAssertFalse(try StreamStore.reader(url: server.url, headers: [:], store: store) is FileByteReader)
    }

    func testATranscodeWhoseConnectionResetsIsNotKept() throws {
        let server = try started()
        server.streamsWithoutLength = true
        server.resetsDroppedConnections = true
        server.closesAfterBodyBytes = server.body.count / 2
        let store = GrowingFileStore.temporary()

        try readToEnd(server.url, store: store)
        readers.forEach { $0.cancel() }

        XCTAssertNil(kept(server.url, in: store))
    }

    /// A transcode that estimates its length and then always ends short.
    func testABodyShorterThanItsStatedLengthIsNotKept() throws {
        let server = try started()
        server.contentLengthLie = server.body.count / 2
        let store = GrowingFileStore.temporary()

        XCTAssertLessThan(try readToEnd(server.url, store: store), server.body.count)
        readers.forEach { $0.cancel() }

        XCTAssertNil(kept(server.url, in: store))
    }

    /// A re-open at a time offset downloads the song's tail, whole as far as HTTP knows; it must not be what the next
    /// play from the start finds.
    func testATimeOffsetReopenIsNeverKeptAsTheSong() throws {
        let server = try started()
        let store = GrowingFileStore.temporary()
        let song = server.url.appending(queryItems: [URLQueryItem(name: "PlaySessionId", value: "a")])
        let reopened = server.url.appending(queryItems: [
            URLQueryItem(name: "PlaySessionId", value: "b"),
            URLQueryItem(name: "StartTimeTicks", value: "300000000"),
        ])

        try readToEnd(reopened, store: store)
        XCTAssertNotNil(waitUntilKept(reopened, in: store))

        XCTAssertNil(kept(song, in: store))
        XCTAssertFalse(try StreamStore.reader(url: song, headers: [:], store: store) is FileByteReader)
    }
}
