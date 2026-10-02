import Foundation
import Shared
import Testing
@testable import S2

/// `PlayIntent`, after Shuttle Podcasts' `PlayIntentTests`: the intent is set the instant a play is asked for, cleared
/// the instant a pause is, and cleared when the play fails or the player gives up; loading is intended without audio.
@MainActor
struct PlayIntentTests {
    /// A player whose `play` takes at once, as the Kotlin controller's does on the main thread.
    private final class FakePlayer: PlayIntentPlayer {
        var playsWhenReady = false
        var isAudible = false
        /// Whether there's a current item for `play` to play.
        var hasQueue = true
        private(set) var commands: [String] = []

        func play() {
            commands.append("play")
            if hasQueue { playsWhenReady = true }
        }

        func pause() {
            commands.append("pause")
            playsWhenReady = false
            isAudible = false
        }
    }

    private let player = FakePlayer()
    private let noSongs = MediaActionResultMessage(message: MediaActionMessageNoSongs.shared, action: nil)

    private func makeSut(timeout: Duration = .seconds(30)) -> PlayIntent {
        PlayIntent(player: player, timeout: timeout)
    }

    @Test func aFreshIntentWantsNothing() {
        let sut = makeSut()
        #expect(!sut.isPlayIntended)
        #expect(!sut.isLoading)
        #expect(sut.loadingKey == nil)
    }

    @Test func playSetsTheIntentBeforeAnyAudio() {
        let sut = makeSut()
        sut.play()
        #expect(player.commands == ["play"])
        #expect(sut.isPlayIntended)
        #expect(sut.isLoading, "intended but nothing playing yet")

        player.isAudible = true
        sut.playerChanged()
        #expect(sut.isPlayIntended)
        #expect(!sut.isLoading)
    }

    @Test func pauseClearsTheIntentAtOnce() {
        let sut = makeSut()
        sut.play()
        player.isAudible = true
        sut.playerChanged()

        sut.pause()
        #expect(player.commands == ["play", "pause"])
        #expect(!sut.isPlayIntended)
        #expect(!sut.isLoading)
    }

    @Test func playWithNothingQueuedLeavesNoIntent() {
        player.hasQueue = false
        let sut = makeSut()
        sut.play()
        #expect(!sut.isPlayIntended)
    }

    /// The player gave up on a failed item, or the queue played out: it no longer intends to play.
    @Test func thePlayerGivingUpClearsTheIntent() {
        let sut = makeSut()
        sut.play()
        player.playsWhenReady = false
        sut.playerChanged()
        #expect(!sut.isPlayIntended)
        #expect(!sut.isLoading)
    }

    @Test func audioPlayingIsIntendedHoweverItStarted() {
        let sut = makeSut()
        player.isAudible = true
        sut.playerChanged()
        #expect(sut.isPlayIntended)
        #expect(!sut.isLoading)
    }

    @Test func toggleFollowsTheIntent() {
        let sut = makeSut()
        sut.toggle()
        #expect(sut.isPlayIntended)
        sut.toggle()
        #expect(!sut.isPlayIntended)
        #expect(player.commands == ["play", "pause"])
    }

    // MARK: Pending plays

    /// A `MediaAction` reads its songs and the player's `load` clears its own intent before it plays, so the play is
    /// intended from the tap until its result, even over what's still playing.
    @Test func aPendingPlayIsIntendedFromTheTapUntilItsResult() {
        player.playsWhenReady = true
        player.isAudible = true
        let sut = makeSut()
        let ticket = sut.begin(key: "ok computer")
        #expect(sut.isPlayIntended)
        #expect(sut.isLoading, "a new play is under way over the old queue")
        #expect(sut.loadingKey == "ok computer")

        // The load stops the old queue and clears the player's intent; the play follows once it's ready.
        player.playsWhenReady = false
        player.isAudible = false
        sut.playerChanged()
        #expect(sut.isPlayIntended)
        player.playsWhenReady = true
        sut.finished(ticket, result: MediaActionResultNone.shared)
        #expect(sut.isPlayIntended)
        #expect(sut.loadingKey == "ok computer", "still opening the stream")

        player.isAudible = true
        sut.playerChanged()
        #expect(sut.isPlayIntended)
        #expect(!sut.isLoading)
        #expect(sut.loadingKey == nil)
    }

    @Test func aPlaysOwnFailureClearsIt() {
        let sut = makeSut()
        sut.finished(sut.begin(), result: noSongs)
        #expect(!sut.isPlayIntended)
        let failed = MediaActionResultMessage(message: MediaActionMessagePlaybackFailed(reason: nil), action: nil)
        sut.finished(sut.begin(key: "a"), result: failed)
        #expect(!sut.isPlayIntended)
        #expect(sut.loadingKey == nil)
    }

    @Test func aSupersededActionsResultDoesNotSettleTheNewOne() {
        let sut = makeSut()
        let superseded = sut.begin(key: "first")
        let ticket = sut.begin(key: "second")
        sut.finished(superseded, result: noSongs)
        #expect(sut.isPlayIntended)
        #expect(sut.loadingKey == "second")
        sut.finished(ticket, result: noSongs)
        #expect(!sut.isPlayIntended)
    }

