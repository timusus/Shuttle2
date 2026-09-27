import Foundation
import ImageIO
import UIKit

/// The one artwork loader: every cover in the app comes from here.
///
/// Ported from Shuttle Podcasts' `Utilities/ArtworkLoader.swift`, trimmed to what S2 actually needs.
/// A song, album or album artist has at most one artwork URL — Kotlin's `ArtworkUrls`
/// (`shared/.../artwork/ArtworkUrls.kt`) already picked the provider and asked the server for a fixed
/// size — so there is no CDN URL rewriting and no candidate list here, just download, downsample and
/// cache the one URL the caller has.
///
/// - **Downsample while decoding.** ImageIO's `CGImageSourceCreateThumbnailAtIndex` reads the image once
///   and produces exactly the pixels that will be drawn, so a 56 pt row decodes a small thumbnail rather
///   than the server's full-size cover. `kCGImageSourceShouldCacheImmediately` pays the decode here, on
///   this background task, instead of on the main thread at draw time.
/// - **Two caches.** An `NSCache` of decoded images keyed by URL *and* size, so the second look at a
///   screen costs nothing at all; behind it a disk `URLCache` on this loader's own session, so a cold
///   launch re-downloads nothing. Artwork is immutable at its URL, which is what makes a long-lived disk
///   cache correct.
/// - **Its own session.** Image traffic never queues behind the API.
/// - **One request per URL and size.** A caller that goes away stops waiting, but the download is never
///   cancelled: it finishes into the cache, so the row that comes back a moment later is instant.
///   Cancelling it was tried in Podcasts and was much worse — SwiftUI rebuilds a list several times in
///   the same millisecond, and every rebuild killed the download the previous one had started.
actor ArtworkLoader {

    static let shared = ArtworkLoader()

    /// Decoded images, keyed by URL and target size. `NSCache` is thread-safe and evicts under memory
    /// pressure on its own, which is what makes it safe to be generous here.
    private nonisolated let memory: NSCache<NSString, UIImage> = {
        let cache = NSCache<NSString, UIImage>()
        cache.countLimit = 400
        cache.totalCostLimit = 64 * 1024 * 1024
        return cache
    }()

    private let session: URLSession
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
        session = URLSession(configuration: configuration)
    }

    // MARK: - Reading

    /// A decoded image already in memory, if there is one. Synchronous, so a row that has been seen
    /// before draws its cover in the first frame instead of flashing the placeholder.
    nonisolated func cached(_ url: URL, maxPixelSize: Int) -> UIImage? {
        memory.object(forKey: Self.key(url, maxPixelSize) as NSString)
    }

    /// The image for `url`, downsampled so its longest side is `maxPixelSize` pixels.
    ///
    /// Returns nil for anything that did not arrive as a decodable image, including a non-2xx response.
    func image(for url: URL, maxPixelSize: Int) async -> UIImage? {
        let key = Self.key(url, maxPixelSize)
        if let hit = memory.object(forKey: key as NSString) { return hit }

        // Every later caller for the same cover joins the download already running rather than
        // starting a second one.
        if let running = inFlight[key] { return await running.value }

        let session = session
        let created = Task<UIImage?, Never> {
            do {
                let (data, response) = try await session.data(from: url)
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

private extension UIImage {
    /// Roughly what the decoded bitmap costs, for `NSCache`'s budget.
    var byteCost: Int {
        guard let cgImage else { return 1 }
        return cgImage.bytesPerRow * cgImage.height
    }
}
