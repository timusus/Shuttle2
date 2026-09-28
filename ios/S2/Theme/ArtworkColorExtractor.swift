import SwiftUI
import UIKit

/// Finds the colour a cover is "about": the saturation-weighted average of its pixels, skipping near-black,
/// near-white and grey ones, so the result is a vibrant, representative hue. Ported from Shuttle Podcasts'
/// `Theme/ArtworkColorExtractor.swift`; S2 feeds it a `UIImage` from `ArtworkLoader`'s candidate walk rather than downloading.
///
/// The colour is raw: never draw it as-is. `ArtworkTintModifier` (`.artworkTint(from:)`) turns it into a
/// scheme-safe tint with `ContrastSafeTint`.
@MainActor
final class ArtworkColorExtractor {
    static let shared = ArtworkColorExtractor()

    /// The colour is averaged over a 64 px thumbnail, so that is all the loader is asked to decode.
    static let seedPixels = 64

    /// Loads a source's cover at a pixel size: `ArtworkLoader`'s candidate walk by default, a stub in tests.
    typealias ImageLoader = (ArtworkSource, Int) async -> UIImage?

    private let loadImage: ImageLoader
    private let maxCacheSize: Int
    /// Keyed by the source's `cacheKey` (its item and artwork version), including covers with no dominant hue
    /// (a nil entry), so a grey cover isn't re-read.
    private var cache: [String: ContrastSafeTint.RGB?] = [:]
    /// Oldest first, for eviction.
    private var order: [String] = []

    init(maxCacheSize: Int = 50, loadImage: @escaping ImageLoader = { source, pixels in
        await ArtworkLoader.shared.image(for: source, maxPixelSize: pixels)
    }) {
        self.maxCacheSize = maxCacheSize
        self.loadImage = loadImage
    }

    /// The dominant colour of `source`'s artwork, from the first of its candidates that loads, or nil when none
    /// does or the cover has no dominant hue (a greyscale cover).
    func color(for source: ArtworkSource) async -> ContrastSafeTint.RGB? {
        let key = source.cacheKey
        if let hit = cache[key] { return hit }
        guard let image = await loadImage(source, Self.seedPixels) else { return nil }
        let color = await Task.detached(priority: .utility) { Self.dominantColor(from: image) }.value
        store(color, for: key)
        return color
    }

    /// Whether `source`'s colour is cached (nil or not). For tests.
    func isCached(_ source: ArtworkSource) -> Bool { cache[source.cacheKey] != nil }

    private func store(_ color: ContrastSafeTint.RGB?, for key: String) {
        if cache[key] == nil, order.count >= maxCacheSize {
            cache.removeValue(forKey: order.removeFirst())
        }
        if cache[key] == nil { order.append(key) }
        cache[key] = .some(color)
    }

    // MARK: - Pixel analysis

    /// The saturation-weighted mean of `image`'s vivid pixels, or nil when none are vivid enough. Decodes the
    /// image as it is, so hand it a thumbnail (`seedPixels`).
    nonisolated static func dominantColor(from image: UIImage) -> ContrastSafeTint.RGB? {
        guard let cgImage = image.cgImage else { return nil }

        let width = cgImage.width
        let height = cgImage.height
        let totalPixels = width * height
        guard totalPixels > 0 else { return nil }

        let bytesPerPixel = 4
        var pixels = [UInt8](repeating: 0, count: totalPixels * bytesPerPixel)
        let drawn = pixels.withUnsafeMutableBytes { buffer -> Bool in
            guard let context = CGContext(
                data: buffer.baseAddress,
                width: width,
                height: height,
                bitsPerComponent: 8,
                bytesPerRow: width * bytesPerPixel,
                space: CGColorSpaceCreateDeviceRGB(),
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
            ) else { return false }
            context.draw(cgImage, in: CGRect(x: 0, y: 0, width: width, height: height))
            return true
        }
        guard drawn else { return nil }

        var totalR = 0.0, totalG = 0.0, totalB = 0.0, totalWeight = 0.0
        for i in 0..<totalPixels {
            let offset = i * bytesPerPixel
            let r = Double(pixels[offset])
            let g = Double(pixels[offset + 1])
            let b = Double(pixels[offset + 2])

            let maxC = max(r, g, b)
            let delta = maxC - min(r, g, b)
            let brightness = maxC / 255
            let saturation = maxC > 0 ? delta / maxC : 0

            // Near-black, near-white and desaturated pixels say nothing about the cover's hue.
            if brightness < 0.15 || (brightness > 0.9 && saturation < 0.3) || saturation < 0.2 {
                continue
            }
            totalR += r * saturation
            totalG += g * saturation
            totalB += b * saturation
            totalWeight += saturation
        }
        guard totalWeight > 0 else { return nil }

        return ContrastSafeTint.RGB(
            red: (totalR / totalWeight).rounded() / 255,
            green: (totalG / totalWeight).rounded() / 255,
            blue: (totalB / totalWeight).rounded() / 255
        )
    }
}

