import AppIntents
import Testing
@testable import S2

/// The App Intents (#758) hand their work to the performers the app registers in `IntentPerformers`, and fail with
/// `notReady` when there are none. Serialized: the registry is global, and each test puts the app's back.
@MainActor
@Suite(.serialized)
struct SystemIntentsTests {
    private final class FakePerformer: PlaybackIntentPerforming, LibraryIntentPerforming {
        private(set) var calls: [String] = []

        func togglePlayback() async throws { calls.append("toggle") }
        func setPlaying(_ playing: Bool) async throws { calls.append("setPlaying \(playing)") }
        func skipToNext() async throws { calls.append("next") }
        func shuffleLibrary() async throws { calls.append("shuffle") }
        func playPlaylist(id: Int64, shuffled: Bool) async throws { calls.append("playlist \(id) \(shuffled)") }
        func playlists() async throws -> [PlaylistEntity] {
            [PlaylistEntity(id: 3, name: "Morning", songCount: 1), PlaylistEntity(id: 4, name: "Evening Jazz", songCount: 20)]
        }
    }

    /// Runs `body` with `performer` registered (nil for none), then puts back what the app registered.
    private func with(_ performer: FakePerformer?, _ body: () async throws -> Void) async rethrows {
        let playback = IntentPerformers.playback
        let library = IntentPerformers.library
        IntentPerformers.playback = performer
        IntentPerformers.library = performer
        defer {
            IntentPerformers.playback = playback
            IntentPerformers.library = library
        }
        try await body()
    }

    @Test func thePlaybackIntentsReachThePerformer() async throws {
        let performer = FakePerformer()
        try await with(performer) {
            _ = try await TogglePlaybackIntent().perform()
            _ = try await SkipToNextIntent().perform()
            var set = SetPlaybackIntent()
            set.value = false
            _ = try await set.perform()
        }
        #expect(performer.calls == ["toggle", "next", "setPlaying false"])
    }

    @Test func theLibraryIntentsReachThePerformer() async throws {
        let performer = FakePerformer()
        try await with(performer) {
            _ = try await ShuffleLibraryIntent().perform()
            _ = try await PlayPlaylistIntent(playlist: PlaylistEntity(id: 4, name: "Evening Jazz", songCount: 20), shuffle: true).perform()
        }
        #expect(performer.calls == ["shuffle", "playlist 4 true"])
    }

    @Test func withoutAPerformerAnIntentFails() async {
        await with(nil) {
            await #expect(throws: ShuttleIntentError.notReady) { _ = try await TogglePlaybackIntent().perform() }
            await #expect(throws: ShuttleIntentError.notReady) { _ = try await ShuffleLibraryIntent().perform() }
        }
    }

    @Test func playlistsAreFoundByIdAndName() async throws {
        let performer = FakePerformer()
        try await with(performer) {
            let query = PlaylistEntityQuery()
            #expect(try await query.entities(for: [4]).map(\.name) == ["Evening Jazz"])
            #expect(try await query.entities(matching: "jazz").map(\.id) == [4])
            #expect(try await query.suggestedEntities().count == 2)
        }
    }
}
