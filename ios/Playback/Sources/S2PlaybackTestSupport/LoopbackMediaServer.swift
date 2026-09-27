// Copied from Shuttle Podcasts (podcasts@9ee6e0954) mobile/ios/Playback/Sources/PlaybackTestSupport/LoopbackMediaServer.swift — see ios/Playback/README.md.
import Foundation
import Network

/// A minimal `Range`-aware HTTP server on the loopback interface, for tests that need a real
/// network origin rather than a `file://` URL.
///
/// The spine capture only installs its resource loader for `http`/`https` (``SpineStreamCapture``
/// refuses a file URL on purpose), so any test of the capture's effect on playback has to serve its
/// fixture over HTTP. This is that origin: one fixed body, byte ranges, and a settable delay on the
/// response so a test can widen a race that a loopback socket would otherwise close too fast to see.
public final class LoopbackMediaServer: @unchecked Sendable {

    private var _body: Data
    private var _bodies: [Data]?
    private let mimeType: String
    private let listener: NWListener
    private let queue = DispatchQueue(label: "loopback-media-server")
    private let lock = NSLock()
    private var _requestedRanges: [Int64] = []
    private var _requestHeads: [String] = []
    private var _servedBytes: Int64 = 0
    private var _respondsWholeBodyIgnoringRange = false
    private var _failNextRequest = false
    private var _rejectNextRangeStartingAt: Int64?
    private var _rejectedHostStatus: (host: String, status: Int)?
    private var _pageForHost: (host: String, contentType: String, body: Data, firstChunkBytes: Int?)?
    private var _delayForOffsetZero: TimeInterval = 0
    private var _delayForRangeStartingAt: (offset: Int64, seconds: TimeInterval)?
    private var _heldBodyAfterBytesForRangeStartingAt: [Int64: Int] = [:]
    private var _heldRemainders: [Int64: (connection: NWConnection, rest: Data)] = [:]
    private var _releasedHeldRanges: Set<Int64> = []
    private var _delayForEveryRange: TimeInterval = 0
    private var _stallsAfterBodyBytes: Int?
    private var _redirectsToAlternateHost = false
    private var connections: [NWConnection] = []

    /// How long a request whose range starts at byte 0 is held before its response is written.
    ///
    /// The restart race is a race against a real HTTP round trip. On loopback that trip is
    /// sub-millisecond, so a test that wants to observe the window has to make it as wide as a
    /// phone's is on a cellular link.
    public var delayForOffsetZero: TimeInterval {
        get { lock.lock(); defer { lock.unlock() }; return _delayForOffsetZero }
        set { lock.lock(); _delayForOffsetZero = newValue; lock.unlock() }
    }

    /// Write only this many bytes of each response body, then hold the connection open forever
    /// without closing it — a host that went quiet mid-episode.
    ///
    /// A dropped connection is a different failure and the source already retries it; what a seek
    /// has to survive is the one where nothing arrives and nothing ends, because that is when the
    /// decoder is parked inside a read and the seek is queued behind it. The declared
    /// `Content-Length` is the full slice, so the client goes on waiting.
    public var stallsAfterBodyBytes: Int? {
        get { lock.lock(); defer { lock.unlock() }; return _stallsAfterBodyBytes }
        set { lock.lock(); _stallsAfterBodyBytes = newValue; lock.unlock() }
    }

    /// The start byte of every range served, in order. The restart is visible here as a second
    /// request for byte 0 after the first one has run to the end of the body.
    public var requestedRanges: [Int64] { lock.lock(); defer { lock.unlock() }; return _requestedRanges }

    /// Every request head served, verbatim, so a test can assert on the headers a client sent.
    public var requestHeads: [String] { lock.lock(); defer { lock.unlock() }; return _requestHeads }

    /// How many body bytes have been written out. A read-ahead bound is only observable from the
    /// server's side: the client's own frontier says what it kept, not what it asked the host for.
    public var servedBytes: Int64 { lock.lock(); defer { lock.unlock() }; return _servedBytes }