// MARK: - Contrast-safe tint

/// Turns a raw extracted artwork colour into one that is legible on the current scheme's ground. Ported from
/// Shuttle Podcasts.
///
/// Plenty of covers are dominated by a colour that is nearly invisible where the tint is drawn: a navy reads at
/// 1.7:1 on a dark player. Android never draws the extracted colour directly either; it seeds a tonal scheme per
/// light/dark. This is the same idea at a fraction of the size: keep the hue, move the tone until the colour
/// clears WCAG AA (4.5:1) against the scheme's ground, and leave colours that already clear it as they are.
enum ContrastSafeTint {

    /// WCAG AA for normal text: the tint is used for small labels and glyphs.
    static let minimumContrast: Double = 4.5

    struct RGB: Equatable, Hashable, Sendable {
        var red: Double
        var green: Double
        var blue: Double

        init(red: Double, green: Double, blue: Double) {
            self.red = red
            self.green = green
            self.blue = blue
        }

        /// A packed `0xRRGGBB` (alpha ignored), as Android and the tests write colours.
        init(hex: UInt32) {
            self.init(
                red: Double((hex >> 16) & 0xFF) / 255,
                green: Double((hex >> 8) & 0xFF) / 255,
                blue: Double(hex & 0xFF) / 255
            )
        }
    }

    // MARK: Contrast

