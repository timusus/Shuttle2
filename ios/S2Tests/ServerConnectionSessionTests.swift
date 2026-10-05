import CryptoKit
import Foundation
import S2Playback
import Shared
import Testing
@testable import S2

/// A server's custom headers and pinned self-signed certificate on the sessions Swift makes itself: streaming and artwork
/// (#921). The rule is Kotlin's `ServerRequestPolicy`, over the same store as the Ktor client's, so these seed a test graph
/// the way the sign-in does and ask the real thing; the streaming and artwork sessions both answer through
/// `ServerConnections.handle` and the policy's headers.
struct ServerConnectionSessionTests {

    /// Self-signed certificates made for these tests alone (CN=pinned.test and CN=other.test); neither key was kept.
    private static let pinnedCertificate = "MIIDDzCCAfegAwIBAgIUdCWeK5DTM2oWLKb/OwJxwrVQRCQwDQYJKoZIhvcNAQELBQAwFjEUMBIGA1UEAwwLcGlubmVkLnRlc3QwIBcNMjYxMDA1MDk1MzQ0WhgPMjEyNjA5MTEwOTUzNDRaMBYxFDASBgNVBAMMC3Bpbm5lZC50ZXN0MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAwjp+0RUsyzFVJhNsY1O/9nKSa8qRmdCrTJ9brt3aprQuAyzyNx4CitYA0TMWja26OymZXLfFmDWw/xyAF8hbLcusqzVnfQjrdYJx0Ka1BJMZC16qBYRGDcyoPMEeW+kWEQBjUMWoIdOFAREJ7WjVmYT+LaTFJ/EQ7JfyaM/UGeHCQXDS+mivdK+lX3hzqtKCtYjndssuHij7R40108Ow5DETXGqPSKBiwS7NTDjAH9/VE/RSyFn2ErwkVjzYFq+J2X6wZDMYwbG1Vi6116IQH6aUAV6u93RTh4CszAomF91OLB5SlPhDiOxwcza+5ZZqQOxLTgRxncaq+x/uYEqvXQIDAQABo1MwUTAdBgNVHQ4EFgQUVI8qjFr+gSg5nAS1fgoZkOrKim8wHwYDVR0jBBgwFoAUVI8qjFr+gSg5nAS1fgoZkOrKim8wDwYDVR0TAQH/BAUwAwEB/zANBgkqhkiG9w0BAQsFAAOCAQEAnCXHBq5696HlZwVFG5aac4+EHKpYhSUv4pXYZ8uMIdok6Sc1tnPLzyMaOM3PJmUNghAcoqpTBil5jvTfB4tX2LHLwFryy7nYCFmAe+qSv5FTMIzMuhzCxHaNOsR40McZk77u+woIxKaqSt7kcr7D5B9WtAvBfoXOe1hCPsZ41uIyJWhfcZ1ciQ4MnU9//u1I/sNCnYxkGJdFfJFcAOoRyuzbEqrIZIZo3Od8kq8O1v/cMaGv8IrHqGB18kvr0lHEYMGuWzCEhecs8mOVuMl+e05Ze8wYuPCTMlNPBomtbfWsBYI2GXtN84T+JuTcqxFayIk5TZPzfQCXnxiYb5bfeA=="
    private static let otherCertificate = "MIIDDTCCAfWgAwIBAgIUGs74C7ce4D5hUnRiacWZkjdd+jAwDQYJKoZIhvcNAQELBQAwFTETMBEGA1UEAwwKb3RoZXIudGVzdDAgFw0yNjEwMDUwOTUzNDRaGA8yMTI2MDkxMTA5NTM0NFowFTETMBEGA1UEAwwKb3RoZXIudGVzdDCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAK795JuifZLx9rYkNut44e4fk+LDviL9hfYictmuD37VO6MLVuKfwcamIoYyxkj/uV+2fOoIAFcDZkEj4T058FEznmJFf5rEvqymd4RDCxrka9ueOA7bKb5/7uBGqz/sLieTHYcAd/6UaqKXTgu8IF4mZpJVvkwQenWim/3xUAaSZQNF2AhLFC0mWJ7Lzmvepr7c/miZ995uRn2kUhINgqEq56c4wHyDE0ekdnojyJd0lNFkZnECbuWTHw5vtAW2SwzJVXcvqHUUUDGRm+lj7g+8Hh5XQ8R+KqYd3F20PyPkLw6Dw2SuEvItxX06Evm/1GBIR42lEb8EQLTI/HBZknECAwEAAaNTMFEwHQYDVR0OBBYEFO39DCkQU/Qxwh/JBWP5kzK7B97xMB8GA1UdIwQYMBaAFO39DCkQU/Qxwh/JBWP5kzK7B97xMA8GA1UdEwEB/wQFMAMBAf8wDQYJKoZIhvcNAQELBQADggEBAI1InyrZGFHzr1Yr2citHcfJ3k8q1JY6TIdLHsX+p64RpQsCc1EaW6lBDC/Jq754ExNIj6LX2/dqcW3VVfqyfckWRZ7W1n9frCDvtfrf3Kmo3mPWvrUW4EuUrX/aSl5pSSTP4/K3hMnyEsmLT7wN3Z17Bb6Jg3l74S0Hf0ohQNbCEgAYImtT6Hr4XSlQ2KIAUYhI3JCQ93aipbuy869GZsmY3kyJ6kD0UFHimsA0WhlMNzaK4SGOfrCkzmWisQOYxV/kfR/nDq2qbJw7cDuDg8eHy1Jp9NWwkIEFjeeZR8NVHTPGd8eyJegDbJeDYMFpjsgp0rA8WKvLmxapR26M36U="

