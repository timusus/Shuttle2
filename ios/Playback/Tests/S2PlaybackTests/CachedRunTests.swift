// Copied from Shuttle Podcasts (podcasts@9ee6e0954) mobile/ios/Playback/Tests/PlaybackTests/CachedRunTests.swift — see ios/Playback/README.md.
import XCTest
@testable import S2Playback
import S2PlaybackTestSupport

/// The run on disk, over a real loopback origin that re-stitches between requests.
///
/// Every case is one of the seams `docs/plans/2026-09-16-streaming-playback-cache.md` names: the
/// overlap that agrees, the overlap that does not, the overlap that is all silence, the host that
/// answers 200 to a range, and the resume that lands inside what the disk already holds. The
/// origin serves a different stitch per request, which is exactly what a real host does.
final class CachedRunTests: XCTestCase {

    private static let kib = 1024
    private var server: LoopbackMediaServer?
    private var source: HTTPRangeByteSource?
    static let testSession = HTTPRangeByteSource.makeSession(configuration: .ephemeral)
    private var storeDirectory: URL!
    private var store: CachedRunStore!

    override func setUp() {
        super.setUp()
        storeDirectory = FileManager.default.temporaryDirectory
            .appendingPathComponent("cached-run-tests-\(UUID().uuidString)", isDirectory: true)
        store = CachedRunStore(directory: storeDirectory)
    }

    override func tearDown() {
        source?.cancel()
        source = nil
        server?.stop()
        server = nil
        try? FileManager.default.removeItem(at: storeDirectory)
        super.tearDown()
    }

    // MARK: - Stitches

    private func makeBytes(_ count: Int, seed: UInt64) -> Data {
        var state = seed
        var bytes = [UInt8]()
        bytes.reserveCapacity(count)
        for _ in 0..<count {
            state = state &* 6364136223846793005 &+ 1442695040888963407
            bytes.append(UInt8((state >> 33) & 0xFF))
        }
        return Data(bytes)
    }

    /// 512 KiB of programme, behind a pre-roll slot: stitches A and B differ only inside the slot,
    /// stitch C's slot is longer so every byte after it sits at a different offset.
    private lazy var content = makeBytes(512 * Self.kib, seed: 0x9E3779B97F4A7C15)
    private lazy var stitchA = makeBytes(64 * Self.kib, seed: 1) + content
    private lazy var stitchB = makeBytes(64 * Self.kib, seed: 2) + content
    private lazy var stitchC = makeBytes(96 * Self.kib, seed: 3) + content

    private func startServer(_ bodies: [Data]) throws -> LoopbackMediaServer {
        let started = try LoopbackMediaServer(body: bodies[0], mimeType: "audio/mpeg")
        started.bodies = bodies
        server = started
        return started
    }

    private func makeSource(
        _ server: LoopbackMediaServer,
        windowBytes: Int,
        tee: AudioByteTee? = nil
    ) -> HTTPRangeByteSource {
        let policy = ReadAheadPolicy(
            windowSeconds: 60,
            appetiteWindowSeconds: 80,
            windowBytes: Int64(windowBytes),
            appetiteWindowBytes: Int64(windowBytes),
            minWindowBytes: 4 * 1024,
            backWindowBytes: 64 * 1024
        )
        let made = HTTPRangeByteSource(
            url: server.url,
            authHeaders: [:],
            readAhead: policy,
            session: Self.testSession,
            tee: tee,
            runStore: store,
            resolvedURLs: nil
        )
        source = made
        return made
    }

    private var key: String { server!.url.absoluteString }

    private func seedRun(_ bytes: Data, startingAt start: Int64 = 0, total: Int) {
        store.replace(key, startingAt: start, totalLength: Int64(total))
        store.append(key, bytes)
    }

