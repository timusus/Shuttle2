import Foundation
import PlaybackStreaming

/// Warms the last stream's origin so the next play skips TCP and TLS; nothing is sent until a stream has played.
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
    private var configuredCheck: @Sendable (URL) -> Bool = { _ in true }

    /// Whether an origin still belongs to a configured server. The app sets it at launch; ``warm()`` forgets and skips an
    /// origin it refuses, so a removed server is never contacted and its host leaves the disk.
    public var isConfiguredServer: @Sendable (URL) -> Bool {
        get { lock.withLock { configuredCheck } }
        set { lock.withLock { configuredCheck = newValue } }
    }

    init(session: URLSession = GrowingFileByteSource.sharedSession, defaults: UserDefaults = .standard, now: @escaping () -> Date = Date.init) {
        self.session = session
        self.defaults = defaults
        self.now = now
    }

    /// Remembers `url`'s server as the one ``warm()`` connects to. A file URL is ignored.
    func noteStreamed(_ url: URL) {
        guard let origin = Self.origin(of: url)?.absoluteString else { return }
        lock.withLock {
            if defaults.string(forKey: Self.originKey) != origin { defaults.set(origin, forKey: Self.originKey) }
        }
    }

    /// Drops the remembered server, as removing it from Sources does.
    public func forget() {
        lock.withLock {
            defaults.removeObject(forKey: Self.originKey)
            lastWarmed = nil
        }
    }

    /// Whether `address` (a server's saved address, with or without a scheme or path) is the server at `origin`.
    public static func address(_ address: String?, isAt origin: URL) -> Bool {
        guard let address, !address.isEmpty else { return false }
        let withScheme = address.contains("://") ? address : "http://\(address)"
        guard let url = URL(string: withScheme), let host = url.host?.lowercased(), host == origin.host?.lowercased() else { return false }
        func port(_ url: URL) -> Int { url.port ?? (url.scheme?.lowercased() == "https" ? 443 : 80) }
        return port(url) == port(origin)
    }

    /// Sends a HEAD to the remembered server with its custom headers, answering its TLS challenge as a stream would.
    /// The answer is ignored: only the connection matters. Nil when nothing was sent (no stream yet, or warmed recently).
    @discardableResult
    public func warm() -> URLSessionTask? {
        guard let origin = defaults.string(forKey: Self.originKey).flatMap(URL.init(string:)) else { return nil }
        guard isConfiguredServer(origin) else {
            forget()
            return nil
        }
        let at = now()
        let due = lock.withLock {
            if let lastWarmed, lastWarmed.origin == origin, at.timeIntervalSince(lastWarmed.at) < Self.minimumInterval { return false }
            lastWarmed = (origin, at)
            return true
        }
        guard due else { return nil }
        var request = URLRequest(url: origin, timeoutInterval: Self.timeoutSeconds)
        request.httpMethod = "HEAD"
        // A warm-up is a guess, not worth cellular data or Low Data Mode
        request.allowsExpensiveNetworkAccess = false
        request.allowsConstrainedNetworkAccess = false
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
