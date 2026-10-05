import Foundation

/// How requests to a media server are made beyond its credentials (#894, #921): the custom headers the user set for it
/// (a reverse proxy's access token, say) and the one self-signed certificate they trusted for it. The app's implementation
/// is Kotlin's, the same the Ktor client and the downloads use, so there is one rule for all three.
///
/// Header values may be secrets: they go on the request and nowhere else, never into a log, a cache key or a disk cache.
public protocol ServerConnectionPolicy: AnyObject, Sendable {
    /// The custom headers for a request to `url`: none for a server without any, or for a host that isn't a configured server.
    func headers(for url: URL) -> [String: String]

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
