import Shared
import Testing
@testable import S2

/// Which copies a song's Play From menu offers (#1069).
@MainActor
struct PlayFromMenuTests {
    private let local = TestSongs.song(1, "Reckoner", artist: "Radiohead", album: "In Rainbows", durationMs: 1, provider: .shuttle, path: "/a.flac", mimeType: "audio/flac", bitRate: 1411)
    private let server = TestSongs.song(2, "Reckoner", artist: "Radiohead", album: "In Rainbows", durationMs: 1, provider: .jellyfin, path: "jellyfin://item/2", mimeType: "audio/mpeg", bitRate: 320)

    private func context(merge: Bool = true, offline: Bool = false) -> CopyContext {
        CopyContext(
            mergeCopies: merge,
            preferences: SongCopyPreferences(primaryCopies: [], primaryAlbums: [], preferred: .local),
            availability: SongCopyAvailability(offline: offline, downloadedPaths: [], unreachableProviders: [], missingSongIds: [], streamingCapKbps: nil)
        )
    }

    private func merged() -> Song {
        TestSongs.song(1, "Reckoner", artist: "Radiohead", album: "In Rainbows", durationMs: 1, provider: .shuttle, path: "/a.flac", copies: [local, server])
    }

    @Test func twoPlayableCopiesAreOfferedBestFirstWithTheFirstPlaying() {
        let choices = PlayFromChoice.choices(for: merged(), context: context())
        #expect(choices.map(\.copy.id) == [1, 2])
        #expect(choices.map(\.isPlaying) == [true, false])
        #expect(choices.map(\.title) == [MediaProviderType.shuttle.title, MediaProviderType.jellyfin.title])
    }

    @Test func noMenuWhenMergingIsOff() {
        #expect(PlayFromChoice.choices(for: merged(), context: context(merge: false)).isEmpty)
    }

    @Test func noMenuWhenOnlyOneCopyCanPlay() {
        #expect(PlayFromChoice.choices(for: merged(), context: context(offline: true)).isEmpty)
    }

    @Test func noMenuForASongWithoutCopiesOrBeforeTheContextLoads() {
        #expect(PlayFromChoice.choices(for: local, context: context()).isEmpty)
        #expect(PlayFromChoice.choices(for: merged(), context: nil).isEmpty)
    }
}
