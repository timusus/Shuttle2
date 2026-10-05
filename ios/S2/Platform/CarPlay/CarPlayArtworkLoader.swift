import CarPlay
import UIKit

/// A CarPlay row's artwork, through the app's `ArtworkLoader` at the size CarPlay draws a row's image (#692, from
/// Shuttle Podcasts). A row is drawn with no image and filled in when its artwork arrives; one seen before (here or
/// on the phone, at this size) is set at once.
@MainActor
final class CarPlayArtworkLoader {
    static let shared = CarPlayArtworkLoader()

    private init() {}

    /// The pixel size CarPlay draws a row's image at, on this display.
    static var rowPixels: Int {
        let size = CPListItem.maximumImageSize
        let scale = UIGraphicsImageRendererFormat.default().scale
        return Int((max(size.width, size.height) * scale).rounded(.up))
    }

    func cached(_ source: ArtworkSource) -> UIImage? {
        ArtworkLoader.shared.cached(source, maxPixelSize: Self.rowPixels)
    }

    func image(for source: ArtworkSource) async -> UIImage? {
        if let hit = cached(source) { return hit }
        return await ArtworkLoader.shared.image(for: source, maxPixelSize: Self.rowPixels)
    }

    /// Sets `source`'s image on `item` now if it's cached, else when it loads.
    func attach(_ source: ArtworkSource, to item: CPListItem) {
        if let hit = cached(source) {
            item.setImage(hit)
            return
        }
        Task { [weak item] in
            let image = await self.image(for: source)
            guard let item, let image else { return }
            item.setImage(image)
        }
    }
}
