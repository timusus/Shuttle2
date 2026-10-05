import XCTest
@testable import S2Playback
import S2PlaybackTestSupport

/// How ``HTTPRangeByteSource`` gets the bytes moving again on a network that stopped bringing them
/// (#896): the stall watchdog, the retry budget and its backoff, and a path change. Over the real
/// loopback origin, as the rest of the byte layer is; only time and the path are faked, so a five
/// second stall takes no five seconds and a Wi-Fi to cellular handoff happens on cue.
final class StreamRecoveryTests: XCTestCase {

    private var server: LoopbackMediaServer?
    private var source: HTTPRangeByteSource?
    private var scheduler = FakeRecoveryScheduler()
    private var paths = FakeNetworkPathMonitor()

    override func tearDown() {
        source?.cancel()
        source = nil
        server?.stop()
        server = nil
        super.tearDown()
    }

    // MARK: - Fixtures

    private func makeBody(_ count: Int) -> Data {
        var state: UInt64 = 0x9E3779B97F4A7C15
        return Data((0..<count).map { _ in
            state = state &* 6364136223846793005 &+ 1442695040888963407
            return UInt8((state >> 33) & 0xFF)
        })
    }

    private func startServer(body: Data) throws -> LoopbackMediaServer {
        let started = try LoopbackMediaServer(body: body, mimeType: "audio/mpeg")
        server = started
        return started
    }

    private func makeSource(_ server: LoopbackMediaServer, windowBytes: Int64) -> HTTPRangeByteSource {
        let made = HTTPRangeByteSource(
            url: server.url,
            authHeaders: [:],
            readAhead: ReadAheadPolicy(
                windowSeconds: 60,
                appetiteWindowSeconds: 80,
                windowBytes: windowBytes,
                appetiteWindowBytes: windowBytes,
                minWindowBytes: 4 * 1024,
                backWindowBytes: 512 * 1024
            ),
            session: HTTPRangeByteSourceTests.testSession,
            runStore: nil,
            resolvedURLs: nil,
            scheduler: scheduler,
            pathMonitor: paths
        )
        source = made
        return made
    }

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

    /// A read on another thread, for the tests whose reader stays blocked while time is stepped.
    private func readInBackground(_ source: HTTPRangeByteSource, upTo count: Int) -> () -> Result<Data, Error>? {
        let lock = NSLock()
        var result: Result<Data, Error>?
        DispatchQueue.global().async {
            let outcome = Result { try self.read(source, upTo: count) }
            lock.withLock { result = outcome }
        }
        return { lock.withLock { result } }
    }

