import Foundation
@testable import S2

/// An `AudioEngine` that records every call and reports only what a test `emit`s.
final class FakeAudioEngine: AudioEngine {
    struct Load: Equatable {
        let current: EngineTrack
        let next: EngineTrack?
        let startMs: Int64
        let playWhenReady: Bool
    }

    private(set) var loads: [Load] = []
    /// Every `setNext`, nil included.
    private(set) var nexts: [EngineTrack?] = []
    /// The other commands, in order: "play", "pause", "seek 1200", "stop", "speed 1.5".
    private(set) var commands: [String] = []
    var position: (uid: String, ms: Int64)?
    var durationMs: Int64?
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