    private func runBytes() -> Data {
        guard let run = store.run(for: key) else { return Data() }
        return store.read(key, at: run.start, maxLength: Int(run.length)) ?? Data()
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

    private func waitUntil(_ timeout: TimeInterval = 5, _ condition: () -> Bool) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if condition() { return true }
            Thread.sleep(forTimeInterval: 0.02)
        }
        return condition()
    }

    // MARK: - (a) A whole play is kept; the next one never asks the network

    func testUninterruptedPlayWritesTheRunAndTheSecondPlayMakesNoRequests() throws {
        let server = try startServer([stitchA])
        let first = makeSource(server, windowBytes: 4 * 1024 * 1024)
        XCTAssertEqual(try read(first, upTo: stitchA.count), stitchA)
        XCTAssertTrue(waitUntil { self.store.run(for: self.key)?.isComplete == true })
        XCTAssertEqual(runBytes(), stitchA)
        first.cancel()
        let requests = server.requestedRanges.count

        let tee = CountingTee()
        let second = makeSource(server, windowBytes: 4 * 1024 * 1024, tee: tee)
        XCTAssertEqual(try read(second, upTo: stitchA.count), stitchA)
        XCTAssertEqual(server.requestedRanges.count, requests, "the second play is served from disk")
        XCTAssertEqual(tee.bytes, stitchA, "the tee sees the disk's bytes exactly as it saw the body's")
        XCTAssertEqual(tee.opens.map(\.startByte), [0])
        XCTAssertEqual(tee.opens.map(\.isContinuation), [false])
        XCTAssertTrue(waitUntil { tee.closes == [Int64(self.stitchA.count)] })
    }

    // MARK: - (b) A continuation whose overlap agrees extends the run

    func testContinuationOnALengthMatchedStitchAppends() throws {
        // First body: bytes 0–128K of A. Every request after it: B. The seam at 128K is validated
        // against B's bytes 64K–128K, which are programme, not pre-roll, so they agree.
        let server = try startServer([stitchA, stitchB])
        let tee = CountingTee()
        let source = makeSource(server, windowBytes: 128 * Self.kib, tee: tee)

        let heard = try read(source, upTo: stitchA.count)
        XCTAssertEqual(heard, stitchA, "A's pre-roll, then the programme both stitches share")
        XCTAssertEqual(tee.bytes, heard)
        XCTAssertGreaterThan(server.requestedRanges.count, 1)
        // `requestHeads[1]` is the side fetch for the file's tail; the seam is the next head.
        XCTAssertTrue(server.requestHeads.dropFirst().contains { $0.contains("bytes=65536-") }, "the seam asks for the run's last 64 KiB again")
        XCTAssertTrue(waitUntil { self.store.run(for: self.key)?.isComplete == true })
        XCTAssertEqual(store.run(for: key)?.start, 0, "appended, never replaced")
        XCTAssertEqual(runBytes(), heard)
    }

    // MARK: - (c) A continuation whose overlap disagrees replaces the run

    func testContinuationOnALongerPreRollStitchReplacesTheRun() throws {
        let server = try startServer([stitchA, stitchC])
        let tee = CountingTee()
        let source = makeSource(server, windowBytes: 128 * Self.kib, tee: tee)

        let seam = 128 * Self.kib
        let heard = try read(source, upTo: stitchC.count)
        XCTAssertEqual(heard, stitchA.prefix(seam) + stitchC.suffix(from: seam))
        XCTAssertEqual(tee.bytes, heard)
        XCTAssertTrue(waitUntil { self.store.run(for: self.key)?.end == Int64(self.stitchC.count) })
        let run = try XCTUnwrap(store.run(for: key))
        XCTAssertEqual(run.start, Int64(seam), "the run restarts at the seam the overlap refused")
        XCTAssertEqual(runBytes(), stitchC.suffix(from: seam))
    }

    // MARK: - (d) A resume inside the run reads the disk, then asks for exactly the seam

    func testResumeInsideTheRunReadsFromDiskAndValidatesAtItsEnd() throws {
        let server = try startServer([stitchA])
        let runEnd = 256 * Self.kib
        seedRun(stitchA.prefix(runEnd), total: stitchA.count)
        let tee = CountingTee()
        let source = makeSource(server, windowBytes: 64 * Self.kib, tee: tee)

        let resumeAt = 100 * Self.kib
        try source.seek(to: Int64(resumeAt))
        let heard = try read(source, upTo: stitchA.count - resumeAt)
        XCTAssertEqual(heard, stitchA.suffix(from: resumeAt))
        XCTAssertEqual(tee.bytes, heard)
        XCTAssertEqual(server.requestedRanges.first, Int64(runEnd - HTTPRangeByteSource.overlapBytes))
        XCTAssertTrue(server.requestHeads[0].contains("bytes=\(runEnd - HTTPRangeByteSource.overlapBytes)-"))
        XCTAssertEqual(tee.opens.map(\.startByte).prefix(2), [Int64(resumeAt), Int64(runEnd)])
        XCTAssertEqual(tee.opens.map(\.isContinuation).prefix(2), [false, true])
        XCTAssertTrue(waitUntil { self.store.run(for: self.key)?.isComplete == true })
        XCTAssertEqual(runBytes(), stitchA)
    }

    // MARK: - (d2) A resume from disk whose seam disagrees is two stitches to the tee

    func testSeamMismatchAfterDiskPumpClosesTheTeeTransactionAndReopensIt() throws {
        let server = try startServer([stitchC])
        let runEnd = 128 * Self.kib
        seedRun(stitchA.prefix(runEnd), total: stitchC.count)
        let tee = CountingTee()
        let source = makeSource(server, windowBytes: 4 * 1024 * 1024, tee: tee)

        let heard = try read(source, upTo: stitchC.count)
        XCTAssertEqual(heard, stitchA.prefix(runEnd) + stitchC.suffix(from: runEnd))
        XCTAssertEqual(tee.bytes, heard)
        // Disk from 0, the body carrying it on at the seam, then — the overlap refused — the same
        // body declared again as a stitch of its own from the seam.
        XCTAssertEqual(tee.opens.map(\.startByte), [0, Int64(runEnd), Int64(runEnd)])
        XCTAssertEqual(tee.opens.map(\.isContinuation), [false, true, false])
        XCTAssertTrue(waitUntil { tee.closes.count == 3 })
        XCTAssertEqual(tee.closes, [Int64(runEnd), Int64(runEnd), Int64(self.stitchC.count)])
        XCTAssertEqual(store.run(for: key)?.start, Int64(runEnd))
        XCTAssertEqual(runBytes(), stitchC.suffix(from: runEnd))
    }

    // MARK: - (d3) A body that stops inside the run before comparing it proves nothing about it

    func testBodyEndingInsideAnUncomparedRunReplacesItWithTheLiveBytes() throws {
        // The run sits at 100K–164K, from another stitch. The first body stalls at 150K: past the
        // run's start, short of the 64 KiB that would let it be compared. The reopened body must
        // not be served the run's bytes from 150K; what the window still holds from 100K becomes
        // the run and the body carries on from 150K, validated at that seam against its own bytes.
        let server = try startServer([stitchA])
        let runStart = 100 * Self.kib
        seedRun(stitchC.subdata(in: runStart..<(runStart + 64 * Self.kib)), startingAt: Int64(runStart), total: stitchA.count)
        let stallAt = 150 * Self.kib
        server.stallsAfterBodyBytes = stallAt
        let tee = CountingTee()
        let source = makeSource(server, windowBytes: 4 * 1024 * 1024, tee: tee)

        XCTAssertEqual(try read(source, upTo: stallAt), stitchA.prefix(stallAt))
        XCTAssertEqual(store.run(for: key)?.start, Int64(runStart), "untouched until the run is reached")
        server.stallsAfterBodyBytes = nil
        source.reopen()

        let rest = try read(source, upTo: stitchA.count - stallAt)
        XCTAssertEqual(rest, stitchA.suffix(from: stallAt), "nothing of the other stitch is heard")
        XCTAssertEqual(tee.bytes, stitchA)
        XCTAssertEqual(tee.opens.map(\.isContinuation), [false, true], "one stitch, carried on")
        XCTAssertTrue(waitUntil { self.store.run(for: self.key)?.end == Int64(self.stitchA.count) })
        XCTAssertEqual(store.run(for: key)?.start, Int64(runStart), "the live bytes from the run's start are the run")
        XCTAssertEqual(runBytes(), stitchA.suffix(from: runStart))
    }

    // MARK: - (e) An overlap that is all silence proves nothing

    func testSilenceOnlyOverlapIsAMismatch() throws {
        var silent = stitchA
        let runEnd = 192 * Self.kib
        silent.replaceSubrange((runEnd - 64 * Self.kib)..<runEnd, with: Data(count: 64 * Self.kib))
        let server = try startServer([silent])
        seedRun(silent.prefix(runEnd), total: silent.count)
        let source = makeSource(server, windowBytes: 64 * Self.kib)

        try source.seek(to: Int64(runEnd))
        XCTAssertEqual(try read(source, upTo: 64 * Self.kib), silent.subdata(in: runEnd..<(runEnd + 64 * Self.kib)))
        XCTAssertEqual(server.requestedRanges.first, Int64(runEnd), "no overlap is asked for: it could not be trusted")
        XCTAssertTrue(waitUntil { self.store.run(for: self.key)?.start == Int64(runEnd) })
    }

    // MARK: - (f) A 200 to the seam's range is a mismatch

    func testWholeBodyAnswerToTheSeamReplacesTheRun() throws {
        let server = try startServer([stitchA])
        server.respondsWholeBodyIgnoringRange = true
        let runEnd = 128 * Self.kib
        // The run holds B's prefix: after the 200, the run must hold what was actually served.
        seedRun(stitchB.prefix(runEnd), total: stitchB.count)
        let source = makeSource(server, windowBytes: 4 * 1024 * 1024)

        try source.seek(to: Int64(runEnd))
        XCTAssertEqual(try read(source, upTo: stitchA.count - runEnd), stitchA.suffix(from: runEnd))
        XCTAssertTrue(waitUntil { self.store.run(for: self.key)?.isComplete == true })
        XCTAssertEqual(store.run(for: key)?.start, 0)
        XCTAssertEqual(runBytes(), stitchA)
    }

    // MARK: - (g) Eviction

    func testEvictionRespectsTheBudgetInLeastRecentlyTouchedOrder() {
        for (index, key) in ["one", "two", "three"].enumerated() {
            store.replace(key, startingAt: 0, totalLength: nil)
            store.append(key, Data(repeating: UInt8(index), count: 100))
            Thread.sleep(forTimeInterval: 0.01)
        }
        // "one" is the oldest, but a touch makes it the newest.
        store.touch("one")
        XCTAssertEqual(store.totalBytes(), 300)

        XCTAssertEqual(store.evict(toBudget: 250, excluding: nil), 1)
        XCTAssertNil(store.run(for: "two"))
        XCTAssertNotNil(store.run(for: "one"))
        XCTAssertNotNil(store.run(for: "three"))
        XCTAssertEqual(store.totalBytes(), 200)

        // The run the player has loaded is never evicted from under it.
        XCTAssertEqual(store.evict(toBudget: 0, excluding: "three"), 1)
        XCTAssertNil(store.run(for: "one"))
        XCTAssertNotNil(store.run(for: "three"))
        XCTAssertEqual(store.evict(toBudget: 250, excluding: "three"), 0)
    }

    // MARK: - (h) A replace drops the old bytes before it records the new start

    func testReplaceNeverLeavesOldBytesUnderANewStart() throws {
        store.replace("one", startingAt: 0, totalLength: nil)
        store.append("one", Data(repeating: 1, count: 100))
        store.replace("one", startingAt: 500, totalLength: nil)
        XCTAssertEqual(store.run(for: "one"), CachedRunStore.Run(start: 500, length: 0, totalLength: nil))
        XCTAssertNil(store.read("one", at: 500, maxLength: 100))
        let names = try FileManager.default.contentsOfDirectory(atPath: storeDirectory.path)
        XCTAssertEqual(names.filter { $0.hasSuffix(".run") }.count, 1)
        XCTAssertEqual(names.filter { $0.hasSuffix(".json") }.count, 1)
    }

    // MARK: - The tail

    func testTheTailSurvivesAReplaceAtTheSameTotalAndNotADifferentOne() {
        let tail = Data(repeating: 7, count: 128)
        XCTAssertNil(store.tail(for: "one"))
        store.setTail("one", tail, totalLength: 5000)
        XCTAssertEqual(store.tail(for: "one"), tail, "kept with no run yet")
        XCTAssertEqual(store.run(for: "one"), CachedRunStore.Run(start: 0, length: 0, totalLength: 5000))

        store.replace("one", startingAt: 0, totalLength: 5000)
        XCTAssertEqual(store.tail(for: "one"), tail, "the body that follows carries it forward")
        store.replace("one", startingAt: 100, totalLength: nil)
        XCTAssertEqual(store.tail(for: "one"), tail, "a replace that names no total keeps the known one")

        store.setTail("one", Data(repeating: 1, count: 128), totalLength: 6000)
        XCTAssertEqual(store.tail(for: "one"), tail, "a tail of a different-sized file is not this file's")
        store.setTotalLength("one", 6000)
        XCTAssertNil(store.tail(for: "one"), "a new total drops the old tail")
        store.replace("one", startingAt: 0, totalLength: 7000)
        XCTAssertNil(store.tail(for: "one"))
    }

    /// The next play finds the footer in the sidecar: the head comes from disk, the ID3v1 look
    /// from the sidecar, and the network is not asked for either.
    func testTheTailIsKeptForTheNextPlay() throws {
        let body = makeBytes(1024 * 1024, seed: 11)
        let total = Int64(body.count)
        let server = try startServer([body])
        let first = makeSource(server, windowBytes: 64 * 1024)
        XCTAssertEqual(try read(first, upTo: 8 * 1024), body.prefix(8 * 1024))
        XCTAssertTrue(waitUntil { self.store.tail(for: self.key) != nil }, "the side fetch lands in the sidecar")
        XCTAssertEqual(store.tail(for: key), body.suffix(128))
        first.cancel()
        let requests = server.requestedRanges.count

        let second = makeSource(server, windowBytes: 64 * 1024)
        XCTAssertEqual(try read(second, upTo: 4 * 1024), body.prefix(4 * 1024))
        try second.seek(to: total - 128)
        XCTAssertEqual(try read(second, upTo: 128), body.suffix(128))
        try second.seek(to: 0)
        XCTAssertEqual(try read(second, upTo: 4 * 1024), body.prefix(4 * 1024))
        XCTAssertEqual(server.requestedRanges.count, requests, "the second play's open asked the network for nothing")
    }

    /// The cache-served resume the phone measured at 4–10 s (#193 follow-up): the head is on disk
    /// but the sidecar has no tail (a run from before tails were kept), so the open fires the
    /// side fetch over the network — a whole redirect chain, on the phone. The footer look must
    /// not wait for it: end-of-stream at once, the window kept for the seek back, and the tail
    /// still kept for the play after this one.
    func testACachedHeadWithNoTailDoesNotWaitForTheSideFetch() throws {
        let body = makeBytes(1024 * 1024, seed: 12)
        let total = Int64(body.count)
        let server = try startServer([body])
        // A run from before tails were kept: the head, the total, no tail.
        store.replace(key, startingAt: 0, totalLength: total)
        store.append(key, body.prefix(256 * 1024))
        XCTAssertNil(store.tail(for: key))
        server.delayForRangeStartingAt = (offset: total - 128, seconds: 2)

        let source = makeSource(server, windowBytes: 64 * 1024)
        let began = Date()
        XCTAssertEqual(try read(source, upTo: 4 * 1024), body.prefix(4 * 1024), "the head comes off disk")
        try source.seek(to: total - 128)
        XCTAssertEqual(try read(source, upTo: 128), Data(), "the footer look is end-of-stream, not a wait")
        try source.seek(to: 0)
        XCTAssertEqual(try read(source, upTo: 4 * 1024), body.prefix(4 * 1024), "the seek back is served from the window")
        XCTAssertLessThan(Date().timeIntervalSince(began), 0.5, "the open must not pay the side fetch's round trip")
        XCTAssertEqual(source.tailStatus, .late)
        XCTAssertEqual(source.transactionCount, 1, "the disk head; the look opened nothing")
        XCTAssertTrue(waitUntil { server.requestedRanges.contains(total - 128) }, "the side fetch was fired")
        XCTAssertEqual(server.requestedRanges, [total - 128], "the only request is the side fetch: \(server.requestedRanges)")

        XCTAssertTrue(waitUntil { self.store.tail(for: self.key) != nil }, "the side fetch still lands in the sidecar")
        XCTAssertEqual(store.tail(for: key), body.suffix(128))
        XCTAssertEqual(source.tailStatus, .late, "late stays late")
    }

    /// The phone's cache-served resume on the shipping policy (#193): a run longer than the
    /// 2 MiB opening window, the footer in the sidecar, and an origin that answers every range 3 s
    /// late. FFmpeg's mp3 open is replayed byte for byte — the 10-byte ID3 look, 32 KiB from 0,
    /// the 128-byte footer at `total - 128`, and 32 KiB from 0 again after the seek back — and
    /// none of it may touch the network or wait: one transaction, the disk one, and no range asked
    /// of the origin at all.
    func testTheMP3OpenSequenceIsServedOffTheRunWithoutATransaction() throws {
        let body = makeBytes(6 * 1024 * 1024, seed: 21)
        let total = Int64(body.count)
        let server = try startServer([body])
        store.replace(key, startingAt: 0, totalLength: total)
        store.append(key, body.prefix(3 * 1024 * 1024))
        store.setTail(key, body.suffix(128), totalLength: total)
        server.delayForEveryRange = 3

        let made = HTTPRangeByteSource(
            url: server.url, authHeaders: [:], readAhead: .default, session: Self.testSession,
            tee: nil, runStore: store, resolvedURLs: nil
        )
        source = made
        let began = Date()
        XCTAssertEqual(try read(made, upTo: 10), body.prefix(10))
        try made.seek(to: 0)
        XCTAssertEqual(try read(made, upTo: 32 * 1024), body.prefix(32 * 1024))
        try made.seek(to: total - 128)
        XCTAssertEqual(try read(made, upTo: 128), body.suffix(128))
        try made.seek(to: 0)
        XCTAssertEqual(try read(made, upTo: 32 * 1024), body.prefix(32 * 1024))
        let elapsed = Date().timeIntervalSince(began)

        XCTAssertLessThan(elapsed, 0.5, "the open sequence waited on something: \(elapsed)s")
        XCTAssertEqual(made.transactionCount, 1, "the disk transaction and nothing else")
        XCTAssertEqual(made.tailStatus, .sidecar)
        XCTAssertEqual(server.requestedRanges, [], "no range reached the origin")
    }
}