    private static let headers = [CoreCustomHeader(name: "CF-Access-Client-Id", value: "id-1"), CoreCustomHeader(name: "CF-Access-Client-Secret", value: "secret-1")]

    /// A policy over a test graph with a server at a host of its own, headers saved and the pinned certificate trusted.
    private struct Fixture {
        let host = "server-\(UUID().uuidString.prefix(8).lowercased()).test"
        let policy: KotlinServerConnectionPolicy

        init(headers: [CoreCustomHeader] = ServerConnectionSessionTests.headers, trusting certificate: String? = ServerConnectionSessionTests.pinnedCertificate) {
            let graph = makeTestGraph(audioPlayer: EngineAudioPlayer(engine: FakeAudioEngine()))
            let origin = CoreServerOrigin.companion.of(host: host, port: 8920)
            graph.prepareServerConnection.invoke(origin: origin, headers: headers)
            if let certificate {
                graph.trustServerCertificate.invoke(origin: origin, fingerprint: Self.fingerprint(of: certificate))
            }
            policy = KotlinServerConnectionPolicy(graph.serverRequestPolicy)
        }

        var url: URL { URL(string: "https://\(host):8920/Items/1/Images/Primary")! }

        static func fingerprint(of certificate: String) -> String {
            SHA256.hash(data: Data(base64Encoded: certificate)!).map { String(format: "%02X", $0) }.joined()
        }
    }

    // MARK: - Headers

    @Test func aRequestToTheServerCarriesItsHeaders() {
        let fixture = Fixture()

        #expect(fixture.policy.headers(for: fixture.url) == ["CF-Access-Client-Id": "id-1", "CF-Access-Client-Secret": "secret-1"])
        #expect(fixture.policy.headers(for: URL(string: "https://other.example:8920/x")!).isEmpty)
        #expect(fixture.policy.headers(for: URL(string: "https://\(fixture.host):9999/x")!).isEmpty)
    }

    @Test func aRedirectToAnotherHostLosesTheHeaders() {
        let fixture = Fixture()
        var request = URLRequest(url: URL(string: "https://cdn.example/a.jpg")!)
        request.setValue("id-1", forHTTPHeaderField: "CF-Access-Client-Id")
        request.setValue("image/jpeg", forHTTPHeaderField: "Accept")

        let elsewhere = fixture.policy.redirected(request, from: fixture.url)
        let sameServer = fixture.policy.redirected(URLRequest(url: fixture.url), from: fixture.url)

        #expect(elsewhere.allHTTPHeaderFields == ["Accept": "image/jpeg"])
        #expect(elsewhere.value(forHTTPHeaderField: "Accept") == "image/jpeg")
        #expect(sameServer.url == fixture.url)
    }

    // MARK: - Artwork

    @Test func anArtworkRequestCarriesTheServersHeadersButIsNeverCachedByTheSession() {
        let fixture = Fixture()
        let candidate = ArtworkCandidate(url: fixture.url, customHeaders: fixture.policy.headers(for: fixture.url))

        #expect(candidate.request.value(forHTTPHeaderField: "CF-Access-Client-Secret") == "secret-1")
        #expect(candidate.request.cachePolicy == .reloadIgnoringLocalCacheData)
        // What the loader keeps on disk is asked for by URL alone, so the headers are not in the cache
        #expect(candidate.keyedCacheRequest?.url == fixture.url)
        #expect(candidate.keyedCacheRequest?.allHTTPHeaderFields == nil)
        #expect(ArtworkCandidate(url: fixture.url).keyedCacheRequest == nil)
    }

