import SwiftUI

/// S2's type scale, after Shuttle Podcasts' `ShuttleTypography`.
///
/// The rule: SF Rounded **bold** for in-content titles and section headers only, SF Pro for everything else,
/// and monospaced digits for every time and count. Rounded body text reads as a toy app. The navigation bar's
/// large title is system chrome and is never overridden. Every style is relative to a text style, so all of
/// them follow Dynamic Type.
extension Font {
    /// A screen's in-content title: the Now Playing song.
    static let s2Title = Font.system(.title, design: .rounded, weight: .bold)
    /// A detail hero's title; an empty state's title.
    static let s2Title2 = Font.system(.title2, design: .rounded, weight: .bold)
    static let s2Title3 = Font.system(.title3, design: .rounded, weight: .semibold)

    /// In-content section headers: Home's shelves, "Up Next".
    static let s2SectionTitle = Font.system(.title3, design: .rounded, weight: .bold)

    /// A row's trailing time or count (a song's duration).
    static let s2RowTime = Font.subheadline.monospacedDigit()
    /// A scrubber's elapsed and remaining times, and any other caption-sized time.
    static let s2Time = Font.caption.monospacedDigit()

    /// A symbol that follows Dynamic Type from a base point size, for glyphs that aren't a text style's size
    /// (transport buttons, empty-state symbols).
    static func s2Glyph(size: CGFloat, weight: UIFont.Weight = .regular, relativeTo style: UIFont.TextStyle = .body) -> Font {
        Font(UIFontMetrics(forTextStyle: style).scaledFont(for: .systemFont(ofSize: size, weight: weight)))
    }
}

/// Colours. The accent is the Asset Catalog's `AccentColor` (the app's global tint, set in project.yml), S2's
/// default Android accent `#0088FF` split for contrast as Podcasts splits its coral: light `#006AD1` (5.3:1 on
/// white, 4.7:1 on `secondarySystemBackground`), dark `#3D9DFF` (7.5:1 on black, 5.0:1 on `#2C2C2E`). Everything
/// else is a system semantic colour (`.primary`, `.secondary`, `systemBackground`, materials); status colours
/// are the system red/green/orange.
extension ShapeStyle where Self == Color {
    /// Small secondary labels that must meet WCAG AA (4.5:1) in light mode too: `secondaryLabel` is only 3.4:1 on
    /// white. Light `#66666B` (5.7:1 on white), dark `#9A9AA0` (5.0:1 on a sheet's `#2C2C2E`). For captions and
    /// row subtitles; body copy keeps `.secondary`.
    static var s2SecondaryText: Color { Color("SecondaryText") }
}
