import Foundation
import Testing
import UIKit
@testable import S2

/// `ArtworkLoader`'s pure decoding step, the same way Podcasts' `ArtworkLoaderTests` covers only what
/// doesn't touch the network. S2 has no CDN URL rewriting to test — Kotlin's `ArtworkUrls` hands over
/// one fixed-size URL per song, album or album artist — so downsampling is the whole surface.
struct ArtworkLoaderTests {

    @Test func downsampleClampsToTheRequestedMaxPixelSize() {
        let data = Self.pngData(width: 400, height: 200)
        let image = ArtworkLoader.downsample(data, maxPixelSize: 100)
        #expect(image != nil)
        #expect(max(image!.size.width, image!.size.height) == 100)
    }

    @Test func downsamplePreservesAspectRatio() {
        let data = Self.pngData(width: 400, height: 200)
        let image = ArtworkLoader.downsample(data, maxPixelSize: 100)
        #expect(image?.size.width == 100)
        #expect(image?.size.height == 50)
    }

    @Test func downsampleReturnsNilForUndecodableData() {
        #expect(ArtworkLoader.downsample(Data([0x00, 0x01, 0x02]), maxPixelSize: 100) == nil)
    }

    @Test func downsampleReturnsNilForEmptyData() {
        #expect(ArtworkLoader.downsample(Data(), maxPixelSize: 100) == nil)
    }

    @Test func nonPositiveMaxPixelSizeStillDecodes() {
        // Clamped to at least 1 pixel rather than crashing ImageIO.
        let data = Self.pngData(width: 40, height: 40)
        #expect(ArtworkLoader.downsample(data, maxPixelSize: 0) != nil)
    }

    @Test func cachedIsNilBeforeAnythingWasLoaded() {
        let url = URL(string: "https://example.com/unloaded-\(UUID()).jpg")!
        #expect(ArtworkLoader.shared.cached(url, maxPixelSize: 64) == nil)
    }

    private static func pngData(width: Int, height: Int) -> Data {
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: width, height: height))
        let image = renderer.image { context in
            UIColor.systemBlue.setFill()
            context.fill(CGRect(x: 0, y: 0, width: width, height: height))
        }
        return image.pngData()!
    }
}
