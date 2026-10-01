import SwiftUI
import UIKit

/// S2's type scale (docs/design/ios-design-language.md §Type).
///
/// The rule: SF Pro **bold** for display type (the navigation bar's large title, a detail hero's title,
/// the Now Playing song, a first-run welcome), SF Pro for everything else, and monospaced digits for every time
/// and count. Every style is relative to a Dynamic Type text style, so all of them follow the user's text size.
extension Font {
    // MARK: Display

    /// The largest in-content title, matching the navigation bar's large title: the first-run welcome.
    static let s2LargeTitle = Font.system(.largeTitle, weight: .bold)
    /// A detail hero's title and the Now Playing song title (a `MarqueeText` line).
    static let s2HeroTitle = Font.system(.title2, weight: .bold)

    // MARK: Titles

    /// A screen-level title that isn't a hero: an empty state's, an onboarding step's.
    static let s2Title = Font.system(.title2, weight: .bold)
    /// A title's supporting line at title size: the welcome's tagline, a sign-in step's heading.
    static let s2Title3 = Font.system(.title3, weight: .semibold)
    /// In-content section headers: Home's shelves, "Up Next" (`SectionHeader`).
    static let s2SectionTitle = Font.system(.title3, weight: .bold)
    /// A card's title: a source card, the resume card's song.
    static let s2Headline = Font.headline
    /// Labels that title a run of rows rather than a section: disc headers, "Now Playing" over the queue's card.
    static let s2GroupHeader = Font.system(.caption, weight: .semibold)
    /// Secondary labels above or below a title: the "Continue" over the resume card, a hero's
    /// "artist · year · 12 songs". `caption` at medium weight, not `caption2` regular, which reads as noise.
    static let s2Eyebrow = Font.system(.caption, weight: .medium)

    // MARK: Rows

    /// A list row's title (`MediaRow`, text-only rows).
    static let s2RowTitle = Font.body
    /// A list row's second line: artist, album, counts.
    static let s2RowSubtitle = Font.subheadline
    /// A row's trailing time or count (a song's duration).
    static let s2RowMeta = Font.subheadline.monospacedDigit()

    // MARK: Body

    /// Hints and footers under a control or section: Home's cold-start line, a sign-in hint.
    static let s2Caption = Font.footnote
    /// A button's label where the style doesn't set one: Shuffle All, Get Started.
    static let s2Button = Font.headline
    /// A scrubber's elapsed and remaining times, and any other caption-sized time.
    static let s2Time = Font.caption.monospacedDigit()

    // MARK: Glyphs

    /// A symbol that follows Dynamic Type from a base point size, for glyphs that aren't a text style's size
    /// (transport buttons, empty-state symbols). Base sizes come from `IconSize` where one fits.
    static func s2Glyph(size: CGFloat, weight: UIFont.Weight = .regular, relativeTo style: UIFont.TextStyle = .body) -> Font {
        Font(UIFontMetrics(forTextStyle: style).scaledFont(for: .systemFont(ofSize: size, weight: weight)))
    }

    /// A symbol at a size the caller has already scaled (a `@ScaledMetric` or a size derived from one), so it
    /// isn't scaled twice.
    static func s2ScaledGlyph(_ size: CGFloat, weight: Font.Weight = .regular) -> Font {
        .system(size: size, weight: weight)
    }
}