    /// Answer `200 OK` with the whole body and no `Content-Range`, ignoring any `Range` header —
    /// what a surprising number of podcast CDNs do to a ranged request.
    public var respondsWholeBodyIgnoringRange: Bool {
        get { lock.lock(); defer { lock.unlock() }; return _respondsWholeBodyIgnoringRange }
        set { lock.lock(); _respondsWholeBodyIgnoringRange = newValue; lock.unlock() }
    }

    /// Drop the next request's connection without a response, once. The transport failure a retry
    /// has to survive.
    public var failNextRequest: Bool {
        get { lock.lock(); defer { lock.unlock() }; return _failNextRequest }
        set { lock.lock(); _failNextRequest = newValue; lock.unlock() }
    }

    /// Hold the response to every request whose `Range` starts at `offset` for `seconds`, and let
    /// every other request through at once. The footer side fetch on a slow CDN while the head
    /// is already on disk: what a probe must not wait on.
    public var delayForRangeStartingAt: (offset: Int64, seconds: TimeInterval)? {
        get { lock.lock(); defer { lock.unlock() }; return _delayForRangeStartingAt }
        set { lock.lock(); _delayForRangeStartingAt = newValue; lock.unlock() }
    }

    /// Send the response headers and the first N body bytes at once, per range start, and hold
    /// the rest of the body until ``releaseHeldBody(forRangeStartingAt:)``. Two things make the
    /// split necessary rather than a headers-only hold: the session hands a response to its
    /// delegate only together with the first bytes of its body, so headers alone put nothing in
    /// front of the client; and the response's disposition is answered from the byte source's own
    /// queue, so a response that lands while that queue is parked by another body's chunk is
    /// stranded, body and all. Sending N bytes up front lets the client answer the disposition
    /// (and see those N bytes) at a moment the test chooses, and the remainder at another (#261).
    public var heldBodyAfterBytesForRangeStartingAt: [Int64: Int] {
        get { lock.lock(); defer { lock.unlock() }; return _heldBodyAfterBytesForRangeStartingAt }
        set { lock.lock(); _heldBodyAfterBytesForRangeStartingAt = newValue; lock.unlock() }
    }

    /// Write the rest of a body held by ``heldBodyAfterBytesForRangeStartingAt`` and close the
    /// connection. Safe to call before the request has arrived: the remainder then goes out with
    /// the first bytes. The remainder counts as served when it is written.
    public func releaseHeldBody(forRangeStartingAt offset: Int64) {
        lock.lock()
        guard let held = _heldRemainders.removeValue(forKey: offset) else {
            _releasedHeldRanges.insert(offset)
            lock.unlock()
            return
        }
        lock.unlock()
        queue.async { self.sendHeldRemainder(held.rest, on: held.connection) }
    }

    private func sendHeldRemainder(_ rest: Data, on connection: NWConnection) {
        lock.lock(); _servedBytes += Int64(rest.count); lock.unlock()
        connection.send(content: rest, completion: .contentProcessed { _ in connection.cancel() })
    }

    /// Hold EVERY response for this long: a slow network, where any read that reaches the origin
    /// at all shows up as this delay. A cache-served start that touches the network is caught by
    /// this where a per-offset delay would need to guess which range it opened.
    public var delayForEveryRange: TimeInterval {
        get { lock.lock(); defer { lock.unlock() }; return _delayForEveryRange }
        set { lock.lock(); _delayForEveryRange = newValue; lock.unlock() }
    }

    /// Answer `503`, once, to the next request whose `Range` starts at this byte, and let every
    /// other request through. The side fetch for the tail failing while the head streams. A
    /// status rather than a dropped connection, because CFNetwork transparently retries a request
    /// whose connection died before any response byte.
    public var rejectNextRangeStartingAt: Int64? {
        get { lock.lock(); defer { lock.unlock() }; return _rejectNextRangeStartingAt }
        set { lock.lock(); _rejectNextRangeStartingAt = newValue; lock.unlock() }
    }

    /// Answer every fixture request whose `Host` header names `host` (e.g. `localhost:<port>`)
    /// with `status` and an empty body, indefinitely. The signed CDN URL whose signature expired:
    /// the remembered end of a chain answers `403` while the chain itself still works.
    public func reject(host: String, status: Int = 403) {
        lock.lock(); _rejectedHostStatus = (host, status); lock.unlock()
    }

