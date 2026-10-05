import XCTest
@testable import S2Playback
import S2PlaybackTestSupport

/// A server's custom headers and pinned certificate reach the streaming sessions (#921).
final class ServerConnectionPolicyTests: XCTestCase {

    private var server: LoopbackMediaServer?
    private var source: HTTPRangeByteSource?
    private let session = HTTPRangeByteSource.makeSession(configuration: .ephemeral)

    override func tearDown() {
        source?.cancel()
        source = nil
        server?.stop()
        server = nil
        super.tearDown()
    }

    /// Hands out `headers` for one host, as the app's policy does for a configured server, and records the TLS challenges it was asked.
    private final class FakePolicy: ServerConnectionPolicy, @unchecked Sendable {
        let host: String
        let headers: [String: String]
        private let lock = NSLock()
        private var answered = 0

        init(host: String, headers: [String: String]) {
            self.host = host
            self.headers = headers
        }

        var challengesAnswered: Int { lock.withLock { answered } }

        func headers(for url: URL) -> [String: String] { url.host == host ? headers : [:] }

        func redirected(_ request: URLRequest, from origin: URL?) -> URLRequest { request }

        func handleChallenge(
            _ challenge: URLAuthenticationChallenge,
            completion: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
        ) {
            lock.withLock { answered += 1 }
            completion(.cancelAuthenticationChallenge, nil)
        }
    }

    private func makeSource(_ server: LoopbackMediaServer, url: URL? = nil, policy: ServerConnectionPolicy?) -> HTTPRangeByteSource {
        let readAhead = ReadAheadPolicy(
            windowSeconds: 60, appetiteWindowSeconds: 80, windowBytes: 1024 * 1024, appetiteWindowBytes: 1024 * 1024,
            minWindowBytes: 4 * 1024, backWindowBytes: 512 * 1024
        )
        let made = HTTPRangeByteSource(
            url: url ?? server.url, authHeaders: [:], readAhead: readAhead, session: session,
            runStore: nil, resolvedURLs: nil, serverPolicy: policy
        )
        source = made
        return made
    }

    private func read(_ source: HTTPRangeByteSource) throws {
        var buffer = [UInt8](repeating: 0, count: 4096)
        _ = try buffer.withUnsafeMutableBytes { try source.read(into: $0.baseAddress!, maxLength: 4096) }
    }

    func testARequestToTheServerCarriesItsCustomHeaders() throws {
        let started = try LoopbackMediaServer(body: Data(repeating: 7, count: 64 * 1024), mimeType: "audio/mpeg")
        server = started
        let policy = FakePolicy(host: started.url.host ?? "", headers: ["CF-Access-Client-Id": "id-1"])

        try read(makeSource(started, policy: policy))

        let head = try XCTUnwrap(started.requestHeads.first)
        XCTAssertTrue(head.contains("CF-Access-Client-Id: id-1"), head)
    }

    func testAServerWithoutHeadersGetsNone() throws {
        let started = try LoopbackMediaServer(body: Data(repeating: 7, count: 64 * 1024), mimeType: "audio/mpeg")
        server = started
        let policy = FakePolicy(host: "another.example", headers: ["CF-Access-Client-Id": "id-1"])

        try read(makeSource(started, policy: policy))

        XCTAssertFalse(try XCTUnwrap(started.requestHeads.first).contains("CF-Access-Client-Id"))
    }

    /// The server's headers are for its own origin: a redirect to another host (a CDN) is a request to that host, which has none.
    func testARedirectToAnotherHostDoesNotCarryTheHeaders() throws {
        let started = try LoopbackMediaServer(body: Data(repeating: 7, count: 64 * 1024), mimeType: "audio/mpeg")
        started.redirectsToAlternateHost = true
        server = started
        let policy = FakePolicy(host: started.url.host ?? "", headers: ["CF-Access-Client-Id": "id-1"])

        try read(makeSource(started, url: started.redirectingURL(hops: 1), policy: policy))

        let firstHop = try XCTUnwrap(started.requestHeads.first { $0.contains("/redirect/1/") })
        XCTAssertTrue(firstHop.contains("CF-Access-Client-Id: id-1"), firstHop)
        let landing = try XCTUnwrap(started.requestHeads.first { $0.contains(LoopbackMediaServer.fixturePath) && $0.contains("Host: localhost") })
        XCTAssertFalse(landing.contains("CF-Access-Client-Id"), "the server's header must not reach the CDN: \(landing)")
    }

    /// The source answers its own TLS challenges, the session having no delegate: through the policy, which decides on the certificate.
    func testTheSourceAnswersATlsChallengeThroughThePolicy() throws {
        let started = try LoopbackMediaServer(body: Data(repeating: 7, count: 1024), mimeType: "audio/mpeg")
        server = started
        let policy = FakePolicy(host: "any", headers: [:])
        let made = makeSource(started, policy: policy)
        let task = session.dataTask(with: started.url)
        defer { task.cancel() }
        let space = URLProtectionSpace(host: "music.example", port: 443, protocol: "https", realm: nil, authenticationMethod: NSURLAuthenticationMethodServerTrust)
        let challenge = URLAuthenticationChallenge(protectionSpace: space, proposedCredential: nil, previousFailureCount: 0, failureResponse: nil, error: nil, sender: NoSender())

        var disposition: URLSession.AuthChallengeDisposition?
        made.urlSession(session, task: task, didReceive: challenge) { answer, _ in disposition = answer }

        XCTAssertEqual(policy.challengesAnswered, 1)
        XCTAssertEqual(disposition, .cancelAuthenticationChallenge)
    }

    private final class NoSender: NSObject, URLAuthenticationChallengeSender {
        func use(_ credential: URLCredential, for challenge: URLAuthenticationChallenge) {}
        func continueWithoutCredential(for challenge: URLAuthenticationChallenge) {}
        func cancel(_ challenge: URLAuthenticationChallenge) {}
    }
}
