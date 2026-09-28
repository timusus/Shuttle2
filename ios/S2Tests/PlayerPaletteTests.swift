import Testing
@testable import S2

/// Now Playing's palette: the tint, its label and the secondary ink clear AA on the backdrop with the worst cover
/// showing through, in both schemes, for coloured, near-black and greyscale covers.
struct PlayerPaletteTests {
    typealias RGB = ContrastSafeTint.RGB

    static let accent = RGB(red: 0.2, green: 0.45, blue: 0.95)
    static let covers: [RGB?] = [
        RGB(red: 0.75, green: 0.45, blue: 0.3), // warm
        RGB(red: 0.95, green: 0.9, blue: 0.1), // bright yellow
        RGB(red: 0.1, green: 0.2, blue: 0.8), // deep blue
        RGB(red: 0.05, green: 0.04, blue: 0.06), // near black
        nil, // greyscale: no dominant colour
    ]

    @Test(arguments: covers, [false, true])
    func inkClearsAAOverTheWorstCover(cover: RGB?, isDarkScheme: Bool) {
        let palette = PlayerPalette(extracted: cover, accent: Self.accent, isDarkScheme: isDarkScheme)
        let controls = PlayerPalette.worstGround(
            under: palette.ground, showThrough: PlayerPalette.controlShowThrough, isDarkScheme: isDarkScheme
        )
        let text = PlayerPalette.worstGround(
            under: palette.ground, showThrough: PlayerPalette.textShowThrough, isDarkScheme: isDarkScheme
        )
        #expect(ContrastSafeTint.contrastRatio(palette.tint, controls) >= ContrastSafeTint.minimumContrast)
        #expect(ContrastSafeTint.contrastRatio(palette.onTint, palette.tint) >= ContrastSafeTint.minimumContrast)
        #expect(ContrastSafeTint.contrastRatio(palette.secondaryInk, text) >= ContrastSafeTint.minimumContrast)
    }

    @Test func aColouredCoverKeepsItsHue() {
        let cover = RGB(red: 0.75, green: 0.45, blue: 0.3)
        let palette = PlayerPalette(extracted: cover, accent: Self.accent, isDarkScheme: false)
        #expect(palette.isTinted)
        #expect(abs(ContrastSafeTint.HSB(palette.ground).hue - ContrastSafeTint.HSB(cover).hue) < 0.02)
    }

    @Test(arguments: [false, true])
    func aGreyscaleCoverFallsBackToANeutralGround(isDarkScheme: Bool) {
        let palette = PlayerPalette(extracted: nil, accent: Self.accent, isDarkScheme: isDarkScheme)
        #expect(!palette.isTinted)
        #expect(palette.ground == (isDarkScheme ? PlayerPalette.neutralDark : PlayerPalette.neutralLight))
    }
}
