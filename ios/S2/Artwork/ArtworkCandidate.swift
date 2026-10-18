import Foundation

/// One place an item's artwork may be: Kotlin's `ArtworkRequest` as a value the loader can hand between
/// tasks. `authorization` is an `Authorization` header (the S2 API's), kept out of the URL; `unmeteredOnly`
/// keeps the request off cellular and hotspots (the wifi-only artwork setting, which covers the S2 API only).
struct ArtworkCandidate: Hashable, Sendable {
    let url: URL
    var authorization: String?
    var unmeteredOnly = false
    /// Further headers the request needs, such as a Plex server's `X-Plex-Token`.
    var headers: [String: String] = [:]
    /// The server's own custom headers (#921), which may be secrets: sent with the request, never kept with a cached response.
    var customHeaders: [String: String] = [:]
    /// The smallest the image's shorter side may be, in pixels: a smaller one counts as absent and the next candidate
    /// is tried (an artist's image, #823). 0 takes any size.
    var minimumSize = 0
    /// What names the image in the caches, when it isn't `url`: the unsigned URL of one signed afresh each time
    /// (a Subsonic server's), whose own URL would never hit.
    var stableKey: String?

    var cacheKey: String { stableKey ?? url.absoluteString }

    /// The request the disk cache keeps this image under, for a candidate with a `stableKey` or custom headers; nil for
    /// any other, which the session caches by its own URL. Built from the URL alone: a cached request carries its
    /// headers, so one with the server's headers would put them on disk.
    var keyedCacheRequest: URLRequest? {
        guard stableKey != nil || !customHeaders.isEmpty else { return nil }
        return URLRequest(url: stableKey.flatMap(URL.init(string:)) ?? url)
    }

    var request: URLRequest {
        var request = URLRequest(url: url)
        if let authorization { request.setValue(authorization, forHTTPHeaderField: "Authorization") }
        for (name, value) in headers { request.setValue(value, forHTTPHeaderField: name) }
        for (name, value) in customHeaders { request.setValue(value, forHTTPHeaderField: name) }
        if unmeteredOnly { request.allowsExpensiveNetworkAccess = false }
        // The loader caches these responses itself, under a key without the signature or the headers; the session mustn't keep either
        if keyedCacheRequest != nil { request.cachePolicy = .reloadIgnoringLocalCacheData }
        return request
    }
}
