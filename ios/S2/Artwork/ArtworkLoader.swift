import Foundation
import ImageIO
import S2Playback
import UIKit

/// The one artwork loader: every cover in the app comes from here.
///
/// Ported from Shuttle Podcasts' `Utilities/ArtworkLoader.swift`, trimmed to what S2 actually needs.
/// Kotlin's `ArtworkUrls` (`shared/.../artwork/ArtworkUrls.kt`) says where an item's artwork may be, in
/// Android's order: a local song's own file (an `s2local:` url, read through `LocalLibrary`), the media server's
/// image, then the S2 artwork API by name. This loader tries those
/// candidates in turn and draws the first that arrives as an image, so an album the server has no image
/// for — or one it fails to serve — still gets a cover, as it does on Android.
///
/// - **Downsample while decoding.** ImageIO's `CGImageSourceCreateThumbnailAtIndex` reads the image once
///   and produces exactly the pixels that will be drawn, so a 56 pt row decodes a small thumbnail rather
///   than the server's full-size cover. `kCGImageSourceShouldCacheImmediately` pays the decode here, on
///   this background task, instead of on the main thread at draw time.
/// - **Two caches.** An `NSCache` of decoded images keyed by item (and by URL) *and* size, so the second
///   look at a screen costs nothing at all — not even the Kotlin lookup, which asks the server about the
///   item; behind it a disk `URLCache` on this loader's own session, so a cold launch re-downloads
///   nothing. Artwork is immutable at its URL, which is what makes a long-lived disk cache correct. A miss
///   is never cached: the next look tries again. A URL signed afresh for every request (a Subsonic server's) is
///   cached under its candidate's `cacheKey`, the unsigned URL, in both, or neither would ever hit.
/// - **Its own session.** Image traffic never queues behind the API.
/// - **One request per URL and size.** A caller that goes away stops waiting, but the download is never
///   cancelled: it finishes into the cache, so the row that comes back a moment later is instant.
///   Cancelling it was tried in Podcasts and was much worse — SwiftUI rebuilds a list several times in
///   the same millisecond, and every rebuild killed the download the previous one had started.
actor ArtworkLoader {

    static let shared = ArtworkLoader()

    /// Performs one request. The shared loader's goes through its own session; tests hand in a stub.
    typealias Fetch = @Sendable (URLRequest) async throws -> (Data, URLResponse)

    /// Decoded images, keyed by item or URL, and target size. `NSCache` is thread-safe and evicts under
    /// memory pressure on its own, which is what makes it safe to be generous here.
    private nonisolated let memory: NSCache<NSString, UIImage> = {
        let cache = NSCache<NSString, UIImage>()
        cache.countLimit = 400
        cache.totalCostLimit = 64 * 1024 * 1024
        return cache
    }()

    private let fetch: Fetch
    #if DEBUG
    /// Reads the debug "Use generated artwork" switch. Injected so a test turns the branch on for its own
    /// loader rather than flipping the process-global `UserDefaults` flag every other loader reads.
    private nonisolated let isGeneratedArtworkEnabled: @Sendable () -> Bool
    #endif
    private var inFlight: [String: Task<UIImage?, Never>] = [:]
    /// The session's disk cache, which a candidate with its own `cacheKey` reads and writes under that key.
    private nonisolated let diskCache: URLCache?

    private init() {
        let configuration = URLSessionConfiguration.default
        let diskCache = URLCache(
            memoryCapacity: 16 * 1024 * 1024,
            diskCapacity: 256 * 1024 * 1024,
            diskPath: "s2-artwork"
        )
        configuration.urlCache = diskCache
        // Artwork at a URL never changes; prefer whatever is on disk and only ask the network when
        // there is nothing there.
        configuration.requestCachePolicy = .returnCacheDataElseLoad
        configuration.httpMaximumConnectionsPerHost = 6
        configuration.timeoutIntervalForRequest = 20
        let session = URLSession(configuration: configuration, delegate: PlexTokenRedirectGuard(), delegateQueue: nil)
        // A signed URL (a Subsonic server's, carrying its token) never goes near the session's cache: the loader caches
        // its response itself, under the unsigned key, and the session would otherwise store it under the signed one
        let uncachedConfiguration = configuration.copy() as! URLSessionConfiguration
        uncachedConfiguration.urlCache = nil
        uncachedConfiguration.requestCachePolicy = .reloadIgnoringLocalCacheData
        let uncachedSession = URLSession(configuration: uncachedConfiguration, delegate: PlexTokenRedirectGuard(), delegateQueue: nil)
        self.init(fetch: { request in
            // This device's songs (#590): the picture in the file, or an image beside it.
            if let url = request.url, url.scheme == LocalLibrary.scheme {
                let library = await MainActor.run { AppGraph.dependencies.localLibrary }
                guard let data = library.artwork(forArtworkURL: url) else { throw URLError(.fileDoesNotExist) }
                return (data, URLResponse(url: url, mimeType: nil, expectedContentLength: data.count, textEncodingName: nil))
            }
            let bypassesCache = request.cachePolicy == .reloadIgnoringLocalCacheData
            return try await (bypassesCache ? uncachedSession : session).data(for: request)
        }, diskCache: diskCache)
    }

    init(fetch: @escaping Fetch, diskCache: URLCache? = nil, isGeneratedArtworkEnabled: @escaping @Sendable () -> Bool = { DebugArtwork.isEnabled }) {
        self.fetch = fetch
        self.diskCache = diskCache
        #if DEBUG
        self.isGeneratedArtworkEnabled = isGeneratedArtworkEnabled
        #endif
    }

    // MARK: - Reading

    /// `source`'s image already in memory, if there is one. Synchronous, so a row that has been seen
    /// before draws its cover in the first frame instead of flashing the placeholder.
    nonisolated func cached(_ source: ArtworkSource, maxPixelSize: Int) -> UIImage? {
        #if DEBUG
        if isGeneratedArtworkEnabled() {
            return memory.object(forKey: DebugArtwork.cacheKey(source, maxPixelSize) as NSString)
        }
        #endif
        return memory.object(forKey: Self.key(source, maxPixelSize) as NSString)
    }

    /// `source`'s image, from the first of its candidates that yields one, downsampled so its longest
    /// side is `maxPixelSize` pixels. Nil when none does, or when the caller went away before one did.
    ///
    /// The candidates are looked up on the main actor, where the Kotlin lookups have always run.
    @MainActor
    func image(for source: ArtworkSource, maxPixelSize: Int) async -> UIImage? {
        #if DEBUG
        if isGeneratedArtworkEnabled() {
            if let hit = cached(source, maxPixelSize: maxPixelSize) { return hit }
            guard let image = DebugArtwork.image(for: source, maxPixelSize: maxPixelSize) else { return nil }
            memory.setObject(image, forKey: DebugArtwork.cacheKey(source, maxPixelSize) as NSString, cost: image.byteCost)
            return image
        }
        #endif
        if let hit = cached(source, maxPixelSize: maxPixelSize) { return hit }
        guard let candidates = try? await source.candidates(), !candidates.isEmpty else { return nil }
        return await image(for: candidates, cacheKey: Self.key(source, maxPixelSize), maxPixelSize: maxPixelSize)
    }

    private func image(for candidates: [ArtworkCandidate], cacheKey: String, maxPixelSize: Int) async -> UIImage? {
        for candidate in candidates {
            // A row scrolled away stops walking the chain; a download it started still lands in the cache.
            if Task.isCancelled { return nil }
            if let image = await image(for: candidate, maxPixelSize: maxPixelSize) {
                memory.setObject(image, forKey: cacheKey as NSString, cost: image.byteCost)
                return image
            }
        }
        return nil
    }

    /// The image `candidate` points at, downsampled so its longest side is `maxPixelSize` pixels.
    ///
    /// Returns nil for anything that did not arrive as a decodable image, including a non-2xx response, and for an
    /// image smaller than the candidate's `minimumSize`.
    func image(for candidate: ArtworkCandidate, maxPixelSize: Int) async -> UIImage? {
        let key = Self.key(candidate.cacheKey, maxPixelSize, minimumSize: candidate.minimumSize)
        if let hit = memory.object(forKey: key as NSString) { return hit }

        // Every later caller for the same cover joins the download already running rather than
        // starting a second one.
        if let running = inFlight[key] { return await running.value }

        let fetch = fetch
        let request = candidate.request
        let minimumSize = candidate.minimumSize
        // The session caches by URL: a signed URL is cached here under its stable key instead
        let keyed = candidate.keyedCacheRequest.flatMap { keyRequest in diskCache.map { (keyRequest, $0) } }
        let created = Task<UIImage?, Never> {
            if case let (keyRequest, diskCache)? = keyed, let cached = diskCache.cachedResponse(for: keyRequest) {
                return Self.downsample(cached.data, maxPixelSize: maxPixelSize, minimumSize: minimumSize)
            }
            do {
                let (data, response) = try await fetch(request)
                if let http = response as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
                    return nil
                }
                if case let (keyRequest, diskCache)? = keyed, let keyURL = keyRequest.url,
                   let stored = HTTPURLResponse(url: keyURL, statusCode: 200, httpVersion: nil, headerFields: nil) {
                    // Stored against the unsigned URL, so the signature never lands on disk with it
                    diskCache.storeCachedResponse(CachedURLResponse(response: stored, data: data), for: keyRequest)
                }
                return Self.downsample(data, maxPixelSize: maxPixelSize, minimumSize: minimumSize)
            } catch {
                return nil
            }
        }
        inFlight[key] = created

        // Deliberately not cancellable: `await task.value` returns when the download finishes even if
        // this caller's own task was cancelled meanwhile, which is what keeps the cache write correct
        // no matter how often the view above is rebuilt.
        let image = await created.value
        inFlight[key] = nil
        if let image {
            memory.setObject(image, forKey: key as NSString, cost: image.byteCost)
        }
        return image
    }

    /// Carries a minimum size, so an image too small for an artist's candidate is never answered from one cached
    /// for a candidate that takes any size.
    private static func key(_ cacheKey: String, _ maxPixelSize: Int, minimumSize: Int) -> String {
        let key = "\(cacheKey)|\(maxPixelSize)"
        return minimumSize > 0 ? "\(key)|min\(minimumSize)" : key
    }

    /// Prefixed so an item's key can never be mistaken for a URL's.
    private nonisolated static func key(_ source: ArtworkSource, _ maxPixelSize: Int) -> String {
        "item:\(source.cacheKey)|\(maxPixelSize)"
    }

    // MARK: - Decoding

    /// Decode straight to the size that will be drawn, rather than decoding the full image and letting
    /// SwiftUI scale it down on the main thread. Nil for an image whose shorter side is under `minimumSize` pixels
    /// (an artist's, `ArtistHeroArtwork.MIN_ARTIST_IMAGE_SIZE`), which counts as absent; one whose size can't be read
    /// passes, for the decoder to judge.
    nonisolated static func downsample(_ data: Data, maxPixelSize: Int, minimumSize: Int = 0) -> UIImage? {
        let sourceOptions = [kCGImageSourceShouldCache: false] as CFDictionary
        guard let source = CGImageSourceCreateWithData(data as CFData, sourceOptions) else { return nil }
        if minimumSize > 0,
           let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any],
           let width = properties[kCGImagePropertyPixelWidth] as? Int,
           let height = properties[kCGImagePropertyPixelHeight] as? Int,
           min(width, height) < minimumSize {
            return nil
        }
        let options = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceShouldCacheImmediately: true,
            kCGImageSourceThumbnailMaxPixelSize: max(1, maxPixelSize),
        ] as CFDictionary
        guard let thumbnail = CGImageSourceCreateThumbnailAtIndex(source, 0, options) else { return nil }
        return UIImage(cgImage: thumbnail)
    }
}

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

