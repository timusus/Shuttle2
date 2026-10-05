import Foundation
import Shared
import Testing
@testable import S2

/// `AppIntentPerformer` (#758): Siri's, the widgets' and the control's plays and pauses go through `PlayIntent` as the
/// app's own buttons do, nothing queued shuffles the library, and library plays dispatch the shared `MediaAction`s.
@MainActor
struct AppIntentPerformerTests {
    private final class FakePlayer: PlayIntentPlayer {
        var playsWhenReady = false
        var isAudible = false
        private(set) var commands: [String] = []

        func play() {
            commands.append("play")
            playsWhenReady = true
        }

        func pause() {
            commands.append("pause")
            playsWhenReady = false
            isAudible = false
        }
    }

    private final class FakeLibrary: IntentLibrary {
        let shuffle = MediaActionShuffle(selection: MediaSelectionPlaylists(playlists: []))
        let playlist = MediaActionPlay(selection: MediaSelectionPlaylists(playlists: []), position: 0)
        var knownIDs: Set<Int64> = [7]
        private(set) var asked: [(id: Int64, shuffled: Bool)] = []

        func playlists() async throws -> [PlaylistEntity] {
            [PlaylistEntity(id: 7, name: "Road Trip", songCount: 12)]
        }

        func shuffleAll() -> any MediaAction {
            shuffle
        }

        func playPlaylist(id: Int64, shuffled: Bool) async throws -> (any MediaAction)? {
            asked.append((id, shuffled))
            return knownIDs.contains(id) ? playlist : nil
        }
    }

    private let player = FakePlayer()
    private let library = FakeLibrary()
    private let intent: PlayIntent
    init() {
        intent = PlayIntent(player: player)
    }

    /// The performer over this test's fakes; `state` is read when it runs, so set it up before acting.
    private final class State {
        var hasQueue = true
        var skips = 0
        var dispatched: [any MediaAction] = []
        var result: any MediaActionResult = MediaActionResultNone.shared
    }

    private let state = State()

    private func makeSut() -> AppIntentPerformer {
        let state = state
        return AppIntentPerformer(
            intent: intent,
            hasQueue: { state.hasQueue },
            skipToNext: { state.skips += 1 },
            library: library,
            dispatch: { action in
                state.dispatched.append(action)
                return state.result
            }
        )
    }

    @Test func toggleResumesTheQueueAsAnAppIntent() async throws {
        try await makeSut().togglePlayback()

        #expect(player.commands == ["play"])
        #expect(intent.lastCommand == PlayIntent.Command(plays: true, source: .appIntent))
        #expect(state.dispatched.isEmpty)
    }

    @Test func toggleWhilePlayingPauses() async throws {
        let sut = makeSut()
        intent.play()

        try await sut.togglePlayback()

        #expect(player.commands == ["play", "pause"])
        #expect(intent.lastCommand == PlayIntent.Command(plays: false, source: .appIntent))
        #expect(!intent.wantsPlayback)
    }

    @Test func toggleWithNothingQueuedShufflesTheLibrary() async throws {
        state.hasQueue = false

        try await makeSut().togglePlayback()

        #expect(player.commands.isEmpty)
        #expect(state.dispatched.count == 1)
        #expect(state.dispatched.first === library.shuffle)
    }

    @Test func setPlayingPlaysAndPauses() async throws {
        let sut = makeSut()

        try await sut.setPlaying(true)
        try await sut.setPlaying(true)
        try await sut.setPlaying(false)

        #expect(player.commands == ["play", "play", "pause"])
    }

    @Test func skipToNextSkips() async throws {
        try await makeSut().skipToNext()

        #expect(state.skips == 1)
    }

    @Test func shuffleLibraryDispatchesTheShuffle() async throws {
        try await makeSut().shuffleLibrary()

        #expect(state.dispatched.count == 1)
        #expect(state.dispatched.first === library.shuffle)
    }

    @Test func aPlaylistPlayDispatchesItsAction() async throws {
        try await makeSut().playPlaylist(id: 7, shuffled: true)

        #expect(library.asked.count == 1)
        #expect(library.asked.first?.id == 7)
        #expect(library.asked.first?.shuffled == true)
        #expect(state.dispatched.first === library.playlist)
    }

    @Test func aPlaylistNoLongerThereFails() async {
        await #expect(throws: ShuttleIntentError.playlistNotFound) {
            try await makeSut().playPlaylist(id: 99, shuffled: false)
        }
        #expect(state.dispatched.isEmpty)
    }

    @Test func anEmptyLibraryFailsWithNoSongs() async {
        state.result = MediaActionResultMessage(message: MediaActionMessageNoSongs.shared, action: nil)

        await #expect(throws: ShuttleIntentError.noSongs) {
            try await makeSut().shuffleLibrary()
        }
        #expect(!intent.wantsPlayback, "a failed play leaves no play intended")
    }

    @Test func aFailedPlayFails() async {
        state.result = MediaActionResultMessage(message: MediaActionMessagePlaybackFailed(reason: nil), action: nil)

        await #expect(throws: ShuttleIntentError.playbackFailed) {
            try await makeSut().playPlaylist(id: 7, shuffled: false)
        }
    }

    @Test func aDispatchedPlayIsIntendedUntilThePlayerHasIt() async throws {
        try await makeSut().shuffleLibrary()

        #expect(intent.wantsPlayback)
    }

    @Test func thePlaylistsComeFromTheLibrary() async throws {
        let playlists = try await makeSut().playlists()

        #expect(playlists == [PlaylistEntity(id: 7, name: "Road Trip", songCount: 12)])
    }
}
