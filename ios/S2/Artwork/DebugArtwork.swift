import SwiftUI
import UIKit

/// A debug switch that replaces every cover with `GeneratedArtwork`, so App Store screenshots never show a
/// commercial cover. It lives in `ArtworkLoader`, the one place every artwork view, the lock screen's Now Playing
/// info and the colour extraction already get their images from, so no screen needs to know about it.
///
/// On with the "Use generated artwork" row in Settings (Debug), or the launch argument `-S2GeneratedArtwork YES`,
/// which lands in `UserDefaults`' argument domain, so the row reads it too. Covers already on screen change on their
/// next load. Release builds have neither the row nor the loader branch: `isEnabled` is a constant `false`.
enum DebugArtwork {
    static let defaultsKey = "S2GeneratedArtwork"

    #if DEBUG
    static var isEnabled: Bool { UserDefaults.standard.bool(forKey: defaultsKey) }

    /// The cache key a generated image is stored under: its own namespace, apart from real covers. The tone is
    /// fixed into the pixels in the interface style at the first render, which a screenshot run never changes.
    static func cacheKey(_ source: ArtworkSource, _ maxPixelSize: Int) -> String {
        "generated|\(source.cacheKey)|\(maxPixelSize)"
    }

    /// `source`'s generated cover: `GeneratedArtwork` seeded by the item's key, square, `maxPixelSize` pixels a side.
    @MainActor
    static func image(for source: ArtworkSource, maxPixelSize: Int) -> UIImage? {
        let side = CGFloat(max(maxPixelSize, 1))
        let renderer = ImageRenderer(
            content: GeneratedArtwork(seed: source.cacheKey, symbol: symbol(for: source))
                .frame(width: side, height: side)
                .environment(\.colorScheme, interfaceStyle == .dark ? .dark : .light)
        )
        renderer.scale = 1
        return renderer.uiImage
    }

    /// The glyph for the item's kind, the `ArtworkPlaceholder` symbols.
    static func symbol(for source: ArtworkSource) -> String {
        if source.isArtist { return "music.mic" }
        return source.cacheKey.hasPrefix("album:") ? "square.stack" : "music.note"
    }

    @MainActor
    private static var interfaceStyle: UIUserInterfaceStyle {
        let scene = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first
        return scene?.windows.first?.traitCollection.userInterfaceStyle == .dark ? .dark : .light
    }
    #else
    static var isEnabled: Bool { false }
    #endif
}