    /// Answer every fixture request whose `Host` header names `host` with `200`, `contentType`
    /// and `body`, ignoring the range. The signed CDN URL whose signature expired on a host that
    /// serves a page about it instead of refusing.
    ///
    /// `firstChunkBytes`, when given, splits the body into two writes — the first this many bytes,
    /// the rest in a second write once the first is processed — instead of one: a body whose sniff
    /// cannot be settled by the first delegate chunk alone (#226).
    public func answerWithPage(
        host: String, contentType: String = "text/html; charset=utf-8", body: Data, firstChunkBytes: Int? = nil
    ) {
        lock.lock(); _pageForHost = (host, contentType, body, firstChunkBytes); lock.unlock()
    }

    /// Send redirect hops to `localhost` instead of `127.0.0.1`: a different host to CFNetwork and
    /// to the source's auth-header scope, on the same listener.
    public var redirectsToAlternateHost: Bool {
        get { lock.lock(); defer { lock.unlock() }; return _redirectsToAlternateHost }
        set { lock.lock(); _redirectsToAlternateHost = newValue; lock.unlock() }
    }

    /// The port the listener bound; zero until `init` returns.
    public private(set) var port: UInt16 = 0

    /// The stitch the origin serves. Settable, because a host re-stitches its ad breaks behind a
    /// stable URL: a test swaps this between requests to stand in for that.
    public var body: Data {
        get { lock.lock(); defer { lock.unlock() }; return _body }
        set { lock.lock(); _body = newValue; _bodies = nil; lock.unlock() }
    }

    /// One stitch per request, in order; the last one serves every request after it. A host that
    /// re-stitches between a listener's first body and their reconnect, made deterministic.
    public var bodies: [Data]? {
        get { lock.lock(); defer { lock.unlock() }; return _bodies }
        set { lock.lock(); _bodies = newValue; lock.unlock() }
    }

    public init(body: Data, mimeType: String) throws {
        self._body = body
        self.mimeType = mimeType
        let parameters = NWParameters.tcp
        parameters.allowLocalEndpointReuse = true
        listener = try NWListener(using: parameters, on: .any)

        let ready = DispatchSemaphore(value: 0)
        listener.stateUpdateHandler = { [weak self] state in
            if case .ready = state {
                self?.port = self?.listener.port?.rawValue ?? 0
                ready.signal()
            }
        }
        listener.newConnectionHandler = { [weak self] connection in
            self?.accept(connection)
        }
        listener.start(queue: queue)
        guard ready.wait(timeout: .now() + 5) == .success, port != 0 else {
            listener.cancel()
            throw ServerError.didNotStart
        }
    }

    /// Thrown by `init` when the listener never reached `.ready`.
    public enum ServerError: Error { case didNotStart }

    /// A tone fixture (`tone.mp3`, `tone-45s.mp3`, `tone_moov_last.m4a`) from this target's
    /// resource bundle; nil if the name is wrong.
    public static func fixtureURL(_ name: String, withExtension ext: String) -> URL? {
        Bundle.module.url(forResource: name, withExtension: ext, subdirectory: "Fixtures")
            ?? Bundle.module.url(forResource: name, withExtension: ext)
    }

    /// The one URL this origin serves.
    public var url: URL { URL(string: "http://127.0.0.1:\(port)/fixture.mp3")! }

    /// The fixture path a redirect chain ends at.
    public static let fixturePath = "/fixture.mp3"

    /// A URL that answers `302` `hops` times before landing on ``url``'s path — an enclosure URL
    /// behind a tracking prefix. Each hop is `/redirect/<n>/fixture.mp3`, the last a `302` to
    /// ``fixturePath`` (on `localhost` when ``redirectsToAlternateHost`` is set).
    public func redirectingURL(hops: Int) -> URL {
        URL(string: "http://127.0.0.1:\(port)/redirect/\(hops)/fixture.mp3")!
    }

    /// The URL the chain from ``redirectingURL(hops:)`` ends at, given the current host setting.
    public var resolvedURL: URL {
        URL(string: "http://\(redirectsToAlternateHost ? "localhost" : "127.0.0.1"):\(port)\(Self.fixturePath)")!
    }

