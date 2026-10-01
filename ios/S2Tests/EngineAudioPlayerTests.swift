import Foundation
import Shared
import Testing
@testable import S2

/// The Kotlin `IosAudioPlayer` adapter over a fake engine: each call reaches the engine, each engine
/// report reaches the Kotlin listener, and, with the real `IosPlayerController` on top, the queue is
/// fed on into the engine and a track that fails to load is skipped.
@MainActor
struct EngineAudioPlayerTests {
    private final class RecordingListener: NSObject, IosAudioPlayerListener {
        var calls: [String] = []

        func onStateChanged(trackId: String, state: IosAudioPlayerState) {
            calls.append("state \(trackId) \(Self.name(state))")
        }

        func onTransition(trackId: String) {
            calls.append("transition \(trackId)")
        }

        func onFailed(trackId: String, message: String) {
            calls.append("failed \(trackId) \(message)")
        }

        func onPosition(trackId: String, positionMs: Int64) {
            calls.append("position \(trackId) \(positionMs)")
        }

        func onSeekUnsupported(trackId: String, positionMs: Int64) {
            calls.append("seekUnsupported \(trackId) \(positionMs)")
        }

        private static func name(_ state: IosAudioPlayerState) -> String {
            switch state {
            case .idle: "idle"
            case .loading: "loading"
            case .playing: "playing"
            case .paused: "paused"
            case .ended: "ended"
            }
        }
    }

    private let engine = FakeAudioEngine()
    private let listener = RecordingListener()
    private let player: EngineAudioPlayer

    init() {
        player = EngineAudioPlayer(engine: engine)
        player.setListener(listener: listener)
    }

    private func track(_ id: String, _ url: String = "file:///music/song.flac", gainDb: Float = 0) -> IosAudioTrack {
        IosAudioTrack(id: id, url: url, headers: ["X-Token": "t"], gainDb: gainDb, expectedDurationMs: 180_000)
    }

    private func engineTrack(_ id: String, _ url: String = "file:///music/song.flac") -> EngineTrack {
        EngineTrack(id: id, url: URL(string: url)!, headers: ["X-Token": "t"], gainDb: 0, expectedDurationMs: 180_000)
    }

    // MARK: - Forwarding

