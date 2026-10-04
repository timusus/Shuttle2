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
    /// Loads, plays, pauses and stops taken, as the engine counts them for its state reports.
    private(set) var commandsTaken = 0

    var hasEventHandler: Bool { handler != nil }

    /// The event handler as set now, as a report the engine has already queued on the main queue holds it.
    var eventHandler: ((EngineEvent) -> Void)? { handler }

    /// Reports `event` as the engine would, on the main queue. A state made with `.state(_:trackId:)` is stamped with
    /// the commands taken so far: a report the engine makes now.
    func emit(_ event: EngineEvent) {
        if case .state(let state, let trackId, EngineEvent.now) = event {
            handler?(.state(state, trackId: trackId, commands: commandsTaken))
        } else {
            handler?(event)
        }
    }

    func setEventHandler(_ handler: ((EngineEvent) -> Void)?) {
        self.handler = handler
    }

    func load(current: EngineTrack, next: EngineTrack?, startMs: Int64, playWhenReady: Bool) {
        commandsTaken += 1
        loads.append(Load(current: current, next: next, startMs: startMs, playWhenReady: playWhenReady))
    }

    func setNext(_ track: EngineTrack?) {
        nexts.append(track)
    }

    func play() {
        commandsTaken += 1
        commands.append("play")
    }

    func pause() {
        commandsTaken += 1
        commands.append("pause")
    }

    func seek(toMs ms: Int64) { commands.append("seek \(ms)") }

    func stop() {
        commandsTaken += 1
        commands.append("stop")
    }

    func setSpeed(_ speed: Float) { commands.append("speed \(speed)") }

    func setEqualizer(enabled: Bool, preampDb: Float, coefficients: [Double]) {
        equalizers.append(Equalizer(enabled: enabled, preampDb: preampDb, coefficients: coefficients))
    }
}

extension EngineEvent {
    /// Stands for the commands the engine has taken when `FakeAudioEngine.emit` reports it.
    static let now = -1

    /// A state the fake engine reports as of now (`FakeAudioEngine.emit`).
    static func state(_ state: EngineState, trackId: String?) -> EngineEvent {
        .state(state, trackId: trackId, commands: now)
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

/// The app's graph over `audioPlayer`, with storage of its own: preferences in their own suite (a graph restores the
/// queue and modes the last one saved, so graphs sharing the standard defaults would restore each other's; tests run
/// in parallel) and an empty in-memory library (not the simulator app's database, whose contents vary).
func makeTestGraph(audioPlayer: IosAudioPlayer) -> IosAppGraph {
    IosAppGraphKt.createIosAppGraph(audioPlayer: audioPlayer, isolatedStorage: "S2Tests.\(UUID().uuidString)", localFiles: IosLocalFilesNone.shared)
}
