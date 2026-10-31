import Shared
import Testing
@testable import S2

/// An album's "n Sources" and its Primary Source menu appear only for an album held by two or more sources (#1069).
struct AlbumSourcesMenuTests {
    private func song(_ id: Int64, copies: [MediaProviderType]) -> Song {
        let all = copies.enumerated().map { index, provider in
            TestSongs.song(id * 10 + Int64(index), "T\(id)", artist: "A", album: "Al", durationMs: 1, provider: provider, path: "\(provider.name)://\(id)")
        }
        return TestSongs.song(id, "T\(id)", artist: "A", album: "Al", durationMs: 1, provider: copies[0], path: all[0].path, copies: all)
    }

    private func context(merge: Bool = true) -> CopyContext {
        CopyContext(
            mergeCopies: merge,
            preferences: SongCopyPreferences(primaryCopies: [], primaryAlbums: [], preferred: .local),
            availability: SongCopyAvailability(offline: false, downloadedPaths: [], unreachableProviders: [], missingSongIds: [], streamingCapKbps: nil)
        )
    }

    @Test func anAlbumInTwoSourcesCountsThemAndCountsSongsPerSource() throws {
        let songs = [song(1, copies: [.shuttle, .jellyfin]), song(2, copies: [.shuttle])]
        let sources = try #require(AlbumSources.choices(for: songs, context: context()))
        #expect(sources.headerText == "2 Sources")
        #expect(sources.sources.map { $0.songsText(of: sources.songCount) } == ["2 of 2 songs", "1 of 2 songs"])
        #expect(sources.automatic)
    }

    @Test func noChoiceForOneSourceOrWithMergingOffOrBeforeTheContextLoads() {
        let one = [song(1, copies: [.shuttle])]
        let two = [song(1, copies: [.shuttle, .jellyfin])]
        #expect(AlbumSources.choices(for: one, context: context()) == nil)
        #expect(AlbumSources.choices(for: two, context: context(merge: false)) == nil)
        #expect(AlbumSources.choices(for: two, context: nil) == nil)
    }
}
