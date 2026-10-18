import Foundation
import PlaybackStreaming

/// Header values may be secrets: they go on the request only, never into a log, cache key or disk cache.
public protocol ServerConnectionPolicy: AnyObject, Sendable {
    /// The custom headers for a request to `url`: none for a server without any, or for a host that isn't a configured server.
    func headers(for url: URL) -> [String: String]

    /// The SHA-256 fingerprint of the leaf certificate the user trusted for `url`'s server, nil when there is none.
    func trustedCertificate(for url: URL) -> String?

    /// `request`, a redirect from `origin`, without the headers of the server it was made to if it now goes to another host.
    func redirected(_ request: URLRequest, from origin: URL?) -> URLRequest

    /// Answers a TLS challenge as the system would, except that the certificate the user trusted for the server is accepted.
    func handleChallenge(
        _ challenge: URLAuthenticationChallenge,
        completion: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
    )
}

/// The policy the app's own sessions use. Set once at launch by the app; nil (nothing added, the system's trust) in the
/// package's own tests that don't care.
public enum ServerConnections {
    private final class Box: @unchecked Sendable {
        private let lock = NSLock()
        private var policy: ServerConnectionPolicy?

        var value: ServerConnectionPolicy? {
            get { lock.withLock { policy } }
            set { lock.withLock { policy = newValue } }
        }
    }

    private static let box = Box()

    public static var policy: ServerConnectionPolicy? {
        get { box.value }
        set { box.value = newValue }
    }

    /// Hands a session's TLS challenge to `policy`, or to the system when there is none.
    public static func handle(
        _ challenge: URLAuthenticationChallenge,
        policy: ServerConnectionPolicy? = ServerConnections.policy,
        completion: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
    ) {
        guard let policy else { return completion(.performDefaultHandling, nil) }
        policy.handleChallenge(challenge, completion: completion)
    }
}

extension ServerConnectionPolicy {
    /// The policy for a stream of `url`, as the growing-file source takes it: the server's custom headers, and its
    /// trusted certificate as an exception to the system's trust. The source keeps both to the server's own origin, as
    /// ``redirected(_:from:)`` does. Read once, when the stream opens.
    func growingFilePolicy(for url: URL) -> GrowingFileConnectionPolicy {
        GrowingFileConnectionPolicy(
            headers: headers(for: url),
            trustedLeafSHA256: trustedCertificate(for: url).map { [$0] } ?? []
        )
    }
}
