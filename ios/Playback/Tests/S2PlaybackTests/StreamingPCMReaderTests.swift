// Copied from Shuttle Podcasts (podcasts@9ee6e0954) mobile/ios/Playback/Tests/PlaybackTests/StreamingPCMReaderTests.swift — see ios/Playback/README.md.
import XCTest
@testable import S2Playback
import S2PlaybackTestSupport

/// **The streaming decoder, over a real range-serving origin.**
///
/// What is being pinned is the thing `AVAssetReader` could not do at all: decode an `https://` URL,
/// seek inside it without re-probing, and do the `moov`-at-end M4A without read-discarding the
/// whole `mdat` — plan `2026-09-09-streaming-audio-pipeline.md` §3 calls that last one "the
/// bandwidth trap to test for", and ``LoopbackMediaServer/servedBytes`` is the only place it is
/// observable, because the client's own frontier says what it kept, not what it asked for.
final class StreamingPCMReaderTests: XCTestCase {

    private var server: LoopbackMediaServer?
    private var reader: StreamingPCMReader?

    override func setUpWithError() throws {
        try XCTSkipUnless(FFmpegStreamDecoder.isAvailable, "no FFmpeg xcframework (scripts/build-ffmpeg.sh)")
    }

    override func tearDown() {
        reader?.cancel()
        reader = nil
        server?.stop()
        server = nil
        super.tearDown()
    }

    // MARK: - Fixtures

    private func fixture(_ name: String, _ ext: String) throws -> Data {
        let url = try XCTUnwrap(
            LoopbackMediaServer.fixtureURL(name, withExtension: ext),
            "missing test fixture \(name).\(ext)"
        )
        return try Data(contentsOf: url)
    }

    /// A read-ahead window measured in kilobytes rather than the production minute of audio. The
    /// fixtures are 45-second tones of about 160 KB, so the shipping window swallows each of them
    /// whole in one transaction and nothing this file asserts about ranges would be visible.
    private func tightPolicy(windowBytes: Int64 = 16 * 1024) -> ReadAheadPolicy {
        ReadAheadPolicy(
            windowSeconds: 1,
            appetiteWindowSeconds: 2,
            windowBytes: windowBytes,
            appetiteWindowBytes: windowBytes * 2,
            minWindowBytes: 4 * 1024,
            backWindowBytes: 4 * 1024
        )
    }

    private func startServer(_ body: Data, mimeType: String) throws -> LoopbackMediaServer {
        let started = try LoopbackMediaServer(body: body, mimeType: mimeType)
        server = started
        return started
    }

    /// Pull chunks until `frames` frames of media have arrived, or the stream ends.
    @discardableResult
    private func pull(_ reader: StreamingPCMReader, untilFrames frames: Int64) -> Int64 {
        while reader.mediaFramesRead < frames {
            guard reader.nextChunk() != nil else { break }
        }
        return reader.mediaFramesRead
    }

    // MARK: - Format and chunks

    func testLoadsFormatAndProducesSamplesOverHTTP() async throws {
        let server = try startServer(try fixture("tone", "mp3"), mimeType: "audio/mpeg")
        let made = try StreamingPCMReader(url: server.url, runStore: nil, resolvedURLs: nil)
        reader = made

        let format = try await made.loadFormat()
        XCTAssertGreaterThan(format.sampleRate, 0)
        XCTAssertGreaterThan(format.channelCount, 0)
        XCTAssertGreaterThan(format.duration, 0, "the mp3 header carries a duration")

        try made.start(at: 0)
        // The first start is not a seek, so it lands where it was asked to.
        XCTAssertEqual(made.landedPosition, 0, accuracy: 0.001)

        let chunk = try XCTUnwrap(made.nextChunk(), "no samples arrived from an https URL")
        XCTAssertFalse(chunk.isEmpty)
        XCTAssertEqual(made.endReason, .running)
        XCTAssertGreaterThan(try XCTUnwrap(made.bytesFetched), 0)
    }