    @Test func engineReportsReachTheListenerInOrder() {
        engine.emit(.state(.loading, trackId: "a"))
        engine.emit(.state(.playing, trackId: "a"))
        engine.emit(.position(trackId: "a", ms: 1_200))
        engine.emit(.seekUnsupported(trackId: "a", ms: 30_000))
        engine.emit(.transition(trackId: "b"))
        engine.emit(.failed(trackId: "b", message: "bad data"))
        engine.emit(.state(.ended, trackId: "b"))
        engine.emit(.state(.idle, trackId: nil))

        #expect(listener.calls == [
            "state a loading",
            "state a playing",
            "position a 1200",
            "seekUnsupported a 30000",
            "transition b",
            "failed b bad data",
            "state b ended",
            "state  idle",
        ])
    }

    @Test func eachCallIsOneEngineCall() {
        player.load(current: track("a"), next: nil, startMs: 1_500, playWhenReady: false)
        player.setNext(next: track("b", "https://example.com/b.mp3"))
        player.setNext(next: nil)
        player.play()
        player.pause()
        player.seek(positionMs: 2_000)
        player.setSpeed(speed: 1.5)
        player.stop()

        #expect(engine.loads == [FakeAudioEngine.Load(current: engineTrack("a"), next: nil, startMs: 1_500, playWhenReady: false)])
        #expect(engine.nexts == [engineTrack("b", "https://example.com/b.mp3"), nil])
        #expect(engine.commands == ["play", "pause", "seek 2000", "speed 1.5", "stop"])
    }

    @Test func theSessionHooksRunBeforePlayingAndOnPausing() {
        var hooks: [String] = []
        player.onWillPlay = {
            hooks.append("willPlay \(engine.commands.count)")
            return true
        }
        player.onPaused = { hooks.append("paused \(engine.commands.count)") }

        player.load(current: track("a"), next: nil, startMs: 0, playWhenReady: false)
        player.load(current: track("a"), next: nil, startMs: 0, playWhenReady: true)
        player.play()
        player.pause()

        // A load that plays and play() activate first; the pause is reported after the engine paused.
        #expect(hooks == ["willPlay 0", "willPlay 0", "paused 2"])
        #expect(engine.loads.count == 2)
    }

    @Test func aSessionThatWontActivateCancelsThePlay() {
        player.onWillPlay = { false }

        player.load(current: track("a"), next: nil, startMs: 0, playWhenReady: true)
        player.play()

        // The load still happens, paused; the play never reaches the engine. Each refusal is a paused
        // report for that track, delivered on the call so it stays ahead of the engine's own events.
        #expect(engine.loads.map(\.playWhenReady) == [false])
        #expect(engine.commands.isEmpty)
        #expect(listener.calls == ["state a paused", "state a paused"])
    }

    @Test func aPlayWhileTheEngineIsPlayingIsNotAskedOfTheSessionNorRefused() {
        var asked = 0
        player.onWillPlay = {
            asked += 1
            return false
        }
        player.load(current: track("a"), next: nil, startMs: 0, playWhenReady: false)
        engine.emit(.state(.playing, trackId: "a"))

        player.play()

        #expect(asked == 0)
        #expect(engine.commands.isEmpty)
        #expect(listener.calls == ["state a playing"])

        // Once paused, a play is the session's to refuse again.
        player.pause()
        player.play()
        #expect(asked == 1)
        #expect(listener.calls == ["state a playing", "state a paused"])
    }

    @Test func positionAndDurationOnlyCountForTheTrackKotlinThinksIsCurrent() {
        engine.position = (uid: "a", ms: 500)
        engine.durationMs = 3_000
        #expect(player.positionMs() == -1)

        player.load(current: track("a"), next: nil, startMs: 0, playWhenReady: true)
        #expect(player.positionMs() == 500)
        #expect(player.durationMs() == 3_000)

        // The engine is already on b, but its transition hasn't reached Kotlin yet.
        engine.position = (uid: "b", ms: 10)
        #expect(player.positionMs() == -1)
        #expect(player.durationMs() == -1)

        engine.emit(.transition(trackId: "b"))
        #expect(player.positionMs() == 10)

        player.stop()
        #expect(player.positionMs() == -1)
    }

    // MARK: - Errors

    @Test func aTrackWhoseURLDoesNotParseFailsAfterTheCallReturns() async {
        player.load(current: track("a", ""), next: nil, startMs: 0, playWhenReady: true)

        #expect(engine.loads.isEmpty)
        #expect(engine.commands == ["stop"])
        #expect(listener.calls.isEmpty)

        await drainMainQueue()
        #expect(listener.calls == ["failed a Not a URL: "])
    }

    @Test func aNextWhoseURLDoesNotParseIsClearedAndFails() async {
        player.setNext(next: track("b", ""))

        #expect(engine.nexts == [nil])
        await drainMainQueue()
        #expect(listener.calls == ["failed b Not a URL: "])
    }

    @Test func aReplacedEngineIsStoppedAndSilenced() {
        let rebuilt = FakeAudioEngine()
        player.load(current: track("a"), next: nil, startMs: 0, playWhenReady: true)

        player.replaceEngine(rebuilt)

        #expect(engine.commands == ["stop"])
        #expect(!engine.hasEventHandler)
        #expect(rebuilt.hasEventHandler)
        rebuilt.emit(.state(.playing, trackId: "a"))
        #expect(listener.calls == ["state a playing"])
    }

    private func coefficients(_ values: [Double]) -> KotlinDoubleArray {
        let array = KotlinDoubleArray(size: Int32(values.count))
        values.enumerated().forEach { array.set(index: Int32($0.offset), value: $0.element) }
        return array
    }

    @Test func theEqualizerReachesTheEngineAsItsCoefficients() {
        player.setEqualizer(enabled: true, preampDb: -3.5, coefficients: coefficients([1.02, -1.93, 0.92, -1.93, 0.95]))

        #expect(engine.equalizers == [.init(enabled: true, preampDb: -3.5, coefficients: [1.02, -1.93, 0.92, -1.93, 0.95])])
    }

    @Test func theEngineRateIsTheRateTheEqualizerIsDesignedAt() {
        engine.outputSampleRate = 44_100
        #expect(player.engineSampleRate() == 44_100)
    }

    @Test func aReplacedEngineGetsTheEqualizer() {
        player.setEqualizer(enabled: true, preampDb: -1, coefficients: coefficients([1, 0, 0, 0, 0]))
        let rebuilt = FakeAudioEngine()

        player.replaceEngine(rebuilt)

        #expect(rebuilt.equalizers == [.init(enabled: true, preampDb: -1, coefficients: [1, 0, 0, 0, 0])])
    }

    @Test func aReplacedEngineWithNoEqualizerSetGetsNone() {
        let rebuilt = FakeAudioEngine()
        player.replaceEngine(rebuilt)
        #expect(rebuilt.equalizers.isEmpty)
    }

    // MARK: - With the Kotlin controller

    @Test func theSavedEqualizerReachesTheEngineWithTheController() {
        let graph = makeTestGraph(audioPlayer: player)
        _ = graph.playerController

        #expect(engine.equalizers.count == 1)
        #expect(engine.equalizers.first?.coefficients.count == 50) // ten bands, five each
    }

    private func queueDemoSongs(on graph: IosAppGraph, skipUnloadable: Bool) async throws {
        let controller = graph.playerController
        _ = try await controller.queueOperations.setQueue(songs: TestSongs.demo, shuffleSongs: nil, position: 0, context: PlayContextNone.shared)
        controller.load(seekPosition: nil, skipUnloadable: skipUnloadable) { _ in }
    }

    @Test func theKotlinControllerFeedsTheNextSongAtEachTransition() async throws {
        let graph = makeTestGraph(audioPlayer: player)
        try await queueDemoSongs(on: graph, skipUnloadable: false)

        // The current song is loaded alone, then the next is handed over for a gapless join.
        #expect(await waitUntil { engine.nexts.last??.url.absoluteString == "demo://2" })
        let first = try #require(engine.loads.first)
        #expect(first.current.url.absoluteString == "demo://1")
        #expect(first.next == nil)
        #expect(!first.playWhenReady)

        // The engine moves on to song 2: Kotlin hands it song 3.
        let second = try #require(engine.nexts.last??.id)
        engine.emit(.transition(trackId: second))
        #expect(await waitUntil { engine.nexts.last??.url.absoluteString == "demo://3" })
        #expect(graph.playerController.queueOperations.queueStateFlow.value.currentItem?.song.name == "Hyperballad")
        #expect(engine.loads.count == 1)
    }

    @Test func aTrackThatFailsToLoadIsSkippedForTheNext() async throws {
        let graph = makeTestGraph(audioPlayer: player)
        try await queueDemoSongs(on: graph, skipUnloadable: true)
        #expect(await waitUntil { !engine.loads.isEmpty })
        let first = try #require(engine.loads.first?.current.id)

        engine.emit(.failed(trackId: first, message: "unsupported"))

        #expect(await waitUntil { engine.loads.count == 2 })
        #expect(engine.loads.last?.current.url.absoluteString == "demo://2")
        #expect(graph.playerController.queueOperations.queueStateFlow.value.currentItem?.song.name == "Hyperballad")
    }

    private func intendsToPlay(_ controller: IosPlayerController) -> Bool {
        controller.playWhenReadyFlow.value.boolValue
    }

    @Test func aSessionRefusalClearsIntentWithoutFinishingTheLoadEarly() async throws {
        player.onWillPlay = { false }
        let graph = makeTestGraph(audioPlayer: player)
        let controller = graph.playerController
        _ = try await controller.queueOperations.setQueue(songs: TestSongs.demo, shuffleSongs: nil, position: 0, context: PlayContextNone.shared)
        var completed = false
        controller.skipToNext(ignoreRepeat: true) { _ in completed = true }

        #expect(await waitUntil { engine.loads.count == 1 })
        #expect(engine.loads.last?.playWhenReady == false)
        #expect(!engine.commands.contains("play"))
        #expect(!intendsToPlay(controller))
        #expect(!completed)
        #expect(controller.playbackStateFlow.value is PlaybackState.Loading)

        let id = try #require(engine.loads.last?.current.id)
        engine.emit(.state(.loading, trackId: id))
        #expect(controller.playbackStateFlow.value is PlaybackState.Loading)
        engine.emit(.state(.paused, trackId: id))
        #expect(await waitUntil { controller.playbackStateFlow.value is PlaybackState.Paused })
        #expect(completed)
        #expect(!intendsToPlay(controller))
        #expect(controller.queueOperations.queueStateFlow.value.currentItem?.song.name == "Hyperballad")

        // The cancelled intent is what a later queue change preserves: the new song loads paused.
        let next = try #require(controller.queueOperations.getNext(ignoreRepeat: true))
        controller.queueOperations.setCurrentItem(currentItem: next)
        #expect(await waitUntil { engine.loads.count == 2 })
        #expect(engine.loads.last?.playWhenReady == false)
        #expect(!intendsToPlay(controller))

        player.onWillPlay = { true }
        controller.play()
        #expect(await waitUntil { engine.commands.contains("play") })
        #expect(intendsToPlay(controller))
    }

    @Test func aRefusedPlayOfAReadySongClearsIntentWithNoEngineTransition() async throws {
        let graph = makeTestGraph(audioPlayer: player)
        let controller = graph.playerController
        try await queueDemoSongs(on: graph, skipUnloadable: false)
        #expect(await waitUntil { !engine.loads.isEmpty })
        let id = try #require(engine.loads.first?.current.id)
        engine.emit(.state(.paused, trackId: id))
        #expect(await waitUntil { controller.playbackStateFlow.value is PlaybackState.Paused })

        player.onWillPlay = { false }
        controller.play()

        #expect(!engine.commands.contains("play"))
        #expect(!intendsToPlay(controller))
        #expect(controller.playbackStateFlow.value is PlaybackState.Paused)
    }

    @Test func aPlayWhileTheSongIsPlayingKeepsItPlayingWhenTheSessionWouldRefuse() async throws {
        let graph = makeTestGraph(audioPlayer: player)
        let controller = graph.playerController
        try await queueDemoSongs(on: graph, skipUnloadable: false)
        #expect(await waitUntil { !engine.loads.isEmpty })
        let id = try #require(engine.loads.first?.current.id)
        controller.play()
        engine.emit(.state(.playing, trackId: id))
        #expect(await waitUntil { controller.playbackStateFlow.value is PlaybackState.Playing })

        player.onWillPlay = { false }
        controller.play()

        #expect(intendsToPlay(controller))
        #expect(controller.playbackStateFlow.value is PlaybackState.Playing)
    }

    @Test func aPausedReportForTheSongASkipReplacedIsIgnored() async throws {
        let graph = makeTestGraph(audioPlayer: player)
        let controller = graph.playerController
        try await queueDemoSongs(on: graph, skipUnloadable: false)
        #expect(await waitUntil { !engine.loads.isEmpty })
        let first = try #require(engine.loads.first?.current.id)
        controller.play()
        engine.emit(.state(.playing, trackId: first))
        #expect(await waitUntil { controller.playbackStateFlow.value is PlaybackState.Playing })
        #expect(intendsToPlay(controller))
        #expect(await waitUntil { engine.nexts.last??.id != nil })

        controller.skipToNext(ignoreRepeat: true, completion: nil)
        #expect(await waitUntil { engine.loads.count == 2 })
        engine.emit(.state(.paused, trackId: first))

        #expect(intendsToPlay(controller))
        #expect(controller.queueOperations.queueStateFlow.value.currentItem?.song.name == "Hyperballad")
        #expect(controller.playbackStateFlow.value is PlaybackState.Loading)
    }

    @Test func aLoadCompletionThatPlaysKeepsTheNewIntent() async throws {
        player.onWillPlay = { false }
        let graph = makeTestGraph(audioPlayer: player)
        let controller = graph.playerController
        _ = try await controller.queueOperations.setQueue(songs: TestSongs.demo, shuffleSongs: nil, position: 0, context: PlayContextNone.shared)
        controller.skipToNext(ignoreRepeat: true) { _ in
            player.onWillPlay = { true }
            controller.play()
        }

        #expect(await waitUntil { engine.loads.count == 1 })
        #expect(!intendsToPlay(controller))
        let id = try #require(engine.loads.last?.current.id)
        engine.emit(.state(.loading, trackId: id))
        engine.emit(.state(.paused, trackId: id))

        #expect(await waitUntil { intendsToPlay(controller) && engine.commands.contains("play") })
        #expect(controller.queueOperations.queueStateFlow.value.currentItem?.song.name == "Hyperballad")
    }
}
