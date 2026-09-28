import SwiftUI
import Testing
import UIKit
@testable import S2

/// The artwork tint: the dominant-colour extraction, the contrast guarantees `ContrastSafeTint` makes in both
/// schemes, the ink on a tint fill, and the extractor's cache.
@MainActor
struct ArtworkTintTests {
    typealias RGB = ContrastSafeTint.RGB

    // MARK: Extraction

    @Test func aSolidColourIsItsOwnDominantColour() throws {
        let color = try #require(ArtworkColorExtractor.dominantColor(from: Self.image([.init(0xC0_30_30)])))
        #expect(Self.hex(color) == 0xC0_30_30)
    }

    @Test func greyWhiteAndBlackPixelsAreIgnored() throws {
        // Three quarters of the cover is neutral; the quarter of blue is still the answer.
        let image = Self.image([.init(0xFF_FF_FF), .init(0x00_00_00), .init(0x80_80_80), .init(0x20_50_D0)])
        let color = try #require(ArtworkColorExtractor.dominantColor(from: image))
        #expect(Self.hex(color) == 0x20_50_D0)
    }

    @Test func aGreyscaleCoverHasNoDominantColour() {
        let image = Self.image([.init(0xFF_FF_FF), .init(0x10_10_10), .init(0x80_80_80), .init(0xC8_C8_C8)])
        #expect(ArtworkColorExtractor.dominantColor(from: image) == nil)
    }

    @Test func moreSaturatedPixelsWeighMore() throws {
        // A vivid red and a dull red, equal areas: the mean leans to the vivid one.
        let image = Self.image([.init(0xFF_00_00), .init(0xB0_70_70)])
        let color = try #require(ArtworkColorExtractor.dominantColor(from: image))
        // Unweighted, green would be 0x38 (0.22); weighted by saturation it's about 0.12.
        #expect(color.red > 0.85)
        #expect(color.green < 0.15)
    }

    // MARK: Contrast guarantees

    /// Navy, near-black, maroon, pastels, a yellow and a mid grey-blue: covers that fail one scheme or both.
    nonisolated static let hardColours: [UInt32] = [0x15_34_6C, 0x0A_0A_20, 0x5A_10_10, 0xFF_E0_F0, 0xFA_E8_5A, 0xFF_D7_00, 0x60_70_80, 0x00_FF_00]

    @Test(arguments: hardColours, [false, true])
    func theSafeTintClearsAAOnTheSchemeGround(hex: UInt32, isDark: Bool) {
        let safe = ContrastSafeTint.safeTint(for: RGB(hex: hex), isDarkScheme: isDark)
        let ground = ContrastSafeTint.background(isDarkScheme: isDark)
        #expect(ContrastSafeTint.contrastRatio(safe, ground) >= ContrastSafeTint.minimumContrast)
        // And so on the plain system background too, which is further from the ink than the chrome ground.
        let plain = isDark ? RGB(hex: 0x00_00_00) : RGB(hex: 0xFF_FF_FF)
        #expect(ContrastSafeTint.contrastRatio(safe, plain) >= ContrastSafeTint.minimumContrast)
    }

    @Test(arguments: hardColours, [false, true])
    func theSafeTintKeepsTheHue(hex: UInt32, isDark: Bool) {
        let original = ContrastSafeTint.HSB(RGB(hex: hex))
        guard original.saturation > 0.1 else { return }
        let safe = ContrastSafeTint.HSB(ContrastSafeTint.safeTint(for: RGB(hex: hex), isDarkScheme: isDark))
        let drift = abs(original.hue - safe.hue)
        #expect(min(drift, 360 - drift) < 2)
    }

    @Test func aColourThatAlreadyPassesIsLeftAlone() {
        let blue = RGB(hex: 0x00_40_90) // 9:1 on the light ground
        #expect(ContrastSafeTint.safeTint(for: blue, isDarkScheme: false) == blue)
        let sky = RGB(hex: 0x80_C0_FF) // 8:1 on the dark ground
        #expect(ContrastSafeTint.safeTint(for: sky, isDarkScheme: true) == sky)
    }

    @Test func theKnownRatiosMatchWCAG() {
        #expect(abs(ContrastSafeTint.contrastRatio(RGB(hex: 0xFF_FF_FF), RGB(hex: 0x00_00_00)) - 21) < 0.01)
        #expect(abs(ContrastSafeTint.contrastRatio(RGB(hex: 0x77_77_77), RGB(hex: 0xFF_FF_FF)) - 4.48) < 0.01)
    }

    // MARK: Ink

