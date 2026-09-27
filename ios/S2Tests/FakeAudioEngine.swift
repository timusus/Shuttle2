import Foundation
import Shared
@testable import S2

/// An `AudioEngine` that records every call and reports only what a test `emit`s.
final class FakeAudioEngine: AudioEngine {
    struct Load: Equatable {
        let current: EngineTrack
        let next: EngineTrack?
        let startMs: Int64
        let playWhenReady: Bool
    }

    struct Equalizer: Equatable {
        let enabled: Bool
        let preampDb: Float
        let coefficients: [Double]
    }

    private(set) var loads: [Load] = []
    /// Every `setNext`, nil included.
    private(set) var nexts: [EngineTrack?] = []
    /// The other commands, in order: "play", "pause", "seek 1200", "stop", "speed 1.5".
    private(set) var commands: [String] = []
    var position: (uid: String, ms: Int64)?
    var durationMs: Int64?
    var outputSampleRate: Double = 48_000
    /// Every `setEqualizer`, in order.
    private(set) var equalizers: [Equalizer] = []
    private var handler: ((EngineEvent) -> Void)?

    var hasEventHandler: Bool { handler != nil }

    /// Reports `event` as the engine would, on the main queue.
    func emit(_ event: EngineEvent) {
        handler?(event)
    }

    func setEventHandler(_ handler: ((EngineEvent) -> Void)?) {
        self.handler = handler
    }

    func load(current: EngineTrack, next: EngineTrack?, startMs: Int64, playWhenReady: Bool) {
        loads.append(Load(current: current, next: next, startMs: startMs, playWhenReady: playWhenReady))
    }

    func setNext(_ track: EngineTrack?) {
        nexts.append(track)
    }

    func play() { commands.append("play") }
    func pause() { commands.append("pause") }
    func seek(toMs ms: Int64) { commands.append("seek \(ms)") }
    func stop() { commands.append("stop") }
    func setSpeed(_ speed: Float) { commands.append("speed \(speed)") }

    func setEqualizer(enabled: Bool, preampDb: Float, coefficients: [Double]) {
        equalizers.append(Equalizer(enabled: enabled, preampDb: preampDb, coefficients: coefficients))
    }
}

/// Waits, letting the main queue run (Kotlin's coroutines and the adapter's posted failures), until
/// `condition` holds; false on timing out.
@MainActor
func waitUntil(timeout: Duration = .seconds(2), _ condition: () -> Bool) async -> Bool {
    let deadline = ContinuousClock.now + timeout
    while !condition() {
        if ContinuousClock.now > deadline { return false }
        try? await Task.sleep(for: .milliseconds(5))
    }
    return true
}

/// Lets everything already queued on the main queue run.
@MainActor
func drainMainQueue() async {
    await withCheckedContinuation { continuation in
        DispatchQueue.main.async { continuation.resume() }
    }
}

/// The app's graph over `audioPlayer`, keeping its preferences in a suite of its own: a graph restores the queue and
/// modes the last one saved, so graphs sharing the standard defaults would restore each other's (tests run in parallel).
func makeTestGraph(audioPlayer: IosAudioPlayer) -> IosAppGraph {
    IosAppGraphKt.createIosAppGraph(audioPlayer: audioPlayer, preferencesSuite: "S2Tests.\(UUID().uuidString)")
}