// MARK: - Tee

private final class CountingTee: AudioByteTee {

    struct Open {
        let startByte: Int64
        let isContinuation: Bool
    }

    private let lock = NSLock()
    private var _opens: [Open] = []
    private var _closes: [Int64] = []
    private var _bytes = Data()

    var opens: [Open] { lock.lock(); defer { lock.unlock() }; return _opens }
    var closes: [Int64] { lock.lock(); defer { lock.unlock() }; return _closes }
    var bytes: Data { lock.lock(); defer { lock.unlock() }; return _bytes }

    func byteSourceDidStart(readAhead: any ReadAheadControl) {}
    func playerWillSeek(toMs ms: Int64, generation: Int) {}

    func byteSourceDidOpenTransaction(startByte: Int64, totalBytes: Int64?, isContinuation: Bool, seekGeneration: Int) {
        lock.lock(); _opens.append(Open(startByte: startByte, isContinuation: isContinuation)); lock.unlock()
    }

    func byteSource(didReceive bytes: Data, at offset: Int64) {
        lock.lock(); _bytes.append(bytes); lock.unlock()
    }

    func byteSourceDidCloseTransaction(endedAtByte: Int64) {
        lock.lock(); _closes.append(endedAtByte); lock.unlock()
    }

    func byteSource(didLearnTotalBytes totalBytes: Int64) {}
}