    @Test func aNoticeIsNotAFailure() {
        let added = MediaActionResultMessage(message: MediaActionMessageAddedToQueue(songCount: 1), action: nil)
        #expect(!PlayIntent.isFailure(added))
        #expect(!PlayIntent.isFailure(MediaActionResultNone.shared))
        #expect(PlayIntent.isFailure(noSongs))
    }

    @Test func onlyPlayingActionsArePlays() {
        let selection = MediaSelectionSongs(songs: TestSongs.demo)
        #expect(PlayIntent.plays(MediaActionPlay(selection: selection, position: 0, context: selection.playContext)))
        #expect(PlayIntent.plays(MediaActionShuffle(selection: selection, context: selection.playContext)))
        #expect(!PlayIntent.plays(MediaActionPlayNext(selection: selection)))
    }

    /// A second tap while the play is being prepared pauses at once, and if the play starts anyway it's paused again.
    @Test func pausingAPendingPlayStandsWhenItStartsAnyway() {
        let sut = makeSut()
        let ticket = sut.begin()
        sut.toggle()
        #expect(!sut.isPlayIntended)
        #expect(player.commands == ["pause"])

        player.playsWhenReady = true
        sut.finished(ticket, result: MediaActionResultNone.shared)
        #expect(player.commands == ["pause", "pause"])
        #expect(!sut.isPlayIntended)
    }

    @Test func aPausedPendingPlayThatFailsNeedsNoSecondPause() {
        let sut = makeSut()
        let ticket = sut.begin()
        sut.pause()
        sut.finished(ticket, result: noSongs)
        #expect(player.commands == ["pause"])
    }

    @Test func aPendingPlayGivesUpAfterItsTimeout() async {
        let sut = makeSut(timeout: .milliseconds(50))
        sut.begin(key: "a")
        #expect(sut.isPlayIntended)
        #expect(await waitUntil { !sut.isPlayIntended })
        #expect(sut.loadingKey == nil)
    }

    @Test func listenersHearEachChangeOnce() {
        let sut = makeSut()
        var heard = 0
        let token = sut.addListener { heard += 1 }
        sut.play()
        sut.playerChanged()
        #expect(heard == 1)
        sut.removeListener(token)
        sut.pause()
        #expect(heard == 1)
    }

    // MARK: Through the real player

    /// The Kotlin controller's `play` takes on the main thread, so the intent is set as the call returns, while the
    /// engine has only been asked to play.
    @Test func playOnTheRealPlayerIsIntendedBeforeTheEnginePlays() async throws {
        let engine = FakeAudioEngine()
        let graph = makeTestGraph(audioPlayer: EngineAudioPlayer(engine: engine))
        let controller = graph.playerController
        let sut = PlayIntent(following: controller)
        _ = try await controller.queueOperations.setQueue(songs: TestSongs.demo, shuffleSongs: nil, position: 0, context: PlayContextNone.shared)
        controller.load(seekPosition: nil, skipUnloadable: false) { _ in }
        #expect(await waitUntil { !engine.loads.isEmpty })
        let id = try #require(engine.loads.first?.current.id)
        engine.emit(.state(.paused, trackId: id))
        #expect(!sut.isPlayIntended)

        sut.play()
        #expect(sut.isPlayIntended && sut.isLoading)
        #expect(engine.commands.last == "play")

        engine.emit(.state(.playing, trackId: id))
        #expect(await waitUntil { !sut.isLoading })
        sut.pause()
        #expect(!sut.isPlayIntended)
    }

    /// Playing the context that's already playing keeps the queue's entries (its content version doesn't move), so
    /// only the action's own result says the player has it: through the real graph's `MediaActionsViewModel`.
    @Test func aPlayDispatchedThroughSendIsIntendedUntilItPlays() async throws {
        let engine = FakeAudioEngine()
        let graph = makeTestGraph(audioPlayer: EngineAudioPlayer(engine: engine))
        let controller = graph.playerController
        let sut = PlayIntent(following: controller)
        _ = try await controller.queueOperations.setQueue(songs: TestSongs.demo, shuffleSongs: nil, position: 0, context: PlayContextNone.shared)
        controller.load(seekPosition: nil, skipUnloadable: false) { _ in }
        #expect(await waitUntil { !engine.loads.isEmpty })
        let id = try #require(engine.loads.first?.current.id)
        engine.emit(.state(.playing, trackId: id))
        #expect(await waitUntil { sut.isPlayIntended && !sut.isLoading })

        let play = MediaActionPlay(selection: MediaSelectionSongs(songs: TestSongs.demo), position: 0, context: PlayContextNone.shared)
        graph.mediaActionsViewModel.send(play, key: "demo", intent: sut)
        #expect(sut.isPlayIntended && sut.isLoading)
        #expect(sut.loadingKey == "demo")

        // The play reloads the engine and returns once it reports the load.
        #expect(await waitUntil { engine.loads.count > 1 })
        #expect(sut.isPlayIntended)
        let reload = try #require(engine.loads.last?.current.id)
        engine.emit(.state(.playing, trackId: reload))
        #expect(await waitUntil { !sut.isLoading })
        #expect(sut.isPlayIntended)
        #expect(sut.loadingKey == nil)
    }
}
