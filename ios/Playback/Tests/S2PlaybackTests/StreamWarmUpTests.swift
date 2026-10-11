import PlaybackStreamingTestSupport
import XCTest
@testable import S2Playback

/// Coming to the foreground opens a connection to the server the last stream came from (#822).
final class StreamWarmUpTests: XCTestCase {

    private var server: PlaybackStreamingTestSupport.LoopbackMediaServer?
    private var defaults: UserDefaults!
    private var suiteName = ""
    private var clock = Date(timeIntervalSince1970: 1_000_000)

    override func setUpWithError() throws {
        suiteName = "StreamWarmUpTests-\(UUID().uuidString)"
        defaults = UserDefaults(suiteName: suiteName)
    }

    override func tearDown() {
        server?.stop()
        server = nil
        defaults.removePersistentDomain(forName: suiteName)
        ServerConnections.policy = nil
        super.tearDown()
    }

    private final class HeaderPolicy: ServerConnectionPolicy, @unchecked Sendable {
        let headers: [String: String]

        init(headers: [String: String]) { self.headers = headers }

        func headers(for url: URL) -> [String: String] { headers }

        func trustedCertificate(for url: URL) -> String? { nil }

        func redirected(_ request: URLRequest, from origin: URL?) -> URLRequest { request }

        func handleChallenge(
            _ challenge: URLAuthenticationChallenge,
            completion: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
        ) {
            completion(.performDefaultHandling, nil)
        }
    }

    private func makeWarmUp() -> StreamWarmUp {
        StreamWarmUp(session: URLSession(configuration: .ephemeral), defaults: defaults) { [unowned self] in clock }
    }

    private func started() throws -> PlaybackStreamingTestSupport.LoopbackMediaServer {
        let made = try PlaybackStreamingTestSupport.LoopbackMediaServer(body: Data(repeating: 7, count: 1024), mimeType: "audio/flac")
        server = made
        return made
    }

    private func streamURL(_ server: PlaybackStreamingTestSupport.LoopbackMediaServer) -> URL {
        URL(string: "http://127.0.0.1:\(server.port)/Audio/item789/universal?PlaySessionId=abc&ApiKey=secret-token")!
    }

    private func waitForRequests(_ server: PlaybackStreamingTestSupport.LoopbackMediaServer, count: Int) {
        let deadline = Date().addingTimeInterval(5)
        while server.requestHeads.count < count, Date() < deadline { Thread.sleep(forTimeInterval: 0.01) }
    }

    func testNothingIsSentBeforeAnyStream() {
        XCTAssertNil(makeWarmUp().warm())
    }

    func testAFileIsNoServer() {
        let warmUp = makeWarmUp()
        warmUp.noteStreamed(URL(fileURLWithPath: "/music/a.flac"))

        XCTAssertNil(warmUp.warm())
    }

    func testItSendsAHeadToTheLastStreamsServerWithItsHeadersAndNoToken() throws {
        let server = try started()
        ServerConnections.policy = HeaderPolicy(headers: ["CF-Access-Client-Id": "id-1"])
        let warmUp = makeWarmUp()
        warmUp.noteStreamed(streamURL(server))

        XCTAssertNotNil(warmUp.warm())
        waitForRequests(server, count: 1)

        let head = try XCTUnwrap(server.requestHeads.first)
        XCTAssertTrue(head.hasPrefix("HEAD / HTTP/1.1"), head)
        XCTAssertTrue(head.lowercased().contains("cf-access-client-id: id-1"), head)
        XCTAssertFalse(head.contains("secret-token"), head)
        XCTAssertEqual(defaults.string(forKey: StreamWarmUp.originKey), "http://127.0.0.1:\(server.port)/")
    }

    func testTheServerIsRememberedAcrossLaunches() throws {
        let server = try started()
        makeWarmUp().noteStreamed(streamURL(server))

        XCTAssertNotNil(makeWarmUp().warm())
    }

    func testAWarmUpWithinThirtySecondsOfTheLastSendsNothing() throws {
        let server = try started()
        let warmUp = makeWarmUp()
        warmUp.noteStreamed(streamURL(server))
        XCTAssertNotNil(warmUp.warm())

        clock += 29
        XCTAssertNil(warmUp.warm())

        clock += 1
        XCTAssertNotNil(warmUp.warm())
    }

    func testAnotherServerIsWarmedAtOnce() throws {
        let server = try started()
        let other = try PlaybackStreamingTestSupport.LoopbackMediaServer(body: Data(repeating: 7, count: 1024), mimeType: "audio/flac")
        defer { other.stop() }
        let warmUp = makeWarmUp()
        warmUp.noteStreamed(streamURL(server))
        XCTAssertNotNil(warmUp.warm())

        warmUp.noteStreamed(streamURL(other))

        XCTAssertNotNil(warmUp.warm())
        waitForRequests(other, count: 1)
        XCTAssertEqual(other.requestHeads.count, 1)
    }

    func testTheRequestAvoidsExpensiveAndConstrainedNetworks() throws {
        let server = try started()
        let warmUp = makeWarmUp()
        warmUp.noteStreamed(streamURL(server))

        let request = try XCTUnwrap(warmUp.warm()?.originalRequest)

        XCTAssertFalse(request.allowsExpensiveNetworkAccess)
        XCTAssertFalse(request.allowsConstrainedNetworkAccess)
    }

    func testForgettingTheServerSendsNothingAndClearsTheDisk() throws {
        let server = try started()
        let warmUp = makeWarmUp()
        warmUp.noteStreamed(streamURL(server))

        warmUp.forget()

        XCTAssertNil(warmUp.warm())
        XCTAssertNil(defaults.string(forKey: StreamWarmUp.originKey))
    }

    func testAServerThatIsNoLongerConfiguredIsNotContactedAndIsForgotten() throws {
        let server = try started()
        let warmUp = makeWarmUp()
        warmUp.noteStreamed(streamURL(server))
        warmUp.isConfiguredServer = { _ in false }

        XCTAssertNil(warmUp.warm())
        XCTAssertNil(defaults.string(forKey: StreamWarmUp.originKey))
        XCTAssertEqual(server.requestHeads.count, 0)
    }

    func testASavedAddressMatchesItsOriginWithOrWithoutSchemePathAndDefaultPort() {
        let origin = URL(string: "https://Music.Example:443/")!

        XCTAssertTrue(StreamWarmUp.address("https://music.example/jellyfin", isAt: origin))
        XCTAssertTrue(StreamWarmUp.address("music.example:443", isAt: origin))
        XCTAssertFalse(StreamWarmUp.address("https://music.example:8096", isAt: origin))
        XCTAssertFalse(StreamWarmUp.address("https://other.example", isAt: origin))
        XCTAssertFalse(StreamWarmUp.address(nil, isAt: origin))
    }
}
