import PlaybackStreaming
import PlaybackStreamingTestSupport
import S2PlaybackTestSupport
import XCTest
@testable import S2Playback

/// A server's custom headers and trusted certificate reach the streaming source (#921), as shuttle-playback's
/// `GrowingFileConnectionPolicy`.
final class ServerConnectionPolicyTests: XCTestCase {

    private var server: PlaybackStreamingTestSupport.LoopbackMediaServer?
    private var source: GrowingFileByteSource?

    override func tearDown() {
        source?.cancel()
        source = nil
        server?.stop()
        server = nil
        ServerConnections.policy = nil
        super.tearDown()
    }

    /// Hands out `headers` and `certificate` for one host, as the app's policy does for a configured server.
    private final class FakePolicy: ServerConnectionPolicy, @unchecked Sendable {
        let host: String
        let headers: [String: String]
        let certificate: String?

        init(host: String, headers: [String: String], certificate: String? = nil) {
            self.host = host
            self.headers = headers
            self.certificate = certificate
        }

        func headers(for url: URL) -> [String: String] { url.host == host ? headers : [:] }

        func trustedCertificate(for url: URL) -> String? { url.host == host ? certificate : nil }

        func redirected(_ request: URLRequest, from origin: URL?) -> URLRequest { request }

        func handleChallenge(
            _ challenge: URLAuthenticationChallenge,
            completion: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
        ) {
            completion(.performDefaultHandling, nil)
        }
    }

    private func started(_ body: Data = Data(repeating: 7, count: 64 * 1024)) throws -> PlaybackStreamingTestSupport.LoopbackMediaServer {
        let made = try PlaybackStreamingTestSupport.LoopbackMediaServer(body: body, mimeType: "audio/mpeg")
        server = made
        return made
    }

    /// Reads the first bytes of `url` through a source given `policy`'s connection policy for it.
    private func readFirstBytes(_ url: URL, policy: ServerConnectionPolicy) throws {
        let made = GrowingFileByteSource(
            url: url, authHeaders: [:], connectionPolicy: policy.growingFilePolicy(for: url), store: .temporary()
        )
        source = made
        var buffer = [UInt8](repeating: 0, count: 4096)
        _ = try buffer.withUnsafeMutableBytes { try made.read(into: $0.baseAddress!, maxLength: 4096) }
    }

    func testThePolicyMapsTheServersHeadersAndCertificate() {
        let policy = FakePolicy(host: "music.example", headers: ["CF-Access-Client-Id": "id-1"], certificate: "ABCDEF")

        XCTAssertEqual(
            policy.growingFilePolicy(for: URL(string: "https://music.example/a")!),
            GrowingFileConnectionPolicy(headers: ["CF-Access-Client-Id": "id-1"], trustedLeafSHA256: ["ABCDEF"])
        )
        XCTAssertEqual(policy.growingFilePolicy(for: URL(string: "https://other.example/a")!), GrowingFileConnectionPolicy())
    }

    func testARequestToTheServerCarriesItsCustomHeaders() throws {
        let server = try started()

        try readFirstBytes(server.url, policy: FakePolicy(host: server.url.host ?? "", headers: ["CF-Access-Client-Id": "id-1"]))

        let head = try XCTUnwrap(server.requestHeads.first)
        XCTAssertTrue(head.contains("CF-Access-Client-Id: id-1"), head)
    }

    func testAServerWithoutHeadersGetsNone() throws {
        let server = try started()

        try readFirstBytes(server.url, policy: FakePolicy(host: "another.example", headers: ["CF-Access-Client-Id": "id-1"]))

        XCTAssertFalse(try XCTUnwrap(server.requestHeads.first).contains("CF-Access-Client-Id"))
    }

    /// The server's headers are for its own origin: a redirect to another host (a CDN) is a request to that host, which has none.
    func testARedirectToAnotherHostDoesNotCarryTheHeaders() throws {
        let server = try started()
        server.redirectsToAlternateHost = true

        try readFirstBytes(
            server.redirectingURL(hops: 1),
            policy: FakePolicy(host: server.url.host ?? "", headers: ["CF-Access-Client-Id": "id-1"])
        )

        let firstHop = try XCTUnwrap(server.requestHeads.first { $0.contains("/redirect/1/") })
        XCTAssertTrue(firstHop.contains("CF-Access-Client-Id: id-1"), firstHop)
        let landing = try XCTUnwrap(server.requestHeads.first {
            $0.contains(PlaybackStreamingTestSupport.LoopbackMediaServer.fixturePath) && $0.contains("Host: localhost")
        })
        XCTAssertFalse(landing.contains("CF-Access-Client-Id"), "the server's header must not reach the CDN: \(landing)")
    }

    /// The app sets its policy after the graph is created, so a track made before then reads it when it opens (#933).
    func testAPolicySetAfterTheTrackIsMadeStillApplies() throws {
        let tone = try XCTUnwrap(S2PlaybackTestSupport.LoopbackMediaServer.fixtureURL("tone", withExtension: "mp3"))
        let server = try started(Data(contentsOf: tone))
        let track = FFmpegTrackSource(url: server.url, store: .temporary())
        defer { track.cancel() }
        ServerConnections.policy = FakePolicy(host: server.url.host ?? "", headers: ["CF-Access-Client-Id": "id-1"])

        _ = try track.open(sampleRate: 48_000, channelCount: 2)

        XCTAssertTrue(try XCTUnwrap(server.requestHeads.first).contains("CF-Access-Client-Id: id-1"))
    }
}
