import SwiftUI
import UIKit

/// Now Playing's colours, derived from the cover's raw extracted colour (`\.artworkTintSource`) per scheme (#624).
///
/// The player's ground is the cover's hue, not the system background: a light, saturated wash in the light scheme
/// and a deep one in the dark, so the screen reads as the artwork top to bottom whatever the cover's own tone. The
/// hue survives but the tone is normalised, so a near-black or washed-out cover still gives a rich ground, and a
/// cover with no dominant hue (greyscale, near-black) falls back to a neutral grey ground and the app accent.
///
/// Everything drawn on the ground is measured against it rather than the scheme's chrome ground: `tint` is the
/// extracted colour (or the accent) moved along `ContrastSafeTint`'s tone ramp until it clears 4.5:1 on `ground`,
/// `secondaryInk` is the label colour faded toward the ground only as far as AA allows. Both are measured with the worst cover
/// showing through as much as `ArtworkBackground` lets it where they sit (`worstGround`).
struct PlayerPalette: Equatable {
    /// The control zone's ground: the lower half of the screen, under the title, scrubber and transport.
    let ground: ContrastSafeTint.RGB
    /// A brighter (light) or lighter (dark) form of the hue for the top of the screen, behind the cover.
    let glow: ContrastSafeTint.RGB
    /// Glyphs and fills in the cover's colour: AA on `ground`.
    let tint: ContrastSafeTint.RGB
    /// Text or a glyph on a `tint` fill (the play circle).
    let onTint: ContrastSafeTint.RGB
    /// Captions and neutral glyphs: AA on `ground`.
    let secondaryInk: ContrastSafeTint.RGB
    /// Whether the ground came from the cover's hue, rather than the neutral fallback.
    let isTinted: Bool

    /// The ground's saturation and brightness per scheme; the cover's hue is kept.
    enum Tone {
        static let lightGroundSaturation: ClosedRange<Double> = 0.2...0.36
        static let lightGroundBrightness = 0.95
        static let lightGlowSaturation: ClosedRange<Double> = 0.35...0.6
        static let lightGlowBrightness = 0.86
        static let darkGroundSaturation: ClosedRange<Double> = 0.4...0.7
        static let darkGroundBrightness = 0.2
        static let darkGlowSaturation: ClosedRange<Double> = 0.45...0.75
        static let darkGlowBrightness = 0.34
    }

    /// The neutral grounds for a cover with no dominant hue.
    static let neutralLight = ContrastSafeTint.RGB(red: 0.9, green: 0.9, blue: 0.92)
    static let neutralLightGlow = ContrastSafeTint.RGB(red: 0.8, green: 0.8, blue: 0.83)
    static let neutralDark = ContrastSafeTint.RGB(red: 0.1, green: 0.1, blue: 0.11)
    static let neutralDarkGlow = ContrastSafeTint.RGB(red: 0.2, green: 0.2, blue: 0.22)

    /// The primary label on the ground: near-black in light, white in dark.
    static func primaryInk(isDarkScheme: Bool) -> ContrastSafeTint.RGB {
        isDarkScheme ? ContrastSafeTint.lightLabel : ContrastSafeTint.darkLabel
    }

    /// - Parameters:
    ///   - extracted: the cover's raw colour (`\.artworkTintSource`), nil for none.
    ///   - accent: the app accent, resolved for the scheme; the tint when there is no cover colour.
    init(extracted: ContrastSafeTint.RGB?, accent: ContrastSafeTint.RGB, isDarkScheme: Bool) {
        if let extracted {
            let hsb = ContrastSafeTint.HSB(extracted)
            func tone(_ saturation: ClosedRange<Double>, _ brightness: Double) -> ContrastSafeTint.RGB {
                ContrastSafeTint.HSB(
                    hue: hsb.hue,
                    saturation: min(max(hsb.saturation, saturation.lowerBound), saturation.upperBound),
                    brightness: brightness
                ).rgb
            }
            if isDarkScheme {
                ground = tone(Tone.darkGroundSaturation, Tone.darkGroundBrightness)
                glow = tone(Tone.darkGlowSaturation, Tone.darkGlowBrightness)
            } else {
                ground = tone(Tone.lightGroundSaturation, Tone.lightGroundBrightness)
                glow = tone(Tone.lightGlowSaturation, Tone.lightGlowBrightness)
            }
            isTinted = true
        } else {
            ground = isDarkScheme ? Self.neutralDark : Self.neutralLight
            glow = isDarkScheme ? Self.neutralDarkGlow : Self.neutralLightGlow
            isTinted = false
        }
        let controlGround = Self.worstGround(under: ground, showThrough: Self.controlShowThrough, isDarkScheme: isDarkScheme)
        let textGround = Self.worstGround(under: ground, showThrough: Self.textShowThrough, isDarkScheme: isDarkScheme)
        tint = ContrastSafeTint.safeTint(for: extracted ?? accent, against: controlGround, isDarkScheme: isDarkScheme)
        onTint = ContrastSafeTint.labelColor(onFill: tint)
        secondaryInk = Self.secondaryInk(on: textGround, isDarkScheme: isDarkScheme)
    }

    /// How much of the blurred cover `ArtworkBackground` lets through, at most, where the title and captions sit
    /// (the secondary ink is measured there), and where the scrubber, transport and capsule sit (the tint).
    static let textShowThrough = 0.18
    static let controlShowThrough = 0.06

    /// `ground` with the worst cover showing through it by `showThrough`: black in the light scheme (where ink is
    /// dark), white in the dark. Ink measured here clears AA on the backdrop whatever the cover.
    static func worstGround(under ground: ContrastSafeTint.RGB, showThrough: Double, isDarkScheme: Bool) -> ContrastSafeTint.RGB {
        let worst = isDarkScheme ? ContrastSafeTint.RGB(red: 1, green: 1, blue: 1) : ContrastSafeTint.RGB(red: 0, green: 0, blue: 0)
        return ContrastSafeTint.wash(of: worst, over: ground, opacity: showThrough)
    }

    /// The label faded toward `ground` as far as it can go and still clear AA: a caption that carries the ground's
    /// hue, the way vibrancy does.
    static func secondaryInk(on ground: ContrastSafeTint.RGB, isDarkScheme: Bool) -> ContrastSafeTint.RGB {
        let label = primaryInk(isDarkScheme: isDarkScheme)
        var opacity = 0.6
        while opacity < 1 {
            let ink = ContrastSafeTint.wash(of: label, over: ground, opacity: opacity)
            if ContrastSafeTint.contrastRatio(ink, ground) >= ContrastSafeTint.minimumContrast { return ink }
            opacity += 0.02
        }
        return label
    }
}

extension PlayerPalette {
    /// The palette for the environment's cover colour and scheme.
    static func resolve(extracted: ContrastSafeTint.RGB?, isDarkScheme: Bool) -> PlayerPalette {
        let accent = ContrastSafeTint.rgb(from: UIColor(named: "AccentColor") ?? .systemBlue, isDarkScheme: isDarkScheme)
        return PlayerPalette(extracted: extracted, accent: accent, isDarkScheme: isDarkScheme)
    }
}