    @MainActor @Test func anImageFromAServerWithHeadersIsKeptOnDiskWithoutThem() async throws {
        let fixture = Fixture()
        let diskCache = URLCache(memoryCapacity: 1_000_000, diskCapacity: 10_000_000, directory: FileManager.default.temporaryDirectory.appendingPathComponent("artwork-\(UUID())"))
        let png = ArtworkLoaderTests.pngData(width: 40, height: 40)
        let fetcher = ArtworkLoaderTests.StubFetcher(responses: [fixture.url: (200, png)])
        let candidate = ArtworkCandidate(url: fixture.url, customHeaders: fixture.policy.headers(for: fixture.url))

        #expect(await ArtworkLoader(fetch: fetcher.fetch, diskCache: diskCache).image(for: candidate, maxPixelSize: 64) != nil)
        // A second loader (a relaunch) finds it on disk, and nothing was stored under the request that had the headers
        #expect(await ArtworkLoader(fetch: fetcher.fetch, diskCache: diskCache).image(for: candidate, maxPixelSize: 64) != nil)

        #expect(await fetcher.requests.count == 1)
        #expect(await fetcher.requests.first?.value(forHTTPHeaderField: "CF-Access-Client-Id") == "id-1")
        #expect(try #require(diskCache.cachedResponse(for: URLRequest(url: fixture.url))).data == png)
    }

    // MARK: - Trust

    @Test func theArtworkSessionAcceptsThePinnedCertificateAndRefusesOthers() throws {
        let fixture = Fixture()
        let delegate = PlexTokenRedirectGuard(policy: fixture.policy)

        #expect(try answer(delegate: delegate, host: fixture.host, certificate: Self.pinnedCertificate) == .useCredential)
        #expect(try answer(delegate: delegate, host: fixture.host, certificate: Self.otherCertificate) == .performDefaultHandling)
        #expect(try answer(delegate: delegate, host: "elsewhere.test", certificate: Self.pinnedCertificate) == .performDefaultHandling)
    }

    @Test func theArtworkSessionTrustsNothingForAServerWithoutAPinnedCertificate() throws {
        let fixture = Fixture(trusting: nil)

        #expect(try answer(delegate: PlexTokenRedirectGuard(policy: fixture.policy), host: fixture.host, certificate: Self.pinnedCertificate) == .performDefaultHandling)
    }

    /// The streaming source answers its challenges with `ServerConnections.handle`, as this does.
    @Test func theStreamingSessionAcceptsThePinnedCertificateAndRefusesOthers() throws {
        let fixture = Fixture()

        #expect(try handle(policy: fixture.policy, host: fixture.host, certificate: Self.pinnedCertificate) == .useCredential)
        #expect(try handle(policy: fixture.policy, host: fixture.host, certificate: Self.otherCertificate) == .performDefaultHandling)
        #expect(try handle(policy: nil, host: fixture.host, certificate: Self.pinnedCertificate) == .performDefaultHandling)
    }

    // MARK: - Helpers

    private func answer(delegate: PlexTokenRedirectGuard, host: String, certificate: String) throws -> URLSession.AuthChallengeDisposition {
        var result: URLSession.AuthChallengeDisposition?
        delegate.urlSession(URLSession.shared, task: URLSession.shared.dataTask(with: URL(string: "https://\(host):8920/")!), didReceive: try challenge(host: host, certificate: certificate)) { disposition, _ in
            result = disposition
        }
        return try #require(result)
    }

    private func handle(policy: ServerConnectionPolicy?, host: String, certificate: String) throws -> URLSession.AuthChallengeDisposition {
        var result: URLSession.AuthChallengeDisposition?
        ServerConnections.handle(try challenge(host: host, certificate: certificate), policy: policy) { disposition, _ in result = disposition }
        return try #require(result)
    }

    /// A server-trust challenge from `host:8920` presenting `certificate`, which the system refuses (self-signed, unknown to it).
    private func challenge(host: String, certificate: String) throws -> URLAuthenticationChallenge {
        let der = try #require(Data(base64Encoded: certificate))
        let certificate = try #require(SecCertificateCreateWithData(nil, der as CFData))
        var trust: SecTrust?
        #expect(SecTrustCreateWithCertificates(certificate, SecPolicyCreateSSL(true, host as CFString), &trust) == errSecSuccess)
        let space = TrustSpace(host: host, trust: try #require(trust))
        return URLAuthenticationChallenge(protectionSpace: space, proposedCredential: nil, previousFailureCount: 0, failureResponse: nil, error: nil, sender: NoSender())
    }

    private final class TrustSpace: URLProtectionSpace, @unchecked Sendable {
        private let trust: SecTrust

        init(host: String, trust: SecTrust) {
            self.trust = trust
            super.init(host: host, port: 8920, protocol: "https", realm: nil, authenticationMethod: NSURLAuthenticationMethodServerTrust)
        }

        required init?(coder: NSCoder) { fatalError("not used") }

        override var serverTrust: SecTrust? { trust }
    }

    private final class NoSender: NSObject, URLAuthenticationChallengeSender {
        func use(_ credential: URLCredential, for challenge: URLAuthenticationChallenge) {}
        func continueWithoutCredential(for challenge: URLAuthenticationChallenge) {}
        func cancel(_ challenge: URLAuthenticationChallenge) {}
    }
}
