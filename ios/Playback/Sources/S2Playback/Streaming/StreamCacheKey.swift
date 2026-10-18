import Foundation

/// Drops the per-play query parameters so replays of a song share one cached download.
enum StreamCacheKey {
    /// Per-play session ids and auth tokens, matched case-insensitively: Jellyfin's `ApiKey`, Emby's `api_key`, the
    /// MediaBrowser header tokens either accepts in the query, Plex's token and transcode session identifier, and Subsonic's per-URL token and salt (`t`, `s`;
    /// no other server's content-identifying parameter is named either).
    static let volatileParameters: Set<String> = [
        "playsessionid", "api_key", "apikey", "x-emby-token", "x-mediabrowser-token",
        "x-plex-token", "x-plex-session-identifier",
        "t", "s",
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