/// The artwork sessions' delegate. Keeps a Plex server's `X-Plex-Token` and the custom headers of any server from following
/// a redirect to another host (`URLSession` carries a request's headers, and its query, over a redirect, so the server could
/// otherwise send them anywhere), and trusts the certificate the user pinned for a server (#921), through the same
/// ``ServerConnectionPolicy`` as sign-in, streaming and downloads.
final class PlexTokenRedirectGuard: NSObject, URLSessionTaskDelegate, Sendable {
    static let tokenName = "X-Plex-Token"

    private let override: ServerConnectionPolicy?

    /// The app's policy, read per request as the loader's sessions are made before it is set; a test hands in its own.
    private var policy: ServerConnectionPolicy? { override ?? ServerConnections.policy }

    init(policy: ServerConnectionPolicy? = nil) {
        self.override = policy
    }

    func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        didReceive challenge: URLAuthenticationChallenge,
        completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
    ) {
        ServerConnections.handle(challenge, policy: policy, completion: completionHandler)
    }

    func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        willPerformHTTPRedirection response: HTTPURLResponse,
        newRequest request: URLRequest,
        completionHandler: @escaping (URLRequest?) -> Void
    ) {
        let origin = task.originalRequest?.url
        let guarded = Self.redirected(request, from: origin, original: task.originalRequest)
        completionHandler(policy?.redirected(guarded, from: origin) ?? guarded)
    }

    /// `request` without the Plex token when it is bound for a different scheme, host or port than `origin`, and with the
    /// `original` request's token again when it is back there (a redirect chain A→B→A): each hop is decided against the
    /// server, as streaming does.
    static func redirected(_ request: URLRequest, from origin: URL?, original: URLRequest? = nil) -> URLRequest {
        guard let url = request.url, let origin else { return request }
        guard !sameServer(url, origin) else {
            guard request.value(forHTTPHeaderField: tokenName) == nil, let token = original?.value(forHTTPHeaderField: tokenName) else { return request }
            var request = request
            request.setValue(token, forHTTPHeaderField: tokenName)
            return request
        }
        var request = request
        request.setValue(nil, forHTTPHeaderField: tokenName)
        if var components = URLComponents(url: url, resolvingAgainstBaseURL: false), let items = components.queryItems {
            let kept = items.filter { $0.name.caseInsensitiveCompare(tokenName) != .orderedSame }
            if kept.count != items.count {
                components.queryItems = kept.isEmpty ? nil : kept
                request.url = components.url
            }
        }
        return request
    }

    private static func sameServer(_ a: URL, _ b: URL) -> Bool {
        a.scheme?.lowercased() == b.scheme?.lowercased()
            && a.host?.lowercased() == b.host?.lowercased()
            && (a.port ?? defaultPort(a)) == (b.port ?? defaultPort(b))
    }

    private static func defaultPort(_ url: URL) -> Int? {
        switch url.scheme?.lowercased() {
        case "http": 80
        case "https": 443
        default: nil
        }
    }
}

private extension UIImage {
    /// Roughly what the decoded bitmap costs, for `NSCache`'s budget.
    var byteCost: Int {
        guard let cgImage else { return 1 }
        return cgImage.bytesPerRow * cgImage.height
    }
}
