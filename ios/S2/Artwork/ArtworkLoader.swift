import Foundation
import ImageIO
import UIKit

/// The one artwork loader: every cover in the app comes from here.
///
/// Ported from Shuttle Podcasts' `Utilities/ArtworkLoader.swift`, trimmed to what S2 actually needs.
/// Kotlin's `ArtworkUrls` (`shared/.../artwork/ArtworkUrls.kt`) says where an item's artwork may be, in
/// Android's order: the media server's image, then the S2 artwork API by name. This loader tries those
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
///   is never cached: the next look tries again.
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
    private var inFlight: [String: Task<UIImage?, Never>] = [:]

    private init() {
        let configuration = URLSessionConfiguration.default
        configuration.urlCache = URLCache(
            memoryCapacity: 16 * 1024 * 1024,
            diskCapacity: 256 * 1024 * 1024,
            diskPath: "s2-artwork"
        )
        // Artwork at a URL never changes; prefer whatever is on disk and only ask the network when
        // there is nothing there.
        configuration.requestCachePolicy = .returnCacheDataElseLoad
        configuration.httpMaximumConnectionsPerHost = 6
        configuration.timeoutIntervalForRequest = 20
        let session = URLSession(configuration: configuration)
        self.init(fetch: { request in try await session.data(for: request) })
    }

    init(fetch: @escaping Fetch) {
        self.fetch = fetch
    }

    // MARK: - Reading

    /// `source`'s image already in memory, if there is one. Synchronous, so a row that has been seen
    /// before draws its cover in the first frame instead of flashing the placeholder.
    nonisolated func cached(_ source: ArtworkSource, maxPixelSize: Int) -> UIImage? {
        memory.object(forKey: Self.key(source, maxPixelSize) as NSString)
    }

    /// `source`'s image, from the first of its candidates that yields one, downsampled so its longest
    /// side is `maxPixelSize` pixels. Nil when none does, or when the caller went away before one did.
    ///
    /// The candidates are looked up on the main actor, where the Kotlin lookups have always run.
    @MainActor
    func image(for source: ArtworkSource, maxPixelSize: Int) async -> UIImage? {
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

    /// The image at `url`, downsampled so its longest side is `maxPixelSize` pixels.
    func image(for url: URL, maxPixelSize: Int) async -> UIImage? {
        await image(for: ArtworkCandidate(url: url), maxPixelSize: maxPixelSize)
    }

    /// The image `candidate` points at, downsampled so its longest side is `maxPixelSize` pixels.
    ///
    /// Returns nil for anything that did not arrive as a decodable image, including a non-2xx response.
    func image(for candidate: ArtworkCandidate, maxPixelSize: Int) async -> UIImage? {
        let key = Self.key(candidate.url, maxPixelSize)
        if let hit = memory.object(forKey: key as NSString) { return hit }

        // Every later caller for the same cover joins the download already running rather than
        // starting a second one.
        if let running = inFlight[key] { return await running.value }

        let fetch = fetch
        let request = candidate.request
        let created = Task<UIImage?, Never> {
            do {
                let (data, response) = try await fetch(request)
                if let http = response as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
                    return nil
                }
                return Self.downsample(data, maxPixelSize: maxPixelSize)
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

    private static func key(_ url: URL, _ maxPixelSize: Int) -> String {
        "\(url.absoluteString)|\(maxPixelSize)"
    }

    /// Prefixed so an item's key can never be mistaken for a URL's.
    private nonisolated static func key(_ source: ArtworkSource, _ maxPixelSize: Int) -> String {
        "item:\(source.cacheKey)|\(maxPixelSize)"
    }

    // MARK: - Decoding

    /// Decode straight to the size that will be drawn, rather than decoding the full image and letting
    /// SwiftUI scale it down on the main thread.
    nonisolated static func downsample(_ data: Data, maxPixelSize: Int) -> UIImage? {
        let sourceOptions = [kCGImageSourceShouldCache: false] as CFDictionary
        guard let source = CGImageSourceCreateWithData(data as CFData, sourceOptions) else { return nil }
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

    var request: URLRequest {
        var request = URLRequest(url: url)
        if let authorization { request.setValue(authorization, forHTTPHeaderField: "Authorization") }
        if unmeteredOnly { request.allowsExpensiveNetworkAccess = false }
        return request
    }
}

private extension UIImage {
    /// Roughly what the decoded bitmap costs, for `NSCache`'s budget.
    var byteCost: Int {
        guard let cgImage else { return 1 }
        return cgImage.bytesPerRow * cgImage.height
    }
}