    private func waitUntil(_ timeout: TimeInterval = 5, _ condition: () -> Bool) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if condition() { return true }
            Thread.sleep(forTimeInterval: 0.02)
        }
        return condition()
    }

    /// Step the clock a second at a time, as the watchdog looks, until a stall has been dropped and
    /// its retry is waiting out the backoff. One step at a time, so the retry is never stepped past.
    private func advanceUntilStallRetry(_ source: HTTPRangeByteSource) -> Bool {
        for _ in 0...Int(HTTPRangeByteSource.stallTimeoutSeconds) + 1 {
            scheduler.advance(by: 1)
            if source.retryPendingForTesting { return true }
        }
        return false
    }

    // MARK: - Stall watchdog

    func testABodyThatGoesQuietBelowTheCeilingIsReopenedAtTheFrontier() throws {
        let body = makeBody(256 * 1024)
        let server = try startServer(body: body)
        let stallAt = 16 * 1024
        server.stallsAfterBodyBytes = stallAt
        let source = makeSource(server, windowBytes: 1024 * 1024)

        XCTAssertEqual(try read(source, upTo: stallAt), body.prefix(stallAt))
        server.stallsAfterBodyBytes = nil
        source.drainQueueForTesting()

        scheduler.advance(by: HTTPRangeByteSource.stallTimeoutSeconds - 1)
        source.drainQueueForTesting()
        XCTAssertEqual(server.requestedRanges, [0], "reopened before the stall timeout")

        scheduler.advance(by: 1)
        XCTAssertTrue(source.retryPendingForTesting, "the stall was never noticed")
        XCTAssertEqual(source.retryAttemptsForTesting, 1, "a stall is not a retry like any other")
        scheduler.advance(by: HTTPRangeByteSource.retryBackoff(attempt: 1))
        XCTAssertTrue(waitUntil { server.requestedRanges.count == 2 }, "the stall was never retried")
        XCTAssertEqual(server.requestedRanges, [0, Int64(stallAt)], "not re-ranged from the frontier")
        XCTAssertEqual(try read(source, upTo: body.count - stallAt), body.suffix(from: stallAt))
        XCTAssertEqual(source.retryAttemptsForTesting, 0, "bytes past the frontier did not give the budget back")
    }

    func testAHostThatNeverAnswersFailsTheReadOnceTheBudgetIsSpent() throws {
        let server = try startServer(body: makeBody(64 * 1024))
        // Accepts every request and never says a word: the stall, not the request timeout, is
        // what sees it, so the stall has to spend the budget.
        server.holdsOffsetZero = true
        defer { server.releaseOffsetZero() }
        let source = makeSource(server, windowBytes: 1024 * 1024)

        let result = readInBackground(source, upTo: 4096)
        XCTAssertTrue(waitUntil { server.requestedRanges.count == 1 })
        for attempt in 1...HTTPRangeByteSource.maxRetryAttempts {
            XCTAssertTrue(advanceUntilStallRetry(source), "stall \(attempt) was not retried")
            XCTAssertEqual(source.retryAttemptsForTesting, attempt)
            scheduler.advance(by: HTTPRangeByteSource.retryBackoff(attempt: attempt))
            XCTAssertTrue(waitUntil { server.requestedRanges.count == attempt + 1 }, "retry \(attempt) never reached the host")
        }
        XCTAssertFalse(advanceUntilStallRetry(source), "a stall was retried past the budget")
        XCTAssertTrue(waitUntil { result() != nil }, "the read outlived the budget")
        guard case .failure(StreamByteReaderError.transport(_)) = try XCTUnwrap(result()) else {
            return XCTFail("expected a transport failure, got \(String(describing: result()))")
        }

        // Failed is failed: the watchdog opens nothing more.
        scheduler.advance(by: 10 * HTTPRangeByteSource.stallTimeoutSeconds)
        source.drainQueueForTesting()
        XCTAssertEqual(server.requestedRanges.count, HTTPRangeByteSource.maxRetryAttempts + 1)
    }

    func testARangeIgnoringHostCannotGiveTheBudgetBackWithItsPrefix() throws {
        let body = makeBody(256 * 1024)
        let server = try startServer(body: body)
        server.respondsWholeBodyIgnoringRange = true
        let reached = 32 * 1024
        server.stallsAfterBodyBytes = reached
        let source = makeSource(server, windowBytes: 1024 * 1024)
        XCTAssertEqual(try read(source, upTo: reached), body.prefix(reached))

        // Every reopen is answered from 0 and goes quiet short of where the first body got to: bytes
        // arrive, the frontier moves, and nothing new is fetched.
        let prefix = reached / 2
        server.stallsAfterBodyBytes = prefix
        let result = readInBackground(source, upTo: 4096)
        for attempt in 1...HTTPRangeByteSource.maxRetryAttempts {
            XCTAssertTrue(advanceUntilStallRetry(source), "stall \(attempt) was not retried")
            XCTAssertEqual(source.retryAttemptsForTesting, attempt, "the prefix gave the budget back")
            scheduler.advance(by: HTTPRangeByteSource.retryBackoff(attempt: attempt))
            XCTAssertTrue(waitUntil { source.fetchFrontier == Int64(prefix) }, "retry \(attempt) brought no prefix")
        }
        XCTAssertFalse(advanceUntilStallRetry(source), "a stall was retried past the budget")
        XCTAssertTrue(waitUntil { result() != nil }, "the read outlived the budget")
        guard case .failure(StreamByteReaderError.transport(_)) = try XCTUnwrap(result()) else {
            return XCTFail("expected a transport failure, got \(String(describing: result()))")
        }
    }

    func testAFullWindowIsNotAStall() throws {
        let body = makeBody(256 * 1024)
        let server = try startServer(body: body)
        let source = makeSource(server, windowBytes: 32 * 1024)

        XCTAssertEqual(try read(source, upTo: 4096), body.prefix(4096))
        // The read is the ceiling's base, so the window ends 4 KiB past the first bounded range: its
        // continuation fills to there, and nothing more is wanted until the decoder reads on. Time is
        // only stepped once the frontier is at the ceiling, so a body still closing in real time is
        // not left idle in fake time.
        let ceiling = Int64(4096 + 32 * 1024)
        XCTAssertTrue(waitUntil { source.fetchFrontier >= ceiling })
        source.drainQueueForTesting()
        let opened = source.transactionCount
        scheduler.advance(by: 3 * HTTPRangeByteSource.stallTimeoutSeconds)
        source.drainQueueForTesting()
        XCTAssertEqual(source.transactionCount, opened, "a window held at its ceiling was taken for a stall")
    }

    // MARK: - Retry budget

    func testTheBackoffDoublesToItsCapAndTheBudgetRidesOutAHandoff() {
        let delays = (1...HTTPRangeByteSource.maxRetryAttempts).map(HTTPRangeByteSource.retryBackoff(attempt:))
        XCTAssertEqual(delays, [0.25, 0.5, 1, 2, 3, 3, 3, 3, 3])
        let total = delays.reduce(0, +)
        XCTAssertGreaterThanOrEqual(total, 15)
        XCTAssertLessThanOrEqual(total, 20)
    }

    func testFailuresAreRetriedWithBackoffUntilTheBudgetIsSpent() throws {
        let server = try startServer(body: makeBody(64 * 1024))
        server.failNextRequests = 1000
        let source = makeSource(server, windowBytes: 1024 * 1024)

        let result = readInBackground(source, upTo: 4096)
        var delays: [TimeInterval] = []
        while delays.count < HTTPRangeByteSource.maxRetryAttempts {
            XCTAssertTrue(waitUntil { source.retryPendingForTesting }, "no retry after \(delays.count) failures")
            let delay = try XCTUnwrap(scheduler.lastScheduledDelay)
            delays.append(delay)
            scheduler.advance(by: delay)
        }
        XCTAssertTrue(waitUntil { result() != nil }, "the read outlived the budget")
        guard case .failure(StreamByteReaderError.transport(_)) = try XCTUnwrap(result()) else {
            return XCTFail("expected a transport failure, got \(String(describing: result()))")
        }
        XCTAssertEqual(delays, (1...HTTPRangeByteSource.maxRetryAttempts).map(HTTPRangeByteSource.retryBackoff(attempt:)))
        // URLSession may quietly resend a GET whose connection dropped, so the origin can see more.
        XCTAssertGreaterThanOrEqual(server.requestedRanges.count, HTTPRangeByteSource.maxRetryAttempts + 1)
    }

    func testABodyThatArrivesResetsTheBudget() throws {
        let body = makeBody(512 * 1024)
        let server = try startServer(body: body)
        // Failing until told otherwise, not a count: URLSession's own resends would use one up.
        server.failNextRequests = 1000
        let source = makeSource(server, windowBytes: 64 * 1024)

        let first = readInBackground(source, upTo: 4096)
        for attempt in 1...3 {
            XCTAssertTrue(waitUntil { source.retryPendingForTesting })
            XCTAssertEqual(scheduler.lastScheduledDelay, HTTPRangeByteSource.retryBackoff(attempt: attempt))
            if attempt == 3 { server.failNextRequests = 0 }
            scheduler.advance(by: HTTPRangeByteSource.retryBackoff(attempt: attempt))
        }
        XCTAssertTrue(waitUntil { first() != nil })
        XCTAssertEqual(try XCTUnwrap(first()).get(), body.prefix(4096))

        // Outside the window: a new transaction, whose failure is the first of a fresh budget.
        server.failNextRequests = 1000
        try source.seek(to: 400 * 1024)
        let second = readInBackground(source, upTo: 4096)
        XCTAssertTrue(waitUntil { source.retryPendingForTesting })
        XCTAssertEqual(scheduler.lastScheduledDelay, HTTPRangeByteSource.retryBackoff(attempt: 1), "the budget carried over a success")
        server.failNextRequests = 0
        scheduler.advance(by: HTTPRangeByteSource.retryBackoff(attempt: 1))
        XCTAssertTrue(waitUntil { second() != nil })
        XCTAssertEqual(try XCTUnwrap(second()).get(), body.subdata(in: (400 * 1024)..<(404 * 1024)))
    }

    // MARK: - Path changes

    func testAPathChangeLeavesAFlowingBodyAndReopensAQuietOneAtOnce() throws {
        let body = makeBody(256 * 1024)
        let server = try startServer(body: body)
        let stallAt = 16 * 1024
        server.stallsAfterBodyBytes = stallAt
        let source = makeSource(server, windowBytes: 1024 * 1024)

        XCTAssertEqual(try read(source, upTo: stallAt), body.prefix(stallAt))
        server.stallsAfterBodyBytes = nil
        // Its last byte came just now: the path it is on works, whatever the monitor now prefers.
        paths.post()
        source.drainQueueForTesting()
        XCTAssertEqual(server.requestedRanges, [0], "a body still bringing bytes was dropped")

        scheduler.advance(by: HTTPRangeByteSource.pathQuietSeconds)
        paths.post()
        XCTAssertTrue(waitUntil { server.requestedRanges.count == 2 }, "the path change was ignored")
        XCTAssertEqual(server.requestedRanges, [0, Int64(stallAt)])
        XCTAssertEqual(try read(source, upTo: body.count - stallAt), body.suffix(from: stallAt))
    }

    func testAPathChangeCutsARetryBackoffShort() throws {
        let body = makeBody(64 * 1024)
        let server = try startServer(body: body)
        server.failNextRequests = 1000
        let source = makeSource(server, windowBytes: 1024 * 1024)

        // The very first open failing: a handoff as the track starts.
        let result = readInBackground(source, upTo: 4096)
        XCTAssertTrue(waitUntil { source.retryPendingForTesting })
        server.failNextRequests = 0
        paths.post()
        XCTAssertTrue(waitUntil { result() != nil }, "the read waited out the backoff")
        XCTAssertEqual(try XCTUnwrap(result()).get(), body.prefix(4096))
        let requests = server.requestedRanges.count

        // The superseded backoff, when it fires, opens nothing more.
        scheduler.advance(by: HTTPRangeByteSource.retryBackoff(attempt: 1))
        source.drainQueueForTesting()
        XCTAssertEqual(server.requestedRanges.count, requests)
    }

    func testCancelStopsObservingThePath() throws {
        let server = try startServer(body: makeBody(16 * 1024))
        let source = makeSource(server, windowBytes: 1024 * 1024)
        XCTAssertEqual(paths.observerCount, 1)
        source.cancel()
        XCTAssertEqual(paths.observerCount, 0)
    }

    func testACancelledSourceIgnoresAPathChangeQueuedBeforeTheCancel() throws {
        let body = makeBody(256 * 1024)
        let server = try startServer(body: body)
        server.stallsAfterBodyBytes = 16 * 1024
        let source = makeSource(server, windowBytes: 1024 * 1024)
        XCTAssertEqual(try read(source, upTo: 16 * 1024), body.prefix(16 * 1024))
        // Quiet long enough that a path change would reopen it.
        scheduler.advance(by: HTTPRangeByteSource.pathQuietSeconds)

        let lock = NSLock()
        var opened: [Int64] = []
        source.testBeforeTransaction = { offset in lock.withLock { opened.append(offset) } }
        // The change is queued, then the source is cancelled before the queue gets to it.
        let gate = DispatchSemaphore(value: 0)
        source.enqueueForTesting { gate.wait() }
        paths.post()
        source.cancel()
        gate.signal()
        source.drainQueueForTesting()
        XCTAssertEqual(lock.withLock { opened }, [], "a cancelled source opened a transaction")
    }

    func testAReleasedSourceStopsObservingThePath() throws {
        let server = try startServer(body: makeBody(16 * 1024))
        autoreleasepool {
            _ = HTTPRangeByteSource(
                url: server.url,
                authHeaders: [:],
                session: HTTPRangeByteSourceTests.testSession,
                runStore: nil,
                resolvedURLs: nil,
                scheduler: scheduler,
                pathMonitor: paths
            )
        }
        XCTAssertEqual(paths.observerCount, 0)
    }

    func testOnlyAUsablePathThatMovedIsAReconnect() {
        let wifi = (satisfied: true, interface: Optional("en0"))
        let cellular = (satisfied: true, interface: Optional("pdp_ip0"))
        let none = (satisfied: false, interface: String?.none)
        XCTAssertTrue(SystemNetworkPathMonitor.isReconnect(from: wifi, to: cellular))
        XCTAssertTrue(SystemNetworkPathMonitor.isReconnect(from: none, to: wifi))
        XCTAssertFalse(SystemNetworkPathMonitor.isReconnect(from: wifi, to: none))
        XCTAssertFalse(SystemNetworkPathMonitor.isReconnect(from: wifi, to: wifi))
    }
}

