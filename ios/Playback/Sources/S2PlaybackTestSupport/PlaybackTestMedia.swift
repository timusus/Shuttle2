// Copied from Shuttle Podcasts (podcasts@9ee6e0954) mobile/ios/Playback/Sources/PlaybackTestSupport/PlaybackTestMedia.swift — see ios/Playback/README.md.
import Foundation

/// Fixture media shared by the playback controller tests, in the package and in the app.
///
/// Local media rather than a stream: the simulator on this machine has no working audio output, so
/// a test that waits for real playback is a coin toss. Readiness, seeking and the position clock
/// all work on a paused player regardless.
public enum PlaybackTestMedia {

    /// Polls `condition` every 50 ms until it holds or `timeout` passes; the final answer.
    public static func waitUntil(_ timeout: TimeInterval = 10, _ condition: () -> Bool) async -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if condition() { return true }
            try? await Task.sleep(nanoseconds: 50_000_000)
        }
        return condition()
    }
}
