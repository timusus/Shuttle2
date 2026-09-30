import Foundation
import Shared
import Testing
@testable import S2

/// Search on an 18,000-song library (#677), on Kotlin/Native: the index build, a keystroke's query, and laying the
/// results out in Swift. The numbers print so a regression shows; the bounds only catch an order of magnitude.
@MainActor
struct SearchPerformanceTests {
    private let syllables = ["ra", "di", "o", "head", "mo", "ka", "lin", "ter", "son", "vel", "bri", "ght", "an", "dor", "mis", "ty"]

    private func phrase(_ seed: Int) -> String {
        let count = 1 + seed % 3
        let parts: [String] = (0..<count).map { part in syllables[(seed / (part + 1) + part * 7) % syllables.count] }
        return parts.joined().capitalized
    }

    @Test func searchesAnEighteenThousandSongLibrary() throws {
        let songs = (0..<18_000).map { id in
            TestSongs.song(Int64(id), "\(phrase(id)) \(phrase(id * 31))", artist: phrase(id / 12), album: phrase(id / 12 + 5), durationMs: 1)
        }
        let documents = songs.map { song in
            SearchDocument<AnyObject>(
                item: song,
                fields: [KotlinPair(first: SearchField.name as __SearchField, second: song.name as NSString?), KotlinPair(first: SearchField.artist as __SearchField, second: song.albumArtist as NSString?)],
                popularity: 0
            )
        }
        let clock = ContinuousClock()

        var index: SearchIndex<AnyObject>?
        let build = clock.measure { index = SearchIndexCompanion.shared.build(documents: documents) }

        var hits: [SearchHit<AnyObject>] = []
        let query = clock.measure { hits = index!.search(query: "ra") { _ in true } }
        // Reading a Kotlin list from Swift copies all of it (it arrives as an `__NSArrayI_Transfer`): the same
        // query with nothing accepted ranks as much but copies nothing, so the difference is the copy.
        let rounds = 20
        let copied = clock.measure { for _ in 0..<rounds { _ = index!.search(query: "ra") { _ in true } } } / rounds
        let ranked = clock.measure { for _ in 0..<rounds { _ = index!.search(query: "ra") { _ in false } } } / rounds

        print("SearchPerformance: \(songs.count) songs indexed in \(build); \"ra\" found \(hits.count) in \(query); reading them from Swift costs \(copied - ranked)")
        #expect(hits.count > 1_000)
        #expect(build < .seconds(30))
    }
}
