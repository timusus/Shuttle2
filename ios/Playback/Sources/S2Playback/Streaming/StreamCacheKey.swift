import Foundation

/// The key a stream's kept bytes (``CachedRunStore``) and remembered redirect end (``ResolvedURLCache``) are filed
/// under: its URL without the query parameters that change from one play of the same song to the next (#822). A
/// Jellyfin or Emby stream URL carries a fresh `PlaySessionId` on every resolve and the account's token, a Plex one
/// its token, so keyed by the URL as given, no later play ever found what an earlier one kept and orphaned runs piled
/// up on disk. What's left still names the item and how it's served (its codec, bitrate and start position), so a
/// different stream of the same song keeps a key of its own.
enum StreamCacheKey {
    /// Per-play session ids and auth tokens, matched case-insensitively: Jellyfin's `ApiKey`, Emby's `api_key`, the
    /// MediaBrowser header tokens either accepts in the query, and Plex's token and transcode session identifier.
    static let volatileParameters: Set<String> = [
        "playsessionid", "api_key", "apikey", "x-emby-token", "x-mediabrowser-token",
        "x-plex-token", "x-plex-session-identifier",
    ]

    /// Plex's bare `session` (a fresh UUID per transcode start) is too generic a name to drop everywhere, so it goes
    /// only from Plex transcode URLs.
    private static let plexTranscodePathMarker = "/transcode/universal/"

    static func key(for url: URL) -> String {
        stableURL(for: url).absoluteString
    }

    /// `url` with the ``volatileParameters`` removed, the rest left in order and encoded as given.
    static func stableURL(for url: URL) -> URL {
        guard var components = URLComponents(url: url, resolvingAgainstBaseURL: false),
              let items = components.percentEncodedQueryItems else { return url }
        let isPlexTranscode = components.path.contains(plexTranscodePathMarker)
        let kept = items.filter {
            let name = ($0.name.removingPercentEncoding ?? $0.name).lowercased()
            return !volatileParameters.contains(name) && !(isPlexTranscode && name == "session")
        }
        guard kept.count != items.count else { return url }
        components.percentEncodedQueryItems = kept.isEmpty ? nil : kept
        return components.url ?? url
    }
}