// MARK: - Fakes

/// Time stands still until a test steps it; work comes due in order and runs on its queue.
final class FakeRecoveryScheduler: RecoveryScheduler {
    private let lock = NSLock()
    private var clock: TimeInterval = 0
    private var pending: [(due: TimeInterval, queue: DispatchQueue, work: () -> Void)] = []
    private var lastDelay: TimeInterval?

    /// The delay of the most recent `schedule` call.
    var lastScheduledDelay: TimeInterval? { lock.withLock { lastDelay } }

    func now() -> TimeInterval { lock.withLock { clock } }

    func schedule(after seconds: TimeInterval, on queue: DispatchQueue, _ work: @escaping () -> Void) {
        lock.withLock {
            pending.append((clock + seconds, queue, work))
            lastDelay = seconds
        }
    }

    /// Move the clock on by `seconds`, running everything that comes due on the way — including
    /// work scheduled by work that ran — each at its own time.
    func advance(by seconds: TimeInterval) {
        let end = lock.withLock { clock + seconds }
        while true {
            let next: (due: TimeInterval, queue: DispatchQueue, work: () -> Void)? = lock.withLock {
                guard let index = pending.indices.filter({ pending[$0].due <= end + 1e-9 })
                    .min(by: { pending[$0].due < pending[$1].due }) else { return nil }
                let item = pending.remove(at: index)
                clock = max(clock, item.due)
                return item
            }
            guard let next else { break }
            next.queue.sync(execute: next.work)
        }
        lock.withLock { clock = end }
    }
}

/// A path that changes when the test says so.
final class FakeNetworkPathMonitor: NetworkPathMonitoring {
    private let lock = NSLock()
    private var observers: [UUID: () -> Void] = [:]

    var observerCount: Int { lock.withLock { observers.count } }

    func addObserver(_ handler: @escaping () -> Void) -> UUID {
        let id = UUID()
        lock.withLock { observers[id] = handler }
        return id
    }

    func removeObserver(_ id: UUID) {
        lock.withLock { observers[id] = nil }
    }

    func post() {
        lock.withLock { Array(observers.values) }.forEach { $0() }
    }
}