    /// WCAG relative luminance.
    static func relativeLuminance(_ rgb: RGB) -> Double {
        func channel(_ value: Double) -> Double {
            let c = min(max(value, 0), 1)
            return c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(rgb.red) + 0.7152 * channel(rgb.green) + 0.0722 * channel(rgb.blue)
    }

    /// WCAG contrast ratio between two colours, 1:1 to 21:1.
    static func contrastRatio(_ a: RGB, _ b: RGB) -> Double {
        let la = relativeLuminance(a)
        let lb = relativeLuminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /// The ground the tint is measured against: the lightest (dark scheme) or darkest (light scheme) surface
    /// tinted ink is drawn on, a material capsule over `ArtworkBackground`'s scrim, about #262628 / #EAEAEE.
    /// A tint that clears 4.5:1 there also clears it on the secondary system background, on plain black or
    /// white, and on the scrim itself (Podcasts measured this on device).
    static func background(isDarkScheme: Bool) -> RGB {
        isDarkScheme
            ? RGB(red: 38 / 255, green: 38 / 255, blue: 40 / 255)
            : RGB(red: 234 / 255, green: 234 / 255, blue: 238 / 255)
    }

    // MARK: SwiftUI bridging

    /// Resolves a `Color` to RGB for the given scheme. Asset and dynamic colours resolve per scheme; colours
    /// that can't be read as RGB fall back to the scheme's foreground, which is safe on its own ground.
    ///
    /// Cannot resolve `.accentColor` (the `UIColor` bridge has no SwiftUI environment, so it answers system
    /// blue): resolve the asset instead, `Color("AccentColor")`.
    static func rgb(from color: Color, isDarkScheme: Bool) -> RGB {
        rgb(from: UIColor(color), isDarkScheme: isDarkScheme)
    }

    static func rgb(from color: UIColor, isDarkScheme: Bool) -> RGB {
        let traits = UITraitCollection(userInterfaceStyle: isDarkScheme ? .dark : .light)
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        guard color.resolvedColor(with: traits).getRed(&r, green: &g, blue: &b, alpha: &a) else {
            return isDarkScheme ? RGB(red: 1, green: 1, blue: 1) : RGB(red: 0, green: 0, blue: 0)
        }
        return RGB(red: Double(r), green: Double(g), blue: Double(b))
    }

    static func color(_ rgb: RGB) -> Color {
        Color(red: rgb.red, green: rgb.green, blue: rgb.blue)
    }

    // MARK: Derivation

    /// The scheme-safe form of `rgb`: unchanged when it already meets `minimumContrast` against the scheme
    /// background, otherwise the nearest colour along a hue-preserving tone ramp that does.
    ///
    /// Dark mode raises brightness and eases saturation down as it goes (a saturated navy lifted with its
    /// saturation intact is neon); light mode only lowers brightness, which keeps a pastel's character.
    static func safeTint(for rgb: RGB, isDarkScheme: Bool) -> RGB {
        safeTint(for: rgb, against: background(isDarkScheme: isDarkScheme), isDarkScheme: isDarkScheme)
    }

    /// As above, measured against an explicit ground, for ink drawn on something other than the scheme's.
    static func safeTint(for rgb: RGB, against ground: RGB, isDarkScheme: Bool) -> RGB {
        guard contrastRatio(rgb, ground) < minimumContrast else { return rgb }
        return firstPassingTone(HSB(rgb), isDarkScheme: isDarkScheme) { tone in
            contrastRatio(tone, ground) >= minimumContrast
        }
    }

    // MARK: Wash

    /// Source-over composite of `tint` at `opacity` over `ground`, per channel in sRGB, which is how an
    /// `.opacity` wash composites.
    static func wash(of tint: RGB, over ground: RGB, opacity: Double) -> RGB {
        RGB(
            red: tint.red * opacity + ground.red * (1 - opacity),
            green: tint.green * opacity + ground.green * (1 - opacity),
            blue: tint.blue * opacity + ground.blue * (1 - opacity)
        )
    }

    // MARK: Ink on a tint fill

    static let lightLabel = RGB(red: 1, green: 1, blue: 1)
    /// Near-black rather than black, so the label reads as ink on the colour, not a hole in it.
    static let darkLabel = RGB(red: 0.1, green: 0.1, blue: 0.1)

    /// The label drawn on a solid `fill` (the player's play circle, a prominent button): white when it clears
    /// AA, else near-black. A light tint (the dark-scheme accent, a pastel cover) takes the dark label.
    static func labelColor(onFill fill: RGB) -> RGB {
        contrastRatio(lightLabel, fill) >= minimumContrast ? lightLabel : darkLabel
    }

    // MARK: Tone ramp

    /// The smallest `t` on the ramp whose colour satisfies `passes`. Both ramps are monotonic in luminance, so
    /// 24 bisections land well inside a 1/255 step.
    private static func firstPassingTone(_ hsb: HSB, isDarkScheme: Bool, passes: (RGB) -> Bool) -> RGB {
        var low = 0.0
        var high = 1.0
        for _ in 0..<24 {
            let mid = (low + high) / 2
            if passes(ramp(hsb, t: mid, isDarkScheme: isDarkScheme)) {
                high = mid
            } else {
                low = mid
            }
        }
        return ramp(hsb, t: high, isDarkScheme: isDarkScheme)
    }

    /// `t` = 0 is the original colour; `t` = 1 is white in dark mode and black in light mode, so a passing
    /// point always exists.
    private static func ramp(_ hsb: HSB, t: Double, isDarkScheme: Bool) -> RGB {
        if isDarkScheme {
            let brightness = hsb.brightness + t * (1 - hsb.brightness)
            let saturation = hsb.saturation * (1 - 0.45 * t)
            return HSB(hue: hsb.hue, saturation: saturation, brightness: brightness).rgb
        } else {
            return HSB(hue: hsb.hue, saturation: hsb.saturation, brightness: hsb.brightness * (1 - t)).rgb
        }
    }

    struct HSB {
        var hue: Double
        var saturation: Double
        var brightness: Double

        init(hue: Double, saturation: Double, brightness: Double) {
            self.hue = hue
            self.saturation = min(max(saturation, 0), 1)
            self.brightness = min(max(brightness, 0), 1)
        }

        init(_ rgb: RGB) {
            let maxC = max(rgb.red, rgb.green, rgb.blue)
            let minC = min(rgb.red, rgb.green, rgb.blue)
            let delta = maxC - minC

            var hue = 0.0
            if delta > 0 {
                switch maxC {
                case rgb.red: hue = ((rgb.green - rgb.blue) / delta).truncatingRemainder(dividingBy: 6)
                case rgb.green: hue = (rgb.blue - rgb.red) / delta + 2
                default: hue = (rgb.red - rgb.green) / delta + 4
                }
                hue *= 60
                if hue < 0 { hue += 360 }
            }
            self.init(hue: hue, saturation: maxC > 0 ? delta / maxC : 0, brightness: maxC)
        }

        var rgb: RGB {
            let c = brightness * saturation
            let h = hue / 60
            let x = c * (1 - abs(h.truncatingRemainder(dividingBy: 2) - 1))
            let m = brightness - c
            let (r, g, b): (Double, Double, Double)
            switch h {
            case ..<1: (r, g, b) = (c, x, 0)
            case ..<2: (r, g, b) = (x, c, 0)
            case ..<3: (r, g, b) = (0, c, x)
            case ..<4: (r, g, b) = (0, x, c)
            case ..<5: (r, g, b) = (x, 0, c)
            default: (r, g, b) = (c, 0, x)
            }
            return RGB(red: r + m, green: g + m, blue: b + m)
        }
    }
}
