import Foundation
import PlaybackStreaming

/// Opens a connection to the server the last stream came from, so the next play's first range request doesn't wait on
/// TCP and TLS (#822). The request goes through the streaming source's own session, which keeps the connection for that
/// request; the Ktor client's pool is a different one, so browsing the library doesn't warm it.
///
/// Only the origin is kept (scheme, host and port), never a path, query or credentials, and nothing is sent until a
/// stream has played: a library of local files never talks to anyone.
public final class StreamWarmUp: @unchecked Sendable {
    public static let shared = StreamWarmUp()

    /// A warm-up of the same origin this soon after the last sends nothing: the connection is still open.
    static let minimumInterval: TimeInterval = 30
    static let originKey = "streamWarmUpOrigin"
    private static let timeoutSeconds: TimeInterval = 6

    private let session: URLSession
    private let defaults: UserDefaults
    private let now: () -> Date
    private let lock = NSLock()
    private var lastWarmed: (origin: URL, at: Date)?

    init(session: URLSession = GrowingFileByteSource.sharedSession, defaults: UserDefaults = .standard, now: @escaping () -> Date = Date.init) {
        self.session = session
        self.defaults = defaults
        self.now = now
    }

    /// Remembers `url`'s server as the one ``warm()`` connects to. A file URL is ignored.
    func noteStreamed(_ url: URL) {
        guard let origin = Self.origin(of: url)?.absoluteString,
              defaults.string(forKey: Self.originKey) != origin else { return }
        defaults.set(origin, forKey: Self.originKey)
    }

    /// Sends a HEAD to the remembered server with its custom headers, answering its TLS challenge as a stream would.
    /// The answer is ignored: only the connection matters. Nil when nothing was sent (no stream yet, or warmed recently).
    @discardableResult
    public func warm() -> URLSessionTask? {
        guard let origin = defaults.string(forKey: Self.originKey).flatMap(URL.init(string:)) else { return nil }
        let at = now()
        let due = lock.withLock {
            if let lastWarmed, lastWarmed.origin == origin, at.timeIntervalSince(lastWarmed.at) < Self.minimumInterval { return false }
            lastWarmed = (origin, at)
            return true
        }
        guard due else { return nil }
        var request = URLRequest(url: origin, timeoutInterval: Self.timeoutSeconds)
        request.httpMethod = "HEAD"
        for (name, value) in ServerConnections.policy?.headers(for: origin) ?? [:] {
            request.setValue(value, forHTTPHeaderField: name)
        }
        let task = session.dataTask(with: request)
        task.delegate = WarmUpDelegate()
        task.resume()
        return task
    }

    static func origin(of url: URL) -> URL? {
        guard let scheme = url.scheme?.lowercased(), scheme == "http" || scheme == "https", let host = url.host else { return nil }
        var components = URLComponents()
        components.scheme = scheme
        components.host = host
        components.port = url.port
        components.path = "/"
        return components.url
    }
}

/// The session has no delegate of its own: each task brings one.
private final class WarmUpDelegate: NSObject, URLSessionTaskDelegate {
    func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        didReceive challenge: URLAuthenticationChallenge,
        completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
    ) {
        ServerConnections.handle(challenge, completion: completionHandler)
    }

    /// A redirect could leave the server, and its custom headers with it; the connection is open by now anyway.
    func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        willPerformHTTPRedirection response: HTTPURLResponse,
        newRequest request: URLRequest,
        completionHandler: @escaping (URLRequest?) -> Void
    ) {
        completionHandler(nil)
    }
}
