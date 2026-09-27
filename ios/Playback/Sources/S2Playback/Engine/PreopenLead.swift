import Foundation

/// How long before the current track's end ``MusicPlaybackController`` opens the next (#620).
///
/// A fixed window leaves a gap whenever an open takes longer than it: a server slow to answer, or a
/// transcode slow to start. The lead is the configured minimum, or twice the slowest of the last
/// few opens (the current track's own load included) when that's longer, so it grows to cover what
/// this server has actually been taking, with room for one open slower than any seen, and shrinks
/// back once the slow ones age out. Capped, so one pathological open doesn't hold the next
/// track's connection idle for minutes: the cap is the point past which an early open costs more
/// than the gap it would save.
struct PreopenLead {
    let minimumSeconds: Double
    let maximumSeconds: Double
    /// How many of the most recent opens count.
    let window: Int
    private(set) var recentSeconds: [Double] = []

    static let safetyFactor = 2.0

    init(minimumSeconds: Double, maximumSeconds: Double = 60, window: Int = 5) {
        self.minimumSeconds = minimumSeconds
        self.maximumSeconds = max(maximumSeconds, minimumSeconds)
        self.window = window
    }

    /// A successful open (to its first decoded chunk) took `openSeconds`.
    mutating func record(openSeconds: Double) {
        recentSeconds.append(openSeconds)
        if recentSeconds.count > window { recentSeconds.removeFirst(recentSeconds.count - window) }
    }

    var seconds: Double {
        let measured = (recentSeconds.max() ?? 0) * Self.safetyFactor
        return min(maximumSeconds, max(minimumSeconds, measured))
    }
}