    @Test(arguments: hardColours, [false, true])
    func theInkOnATintFillClearsAA(hex: UInt32, isDark: Bool) throws {
        let values = ArtworkTintValues(extracted: RGB(hex: hex), isDarkScheme: isDark)
        let fill = try #require(values.safeRGB)
        // A scheme-safe fill is dark enough for white (light scheme) or light enough for near-black (dark scheme).
        let ink = ContrastSafeTint.labelColor(onFill: fill)
        #expect(ContrastSafeTint.contrastRatio(ink, fill) >= ContrastSafeTint.minimumContrast)
        #expect(values.isTinted)
    }

    @Test func noExtractedColourFallsBackToTheAccent() {
        let values = ArtworkTintValues(extracted: nil, isDarkScheme: true)
        #expect(!values.isTinted)
        #expect(values.safeRGB == nil)
        #expect(values.tint == .accentColor)
    }

    @Test func theAccentInkIsWhiteInLightModeAndDarkInDarkMode() {
        let light = ContrastSafeTint.rgb(from: TintedChromeInk.onAccent, isDarkScheme: false)
        let dark = ContrastSafeTint.rgb(from: TintedChromeInk.onAccent, isDarkScheme: true)
        #expect(Self.hex(light) == 0xFF_FF_FF)
        #expect(Self.hex(dark) == Self.hex(ContrastSafeTint.darkLabel))
    }

    // MARK: Extractor cache

    @Test func theExtractorLoadsEachCoverOnceAtSeedSize() async {
        var loads: [Int] = []
        let extractor = ArtworkColorExtractor { _, pixels in
            loads.append(pixels)
            return Self.image([.init(0x20_80_40)])
        }
        let source = Self.source("a")
        let first = await extractor.color(for: source)
        let second = await extractor.color(for: source)
        #expect(first != nil)
        #expect(first == second)
        #expect(loads == [ArtworkColorExtractor.seedPixels])
    }

    @Test func aCoverWithNoHueIsCachedAsNone() async {
        var loads = 0
        let extractor = ArtworkColorExtractor { _, _ in
            loads += 1
            return Self.image([.init(0x80_80_80)])
        }
        let source = Self.source("grey")
        #expect(await extractor.color(for: source) == nil)
        #expect(await extractor.color(for: source) == nil)
        #expect(loads == 1)
    }

    @Test func theCacheEvictsTheOldestCover() async {
        let extractor = ArtworkColorExtractor(maxCacheSize: 2) { _, _ in Self.image([.init(0x20_80_40)]) }
        let sources = (0..<3).map { Self.source("\($0)") }
        for source in sources { _ = await extractor.color(for: source) }
        #expect(!extractor.isCached(sources[0]))
        #expect(extractor.isCached(sources[1]))
        #expect(extractor.isCached(sources[2]))
    }

    @Test func aNewArtworkVersionIsReadAgain() async {
        var loads = 0
        let extractor = ArtworkColorExtractor { _, _ in
            loads += 1
            return Self.image([.init(0x20_80_40)])
        }
        _ = await extractor.color(for: ArtworkSource(id: "a", cacheKey: ArtworkSource.itemKey("album", "a", version: "1")) { [] })
        _ = await extractor.color(for: ArtworkSource(id: "a", cacheKey: ArtworkSource.itemKey("album", "a", version: "2")) { [] })
        #expect(loads == 2)
    }

    static func source(_ id: String) -> ArtworkSource {
        ArtworkSource(id: id) { [ArtworkCandidate(url: URL(string: "https://example.com/\(id).jpg")!)] }
    }

    // MARK: Helpers

    struct Pixel {
        let hex: UInt32
        init(_ hex: UInt32) { self.hex = hex }
    }

    /// An image of equal vertical stripes, one per pixel colour, 8 px each.
    static func image(_ stripes: [Pixel]) -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.preferredRange = .standard
        let size = CGSize(width: 8 * stripes.count, height: 8)
        return UIGraphicsImageRenderer(size: size, format: format).image { context in
            for (index, stripe) in stripes.enumerated() {
                let rgb = RGB(hex: stripe.hex)
                UIColor(red: rgb.red, green: rgb.green, blue: rgb.blue, alpha: 1).setFill()
                context.fill(CGRect(x: 8 * index, y: 0, width: 8, height: 8))
            }
        }
    }

    static func hex(_ rgb: RGB) -> UInt32 {
        func channel(_ value: Double) -> UInt32 { UInt32((min(max(value, 0), 1) * 255).rounded()) }
        return channel(rgb.red) << 16 | channel(rgb.green) << 8 | channel(rgb.blue)
    }
}
