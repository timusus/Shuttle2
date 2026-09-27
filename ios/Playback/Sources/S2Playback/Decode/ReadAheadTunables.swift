// Copied from Shuttle Podcasts (podcasts@9ee6e0954) mobile/ios/Spine/Sources/SpineNative/ReadAheadTunables.swift — see ios/Playback/README.md.
import Foundation

/// **How far ahead of the listener a streaming byte source may fetch.**
///
/// The numbers the player's `ReadAheadPolicy` is built from. They live here, below both the player
/// and the skip layer, because a tunable the player compiles against must not flow up from the
/// scanner: the player package depends on `SpineNative` only, and `Spine.ReadAheadPolicy` (the
/// `AVPlayer`-era copy the resource loader read) keeps the same values for as long as it exists.
///
/// `appetiteSeconds` is `MAX_AD_MS / 1000 + 20` — the longest ad the scanner will ever confirm plus
/// a margin — spelled as a literal here so this target does not reach into the scanner for it.
/// `ReadAheadTunablesTests` in the app pins the two copies to each other.
public enum ReadAheadTunables {

    /// The ordinary window, in seconds of audio.
    public static let defaultSeconds: Int64 = 60

    /// The window while the scan holds an appetite open at its frontier.
    public static let appetiteSeconds: Int64 = 300 + 20

    /// Used until the caller can say what a second of audio costs.
    public static let defaultBytes: Int64 = 2 * 1024 * 1024
    public static let appetiteBytes: Int64 = 16 * 1024 * 1024
}
