// Copied from Shuttle Podcasts (podcasts@9ee6e0954) mobile/ios/Playback/Tests/PlaybackTests/HTTPRangeByteSourceTests.swift — see ios/Playback/README.md.
import XCTest
@testable import S2Playback
import S2PlaybackTestSupport

/// The byte layer of the streaming player, over a real loopback origin.
///
/// A fake `URLProtocol` would prove nothing here: everything this class exists to get right — a
/// bounded `Range`, a suspended body, a host that ignores the range, a connection that dies — is a
/// property of a real HTTP transaction. ``LoopbackMediaServer`` is that origin, and its
/// `requestedRanges` and `servedBytes` are the only place the read-ahead bound is observable at all.
final class HTTPRangeByteSourceTests: XCTestCase {

    private var server: LoopbackMediaServer?
    private var source: HTTPRangeByteSource?
    /// One session for the whole suite, as production has one for the whole process (#225).
    /// Ephemeral so nothing it learns lands on disk between runs.
    static let testSession = HTTPRangeByteSource.makeSession(configuration: .ephemeral)

    override func tearDown() {
        // `URLSession` retains its delegate, so a source that is never cancelled outlives the test.
        source?.cancel()
        source = nil
        server?.stop()
        server = nil
        super.tearDown()
    }

    // MARK: - Fixtures

    /// Deterministic pseudo-random bytes: a failure has to be reproducible, and a repeating pattern
    /// would hide an off-by-one in the window arithmetic.
    private func makeBody(_ count: Int) -> Data {
        var state: UInt64 = 0x9E3779B97F4A7C15
        var bytes = [UInt8]()
        bytes.reserveCapacity(count)
        for _ in 0..<count {
            state = state &* 6364136223846793005 &+ 1442695040888963407
            bytes.append(UInt8((state >> 33) & 0xFF))
        }
        return Data(bytes)
    }

    /// The same pseudo-random bytes as ``makeBody(_:)``, but starting with an ID3 tag: a body
    /// ``HTTPRangeByteSource/looksLikeMedia(_:)`` accepts, for a sniff test that needs one to hold
    /// under a type that names nothing (#226).
    private func makeMediaBody(_ count: Int) -> Data {
        var body = Data("ID3".utf8)
        body.append(makeBody(count - body.count))
        return body
    }

    private func startServer(body: Data) throws -> LoopbackMediaServer {
        let started = try LoopbackMediaServer(body: body, mimeType: "audio/mpeg")
        server = started
        return started
    }

    private func makeSource(
        _ server: LoopbackMediaServer,
        policy: ReadAheadPolicy,
        authHeaders: [String: String] = [:],
        url: URL? = nil,
        resolvedURLs: ResolvedURLCache? = nil,
        tee: AudioByteTee? = nil
    ) -> HTTPRangeByteSource {
        let made = HTTPRangeByteSource(
            url: url ?? server.url,
            authHeaders: authHeaders,
            readAhead: policy,
            session: Self.testSession,
            tee: tee,
            runStore: nil,
            resolvedURLs: resolvedURLs
        )
        source = made
        return made
    }

    private func policy(
        windowBytes: Int64,
        minWindowBytes: Int64 = 4 * 1024,
        backWindowBytes: Int64 = 512 * 1024
    ) -> ReadAheadPolicy {
        ReadAheadPolicy(
            windowSeconds: 60,
            appetiteWindowSeconds: 80,
            windowBytes: windowBytes,
            appetiteWindowBytes: windowBytes,
            minWindowBytes: minWindowBytes,
            backWindowBytes: backWindowBytes
        )
    }

    // MARK: - Reading

    private func read(_ source: HTTPRangeByteSource, upTo count: Int) throws -> Data {
        var out = Data()
        var scratch = [UInt8](repeating: 0, count: 32 * 1024)
        while out.count < count {
            let want = min(scratch.count, count - out.count)
            var read = 0
            try scratch.withUnsafeMutableBytes { raw in
                guard let base = raw.baseAddress else { return }
                read = try source.read(into: base, maxLength: want)
            }
            if read == 0 { break }
            out.append(contentsOf: scratch[0..<read])
        }
        return out
    }

