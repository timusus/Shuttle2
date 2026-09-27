// Copied from Shuttle Podcasts (podcasts@9ee6e0954) mobile/ios/Playback/Sources/Playback/Streaming/ReadAheadPolicy.swift — see ios/Playback/README.md.
import Foundation

/// **How far ahead of the listener a byte source is allowed to fetch.**
///
/// The rule was proved on device by the `AVPlayer`-era resource loader and is shared by the
/// scanner's own ring (`Spine.ReadAheadPolicy`). The streaming player needs the same rule as a
/// *value*: its tests have to run a source with a 64 KiB window and watch the fetch stop, which a
/// set of `static let`s cannot express. So this is the one definition the player compiles against,
/// with the window sizes made instance state and the production numbers read from
/// ``SpineNative/ReadAheadTunables`` — below the player, not above it, so the scanner is not a
/// dependency of the thing that fetches its bytes.
///
/// `minWindowBytes` and `backWindowBytes` are the player's own: the smallest `Range` worth opening
/// (a transaction opened right at the ceiling would otherwise ask for a degenerate handful of bytes
/// and immediately end), and the back window kept so a small backwards move is served from bytes
/// already held. A byte source that a test drives at 64 KiB needs both to scale down with the rest.
public struct ReadAheadPolicy: Equatable {

    /// The ordinary window, in seconds of audio.
    public var windowSeconds: Int64
    /// The window while the spine holds an appetite open: `MAX_AD_MS / 1000 + 20`.
    public var appetiteWindowSeconds: Int64
    /// Used until the caller can say what a second of audio costs.
    public var windowBytes: Int64
    /// The appetite-open window before the byte rate is known.
    public var appetiteWindowBytes: Int64
    /// The smallest `Range` worth opening. A transaction opened right at the ceiling would
    /// otherwise ask for a degenerate handful of bytes and immediately end.
    public var minWindowBytes: Int64
    /// How far behind the read position already-fetched bytes are kept, so a short backwards seek
    /// is served from the window instead of opening a transaction.
    public var backWindowBytes: Int64

    /// The shipping tunables; the app's parity test holds them level with Android's.
    public static let `default` = ReadAheadPolicy(
        windowSeconds: ReadAheadTunables.defaultSeconds,
        appetiteWindowSeconds: ReadAheadTunables.appetiteSeconds,
        windowBytes: ReadAheadTunables.defaultBytes,
        appetiteWindowBytes: ReadAheadTunables.appetiteBytes,
        minWindowBytes: 512 * 1024,
        backWindowBytes: 512 * 1024
    )

    /// The absolute byte a transaction may read up to.
    ///
    /// - Parameters:
    ///   - baseByte: the listener's position in bytes — for the streaming player that is the
    ///     decoder's own read position, which runs only a few seconds ahead of the render. Never
    ///     the fetch frontier: a ceiling measured from what has been fetched chases itself and the
    ///     transaction runs away with the whole episode.
    ///   - bytesPerSecond: from `totalBytes / duration`, or 0 while it is unknown.
    ///   - appetiteOpen: the spine has a run open at its scan frontier.
    func ceiling(baseByte: Int64, bytesPerSecond: Int64, appetiteOpen: Bool) -> Int64 {
        let rate = max(bytesPerSecond, 0)
        let window: Int64
        if appetiteOpen {
            window = rate > 0 ? appetiteWindowSeconds * rate : appetiteWindowBytes
        } else {
            window = rate > 0 ? windowSeconds * rate : windowBytes
        }
        let hardCap = rate > 0 ? baseByte + appetiteWindowSeconds * rate : baseByte + appetiteWindowBytes
        return min(baseByte + window, hardCap)
    }
}