    /// Cancels the listener and every open connection.
    public func stop() {
        listener.cancel()
        lock.lock()
        let open = connections
        connections = []
        lock.unlock()
        for connection in open { connection.cancel() }
    }

    // MARK: - Serving

    private func accept(_ connection: NWConnection) {
        lock.lock(); connections.append(connection); lock.unlock()
        connection.start(queue: queue)
        receiveHead(connection, buffer: Data())
    }

    /// HTTP/1.1 requests here are tiny and header-only, so the head is read until the blank line and
    /// nothing else is expected on the connection.
    private func receiveHead(_ connection: NWConnection, buffer: Data) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 8192) { [weak self] data, _, isComplete, error in
            guard let self else { return }
            var accumulated = buffer
            if let data { accumulated.append(data) }
            if let terminator = accumulated.range(of: Data("\r\n\r\n".utf8)) {
                let head = String(decoding: accumulated[..<terminator.lowerBound], as: UTF8.self)
                self.respond(to: head, on: connection)
                return
            }
            if error != nil || isComplete {
                connection.cancel()
                return
            }
            self.receiveHead(connection, buffer: accumulated)
        }
    }

    private func respond(to head: String, on connection: NWConnection) {
        let path = Self.parsePath(head)
        if let hop = Self.redirectHop(path) {
            lock.lock()
            _requestHeads.append(head)
            let alternate = _redirectsToAlternateHost
            lock.unlock()
            let next = hop > 1
                ? "http://127.0.0.1:\(port)/redirect/\(hop - 1)/fixture.mp3"
                : "http://\(alternate ? "localhost" : "127.0.0.1"):\(port)\(Self.fixturePath)"
            let header = "HTTP/1.1 302 Found\r\nLocation: \(next)\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
            connection.send(content: Data(header.utf8), completion: .contentProcessed { _ in connection.cancel() })
            return
        }
        lock.lock()
        let body = _bodies.map { $0[min(_requestedRanges.count, $0.count - 1)] } ?? _body
        lock.unlock()
        let range = Self.parseRange(head, total: Int64(body.count))
        lock.lock()
        _requestedRanges.append(range.lowerBound)
        _requestHeads.append(head)
        var delay = range.lowerBound == 0 ? _delayForOffsetZero : 0
        if let held = _delayForRangeStartingAt, held.offset == range.lowerBound { delay = held.seconds }
        delay = max(delay, _delayForEveryRange)
        let wholeBody = _respondsWholeBodyIgnoringRange
        let shouldFail = _failNextRequest
        if shouldFail { _failNextRequest = false }
        var rejectedRange = false
        if _rejectNextRangeStartingAt == range.lowerBound {
            _rejectNextRangeStartingAt = nil
            rejectedRange = true
        }
        let stallAfter = _stallsAfterBodyBytes
        let rejected = _rejectedHostStatus
        let page = _pageForHost
        let heldAfter = _heldBodyAfterBytesForRangeStartingAt[range.lowerBound]
        lock.unlock()

        guard !shouldFail else {
            connection.cancel()
            return
        }
        if rejectedRange || (rejected != nil && Self.parseHost(head) == rejected?.host) {
            let status = rejectedRange ? 503 : rejected?.status ?? 503
            let header = "HTTP/1.1 \(status) Rejected\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
            connection.send(content: Data(header.utf8), completion: .contentProcessed { _ in connection.cancel() })
            return
        }

        if let page, Self.parseHost(head) == page.host {
            let header = Data("HTTP/1.1 200 OK\r\nContent-Type: \(page.contentType)\r\nContent-Length: \(page.body.count)\r\nConnection: close\r\n\r\n".utf8)
            if let firstChunkBytes = page.firstChunkBytes, firstChunkBytes < page.body.count {
                var first = header
                first.append(page.body.prefix(firstChunkBytes))
                let rest = Data(page.body.dropFirst(firstChunkBytes))
                connection.send(content: first, completion: .contentProcessed { [queue] _ in
                    // A gap wide enough that the two writes land as separate reads on the client
                    // instead of coalescing into one over a loopback connection this fast.
                    queue.asyncAfter(deadline: .now() + 0.05) {
                        connection.send(content: rest, completion: .contentProcessed { _ in connection.cancel() })
                    }
                })
                return
            }
            var payload = header
            payload.append(page.body)
            connection.send(content: payload, completion: .contentProcessed { _ in connection.cancel() })
            return
        }

        let slice = wholeBody ? body : body.subdata(in: Int(range.lowerBound)..<Int(range.upperBound + 1))
        var header: String
        if wholeBody {
            header = "HTTP/1.1 200 OK\r\n"
            header += "Content-Type: \(mimeType)\r\n"
            header += "Accept-Ranges: bytes\r\n"
        } else {
            header = "HTTP/1.1 206 Partial Content\r\n"
            header += "Content-Type: \(mimeType)\r\n"
            header += "Accept-Ranges: bytes\r\n"
            header += "Content-Range: bytes \(range.lowerBound)-\(range.upperBound)/\(body.count)\r\n"
        }
        header += "Content-Length: \(slice.count)\r\n"
        header += "Connection: close\r\n\r\n"
        if let heldAfter, heldAfter < slice.count {
            // Headers and the first bytes now, the rest on release — see
            // ``heldBodyAfterBytesForRangeStartingAt``.
            var first = Data(header.utf8)
            first.append(slice.prefix(heldAfter))
            let rest = Data(slice.dropFirst(heldAfter))
            lock.lock()
            _servedBytes += Int64(heldAfter)
            let alreadyReleased = _releasedHeldRanges.remove(range.lowerBound) != nil
            if !alreadyReleased { _heldRemainders[range.lowerBound] = (connection, rest) }
            lock.unlock()
            connection.send(content: first, completion: .contentProcessed { [weak self] _ in
                guard alreadyReleased, let self else { return }
                self.sendHeldRemainder(rest, on: connection)
            })
            return
        }
        let written: Data
        if let stallAfter { written = slice.subdata(in: 0..<min(stallAfter, slice.count)) } else { written = slice }
        var payload = Data(header.utf8)
        payload.append(written)
        lock.lock(); _servedBytes += Int64(written.count); lock.unlock()

        let stalls = stallAfter != nil
        let send = {
            connection.send(content: payload, completion: .contentProcessed { _ in
                // A stalled body is never finished and never closed: closing would look like a
                // short read the source retries, which is the recoverable failure, not this one.
                if !stalls { connection.cancel() }
            })
        }
        if delay > 0 {
            queue.asyncAfter(deadline: .now() + delay, execute: send)
        } else {
            send()
        }
    }

    /// The `Host` header's value, or nil when the request carries none.
    static func parseHost(_ head: String) -> String? {
        for line in head.split(separator: "\r\n") where line.lowercased().hasPrefix("host:") {
            return line.dropFirst("host:".count).trimmingCharacters(in: .whitespaces)
        }
        return nil
    }

    /// The request target of `GET /path HTTP/1.1`, query and all.
    static func parsePath(_ head: String) -> String {
        let parts = head.split(separator: "\r\n").first?.split(separator: " ") ?? []
        return parts.count > 1 ? String(parts[1]) : "/"
    }

    /// `n` for `/redirect/<n>/...`, else nil.
    static func redirectHop(_ path: String) -> Int? {
        let parts = path.split(separator: "/")
        guard parts.count >= 2, parts[0] == "redirect" else { return nil }
        return Int(parts[1])
    }

    /// `bytes=a-b`, `bytes=a-`, or no header at all (the whole body).
    static func parseRange(_ head: String, total: Int64) -> ClosedRange<Int64> {
        let whole: ClosedRange<Int64> = 0...max(total - 1, 0)
        guard let line = head.split(separator: "\r\n").first(where: {
            $0.lowercased().hasPrefix("range:")
        }) else { return whole }
        guard let spec = line.split(separator: "=").last else { return whole }
        let bounds = spec.split(separator: "-", omittingEmptySubsequences: false)
        guard let start = Int64(bounds.first ?? "") else { return whole }
        let end = bounds.count > 1 ? Int64(bounds[1]) : nil
        let clampedStart = min(max(start, 0), max(total - 1, 0))
        let clampedEnd = min(end ?? (total - 1), total - 1)
        guard clampedEnd >= clampedStart else { return clampedStart...clampedStart }
        return clampedStart...clampedEnd
    }
}
