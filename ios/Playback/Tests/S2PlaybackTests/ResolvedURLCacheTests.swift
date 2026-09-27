// Copied from Shuttle Podcasts (podcasts@9ee6e0954) mobile/ios/Playback/Tests/PlaybackTests/ResolvedURLCacheTests.swift — see ios/Playback/README.md.
import XCTest
@testable import S2Playback

/// The map from an enclosure URL to where its redirect chain ended (#193): what it keeps, what it
/// forgets, and that it survives a relaunch.
final class ResolvedURLCacheTests: XCTestCase {

    private let original = URL(string: "https://feed.example/episodes/1.mp3?ref=x")!
    private let cdn = URL(string: "https://cdn.example/1.mp3")!

    private func temporaryFile() -> URL {
        FileManager.default.temporaryDirectory
            .appendingPathComponent("resolved-urls-\(UUID().uuidString).json")
    }

    func testAnEntryRoundTripsThroughTheFile() {
        let file = temporaryFile()
        defer { try? FileManager.default.removeItem(at: file) }
        ResolvedURLCache(fileURL: file).record(original: original, resolved: cdn)

        let reloaded = ResolvedURLCache(fileURL: file)
        XCTAssertEqual(reloaded.resolved(for: original), cdn)
        XCTAssertEqual(reloaded.count, 1)
    }

    func testAChainThatEndsWhereItStartedIsNotRemembered() {
        let cache = ResolvedURLCache(fileURL: nil)
        cache.record(original: original, resolved: cdn)
        cache.record(original: original, resolved: original)
        XCTAssertNil(cache.resolved(for: original))
        XCTAssertEqual(cache.count, 0)
    }

    func testAnEntryExpiresAfterTheTTL() {
        var clock: TimeInterval = 100
        let cache = ResolvedURLCache(fileURL: nil, now: { clock })
        cache.record(original: original, resolved: cdn)
        clock += ResolvedURLCache.ttl - 1
        XCTAssertEqual(cache.resolved(for: original), cdn, "still inside the TTL")
        clock += 2
        XCTAssertNil(cache.resolved(for: original), "past the TTL")
        XCTAssertEqual(cache.count, 0, "an expired entry is dropped on the read that found it")
    }

    func testInvalidateForgetsOneEntry() {
        let cache = ResolvedURLCache(fileURL: nil)
        let other = URL(string: "https://feed.example/episodes/2.mp3")!
        cache.record(original: original, resolved: cdn)
        cache.record(original: other, resolved: cdn)
        cache.invalidate(original)
        XCTAssertNil(cache.resolved(for: original))
        XCTAssertEqual(cache.resolved(for: other), cdn)
    }

    func testTheMapIsCappedOldestOut() {
        var clock: TimeInterval = 0
        let cache = ResolvedURLCache(fileURL: nil, now: { clock })
        for i in 0..<(ResolvedURLCache.maxEntries + 5) {
            clock += 1
            cache.record(original: URL(string: "https://feed.example/\(i).mp3")!, resolved: cdn)
        }
        XCTAssertEqual(cache.count, ResolvedURLCache.maxEntries)
        XCTAssertNil(cache.resolved(for: URL(string: "https://feed.example/0.mp3")!), "the oldest is gone")
        XCTAssertEqual(cache.resolved(for: URL(string: "https://feed.example/\(ResolvedURLCache.maxEntries + 4).mp3")!), cdn)
    }
}
