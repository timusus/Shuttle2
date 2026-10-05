import CarPlay
import UIKit

/// A CarPlay row's artwork, through the app's `ArtworkLoader` at the size CarPlay draws it (#692, from Shuttle
/// Podcasts). A row is drawn with no image (an image row with its symbols) and filled in when its artwork arrives; one
/// seen before (here or on the phone, at this size) is set at once.
@MainActor
final class CarPlayArtworkLoader {
    static let shared = CarPlayArtworkLoader()

    private init() {}

    private var placeholders: [String: UIImage] = [:]

    /// The pixel size CarPlay draws a list row's image at, on this display.
    static var rowPixels: Int {
        pixels(CPListItem.maximumImageSize)
    }

    /// The pixel size CarPlay draws an image row's images at (a Home shelf), on this display.
    static var imageRowPixels: Int {
        if #available(iOS 26.0, *) {
            pixels(CPListImageRowItemRowElement.maximumImageSize)
        } else {
            pixels(CPListImageRowItem.maximumImageSize)
        }
    }

    private static func pixels(_ size: CGSize) -> Int {
        let scale = UIGraphicsImageRendererFormat.default().scale
        return Int((max(size.width, size.height) * scale).rounded(.up))
    }

    func cached(_ source: ArtworkSource?, pixels: Int? = nil) -> UIImage? {
        let pixels = pixels ?? Self.rowPixels
        return source.flatMap { ArtworkLoader.shared.cached($0, maxPixelSize: pixels) }
    }

    func image(for source: ArtworkSource, pixels: Int? = nil) async -> UIImage? {
        let pixels = pixels ?? Self.rowPixels
        if let hit = cached(source, pixels: pixels) { return hit }
        return await ArtworkLoader.shared.image(for: source, maxPixelSize: pixels)
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

    /// Loads the artwork `item` doesn't show yet (`shown` is what it was drawn with, in `sources`' order) and redraws
    /// its images once, when every load has finished, rather than once per image.
    func attach(_ sources: [ArtworkSource?], shown: [UIImage], to item: CPListImageRowItem) {
        let pixels = Self.imageRowPixels
        let missing = sources.enumerated().compactMap { index, source in
            source.flatMap { cached($0, pixels: pixels) == nil ? (index, $0) : nil }
        }
        guard !missing.isEmpty else { return }
        Task { [weak item] in
            var images = shown
            var loadedAny = false
            for (index, source) in missing {
                guard let image = await self.image(for: source, pixels: pixels) else { continue }
                images[index] = image
                loadedAny = true
            }
            guard let item, loadedAny else { return }
            if #available(iOS 26.0, *) {
                let elements = item.elements
                for (element, image) in zip(elements, images) { element.image = image }
                item.elements = elements
            } else {
                item.update(images)
            }
        }
    }

    /// The image drawn for an item until its artwork arrives, or for one with none: its symbol on a plain tile.
    static func placeholder(_ symbol: String?) -> UIImage {
        let name = symbol ?? "music.note"
        if let hit = shared.placeholders[name] { return hit }
        let side = CGFloat(imageRowPixels) / UIGraphicsImageRendererFormat.default().scale
        let size = CGSize(width: max(side, 1), height: max(side, 1))
        let image = UIGraphicsImageRenderer(size: size).image { _ in
            UIColor.systemGray4.setFill()
            UIBezierPath(roundedRect: CGRect(origin: .zero, size: size), cornerRadius: size.width * 0.08).fill()
            let configuration = UIImage.SymbolConfiguration(pointSize: size.width * 0.4, weight: .regular)
            if let glyph = UIImage(systemName: name, withConfiguration: configuration)?.withTintColor(.secondaryLabel, renderingMode: .alwaysOriginal) {
                glyph.draw(at: CGPoint(x: (size.width - glyph.size.width) / 2, y: (size.height - glyph.size.height) / 2))
            }
        }
        shared.placeholders[name] = image
        return image
    }
}
