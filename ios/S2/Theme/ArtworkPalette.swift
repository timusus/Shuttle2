import SwiftUI
import UIKit

/// The colours of artwork S2 draws itself (#646): the neutral placeholder for a cover that's missing or still
/// loading, and the muted tones of `GeneratedArtwork` for a genre or playlist, which have no cover of their own.
enum ArtworkPalette {
    /// The placeholder's fill: a system grey, the same quiet tone whatever the screen's tint.
    static let placeholderFill = Color(.systemGray5)
    /// The placeholder's glyph: a step darker (lighter in dark mode) than the fill, so it reads without shouting.
    static let placeholderGlyph = Color(.systemGray2)
    /// A generated artwork's glyph: secondary ink over its muted tone, as Android's `onSurfaceVariant`.
    static let generatedGlyph = Color(.secondaryLabel)

    /// A glyph's share of the artwork's side: the placeholder's, and the smaller one on generated artwork
    /// (Android's `GeneratedArtworkColors.GLYPH_SCALE`).
    static let placeholderGlyphScale: CGFloat = 0.38
    static let generatedGlyphScale: CGFloat = 0.32

    /// Eight muted tones (slate, sage, sand, clay, dusk, teal, rose, ochre), light and dark: Android's
    /// `GeneratedArtworkColors`, value for value, so a genre has the same tone on both platforms.
    private static let light: [UInt32] = [0xD5DBE3, 0xD3DDD0, 0xE5DCCB, 0xE3D2CC, 0xDAD5E3, 0xCFDEDD, 0xE4D3D9, 0xE3DBC4]
    private static let dark: [UInt32] = [0x3A4350, 0x3B4739, 0x4B4336, 0x4C3A34, 0x423C4E, 0x344847, 0x4B3940, 0x4A4330]

    /// The palette slot for `seed`, case-insensitive: FNV-1a over its UTF-8 bytes, as Android's `index(seed)`.
    /// (Swift's `hashValue` is seeded per process, so it wouldn't be stable across launches.)
    static func toneIndex(_ seed: String) -> Int {
        var hash: UInt64 = 0xcbf2_9ce4_8422_2325
        for byte in seed.lowercased().utf8 {
            hash ^= UInt64(byte)
            hash = hash &* 0x0000_0100_0000_01b3
        }
        return Int(hash % UInt64(light.count))
    }

    /// `seed`'s tone, its light or dark shade following the interface style.
    static func tone(_ seed: String) -> Color {
        let index = toneIndex(seed)
        return Color(uiColor: UIColor { traits in
            rgb(traits.userInterfaceStyle == .dark ? dark[index] : light[index])
        })
    }

    private static func rgb(_ hex: UInt32) -> UIColor {
        UIColor(
            red: CGFloat((hex >> 16) & 0xFF) / 255,
            green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255,
            alpha: 1
        )
    }
}