    /// Poll rather than sleep: every wait here is on a real socket, and a fixed sleep is either
    /// flaky or slow.
    private func waitUntil(_ timeout: TimeInterval = 5, _ condition: () -> Bool) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if condition() { return true }
            Thread.sleep(forTimeInterval: 0.02)
        }
        return condition()
    }

    // MARK: - Tests

    func testSequentialReadsReproduceTheBodyAndThenReportEOF() throws {
        let body = makeBody(300 * 1024)
        let server = try startServer(body: body)
        let source = makeSource(server, policy: policy(windowBytes: 4 * 1024 * 1024))

        let read = try self.read(source, upTo: body.count)
        XCTAssertEqual(read, body)
        XCTAssertEqual(source.totalLength, Int64(body.count))
        XCTAssertEqual(source.position, Int64(body.count))

        var scratch = [UInt8](repeating: 0, count: 16)
        var eof = -1
        try scratch.withUnsafeMutableBytes { raw in
            guard let base = raw.baseAddress else { return }
            eof = try source.read(into: base, maxLength: 16)
        }
        XCTAssertEqual(eof, 0, "past the last byte the reader is at EOF, not blocked")
    }

    func testSeekBeyondTotalLengthIsUnseekable() throws {
        let body = makeBody(64 * 1024)
        let server = try startServer(body: body)
        let source = makeSource(server, policy: policy(windowBytes: 1024 * 1024))

        _ = try read(source, upTo: 4096)
        XCTAssertEqual(source.totalLength, Int64(body.count))
        XCTAssertThrowsError(try source.seek(to: Int64(body.count) + 1)) { error in
            XCTAssertEqual(error as? StreamByteReaderError, .unseekable)
        }
    }

    func testSeekInsideTheWindowOpensNoTransaction() throws {
        let body = makeBody(512 * 1024)
        let server = try startServer(body: body)
        let source = makeSource(server, policy: policy(windowBytes: 4 * 1024 * 1024))

        _ = try read(source, upTo: 64 * 1024)
        XCTAssertTrue(waitUntil { source.bytesFetched == Int64(body.count) }, "whole body buffered")

        try source.seek(to: 400 * 1024)
        let after = try read(source, upTo: 1024)
        XCTAssertEqual(after, body.subdata(in: (400 * 1024)..<(401 * 1024)))
        XCTAssertEqual(server.requestedRanges.count, 1, "a seek into held bytes is not a transaction")
    }

    func testSeekOutsideTheWindowOpensOneTransactionAtThatOffset() throws {
        let body = makeBody(512 * 1024)
        let server = try startServer(body: body)
        // A back window small enough that the bytes at the seek target have been released.
        let source = makeSource(server, policy: policy(windowBytes: 4 * 1024 * 1024, backWindowBytes: 16 * 1024))

        _ = try read(source, upTo: 400 * 1024)
        XCTAssertEqual(server.requestedRanges, [0])

        try source.seek(to: 1024)
        let after = try read(source, upTo: 1024)
        XCTAssertEqual(after, body.subdata(in: 1024..<2048))
        XCTAssertEqual(server.requestedRanges, [0, 1024], "exactly one new transaction, at the target")
    }

    func testReadAheadIsBoundedAndResumesWhenTheDecoderCatchesUp() throws {
        let body = makeBody(4 * 1024 * 1024)
        let server = try startServer(body: body)
        let window: Int64 = 64 * 1024
        let source = makeSource(server, policy: policy(windowBytes: window, backWindowBytes: 64 * 1024))

        // One small read opens the transaction and then leaves the reader stalled at the front.
        _ = try read(source, upTo: 4096)
        Thread.sleep(forTimeInterval: 1)
        let stalled = server.servedBytes
        XCTAssertLessThan(
            stalled,
            window + 16 * 1024,
            "the host is asked for the window and no more, so nothing can arrive late"
        )

        _ = try read(source, upTo: 32 * 1024)
        XCTAssertTrue(
            waitUntil { server.servedBytes > stalled },
            "reading raises the ceiling, which extends the fetch with a continuation"
        )
    }

    func testTeeSeesEveryByteOnceInOffsetOrder() throws {
        let body = makeBody(256 * 1024)
        let server = try startServer(body: body)
        let recorder = RecordingTee()
        let made = HTTPRangeByteSource(
            url: server.url,
            authHeaders: [:],
            // Small enough that the body takes several continuations, so the flags are exercised.
            readAhead: policy(windowBytes: 32 * 1024, backWindowBytes: 32 * 1024),
            session: Self.testSession,
            tee: recorder,
            runStore: nil,
            resolvedURLs: nil
        )
        source = made

        let read = try self.read(made, upTo: body.count)
        XCTAssertEqual(read, body)
        XCTAssertTrue(waitUntil { recorder.opens.count == recorder.closes.count })

        XCTAssertEqual(recorder.bytes, body, "every byte, once, in order")
        XCTAssertTrue(recorder.offsetsAreContiguousFromZero, "no gaps and no repeats in the offsets")
        XCTAssertEqual(recorder.opens.first?.startByte, 0)
        XCTAssertEqual(recorder.opens.first?.isContinuation, false, "the first open is where the listener is")
        XCTAssertGreaterThan(recorder.opens.count, 1, "a 32 KiB window cannot carry 256 KiB in one body")
        XCTAssertTrue(
            recorder.opens.dropFirst().allSatisfy { $0.isContinuation },
            "picking the stream up at the frontier is a continuation, never an anchor"
        )
        XCTAssertEqual(recorder.learnedTotals.first, Int64(body.count))
    }

    func testCancelUnblocksABlockedRead() throws {
        let body = makeBody(64 * 1024)
        let server = try startServer(body: body)
        // Longer than any wait below, so the only way the read can come back is the cancel.
        server.delayForOffsetZero = 30
        let source = makeSource(server, policy: policy(windowBytes: 1024 * 1024))

        let threw = expectation(description: "read throws cancelled")
        // A dedicated thread, not the global pool: under a full bundle run the pool is shared with
        // every other test's blocked reads and the block may not be scheduled for seconds, which
        // is a starved thread, not a cancel that failed to wake anyone.
        let reader = Thread {
            var scratch = [UInt8](repeating: 0, count: 1024)
            do {
                _ = try scratch.withUnsafeMutableBytes { raw -> Int in
                    guard let base = raw.baseAddress else { return 0 }
                    return try source.read(into: base, maxLength: 1024)
                }
                XCTFail("a cancelled read must not return bytes")
            } catch {
                XCTAssertEqual(error as? StreamByteReaderError, .cancelled)
                threw.fulfill()
            }
        }
        reader.start()

        // Cancel only once the read is provably blocked: it has armed the fetch and the origin has
        // the request. A fixed sleep here either raced the reader or padded every run.
        XCTAssertTrue(waitUntil { source.ensureFetchingCalls >= 1 && server.requestedRanges.count >= 1 },
                      "the read never reached the origin")
        source.cancel()
        wait(for: [threw], timeout: 5)
    }

    func testRequestsCarryTheAuthHeadersAndAskForIdentityEncoding() throws {
        let body = makeBody(64 * 1024)
        let server = try startServer(body: body)
        let source = makeSource(server, policy: policy(windowBytes: 1024 * 1024), authHeaders: ["Authorization": "Bearer test-token"])

        _ = try read(source, upTo: 4096)
        let head = try XCTUnwrap(server.requestHeads.first)
        XCTAssertTrue(head.contains("Authorization: Bearer test-token"), head)
        XCTAssertTrue(head.contains("Accept-Encoding: identity"), "a compressed body has no usable offset mapping")
        XCTAssertTrue(head.contains("Range: bytes=0-"), head)
    }

    // MARK: - The shared session (#225)

    /// Two sources built the default way go through the one process-wide session, which is what
    /// lets the second play of a host reuse the first play's connection.
    func testSourcesShareTheProcessSessionByDefault() throws {
        let body = makeBody(16 * 1024)
        let server = try startServer(body: body)
        let first = HTTPRangeByteSource(url: server.url, authHeaders: [:], runStore: nil, resolvedURLs: nil)
        let second = HTTPRangeByteSource(url: server.url, authHeaders: [:], runStore: nil, resolvedURLs: nil)
        defer { first.cancel(); second.cancel() }

        XCTAssertTrue(first.sessionForTesting === second.sessionForTesting)
        XCTAssertTrue(first.sessionForTesting === HTTPRangeByteSource.sharedSession)
        XCTAssertNotNil(first.sessionForTesting.delegateQueue.underlyingQueue)
        XCTAssertEqual(first.sessionForTesting.configuration.requestCachePolicy, .reloadIgnoringLocalCacheData)
        XCTAssertNil(first.sessionForTesting.configuration.urlCache)
    }

    /// Sharing the session must not share the headers: a source's `Authorization` belongs to its
    /// own feed host. A second source on the same session, reading the same origin through
    /// another host name and holding no auth of its own, sends none — the session carries no
    /// credential from the first source's requests, redirect or not.
    func testASharedSessionCarriesNoAuthFromOneSourceToAnother() throws {
        let body = makeBody(64 * 1024)
        let server = try startServer(body: body)
        server.redirectsToAlternateHost = true
        let feed = HTTPRangeByteSource(
            url: server.redirectingURL(hops: 1), authHeaders: ["Authorization": "Bearer feed-token"],
            readAhead: policy(windowBytes: 1024 * 1024), session: Self.testSession, runStore: nil, resolvedURLs: nil
        )
        defer { feed.cancel() }
        _ = try read(feed, upTo: 4096)
        let cdn = HTTPRangeByteSource(
            url: server.resolvedURL, authHeaders: [:],
            readAhead: policy(windowBytes: 1024 * 1024), session: Self.testSession, runStore: nil, resolvedURLs: nil
        )
        defer { cdn.cancel() }
        _ = try read(cdn, upTo: 4096)
        XCTAssertTrue(feed.sessionForTesting === cdn.sessionForTesting)

        let firstHop = try XCTUnwrap(heads(server, path: "/redirect/1/fixture.mp3").first)
        XCTAssertTrue(firstHop.contains("Authorization: Bearer feed-token"), firstHop)
        let landings = heads(server, path: LoopbackMediaServer.fixturePath)
        XCTAssertEqual(landings.count, 2, "one landing per source: \(landings)")
        for landing in landings {
            XCTAssertTrue(landing.contains("Host: localhost"), landing)
            XCTAssertFalse(landing.contains("Authorization"), "the feed's token must not reach the CDN: \(landing)")
        }
    }

    func testWholeBodyResponseStillServesASeekTarget() throws {
        let body = makeBody(256 * 1024)
        let server = try startServer(body: body)
        server.respondsWholeBodyIgnoringRange = true
        let source = makeSource(server, policy: policy(windowBytes: 4 * 1024 * 1024))

        try source.seek(to: 100 * 1024)
        let read = try self.read(source, upTo: 4096)
        XCTAssertEqual(read, body.subdata(in: (100 * 1024)..<(100 * 1024 + 4096)))
        XCTAssertEqual(source.totalLength, Int64(body.count), "a 200's Content-Length is the whole resource")
    }

    /// **A blocked read waits; it does not spin.**
    ///
    /// The old loop re-armed the fetch and slept 50 ms, for the whole duration of a stall: on a
    /// cellular hiccup that is hundreds of hops onto the transaction queue asking a transaction
    /// that is already open to open again. The bytes arrive at the same moment either way, so the
    /// only thing the poll bought was work — and the only way to see the difference is to count.
    func testAStalledReadWaitsRatherThanSpinning() throws {
        let body = makeBody(64 * 1024)
        let server = try startServer(body: body)
        server.delayForOffsetZero = 2
        let source = makeSource(server, policy: policy(windowBytes: 1024 * 1024))

        let finished = expectation(description: "the read returned once the body arrived")
        DispatchQueue.global().async { [self] in
            let read = try? self.read(source, upTo: 4096)
            XCTAssertEqual(read, body.subdata(in: 0..<4096), "the read still completes when data arrives")
            finished.fulfill()
        }
        wait(for: [finished], timeout: 10)

        XCTAssertLessThan(source.ensureFetchingCalls, 5,
                          "\(source.ensureFetchingCalls) re-arms across a 2 s stall: the read is polling")
    }

    /// **A seek during a discarded prefix aborts the body it is discarding.**
    ///
    /// A host that answers a ranged request with the whole resource makes every seek a re-download
    /// of everything before the target. That behaviour is kept — it is what the AVPlayer loader
    /// does, and the alternative is refusing to play at all — but the transaction has to be
    /// abortable: a listener who seeks twice must not wait out the first seek's prefix before the
    /// second one starts. And once the host has ignored one `Range`, the next transaction is opened
    /// at 0, which is what it is going to be anyway.
    func testASeekDuringAnIgnoredRangeAbortsTheOldPrefix() throws {
        let body = makeBody(512 * 1024)
        let server = try startServer(body: body)
        server.respondsWholeBodyIgnoringRange = true
        let source = makeSource(server, policy: policy(windowBytes: 8 * 1024, backWindowBytes: 4 * 1024))

        try source.seek(to: 400 * 1024)
        XCTAssertTrue(waitUntil { server.requestedRanges.count >= 1 })

        // A second seek, while the first one's prefix is still being discarded.
        try source.seek(to: 200 * 1024)
        let read = try self.read(source, upTo: 4096)
        XCTAssertEqual(read, body.subdata(in: (200 * 1024)..<(200 * 1024 + 4096)),
                       "the second seek's bytes, not the first seek's prefix")
        XCTAssertEqual(source.position, Int64(200 * 1024 + 4096))

        XCTAssertGreaterThanOrEqual(server.requestedRanges.count, 2, "the old body was abandoned for a new one")
        XCTAssertEqual(server.requestedRanges.last, 0,
                       "a host that ignores ranges is asked from 0, not re-declared mid-body")
    }

    // MARK: - Redirects

    /// Every request head the server saw for a given path.
    private func heads(_ server: LoopbackMediaServer, path: String) -> [String] {
        server.requestHeads.filter { $0.hasPrefix("GET \(path) ") }
    }

    func testARedirectedRequestKeepsItsRangeAndDropsTheAuthHeaderOnAnotherHost() throws {
        let body = makeBody(64 * 1024)
        let server = try startServer(body: body)
        server.redirectsToAlternateHost = true
        let original = server.redirectingURL(hops: 2)
        let source = makeSource(
            server, policy: policy(windowBytes: 1024 * 1024),
            authHeaders: ["Authorization": "Bearer feed-token"], url: original
        )

        _ = try read(source, upTo: 4096)

        let firstHop = try XCTUnwrap(heads(server, path: "/redirect/2/fixture.mp3").first)
        XCTAssertTrue(firstHop.contains("Authorization: Bearer feed-token"), firstHop)
        let landing = try XCTUnwrap(heads(server, path: LoopbackMediaServer.fixturePath).first)
        XCTAssertTrue(landing.contains("Range: bytes=0-"), "the range must survive the redirect: \(landing)")
        XCTAssertTrue(landing.contains("Accept-Encoding: identity"), landing)
        XCTAssertFalse(landing.contains("Authorization"), "the feed's token must not reach the CDN: \(landing)")
        XCTAssertEqual(source.resolvedURLForTesting, server.resolvedURL)
    }

    func testLaterTransactionsGoStraightToTheResolvedURL() throws {
        let body = makeBody(4 * 1024 * 1024)
        let server = try startServer(body: body)
        let original = server.redirectingURL(hops: 2)
        let source = makeSource(server, policy: policy(windowBytes: 256 * 1024, backWindowBytes: 64 * 1024), url: original)

        _ = try read(source, upTo: 4096)
        XCTAssertEqual(heads(server, path: "/redirect/2/fixture.mp3").count, 1)
        // Outside the window: a new transaction, which must not walk the chain again.
        try source.seek(to: 3 * 1024 * 1024)
        XCTAssertEqual(try read(source, upTo: 4096), body[(3 * 1024 * 1024)..<(3 * 1024 * 1024 + 4096)])
        XCTAssertEqual(heads(server, path: "/redirect/2/fixture.mp3").count, 1, "the chain was walked twice")
        XCTAssertEqual(heads(server, path: "/redirect/1/fixture.mp3").count, 1)
        XCTAssertTrue(server.requestedRanges.contains(3 * 1024 * 1024), "\(server.requestedRanges)")
    }

    /// The start timing's HTTP stage (#193): hops are counted per hop, the first response records
    /// them with its status, and a later transaction on the resolved URL adds none.
    func testTheFirstResponseReportsTheRedirectChainAndItsStatus() throws {
        let body = makeBody(4 * 1024 * 1024)
        let server = try startServer(body: body)
        server.redirectsToAlternateHost = true
        let original = server.redirectingURL(hops: 2)
        let source = makeSource(server, policy: policy(windowBytes: 256 * 1024, backWindowBytes: 64 * 1024), url: original)
        XCTAssertNil(source.firstResponse, "nothing has been requested yet")
        XCTAssertEqual(source.redirectHops, 0)

        let before = StartupTiming.now()
        _ = try read(source, upTo: 4096)

        let first = try XCTUnwrap(source.firstResponse)
        XCTAssertEqual(first.status, 206)
        XCTAssertEqual(first.redirects, 2)
        // `/redirect/2` -> `/redirect/1` on the same host, then the fixture on the alternate one.
        XCTAssertEqual(first.hosts, ["127.0.0.1", "localhost"])
        XCTAssertEqual(source.redirectHops, 2)
        let at = try XCTUnwrap(source.firstResponseAt)
        XCTAssertGreaterThanOrEqual(at, before)
        XCTAssertLessThanOrEqual(at, StartupTiming.now())

        // Outside the window: a second transaction, straight to the resolved URL.
        try source.seek(to: 3 * 1024 * 1024)
        _ = try read(source, upTo: 4096)
        // At least the seek's body; a continuation of the first window may have opened too.
        XCTAssertGreaterThanOrEqual(source.transactionCount, 2)
        XCTAssertEqual(source.redirectHops, 2, "the resolved URL must not be re-walked")
        XCTAssertEqual(source.firstResponse, first, "the first response is recorded once")
    }

    func testARedirectFreeSourceReportsZeroHops() throws {
        let body = makeBody(64 * 1024)
        let server = try startServer(body: body)
        let source = makeSource(server, policy: policy(windowBytes: 1024 * 1024))

        _ = try read(source, upTo: 4096)

        let first = try XCTUnwrap(source.firstResponse)
        XCTAssertEqual(first, StartupTiming.FirstResponse(status: 206, redirects: 0, hosts: []))
    }

    /// The other half of the start timing's HTTP stage (#193): `resume()` is called before the
    /// response it produces can possibly have landed.
    func testRequestIssuedAtIsStampedBeforeTheFirstResponse() throws {
        let body = makeBody(64 * 1024)
        let server = try startServer(body: body)
        let source = makeSource(server, policy: policy(windowBytes: 1024 * 1024))
        XCTAssertNil(source.requestIssuedAt, "nothing has been requested yet")

        _ = try read(source, upTo: 4096)

        let issuedAt = try XCTUnwrap(source.requestIssuedAt)
        let firstResponseAt = try XCTUnwrap(source.firstResponseAt)
        XCTAssertLessThanOrEqual(issuedAt, firstResponseAt)
    }

    /// `URLSessionTaskMetrics` (#193): they land only once the first transaction itself finishes,
    /// which on a bounded window means after every byte of it has been read, well after
    /// ``firstResponseAt``. A real loopback round trip, delayed so `server` is observably nonzero
    /// rather than rounding to 0 on a socket that never leaves the machine.
    func testNetMetricsArePopulatedOnceTheFirstTransactionFinishes() throws {
        let body = makeBody(64 * 1024)
        let server = try startServer(body: body)
        server.delayForEveryRange = 0.05
        let source = makeSource(server, policy: policy(windowBytes: 1024 * 1024))
        XCTAssertNil(source.netMetrics, "the transaction has not finished yet")

        _ = try read(source, upTo: body.count)

        XCTAssertTrue(
            waitUntil(5) { source.netMetrics != nil },
            "metrics should land once the bounded transaction completes"
        )
        let metrics = try XCTUnwrap(source.netMetrics)
        XCTAssertNotNil(metrics.connectMs, "a fresh socket always has a connect phase")
        XCTAssertGreaterThanOrEqual(metrics.connectMs ?? -1, 0)
        let serverStageMs = try XCTUnwrap(metrics.serverMs)
        XCTAssertGreaterThan(serverStageMs, 0, "the artificial response delay must show up as server time")
        XCTAssertFalse(metrics.networkProtocol.isEmpty)
        XCTAssertNotEqual(metrics.networkProtocol, "-", "a completed transaction always has a protocol name")
    }

    func testAFailedRequestIsRetriedAndTheReadStillSucceeds() throws {
        let body = makeBody(128 * 1024)
        let server = try startServer(body: body)
        server.failNextRequests = 1
        let source = makeSource(server, policy: policy(windowBytes: 1024 * 1024))

        let read = try self.read(source, upTo: 4096)
        XCTAssertEqual(read, body.subdata(in: 0..<4096))
        XCTAssertGreaterThanOrEqual(server.requestedRanges.count, 2, "the dropped connection was retried")
    }

    // MARK: - Remembered resolved URL (#193)

    /// S2: a server stream's URL carries a fresh session id and the token on every play (#822): the
    /// next play of the same song still reads the bytes the last one kept.
    func testARunIsFoundAgainUnderANewSessionIdAndToken() throws {
        let body = makeMediaBody(128 * 1024)
        let runEnd: Int64 = 32 * 1024
        let server = try startServer(body: body)
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("byte-source-key-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let store = CachedRunStore(directory: directory)
        func played(session: String, token: String) -> URL {
            URL(string: "\(server.url.absoluteString)?UserId=u&PlaySessionId=\(session)&AudioCodec=mp3&ApiKey=\(token)")!
        }
        let key = StreamCacheKey.key(for: played(session: "first", token: "t1"))
        store.replace(key, startingAt: 0, totalLength: Int64(body.count))
        XCTAssertTrue(store.append(key, body.prefix(Int(runEnd))))

        let made = HTTPRangeByteSource(
            url: played(session: "second", token: "t2"),
            authHeaders: [:],
            readAhead: policy(windowBytes: 64 * 1024),
            session: Self.testSession,
            tee: nil,
            runStore: store,
            resolvedURLs: nil
        )
        source = made

        XCTAssertEqual(try read(made, upTo: 4096), body.prefix(4096))
        let fromStart = server.requestHeads.filter { $0.contains("Range: bytes=0-") }
        XCTAssertTrue(fromStart.isEmpty, "the kept run was fetched again: \(fromStart)")
    }

    /// A play that walked the chain leaves its end in the cache, and the next play opens straight
    /// there: no `/redirect/*` request at all, and the first response says so.
    func testARememberedResolvedURLSkipsTheChainOnTheNextPlay() throws {
        let body = makeBody(64 * 1024)
        let server = try startServer(body: body)
        let original = server.redirectingURL(hops: 2)
        let cache = ResolvedURLCache(fileURL: nil)

        let firstPlay = makeSource(server, policy: policy(windowBytes: 1024 * 1024), url: original, resolvedURLs: cache)
        _ = try read(firstPlay, upTo: 4096)
        XCTAssertEqual(heads(server, path: "/redirect/2/fixture.mp3").count, 1)
        XCTAssertEqual(cache.resolved(for: original), server.url, "the chain's end was not recorded")
        XCTAssertEqual(firstPlay.firstResponse?.remembered, false)
        firstPlay.cancel()

        let secondPlay = makeSource(server, policy: policy(windowBytes: 1024 * 1024), url: original, resolvedURLs: cache)
        XCTAssertEqual(try read(secondPlay, upTo: 4096), body.prefix(4096))
        XCTAssertEqual(heads(server, path: "/redirect/2/fixture.mp3").count, 1, "the chain was walked again")
        XCTAssertEqual(heads(server, path: "/redirect/1/fixture.mp3").count, 1)
        let first = try XCTUnwrap(secondPlay.firstResponse)
        XCTAssertTrue(first.remembered)
        XCTAssertEqual(first.status, 206)
        XCTAssertEqual(first.redirects, 0)
        XCTAssertEqual(secondPlay.resolvedURLForTesting, server.url)
    }

    /// S2: the chain's end is remembered under the URL less its session id and token (#822), so the
    /// next play, with new ones, opens straight there.
    func testARememberedResolvedURLIsFoundAgainUnderANewSessionIdAndToken() throws {
        let body = makeBody(64 * 1024)
        let server = try startServer(body: body)
        let chain = server.redirectingURL(hops: 2).absoluteString
        let cache = ResolvedURLCache(fileURL: nil)
        func walks() -> Int { server.requestHeads.filter { $0.hasPrefix("GET /redirect/2/") }.count }

        let firstPlay = makeSource(
            server, policy: policy(windowBytes: 1024 * 1024),
            url: URL(string: "\(chain)?PlaySessionId=a&api_key=t1")!, resolvedURLs: cache
        )
        _ = try read(firstPlay, upTo: 4096)
        firstPlay.cancel()
        XCTAssertEqual(walks(), 1)

        let secondPlay = makeSource(
            server, policy: policy(windowBytes: 1024 * 1024),
            url: URL(string: "\(chain)?PlaySessionId=b&api_key=t2")!, resolvedURLs: cache
        )
        XCTAssertEqual(try read(secondPlay, upTo: 4096), body.prefix(4096))
        XCTAssertEqual(walks(), 1, "the chain was walked again")
        XCTAssertEqual(secondPlay.firstResponse?.remembered, true)
    }

    /// The signed CDN URL whose signature ran out: `403` from the remembered end is not a retry
    /// of that URL, it is one fallback to the original URL, which walks the chain to wherever it
    /// now leads, and the entry is replaced with that.
    func testARememberedURLThatAnswers403FallsBackToTheChainOnceAndRefreshesTheEntry() throws {
        let body = makeBody(64 * 1024)
        let server = try startServer(body: body)
        let original = server.redirectingURL(hops: 2)
        // Remembered: the chain used to end on the alternate host, which now rejects everything.
        let stale = URL(string: "http://localhost:\(server.port)\(LoopbackMediaServer.fixturePath)")!
        let cache = ResolvedURLCache(fileURL: nil)
        cache.record(original: original, resolved: stale)
        server.reject(host: "localhost:\(server.port)", status: 403)

        let source = makeSource(server, policy: policy(windowBytes: 1024 * 1024), url: original, resolvedURLs: cache)
        XCTAssertEqual(try read(source, upTo: 4096), body.prefix(4096))

        let rejected = heads(server, path: LoopbackMediaServer.fixturePath).filter { $0.contains("Host: localhost:") }
        XCTAssertEqual(rejected.count, 1, "the remembered URL must be tried once, not retried: \(rejected)")
        XCTAssertEqual(heads(server, path: "/redirect/2/fixture.mp3").count, 1, "the fallback walks the chain from the original URL")
        let first = try XCTUnwrap(source.firstResponse)
        XCTAssertFalse(first.remembered, "the first ACCEPTED response came from the chain")
        XCTAssertEqual(first.status, 206)
        XCTAssertEqual(first.redirects, 2)
        XCTAssertEqual(cache.resolved(for: original), server.url, "the entry must be refreshed to the chain's new end")
        XCTAssertEqual(source.resolvedURLForTesting, server.url)
    }

    /// The signed URL past its expiry on a host that answers a page about it: `200 text/html`
    /// from the remembered end. The same one fallback as a `403`: the original URL walks the
    /// chain, the entry is replaced, and not a byte of the page reaches the decoder.
    func testARememberedURLThatAnswersAPageFallsBackToTheChainOnceAndRefreshesTheEntry() throws {
        let body = makeBody(64 * 1024)
        let server = try startServer(body: body)
        let original = server.redirectingURL(hops: 2)
        let stale = URL(string: "http://localhost:\(server.port)\(LoopbackMediaServer.fixturePath)")!
        let cache = ResolvedURLCache(fileURL: nil)
        cache.record(original: original, resolved: stale)
        server.answerWithPage(host: "localhost:\(server.port)", body: Data("<!DOCTYPE html><html><body>This link has expired.</body></html>".utf8))
        let recorder = RecordingTee()

        let source = makeSource(server, policy: policy(windowBytes: 1024 * 1024), url: original, resolvedURLs: cache, tee: recorder)
        XCTAssertEqual(try read(source, upTo: 4096), body.prefix(4096))

        let rejected = heads(server, path: LoopbackMediaServer.fixturePath).filter { $0.contains("Host: localhost:") }
        XCTAssertEqual(rejected.count, 1, "the remembered URL must be tried once, not retried: \(rejected)")
        XCTAssertEqual(heads(server, path: "/redirect/2/fixture.mp3").count, 1, "the fallback walks the chain from the original URL")
        let first = try XCTUnwrap(source.firstResponse)
        XCTAssertFalse(first.remembered, "the page is nobody's first response")
        XCTAssertEqual(first.status, 206)
        XCTAssertEqual(cache.resolved(for: original), server.url, "the entry must be replaced by the chain's end")
        XCTAssertEqual(source.resolvedURLForTesting, server.url)
        XCTAssertEqual(source.transactionCount, 2, "the page's transaction and the chain's")
        XCTAssertEqual(recorder.opens.count, 1, "the page was never opened for the tee")
        for (offset, bytes) in recorder.chunks {
            XCTAssertEqual(bytes, body.subdata(in: Int(offset)..<Int(offset) + bytes.count), "page bytes reached the tee at \(offset)")
        }
    }

    /// The same page under a type that says nothing (`application/octet-stream`, or none at all)
    /// is caught by its first bytes instead: nothing the decoder plays starts with `<`.
    func testARememberedURLThatAnswersAPageAsOctetStreamIsCaughtByItsFirstBytes() throws {
        let body = makeBody(64 * 1024)
        let server = try startServer(body: body)
        let original = server.redirectingURL(hops: 2)
        let stale = URL(string: "http://localhost:\(server.port)\(LoopbackMediaServer.fixturePath)")!
        let cache = ResolvedURLCache(fileURL: nil)
        cache.record(original: original, resolved: stale)
        server.answerWithPage(
            host: "localhost:\(server.port)", contentType: "application/octet-stream",
            body: Data("<!DOCTYPE html><html><body>This link has expired.</body></html>".utf8)
        )
        let recorder = RecordingTee()

        let source = makeSource(server, policy: policy(windowBytes: 1024 * 1024), url: original, resolvedURLs: cache, tee: recorder)
        XCTAssertEqual(try read(source, upTo: 4096), body.prefix(4096))

        let rejected = heads(server, path: LoopbackMediaServer.fixturePath).filter { $0.contains("Host: localhost:") }
        XCTAssertEqual(rejected.count, 1, "the remembered URL must be tried once, not retried: \(rejected)")
        let first = try XCTUnwrap(source.firstResponse)
        XCTAssertFalse(first.remembered, "the page is nobody's first response")
        XCTAssertEqual(first.status, 206)
        XCTAssertEqual(cache.resolved(for: original), server.url, "the entry must be replaced by the chain's end")
        XCTAssertEqual(recorder.opens.count, 1, "the page was never opened for the tee")
        for (offset, bytes) in recorder.chunks {
            XCTAssertEqual(bytes, body.subdata(in: Int(offset)..<Int(offset) + bytes.count), "page bytes reached the tee at \(offset)")
        }
    }

    /// The same held response, this time genuinely audio, delivered in a first chunk far short of
    /// the 12 bytes ``HTTPRangeByteSource/looksLikeMedia(_:)`` needs: the sniff has to carry the
    /// held bytes across the chunk boundary and judge the whole of them, not just the first
    /// delivery, or a real audio body under a type that names nothing gets force-cancelled into a
    /// needless fallback (#226).
    func testARememberedURLThatAnswersRealAudioAsOctetStreamInAShortFirstChunkIsAccepted() throws {
        let body = makeMediaBody(64 * 1024)
        let server = try startServer(body: body)
        let original = server.redirectingURL(hops: 2)
        let stale = URL(string: "http://localhost:\(server.port)\(LoopbackMediaServer.fixturePath)")!
        let cache = ResolvedURLCache(fileURL: nil)
        cache.record(original: original, resolved: stale)
        server.answerWithPage(
            host: "localhost:\(server.port)", contentType: "application/octet-stream",
            body: body, firstChunkBytes: 4
        )
        let recorder = RecordingTee()

        let source = makeSource(server, policy: policy(windowBytes: 1024 * 1024), url: original, resolvedURLs: cache, tee: recorder)
        XCTAssertEqual(try read(source, upTo: 4096), body.prefix(4096))

        XCTAssertEqual(heads(server, path: "/redirect/2/fixture.mp3").count, 0,
                        "the sniff accepted the held response: the chain must never be walked")
        let first = try XCTUnwrap(source.firstResponse)
        XCTAssertTrue(first.remembered, "the accepted body is the remembered URL's own response")
        XCTAssertEqual(cache.resolved(for: original), stale, "the entry is kept, not replaced, when the remembered URL is accepted")
        XCTAssertEqual(recorder.opens.count, 1)
        for (offset, bytes) in recorder.chunks {
            XCTAssertEqual(bytes, body.subdata(in: Int(offset)..<Int(offset) + bytes.count), "bytes at \(offset) must match the body")
        }
        XCTAssertEqual(recorder.bytes.prefix(4096), body.prefix(4096), "no bytes lost across the sniff's chunk boundary")
    }

    /// A remembered host that cannot be reached at all is the same case as a `403`: one fallback
    /// to the original URL, not the transport retry a proven URL would get.
    func testARememberedURLThatFailsToConnectFallsBackToTheChain() throws {
        let body = makeBody(64 * 1024)
        let server = try startServer(body: body)
        let original = server.redirectingURL(hops: 1)
        // A port nothing listens on: connection refused, immediately.
        let closed = try LoopbackMediaServer(body: Data(count: 16), mimeType: "audio/mpeg")
        let dead = URL(string: "http://127.0.0.1:\(closed.port)\(LoopbackMediaServer.fixturePath)")!
        closed.stop()
        let cache = ResolvedURLCache(fileURL: nil)
        cache.record(original: original, resolved: dead)

        let source = makeSource(server, policy: policy(windowBytes: 1024 * 1024), url: original, resolvedURLs: cache)
        XCTAssertEqual(try read(source, upTo: 4096), body.prefix(4096))

        XCTAssertEqual(heads(server, path: "/redirect/1/fixture.mp3").count, 1)
        XCTAssertEqual(source.firstResponse?.remembered, false)
        XCTAssertEqual(cache.resolved(for: original), server.url)
    }

    /// The cache entry is a day old: not used, the chain is walked, and the walk re-records it.
    func testAnExpiredEntryIsIgnoredAndReplacedByTheWalk() throws {
        let body = makeBody(64 * 1024)
        let server = try startServer(body: body)
        let original = server.redirectingURL(hops: 1)
        var clock: TimeInterval = 1_000_000
        let cache = ResolvedURLCache(fileURL: nil, now: { clock })
        let stale = URL(string: "http://localhost:\(server.port)\(LoopbackMediaServer.fixturePath)")!
        cache.record(original: original, resolved: stale)
        clock += ResolvedURLCache.ttl + 1

        let source = makeSource(server, policy: policy(windowBytes: 1024 * 1024), url: original, resolvedURLs: cache)
        _ = try read(source, upTo: 4096)

        XCTAssertEqual(heads(server, path: "/redirect/1/fixture.mp3").count, 1, "an expired entry must not be trusted")
        XCTAssertTrue(heads(server, path: LoopbackMediaServer.fixturePath).allSatisfy { !$0.contains("Host: localhost:") })
        XCTAssertEqual(source.firstResponse?.remembered, false)
        XCTAssertEqual(cache.resolved(for: original), server.url)
    }

    /// The private-feed rule holds for a remembered end too: the feed's token goes to the
    /// original host only, so a remembered CDN on another host never sees it, and a remembered
    /// URL on the feed's own host still does.
    func testAuthHeadersFollowTheOriginalHostRuleForARememberedURL() throws {
        let body = makeBody(64 * 1024)
        let server = try startServer(body: body)
        let original = server.redirectingURL(hops: 1)
        let onOtherHost = URL(string: "http://localhost:\(server.port)\(LoopbackMediaServer.fixturePath)")!
        let cache = ResolvedURLCache(fileURL: nil)
        cache.record(original: original, resolved: onOtherHost)

        let crossHost = makeSource(
            server, policy: policy(windowBytes: 1024 * 1024),
            authHeaders: ["Authorization": "Bearer feed-token"], url: original, resolvedURLs: cache
        )
        _ = try read(crossHost, upTo: 4096)
        let landing = try XCTUnwrap(heads(server, path: LoopbackMediaServer.fixturePath).last)
        XCTAssertTrue(landing.contains("Host: localhost:"), landing)
        XCTAssertFalse(landing.contains("Authorization"), "the feed's token must not reach a remembered CDN: \(landing)")
        XCTAssertTrue(landing.contains("Range: bytes=0-"), landing)
        crossHost.cancel()

        cache.record(original: original, resolved: server.url)
        let sameHost = makeSource(
            server, policy: policy(windowBytes: 1024 * 1024),
            authHeaders: ["Authorization": "Bearer feed-token"], url: original, resolvedURLs: cache
        )
        _ = try read(sameHost, upTo: 4096)
        let sameHostLanding = try XCTUnwrap(heads(server, path: LoopbackMediaServer.fixturePath).last)
        XCTAssertTrue(sameHostLanding.contains("Host: 127.0.0.1:"), sameHostLanding)
        XCTAssertTrue(sameHostLanding.contains("Authorization: Bearer feed-token"), sameHostLanding)
        XCTAssertEqual(heads(server, path: "/redirect/1/fixture.mp3").count, 0, "neither play should have walked the chain")
    }

    // MARK: - The tail (#193)

    /// FFmpeg's mp3 open: read the head, seek to `size - 128` for an ID3v1 footer, seek back. The
    /// footer comes from the side fetch, so neither seek opens a transaction and the head's window
    /// is still there for the seek back.
    func testASeekIntoTheTailOpensNoTransactionAndKeepsTheWindow() throws {
        let body = makeBody(1024 * 1024)
        let total = Int64(body.count)
        let server = try startServer(body: body)
        let source = makeSource(server, policy: policy(windowBytes: 64 * 1024, backWindowBytes: 16 * 1024))

        XCTAssertEqual(try read(source, upTo: 4096), body.prefix(4096))
        XCTAssertTrue(waitUntil { source.tailStatus == .fetched }, "the side fetch lands beside the head")
        try source.seek(to: total - 128)
        XCTAssertEqual(try read(source, upTo: 128), body.suffix(128))
        XCTAssertEqual(try read(source, upTo: 16), Data(), "the tail ends at the end of the file")
        try source.seek(to: 4096)
        XCTAssertEqual(try read(source, upTo: 4096), body[4096..<8192])

        // The head, one side fetch, and at most the head's own continuations: nothing opened at
        // the tail as a transaction, nothing opened for the seek back into the window.
        let ranges = server.requestedRanges
        XCTAssertEqual(ranges.prefix(2), [0, total - 128], "\(ranges)")
        XCTAssertEqual(ranges.filter { $0 == total - 128 }.count, 1, "\(ranges)")
        XCTAssertFalse(ranges.contains(4096), "the seek back must be served from the window: \(ranges)")
        XCTAssertTrue(ranges.dropFirst(2).allSatisfy { $0 >= 64 * 1024 }, "only continuations past the first range: \(ranges)")
        XCTAssertEqual(source.bytesFetched, source.fetchFrontier, "the tail is not counted as fetched")
    }

    /// The side fetch dies: nothing waited on it, and a seek into the tail after that is the
    /// ordinary kind, a transaction at that offset, whose read gets its bytes.
    func testAFailedTailFetchLeavesTheSeekToATransaction() throws {
        let body = makeBody(1024 * 1024)
        let total = Int64(body.count)
        let server = try startServer(body: body)
        server.rejectNextRangeStartingAt = total - 128
        let source = makeSource(server, policy: policy(windowBytes: 64 * 1024, backWindowBytes: 16 * 1024))

        XCTAssertEqual(try read(source, upTo: 4096), body.prefix(4096))
        XCTAssertTrue(waitUntil { source.tailStatus == .dropped }, "the 503 lands")
        try source.seek(to: total - 128)
        XCTAssertEqual(try read(source, upTo: 128), body.suffix(128))
        let ranges = server.requestedRanges
        XCTAssertEqual(ranges.filter { $0 == total - 128 }.count, 2, "the rejected side fetch, then the seek's own transaction: \(ranges)")
        XCTAssertGreaterThanOrEqual(source.transactionCount, 2)
    }

    /// The side fetch is slow (#193 follow-up: on the phone it was a whole redirect chain, 4–10 s,
    /// with the head already on disk). The look FFmpeg takes at the footer is answered with
    /// end-of-stream at once, the window is untouched for the seek back, and the tail still lands
    /// for the next play. The probe never waits on the tail.
    func testASeekIntoATailStillInFlightIsAnsweredWithEndOfStreamNotAWait() throws {
        let body = makeBody(1024 * 1024)
        let total = Int64(body.count)
        let server = try startServer(body: body)
        server.delayForRangeStartingAt = (offset: total - 128, seconds: 2)
        let source = makeSource(server, policy: policy(windowBytes: 64 * 1024, backWindowBytes: 16 * 1024))

        XCTAssertEqual(try read(source, upTo: 4096), body.prefix(4096))
        XCTAssertTrue(waitUntil { server.requestedRanges.contains(total - 128) }, "the side fetch was fired beside the head")
        XCTAssertEqual(source.tailStatus, .pending)

        let began = Date()
        try source.seek(to: total - 128)
        XCTAssertEqual(try read(source, upTo: 128), Data(), "a footer that has not arrived is end-of-stream, not a wait")
        XCTAssertLessThan(Date().timeIntervalSince(began), 0.5, "the look must not block on the side fetch")
        XCTAssertEqual(source.tailStatus, .late)
        try source.seek(to: 4096)
        XCTAssertEqual(try read(source, upTo: 4096), body[4096..<8192], "the seek back is served from the window")

        let ranges = server.requestedRanges
        XCTAssertFalse(ranges.contains(4096), "no transaction for the seek back: \(ranges)")
        XCTAssertEqual(ranges.filter { $0 == total - 128 }.count, 1, "the look opened no transaction of its own: \(ranges)")
        XCTAssertTrue(ranges.dropFirst().allSatisfy { $0 == total - 128 || $0 >= 64 * 1024 }, "only the side fetch and the head's continuations: \(ranges)")
        XCTAssertEqual(source.tailStatus, .late, "late stays late: the probe went without it")
    }

    /// A body whose bounded range already reaches the end of the file needs no side fetch.
    func testNoTailFetchWhenTheFirstRangeReachesTheEnd() throws {
        let body = makeBody(64 * 1024)
        let server = try startServer(body: body)
        let source = makeSource(server, policy: policy(windowBytes: 1024 * 1024))

        XCTAssertEqual(try read(source, upTo: body.count), body)
        try source.seek(to: Int64(body.count) - 128)
        XCTAssertEqual(try read(source, upTo: 128), body.suffix(128))
        XCTAssertEqual(server.requestedRanges, [0])
    }

    /// S2: only an mp3 has the ID3v1 footer FFmpeg looks for: a body typed as another kind of audio
    /// opens no side fetch for it, so it never races the head on a second connection (#822).
    func testNoTailFetchForABodyTypedAsAnotherKindOfAudio() throws {
        let body = makeBody(512 * 1024)
        let flac = try LoopbackMediaServer(body: body, mimeType: "audio/flac")
        server = flac
        let source = makeSource(flac, policy: policy(windowBytes: 64 * 1024))

        XCTAssertEqual(try read(source, upTo: 4096), body.prefix(4096))
        Thread.sleep(forTimeInterval: 0.2)
        let total = Int64(body.count)
        XCTAssertFalse(flac.requestedRanges.contains(total - 128), "a tail fetch: \(flac.requestedRanges)")
    }

    func testAStreamsHeadOrTypeRulesOutMP3OnlyForAnotherContainer() {
        for magic in ["fLaC", "OggS", "RIFF", "wvpk", "MAC "] {
            XCTAssertTrue(HTTPRangeByteSource.headRulesOutMP3(Data((magic + "\0\0\0\0\0\0\0\0").utf8)), magic)
        }
        XCTAssertTrue(HTTPRangeByteSource.headRulesOutMP3(Data([0, 0, 0, 0x20] + Array("ftypM4A ".utf8))))
        XCTAssertFalse(HTTPRangeByteSource.headRulesOutMP3(Data("ID3\u{04}\0\0\0\0\0\0\0\0".utf8)), "ID3v2")
        XCTAssertFalse(HTTPRangeByteSource.headRulesOutMP3(Data([0xFF, 0xFB, 0x90, 0x64])), "a bare frame header")
        XCTAssertFalse(HTTPRangeByteSource.headRulesOutMP3(Data("fL".utf8)), "too short to tell")

        for type in ["audio/flac", "audio/mp4", "AUDIO/OGG", "application/ogg", "audio/x-wav"] {
            XCTAssertTrue(HTTPRangeByteSource.typeRulesOutMP3(type), type)
        }
        for type in ["audio/mpeg", "audio/MP3", "audio/x-mpeg-3", "application/octet-stream", nil] as [String?] {
            XCTAssertFalse(HTTPRangeByteSource.typeRulesOutMP3(type), type ?? "nil")
        }
    }

    // MARK: - Superseded transactions

    /// A seek resets the window on the caller's thread; the chunks of the body it supersedes are
    /// already on the queue, ahead of the seek's own transaction, and still belong to `task`. On
    /// task identity alone they would land at the new window's frontier, at an offset they were
    /// never at, and reach the tee labelled with it (#224). Loopback delivers a head and its first
    /// chunk together, so no hold on the queue can get between them; the chunk is held at the
    /// source's door instead, and the seek lands while it waits there.
    func testASupersededBodysBytesNeverReachTheSeeksWindow() throws {
        let body = makeBody(512 * 1024)
        let server = try startServer(body: body)
        let recorder = RecordingTee()
        let made = HTTPRangeByteSource(
            url: server.url,
            authHeaders: [:],
            readAhead: policy(windowBytes: 64 * 1024, backWindowBytes: 16 * 1024),
            session: Self.testSession,
            tee: recorder,
            runStore: nil,
            resolvedURLs: nil
        )
        source = made

        let chunkArrived = DispatchSemaphore(value: 0)
        let chunkReleased = DispatchSemaphore(value: 0)
        made.testBeforeNextChunk = {
            chunkArrived.signal()
            chunkReleased.wait()
        }
        try made.seek(to: 100)
        XCTAssertEqual(chunkArrived.wait(timeout: .now() + 5), .success, "the first body's first chunk never arrived")
        // The chunk is on the queue, held before it has looked at the window. The seek resets the
        // window here, on this thread, and queues its transaction BEHIND the chunk.
        let target: Int64 = 300 * 1024
        try made.seek(to: target)
        chunkReleased.signal()

        XCTAssertEqual(try read(made, upTo: 4096), body.subdata(in: Int(target)..<Int(target) + 4096),
                       "the seek's bytes, not the first body's")
        XCTAssertTrue(waitUntil { server.requestedRanges.count >= 2 })
        XCTAssertEqual(server.requestedRanges, [100, target], "one transaction per seek, nothing for the stale body: \(server.requestedRanges)")
        for (offset, bytes) in recorder.chunks {
            XCTAssertEqual(bytes, body.subdata(in: Int(offset)..<Int(offset) + bytes.count),
                           "the tee was handed bytes at an offset they were never at (\(offset), \(bytes.count) bytes)")
        }
        XCTAssertTrue(recorder.chunks.allSatisfy { $0.offset >= target }, "the first body's bytes reached the tee after the seek: \(recorder.chunks.map(\.offset))")
    }

    /// FFmpeg's ID3v1 look served off disk, then the seek back (#315). The pump appends the footer
    /// and hands it to the tee before it calls the stream over, so the decoder can read it and seek
    /// back in between. The end-of-stream it then declares belonged to the footer's window; left on
    /// the seek's, it answered the probe's next read with EOF and the load failed as unprobeable.
    /// The tee holds the pump at that point while the seek lands, and the seek's own transaction is
    /// held at its door while the read is taken, so the stale verdict, if there is one, is there.
    func testAFooterServedFromDiskLeavesNoEndOfStreamOnTheSeekBack() throws {
        let body = makeMediaBody(256 * 1024)
        let total = Int64(body.count)
        let server = try startServer(body: body)
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("byte-source-disk-tail-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let store = CachedRunStore(directory: directory)
        let key = server.url.absoluteString
        // The whole episode on disk, and no tail in the sidecar: what a first play of a file smaller
        // than the first window leaves, when the footer look beat the head to the end.
        store.replace(key, startingAt: 0, totalLength: total)
        XCTAssertTrue(store.append(key, body))
        XCTAssertNil(store.tail(for: key))

        let footerTeed = DispatchSemaphore(value: 0)
        let footerReleased = DispatchSemaphore(value: 0)
        let tee = HoldingTee(holdAt: total - 128, arrived: footerTeed, released: footerReleased)
        let made = HTTPRangeByteSource(
            url: server.url,
            authHeaders: [:],
            readAhead: policy(windowBytes: 16 * 1024, backWindowBytes: 16 * 1024),
            session: Self.testSession,
            tee: tee,
            runStore: store,
            resolvedURLs: nil
        )
        source = made
        let seekBack: Int64 = 105
        let seekBackOpening = DispatchSemaphore(value: 0)
        let seekBackReleased = DispatchSemaphore(value: 0)
        made.testBeforeTransaction = { offset in
            guard offset == seekBack else { return }
            seekBackOpening.signal()
            seekBackReleased.wait()
        }
        defer {
            footerReleased.signal()
            seekBackReleased.signal()
        }

        try made.seek(to: total - 128)
        XCTAssertEqual(footerTeed.wait(timeout: .now() + 5), .success, "the footer was never pumped off disk")
        // The pump is parked in the tee with the footer already in the window.
        XCTAssertEqual(try read(made, upTo: 128), body.suffix(128))
        try made.seek(to: seekBack)
        footerReleased.signal()
        XCTAssertEqual(seekBackOpening.wait(timeout: .now() + 5), .success, "the seek back never opened")

        // Everything ahead of the seek's transaction has run. Its bytes are not in yet, so this read
        // has to wait for them: the release comes from another thread, after it has started.
        DispatchQueue.global().asyncAfter(deadline: .now() + 0.3) { seekBackReleased.signal() }
        XCTAssertEqual(
            try read(made, upTo: 4096),
            body.subdata(in: Int(seekBack)..<Int(seekBack) + 4096),
            "the seek back was answered with the footer's end-of-stream"
        )
        XCTAssertEqual(server.requestedRanges, [], "every byte was on disk")
    }

    /// A run used up, and the network picked up at its end, with a seek landing in between (#318).
    /// The throttle reads the frontier and decides to resume; the seek resets the window on the
    /// caller's thread before the continuation opens. Opened anyway, that continuation adopts the
    /// seek's window and asks the host for bytes at the run's end to fill it. The continuation is
    /// held at its door while the seek lands, and the seek's own transaction must be the first
    /// thing opened against the window it reset.
    func testAResumeAtTheRunsEndOpensNothingOnTheSeeksWindow() throws {
        let body = makeMediaBody(256 * 1024)
        let total = Int64(body.count)
        let runEnd: Int64 = 32 * 1024
        let server = try startServer(body: body)
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("byte-source-resume-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let store = CachedRunStore(directory: directory)
        let key = server.url.absoluteString
        store.replace(key, startingAt: 0, totalLength: total)
        XCTAssertTrue(store.append(key, body.prefix(Int(runEnd))))

        let made = HTTPRangeByteSource(
            url: server.url,
            authHeaders: [:],
            readAhead: policy(windowBytes: 64 * 1024, backWindowBytes: 16 * 1024),
            session: Self.testSession,
            tee: nil,
            runStore: store,
            resolvedURLs: nil
        )
        source = made
        let target: Int64 = 100 * 1024
        let resumeOpening = DispatchSemaphore(value: 0)
        let resumeReleased = DispatchSemaphore(value: 0)
        let seekOpening = DispatchSemaphore(value: 0)
        let lock = NSLock()
        var heldResume = false
        var openedBeforeTheSeeks: Int?
        made.testBeforeTransaction = { [weak made] offset in
            if offset == runEnd {
                lock.lock()
                let hold = !heldResume
                heldResume = true
                lock.unlock()
                guard hold else { return }
                resumeOpening.signal()
                resumeReleased.wait()
            } else if offset == target {
                lock.lock()
                if openedBeforeTheSeeks == nil { openedBeforeTheSeeks = made?.transactionCount }
                lock.unlock()
                seekOpening.signal()
            }
        }
        defer { resumeReleased.signal() }

        XCTAssertEqual(try read(made, upTo: 4096), body.prefix(4096))
        XCTAssertEqual(resumeOpening.wait(timeout: .now() + 5), .success, "the run's end never resumed")
        let openedBeforeTheResume = made.transactionCount
        try made.seek(to: target)
        resumeReleased.signal()
        XCTAssertEqual(seekOpening.wait(timeout: .now() + 5), .success, "the seek never opened")

        lock.lock()
        let opened = openedBeforeTheSeeks
        lock.unlock()
        XCTAssertEqual(opened, openedBeforeTheResume,
                       "a continuation at the run's end was opened on the seek's window ahead of the seek's own transaction")
        XCTAssertEqual(try read(made, upTo: 4096), body.subdata(in: Int(target)..<Int(target) + 4096))
    }

    /// The same resume from the network completion path (#318): a bounded `Range` runs out short of
    /// the end and the body is picked up at its frontier, with the seek landing between the close's
    /// guard and the continuation's open.
    func testAResumeAfterABoundedRangeOpensNothingOnTheSeeksWindow() throws {
        let body = makeBody(512 * 1024)
        let windowBytes: Int64 = 64 * 1024
        let server = try startServer(body: body)
        let made = makeSource(server, policy: policy(windowBytes: windowBytes, backWindowBytes: 16 * 1024))
        let target: Int64 = 300 * 1024
        let resumeOpening = DispatchSemaphore(value: 0)
        let resumeReleased = DispatchSemaphore(value: 0)
        let seekOpening = DispatchSemaphore(value: 0)
        let lock = NSLock()
        var heldResume = false
        var openedBeforeTheSeeks: Int?
        made.testBeforeTransaction = { [weak made] offset in
            if offset > 0, offset < target {
                lock.lock()
                let hold = !heldResume
                heldResume = true
                lock.unlock()
                guard hold else { return }
                resumeOpening.signal()
                resumeReleased.wait()
            } else if offset == target {
                lock.lock()
                if openedBeforeTheSeeks == nil { openedBeforeTheSeeks = made?.transactionCount }
                lock.unlock()
                seekOpening.signal()
            }
        }
        defer { resumeReleased.signal() }

        // Reading on past the first range's end is what leaves the ceiling room to resume into.
        XCTAssertEqual(try read(made, upTo: 16 * 1024), body.prefix(16 * 1024))
        XCTAssertEqual(resumeOpening.wait(timeout: .now() + 5), .success, "the bounded range never resumed")
        let openedBeforeTheResume = made.transactionCount
        try made.seek(to: target)
        resumeReleased.signal()
        XCTAssertEqual(seekOpening.wait(timeout: .now() + 5), .success, "the seek never opened")

        lock.lock()
        let opened = openedBeforeTheSeeks
        lock.unlock()
        XCTAssertEqual(opened, openedBeforeTheResume,
                       "a continuation at the stale frontier was opened on the seek's window ahead of the seek's own transaction")
        XCTAssertEqual(try read(made, upTo: 4096), body.subdata(in: Int(target)..<Int(target) + 4096))
    }

    // MARK: - The shared session's delegate queue

    /// A source whose own queue is busy — held here at the door of its first chunk, as a run
    /// write or a seam compare would hold it — must not hold the session's one delegate queue,
    /// and with it every other source's bytes (#225 follow-up). Deterministic rather than timed:
    /// the stalled source is released only AFTER the other's first bytes have been read, so a
    /// hop that blocked the delegate queue would never let that read complete at all.
    func testASourceStalledOnItsOwnQueueDoesNotHoldAnotherSourcesBytes() throws {
        let slowBody = makeBody(256 * 1024)
        let fastBody = makeBody(64 * 1024)
        let slowServer = try startServer(body: slowBody)
        let fastServer = try LoopbackMediaServer(body: fastBody, mimeType: "audio/mpeg")
        defer { fastServer.stop() }
        let slow = makeSource(slowServer, policy: policy(windowBytes: 1024 * 1024))
        let fast = HTTPRangeByteSource(
            url: fastServer.url,
            authHeaders: [:],
            readAhead: policy(windowBytes: 1024 * 1024),
            session: Self.testSession,
            tee: nil,
            runStore: nil,
            resolvedURLs: nil
        )
        defer { fast.cancel() }

        let chunkArrived = DispatchSemaphore(value: 0)
        let chunkReleased = DispatchSemaphore(value: 0)
        slow.testBeforeNextChunk = {
            chunkArrived.signal()
            chunkReleased.wait()
        }
        try slow.seek(to: 1)
        XCTAssertEqual(chunkArrived.wait(timeout: .now() + 5), .success, "the slow source's first chunk never arrived")

        // The slow source's queue is now parked inside a chunk. The fast source opens, answers its
        // response and takes its first bytes with that queue still parked.
        let fastRead = expectation(description: "the fast source's first bytes")
        var got = Data()
        let reader = Thread {
            got = (try? self.read(fast, upTo: 4096)) ?? Data()
            fastRead.fulfill()
        }
        reader.start()
        wait(for: [fastRead], timeout: 5)
        XCTAssertEqual(got, fastBody.prefix(4096), "the fast source's bytes waited on the slow source's queue")

        chunkReleased.signal()
        XCTAssertEqual(try read(slow, upTo: slowBody.count - 1), slowBody.dropFirst(), "the slow source still gets its whole body, in order")
    }

    /// With the hop asynchronous the session no longer waits for a chunk to be handled before it
    /// hands over the next, so a body that outruns the source's queue piles up on the hop. The
    /// in-flight budget suspends the task from the delegate queue when the pile crosses the
    /// high-water mark, and what piled up is still handed on in order once the queue is free.
    func testABodyThatOutrunsTheQueueIsHeldAtTheBudgetAndDrainedInOrder() throws {
        let body = makeBody(16 * 1024 * 1024)
        let server = try startServer(body: body)
        let recorder = RecordingTee()
        let made = HTTPRangeByteSource(
            url: server.url,
            authHeaders: [:],
            // One transaction for the whole body, so the budget — not the `Range` — is the bound.
            readAhead: policy(windowBytes: 32 * 1024 * 1024),
            session: Self.testSession,
            tee: recorder,
            runStore: nil,
            resolvedURLs: nil
        )
        source = made

        let chunkArrived = DispatchSemaphore(value: 0)
        let chunkReleased = DispatchSemaphore(value: 0)
        made.testBeforeNextChunk = {
            chunkArrived.signal()
            chunkReleased.wait()
        }
        try made.seek(to: 1)
        XCTAssertEqual(chunkArrived.wait(timeout: .now() + 5), .success, "the first chunk never arrived")
        // Loopback hands the rest of the body over as fast as the delegate queue will take it;
        // with the source's queue parked, that is the budget's job to stop.
        XCTAssertTrue(waitUntil { made.inboundPauseCountForTesting >= 1 }, "the budget never suspended the body")
        let peak = made.peakInFlightBytesForTesting
        XCTAssertGreaterThanOrEqual(peak, HTTPRangeByteSource.inFlightHighWaterBytesForTesting)
        // `suspend()` stops the socket being read, not what CFNetwork had already read: on
        // loopback up to ~2 MiB more lands after the mark (2026-09-21, four runs: 0.55–2.3 MiB).
        // The bound is the mark plus that, never the body.
        XCTAssertLessThan(peak, 4 * 1024 * 1024, "the body kept arriving after the budget suspended it: \(peak) bytes in flight")

        chunkReleased.signal()
        XCTAssertEqual(try read(made, upTo: body.count - 1), body.dropFirst())
        XCTAssertTrue(waitUntil { recorder.opens.count == recorder.closes.count })
        XCTAssertEqual(recorder.bytes, body.dropFirst(), "every byte, once, in the order it was enqueued")
        XCTAssertEqual(recorder.chunks.first?.offset, 1)
        var expected: Int64 = 1
        for (offset, bytes) in recorder.chunks {
            XCTAssertEqual(offset, expected, "a chunk landed out of order")
            expected += Int64(bytes.count)
        }
        XCTAssertEqual(made.inFlightBytesForTesting, 0, "every enqueued chunk was accounted for on its turn")
    }

    /// The budget counts the paced body's bytes, not the tail fetch's (#261): the side request is
    /// never the paced task, so before the fix its chunk was added to the counter but could never
    /// trip the gate — the chunk that crossed the mark left the body unsuspended and the bound
    /// silently exceeded. Here the head's parked backlog stops one tail chunk short of the mark,
    /// so that chunk is exactly the bytes that would cross it: counted, the peak lands on the mark
    /// with nothing suspended; excluded, it stays where the head left it.
    ///
    /// The shape is dictated by two facts, both seen in a trace. The session hands a response to
    /// the delegate only together with the first bytes of its body, so the tail (fired from the
    /// head's response) cannot be answered before the head's first chunk is on the queue; and
    /// its disposition is answered from that same queue, so a queue parked by the head's first
    /// chunk strands the tail's response and its body never arrives. Both bodies are therefore
    /// split by the server and released by the test: the head's first chunk and the tail's first
    /// byte go through a free queue, the park is set at the head's second chunk, and the rest of
    /// the tail is written only once that park holds — the moment the counter can be observed
    /// with both in flight.
    func testTheTailFetchsBytesAreNotCountedAgainstTheInFlightBudget() throws {
        let body = makeBody(1024 * 1024)
        let total = Int64(body.count)
        let server = try startServer(body: body)
        let tailStart = total - Int64(HTTPRangeByteSource.tailBytes)
        let headFirstChunk = 4096
        let tailFirstChunk = 1
        let tailRest = HTTPRangeByteSource.tailBytes - tailFirstChunk
        // What the park holds in flight: one tail remainder short of the high-water mark.
        let parked = HTTPRangeByteSource.inFlightHighWaterBytesForTesting - tailRest
        let window = Int64(headFirstChunk + parked)
        server.heldBodyAfterBytesForRangeStartingAt = [0: headFirstChunk, tailStart: tailFirstChunk]
        let made = HTTPRangeByteSource(
            url: server.url,
            authHeaders: [:],
            readAhead: policy(windowBytes: window),
            session: Self.testSession,
            tee: nil,
            runStore: nil,
            resolvedURLs: nil
        )
        source = made

        // A read from 0 — not a seek — because the side fetch is only fired beside a transaction
        // that starts at 0. It is satisfied by the head's first chunk and never waits on the rest.
        let reader = Thread {
            _ = try? self.read(made, upTo: headFirstChunk)
        }
        reader.start()
        // The head's first chunk and the tail's first byte have both had their turn: the tail's
        // disposition is answered and nothing is on the queue.
        XCTAssertTrue(
            waitUntil { made.tailBytesEnqueuedForTesting == tailFirstChunk && made.bytesFetched == Int64(headFirstChunk) && made.inFlightBytesForTesting == 0 },
            "the first chunks did not both land: tail \(made.tailBytesEnqueuedForTesting), fetched \(made.bytesFetched), in flight \(made.inFlightBytesForTesting)"
        )

        // Park the queue at the head's next chunk, then let it come.
        let chunkArrived = DispatchSemaphore(value: 0)
        let chunkReleased = DispatchSemaphore(value: 0)
        made.testBeforeNextChunk = {
            chunkArrived.signal()
            chunkReleased.wait()
        }
        server.releaseHeldBody(forRangeStartingAt: 0)
        XCTAssertEqual(chunkArrived.wait(timeout: .now() + 5), .success, "the head's second chunk never arrived")
        XCTAssertTrue(waitUntil { made.inFlightBytesForTesting == parked }, "the head's backlog is not all on the hop: \(made.inFlightBytesForTesting)")

        // Now the rest of the tail, into a parked queue: enqueued, and not dequeued until the park lifts.
        server.releaseHeldBody(forRangeStartingAt: tailStart)
        XCTAssertTrue(waitUntil { made.tailBytesEnqueuedForTesting == HTTPRangeByteSource.tailBytes }, "the tail's remainder never reached the delegate: \(made.tailBytesEnqueuedForTesting)")

        XCTAssertEqual(made.peakInFlightBytesForTesting, parked, "the tail's bytes were counted against the budget")
        XCTAssertEqual(made.inFlightBytesForTesting, parked, "the tail's bytes were counted against the budget")
        XCTAssertEqual(made.inboundPauseCountForTesting, 0, "nothing crossed the mark")

        chunkReleased.signal()
        XCTAssertTrue(waitUntil { made.inFlightBytesForTesting == 0 }, "every counted chunk was accounted for on its turn")
        XCTAssertTrue(waitUntil { made.tailStatus == .fetched }, "the side fetch still lands")
    }

    /// A cancel lands on the source's queue behind whatever chunks the hop has already queued.
    /// Those chunks and the cancelled task's own close run after it; none of them may touch the
    /// tee, open a retry or reach the reader.
    func testACancelledSourceHandsNothingOnAfterTheCancelLands() throws {
        let body = makeBody(1024 * 1024)
        let server = try startServer(body: body)
        let recorder = RecordingTee()
        let made = HTTPRangeByteSource(
            url: server.url,
            authHeaders: [:],
            readAhead: policy(windowBytes: 2 * 1024 * 1024),
            session: Self.testSession,
            tee: recorder,
            runStore: nil,
            resolvedURLs: nil
        )
        source = made

        let chunkArrived = DispatchSemaphore(value: 0)
        let chunkReleased = DispatchSemaphore(value: 0)
        made.testBeforeNextChunk = {
            chunkArrived.signal()
            chunkReleased.wait()
        }
        try made.seek(to: 1)
        XCTAssertEqual(chunkArrived.wait(timeout: .now() + 5), .success, "the first chunk never arrived")
        // Cancelled with the first chunk held and the rest of the body queued behind it.
        made.cancel()
        chunkReleased.signal()
        made.drainQueueForTesting()

        // Everything the hop had queued before the cancel has run. From here nothing arrives.
        let teed = recorder.chunks.count
        let fetched = made.bytesFetched
        Thread.sleep(forTimeInterval: 0.3)
        made.drainQueueForTesting()
        XCTAssertEqual(recorder.chunks.count, teed, "the tee was handed bytes after the cancel landed")
        XCTAssertEqual(made.bytesFetched, fetched, "the window took bytes after the cancel landed")
        XCTAssertEqual(server.requestedRanges, [1], "the cancelled task's close opened another transaction")
        XCTAssertEqual(made.inFlightBytesForTesting, 0)
        var scratch = [UInt8](repeating: 0, count: 16)
        XCTAssertThrowsError(try scratch.withUnsafeMutableBytes { raw -> Int in
            try made.read(into: raw.baseAddress!, maxLength: 16)
        }) { XCTAssertEqual($0 as? StreamByteReaderError, .cancelled) }
    }
}

/// Parks the byte source's queue in the tee the first time it is handed bytes at `holdAt`: the
/// bytes are in the window by then, and whatever the source does after the tee call has not run.
private final class HoldingTee: AudioByteTee {

    private let holdAt: Int64
    private let arrived: DispatchSemaphore
    private let released: DispatchSemaphore
    private let lock = NSLock()
    private var held = false

    init(holdAt: Int64, arrived: DispatchSemaphore, released: DispatchSemaphore) {
        self.holdAt = holdAt
        self.arrived = arrived
        self.released = released
    }

    func byteSourceDidStart(readAhead: any ReadAheadControl) {}
    func playerWillSeek(toMs ms: Int64, generation: Int) {}
    func byteSourceDidOpenTransaction(startByte: Int64, totalBytes: Int64?, isContinuation: Bool, seekGeneration: Int) {}
    func byteSourceDidCloseTransaction(endedAtByte: Int64) {}
    func byteSource(didLearnTotalBytes: Int64) {}

    func byteSource(didReceive bytes: Data, at offset: Int64) {
        lock.lock()
        let hold = offset == holdAt && !held
        if hold { held = true }
        lock.unlock()
        guard hold else { return }
        arrived.signal()
        released.wait()
    }
}

/// Records every tee callback, off the byte source's own queue.
private final class RecordingTee: AudioByteTee {

    func byteSourceDidStart(readAhead: any ReadAheadControl) {}
    func playerWillSeek(toMs ms: Int64, generation: Int) {}

    struct Open {
        let startByte: Int64
        let totalBytes: Int64?
        let isContinuation: Bool
        let seekGeneration: Int
    }

    private let lock = NSLock()
    private var _opens: [Open] = []
    private var _closes: [Int64] = []
    private var _bytes = Data()
    private var _offsets: [(Int64, Int)] = []
    private var _chunks: [(offset: Int64, bytes: Data)] = []
    private var _learnedTotals: [Int64] = []

    var opens: [Open] { lock.lock(); defer { lock.unlock() }; return _opens }
    var closes: [Int64] { lock.lock(); defer { lock.unlock() }; return _closes }
    var bytes: Data { lock.lock(); defer { lock.unlock() }; return _bytes }
    /// Every chunk with the offset it was labelled with, so a test can check the label against
    /// the body.
    var chunks: [(offset: Int64, bytes: Data)] { lock.lock(); defer { lock.unlock() }; return _chunks }
    var learnedTotals: [Int64] { lock.lock(); defer { lock.unlock() }; return _learnedTotals }

    var offsetsAreContiguousFromZero: Bool {
        lock.lock()
        defer { lock.unlock() }
        var expected: Int64 = 0
        for (offset, count) in _offsets {
            if offset != expected { return false }
            expected += Int64(count)
        }
        return true
    }

    func byteSourceDidOpenTransaction(
        startByte: Int64,
        totalBytes: Int64?,
        isContinuation: Bool,
        seekGeneration: Int
    ) {
        lock.lock()
        _opens.append(
            Open(
                startByte: startByte,
                totalBytes: totalBytes,
                isContinuation: isContinuation,
                seekGeneration: seekGeneration
            )
        )
        lock.unlock()
    }

    func byteSource(didReceive bytes: Data, at offset: Int64) {
        lock.lock()
        _bytes.append(bytes)
        _offsets.append((offset, bytes.count))
        _chunks.append((offset, bytes))
        lock.unlock()
    }

    func byteSourceDidCloseTransaction(endedAtByte: Int64) {
        lock.lock()
        _closes.append(endedAtByte)
        lock.unlock()
    }

    func byteSource(didLearnTotalBytes: Int64) {
        lock.lock()
        _learnedTotals.append(didLearnTotalBytes)
        lock.unlock()
    }
}
