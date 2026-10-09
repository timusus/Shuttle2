import Shared
import SwiftUI

/// The soft wash of the screen's tint behind a hero: fading out into the list below a stacked hero, or a panel
/// that never quite fades behind the two-column hero, so the column reads as one tinted surface.
struct DetailWash: View {
    enum Style { case fading, panel }
    let style: Style

    @Environment(\.artworkTint) private var tint
    @Environment(\.detailPalette) private var detailPalette
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        let palette = detailPalette ?? .inheriting(isDarkScheme: colorScheme == .dark)
        let wash = palette.wash.map(ContrastSafeTint.color) ?? tint
        let peak = palette.washOpacity
        switch style {
        case .fading:
            LinearGradient(colors: [wash.opacity(peak), wash.opacity(0.06), wash.opacity(0)], startPoint: .top, endPoint: .bottom)
        case .panel:
            LinearGradient(colors: [wash.opacity(peak - 0.04), wash.opacity(0.06)], startPoint: .top, endPoint: .bottom)
        }
    }
}

/// A detail screen's hero colours for its cover and scheme (#744), after Now Playing's `PlayerPalette`.
///
/// Dark, or with no cover colour, the hero draws in the tint in scope (`\.artworkTint`): the wash is the tint itself.
/// Light with a cover colour, that tint is the cover darkened until it reads on the page, and a wash of it was a
/// muddy beige or grey-blue, so the hero looked flat. There the wash is the cover's hue at a vivid tone instead, as
/// strong as the secondary text still clearing AA on it allows, and the tint is measured against the darkest ground
/// it's drawn on (the Shuffle capsule over the top of the wash), so the artist line, the Shuffle label and the
/// playing row clear AA too.
struct DetailPalette: Equatable {
    typealias RGB = ContrastSafeTint.RGB

    /// The wash's colour; nil draws it in the tint in scope.
    let wash: RGB?
    /// The wash's opacity at its strongest (the top of the fading wash; the panel's is a little less).
    let washOpacity: Double
    /// The Shuffle capsule's fill: the wash colour (or the tint) at this opacity over the page.
    let tonalOpacity: Double
    /// The hero's tint and the ink on a tint fill; nil keeps the scope's (`.artworkTint(from:)`).
    let tint: RGB?
    let ink: RGB?

    enum Tone {
        /// The wash's colour in the light scheme: the cover's hue, its saturation clamped here, at this brightness.
        static let lightWashSaturation: ClosedRange<Double> = 0.45...0.85
        static let lightWashBrightness = 0.85
        /// The light wash's strongest opacity, before the secondary text's contrast lowers it.
        static let lightWashMaxOpacity = 0.32
        static let lightTonalOpacity = 0.2
        /// The wash and the Shuffle capsule as they were before #744, kept for dark and the accent.
        static let washOpacity = 0.22
        static let darkTonalOpacity = 0.26
        static let lightAccentTonalOpacity = 0.16
    }

    /// The page under the light hero: `List`'s plain ground.
    static let lightPage = RGB(red: 1, green: 1, blue: 1)

    /// - Parameters:
    ///   - extracted: the cover's raw colour (`\.artworkTintSource`), nil for none.
    ///   - secondaryText: `s2TextSecondary` in the light scheme; the wash stays light enough for it to clear AA.
    init(extracted: RGB?, secondaryText: RGB, isDarkScheme: Bool) {
        guard let extracted, !isDarkScheme else {
            wash = nil
            washOpacity = Tone.washOpacity
            tonalOpacity = isDarkScheme ? Tone.darkTonalOpacity : Tone.lightAccentTonalOpacity
            tint = nil
            ink = nil
            return
        }
        let hsb = ContrastSafeTint.HSB(extracted)
        let wash = ContrastSafeTint.HSB(
            hue: hsb.hue,
            saturation: min(max(hsb.saturation, Tone.lightWashSaturation.lowerBound), Tone.lightWashSaturation.upperBound),
            brightness: Tone.lightWashBrightness
        ).rgb
        let washOpacity = Self.strongestWash(of: wash, keeping: secondaryText)
        let washTop = ContrastSafeTint.wash(of: wash, over: Self.lightPage, opacity: washOpacity)
        let darkestGround = ContrastSafeTint.wash(of: wash, over: washTop, opacity: Tone.lightTonalOpacity)
        let tint = ContrastSafeTint.safeTint(for: extracted, against: darkestGround, isDarkScheme: false)
        self.wash = wash
        self.washOpacity = washOpacity
        tonalOpacity = Tone.lightTonalOpacity
        self.tint = tint
        ink = ContrastSafeTint.labelColor(onFill: tint)
    }

    /// The highest opacity, up to `Tone.lightWashMaxOpacity`, at which `ink` still clears AA on `wash` over the page.
    /// The wash is darker than the page, so the ground only darkens as the opacity rises.
    static func strongestWash(of wash: RGB, keeping ink: RGB) -> Double {
        func passes(_ opacity: Double) -> Bool {
            let ground = ContrastSafeTint.wash(of: wash, over: lightPage, opacity: opacity)
            return ContrastSafeTint.contrastRatio(ink, ground) >= ContrastSafeTint.minimumContrast
        }
        if passes(Tone.lightWashMaxOpacity) { return Tone.lightWashMaxOpacity }
        var low = 0.0
        var high = Tone.lightWashMaxOpacity
        for _ in 0..<20 {
            let mid = (low + high) / 2
            if passes(mid) { low = mid } else { high = mid }
        }
        return low
    }

    /// The palette that draws everything in the tint in scope: dark, or no cover colour.
    static func inheriting(isDarkScheme: Bool) -> DetailPalette {
        DetailPalette(extracted: nil, secondaryText: lightPage, isDarkScheme: isDarkScheme)
    }

    /// The palette for the environment's cover colour and scheme.
    static func resolve(extracted: RGB?, isDarkScheme: Bool) -> DetailPalette {
        DetailPalette(
            extracted: extracted,
            secondaryText: ContrastSafeTint.rgb(from: UIColor.s2TextSecondary, isDarkScheme: false),
            isDarkScheme: isDarkScheme
        )
    }
}

extension EnvironmentValues {
    /// Set by `DetailScaffold` for its wash and the hero's capsules.
    @Entry var detailPalette: DetailPalette?
}

/// Inside `.artworkTint(from:)`: resolves the screen's `DetailPalette` and, where it has its own tint (light, with a
/// cover colour), hands that on as `\.artworkTint` and `\.artworkTintInk`, as Now Playing does with its palette.
struct DetailPaletteProvider: ViewModifier {
    @Environment(\.artworkTintSource) private var tintSource
    @Environment(\.artworkTint) private var scopeTint
    @Environment(\.artworkTintInk) private var scopeInk
    @Environment(\.colorScheme) private var colorScheme

    func body(content: Content) -> some View {
        let palette = DetailPalette.resolve(extracted: tintSource, isDarkScheme: colorScheme == .dark)
        content
            .environment(\.detailPalette, palette)
            .environment(\.artworkTint, palette.tint.map(ContrastSafeTint.color) ?? scopeTint)
            .environment(\.artworkTintInk, palette.ink.map(ContrastSafeTint.color) ?? scopeInk)
    }
}