    /// The whole point of ``StreamingPCMReader/landedPosition``: a seek lands on a frame boundary,
    /// and the caller anchors its clock to THAT (plan §5.1).
    func testSeekLandsNearTheTargetAndOpensANewRange() async throws {
        let server = try startServer(try fixture("tone", "mp3"), mimeType: "audio/mpeg")
        let made = try StreamingPCMReader(url: server.url, readAhead: tightPolicy(), runStore: nil, resolvedURLs: nil)
        reader = made

        _ = try await made.loadFormat()
        try made.start(at: 0)
        pull(made, untilFrames: 8192)
        let rangesBefore = server.requestedRanges.count

        try made.start(at: 10)
        XCTAssertEqual(made.landedPosition, 10, accuracy: 0.5)
        XCTAssertNotNil(made.nextChunk(), "the decoder produced nothing after the seek")
        XCTAssertGreaterThan(
            server.requestedRanges.count,
            rangesBefore,
            "a seek past the window must open a transaction at the new offset"
        )
        XCTAssertGreaterThan(try XCTUnwrap(made.transactionCount), 1)
    }

    /// **The bandwidth trap.** With `moov` after `mdat`, FFmpeg reaches the sample table by seeking
    /// when the IO is seekable and by read-discarding the whole `mdat` when it is not. One second
    /// of audio must not cost most of the file.
    func testMoovAtEndDoesNotFetchMostOfTheBodyForOneSecondOfAudio() async throws {
        let body = try fixture("tone_moov_last", "m4a")
        let server = try startServer(body, mimeType: "audio/mp4")
        let made = try StreamingPCMReader(url: server.url, readAhead: tightPolicy(), runStore: nil, resolvedURLs: nil)
        reader = made

        let format = try await made.loadFormat()
        try made.start(at: 0)
        pull(made, untilFrames: Int64(format.sampleRate))

        // A working AVSEEK_SIZE seek touches only the head (format probe) and the tail (the moov
        // box). A read-discard walks forward from the head instead, and shows up here as a range
        // opened somewhere in the middle 80% of the body — not as a byte count, which drifts with
        // every change to the fixture or the read-ahead window sizes.
        let headBoundary = Int64(Double(body.count) * 0.10)
        let tailBoundary = Int64(Double(body.count) * 0.90)
        XCTAssertTrue(
            server.requestedRanges.allSatisfy { $0 <= headBoundary || $0 >= tailBoundary },
            "a range opened in the middle of the body, not the head or tail window: \(server.requestedRanges)"
        )
        // And a single head request that simply keeps reading to the end would open no mid-body
        // range at all, so also bound what was actually served: half the body is far above any
        // head+tail pair and far below a walk through the file.
        XCTAssertLessThan(
            server.servedBytes, Int64(body.count / 2),
            "served \(server.servedBytes) of \(body.count) bytes — most of the body was read to reach the moov box"
        )
    }

    /// FFmpeg's mp3 open looks for an ID3v1 footer at `size - 128` (#193). The footer is fetched
    /// beside the head, so the look costs no transaction of its own.
    func testTheOpenFetchesTheTailBesideTheHeadNotAfterIt() async throws {
        let body = try fixture("tone", "mp3")
        let server = try startServer(body, mimeType: "audio/mpeg")
        let made = try StreamingPCMReader(url: server.url, readAhead: tightPolicy(), runStore: nil, resolvedURLs: nil)
        reader = made

        _ = try await made.loadFormat()

        let tailStart = Int64(body.count) - 128
        // The probe no longer waits for the side fetch, so it can return before the origin has
        // even parsed the tail's request line.
        let sawTail = Date().addingTimeInterval(5)
        while !server.requestedRanges.contains(tailStart), Date() < sawTail { try await Task.sleep(nanoseconds: 20_000_000) }
        let ranges = server.requestedRanges
        XCTAssertEqual(ranges.first, 0)
        XCTAssertEqual(ranges.filter { $0 == tailStart }.count, 1, "one side fetch for the footer: \(ranges)")
        // Before: the footer look was a transaction at the tail, then another back at the head.
        // Now anything after the head is the side fetch or a continuation past the tight window.
        XCTAssertTrue(
            ranges.dropFirst().allSatisfy { $0 == tailStart || $0 >= 16 * 1024 },
            "the footer look must not cost a transaction, nor the seek back: \(ranges)"
        )
    }

    /// The cache-served resume the phone measured at a 4–10 s probe (#193 follow-up): the head
    /// is on disk, the sidecar has no tail, and the side fetch for it is slow. FFmpeg's `open()`
    /// must come back on the head alone; the footer look is answered with end-of-stream and the
    /// tail lands in the sidecar afterwards, for the next play.
    func testTheProbeDoesNotWaitForASlowTailWhenTheHeadIsOnDisk() async throws {
        let body = try fixture("tone", "mp3")
        let total = Int64(body.count)
        let server = try startServer(body, mimeType: "audio/mpeg")
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("reader-tail-tests-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let store = CachedRunStore(directory: directory)
        let key = server.url.absoluteString
        // A run from before tails were kept: most of the head, the total, no tail.
        store.replace(key, startingAt: 0, totalLength: total)
        _ = store.append(key, body.prefix(96 * 1024))
        XCTAssertNil(store.tail(for: key))
        server.delayForRangeStartingAt = (offset: total - 128, seconds: 3)

        let made = try StreamingPCMReader(url: server.url, readAhead: tightPolicy(), runStore: store, resolvedURLs: nil)
        reader = made
        let began = StartupTiming.now()
        let format = try await made.loadFormat()
        let probeSeconds = StartupTiming.now() - began

        XCTAssertGreaterThan(format.duration, 0)
        XCTAssertLessThan(probeSeconds, 1.0, "the probe waited on the side fetch: \(probeSeconds)s")
        XCTAssertEqual(made.tailStatus, .late, "the look came before the tail and was not waited for")
        let sawTail = Date().addingTimeInterval(5)
        while !server.requestedRanges.contains(total - 128), Date() < sawTail { try await Task.sleep(nanoseconds: 20_000_000) }
        XCTAssertEqual(server.requestedRanges.filter { $0 == total - 128 }.count, 1, "one side fetch, no transaction for the look: \(server.requestedRanges)")
        XCTAssertFalse(server.requestedRanges.contains(0), "the head came off disk: \(server.requestedRanges)")

        try made.start(at: 0)
        pull(made, untilFrames: Int64(format.sampleRate))
        XCTAssertGreaterThan(made.mediaFramesRead, 0, "the head decodes without the footer")

        let deadline = Date().addingTimeInterval(6)
        while store.tail(for: key) == nil, Date() < deadline { try await Task.sleep(nanoseconds: 50_000_000) }
        XCTAssertEqual(store.tail(for: key), body.suffix(128), "the slow side fetch still lands for the next play")
    }

    /// The cache-served resume the phone measured at 1079–1086 ms (#193): head and mp3 footer on
    /// disk, the shipping read-ahead policy, and a network that answers every range 3 s late. The
    /// probe must come back off the run in milliseconds and never ask the origin for a byte the
    /// run holds: FFmpeg's mp3 open reads the first 32 KiB, the 128-byte footer from the sidecar,
    /// and the first 32 KiB again after `ff_id3v1_read` seeks back — 65664 bytes, the phone's
    /// exact `probe-bytes` — so a 64 KiB head is already enough, and a 96 KiB one has 32 KiB to
    /// spare. The continuation past a run this short opens beside the probe; the probe does not
    /// wait on it, and the head is never asked for over the network.
    func testACachedHeadAndFooterProbeOffDiskWhateverTheNetworkDoes() async throws {
        let body = try fixture("tone", "mp3")
        let total = Int64(body.count)
        for headBytes in [64 * 1024, 96 * 1024] {
            let server = try LoopbackMediaServer(body: body, mimeType: "audio/mpeg")
            defer { server.stop() }
            let directory = FileManager.default.temporaryDirectory
                .appendingPathComponent("reader-head-tests-\(UUID().uuidString)", isDirectory: true)
            defer { try? FileManager.default.removeItem(at: directory) }
            let store = CachedRunStore(directory: directory)
            let key = server.url.absoluteString
            store.replace(key, startingAt: 0, totalLength: total)
            _ = store.append(key, body.prefix(headBytes))
            store.setTail(key, body.suffix(128), totalLength: total)
            server.delayForEveryRange = 3

            let made = try StreamingPCMReader(url: server.url, readAhead: .default, runStore: store, resolvedURLs: nil)
            let began = StartupTiming.now()
            let format = try await made.loadFormat()
            let waited = StartupTiming.now() - began
            let probe = try XCTUnwrap(made.probeFinishedAt) - (try XCTUnwrap(made.probeStartedAt))
            made.cancel()

            XCTAssertGreaterThan(format.duration, 0, "head=\(headBytes)")
            XCTAssertLessThan(waited, 0.5, "head=\(headBytes): the probe waited on the network: \(waited)s")
            XCTAssertLessThan(probe, 0.5, "head=\(headBytes): `open()` itself waited: \(probe)s")
            XCTAssertEqual(made.probe?.bytes, 65664, "head=\(headBytes): the mp3 open reads 32 KiB, the footer, 32 KiB again")
            XCTAssertEqual(made.tailStatus, .sidecar, "head=\(headBytes)")
            let ranges = server.requestedRanges
            XCTAssertFalse(ranges.contains(0), "head=\(headBytes): the head came off disk: \(ranges)")
            XCTAssertFalse(ranges.contains(total - 128), "head=\(headBytes): the footer came off the sidecar: \(ranges)")
            XCTAssertTrue(
                ranges.allSatisfy { $0 >= Int64(headBytes) - 64 * 1024 },
                "head=\(headBytes): only a continuation past the run may reach the origin: \(ranges)"
            )
        }
    }

    /// A blocked read must not survive the reader. ``StreamingPCMReader/cancel()`` is the one call
    /// allowed from another thread and it is what turns a stalled network read into a clean end.
    func testCancelUnblocksAPendingRead() async throws {
        let server = try startServer(try fixture("tone", "mp3"), mimeType: "audio/mpeg")
        // Held long enough that the pull below is certainly waiting on bytes when cancel lands.
        server.delayForOffsetZero = 5
        let made = try StreamingPCMReader(url: server.url, runStore: nil, resolvedURLs: nil)
        reader = made

        let finished = expectation(description: "loadFormat returned")
        Task.detached {
            _ = try? await made.loadFormat()
            finished.fulfill()
        }
        // Long enough for the request to be in flight and the read to be parked.
        try await Task.sleep(nanoseconds: 500_000_000)
        made.cancel()
        await fulfillment(of: [finished], timeout: 5)
    }

    // MARK: - Downloads

    /// A `file://` URL takes ``FileByteReader`` and never touches the network — no server is
    /// started here at all, so a stray request would fail rather than quietly succeed.
    func testFileURLPlaysThroughTheFileReader() async throws {
        let url = try XCTUnwrap(LoopbackMediaServer.fixtureURL("tone", withExtension: "mp3"))
        let made = try StreamingPCMReader(url: url, runStore: nil, resolvedURLs: nil)
        reader = made

        let format = try await made.loadFormat()
        XCTAssertGreaterThan(format.sampleRate, 0)
        try made.start(at: 0)
        XCTAssertNotNil(made.nextChunk())
        XCTAssertNil(made.bytesFetched, "a file has no HTTP byte source")
        XCTAssertNil(made.byteSource)
    }

    /// Not audio at all: the failure has to be `unprobeable`, because that is what the `AVPlayer`
    /// fallback will key off (plan §1).
    func testGarbageBodyIsReportedAsUnprobeable() async throws {
        let server = try startServer(Data(repeating: 0x7A, count: 64 * 1024), mimeType: "text/html")
        let made = try StreamingPCMReader(url: server.url, runStore: nil, resolvedURLs: nil)
        reader = made

        do {
            _ = try await made.loadFormat()
            XCTFail("a body of filler must not probe as audio")
        } catch let error as StreamingPCMReader.ReaderError {
            guard case .unprobeable = error else { return XCTFail("wrong case: \(error)") }
        }
    }
}
