import SwiftUI
import UIKit

/// Colour roles (docs/design/ios-design-language.md §Colour). Each is a system semantic colour, or an asset with a
/// dark variant, so dark mode and Increase Contrast come free. Screens draw with a role, never a literal.
///
/// Chrome is neutral, as on Android: the accent is near-black / near-white (the `AccentColor` asset). The system
/// ignores a neutral asset as the global accent (`NSAccentColorName`) and falls back to system blue, so
/// `AccentTint.apply()` sets UIKit's window tint and `S2App` applies `.tint(.s2Accent)` at the root; draw with
/// `.s2Accent`, never `Color.accentColor`. Colour comes from artwork: `.artworkTint(from:)` overrides the accent
/// in the player, the mini player and detail heroes, and `\.artworkTint` falls back to the accent everywhere else.
extension UIColor {
    /// Controls, selection, the tab bar's selected item, prominent buttons' fill: near-black in light mode,
    /// near-white in dark (`AccentColor`).
    static let s2Accent = UIColor(named: "AccentColor") ?? .label
    /// A label or glyph drawn on an accent fill.
    static let s2OnAccent = UIColor.systemBackground

    /// The screen's ground.
    static let s2Surface = UIColor.systemBackground
    /// A card, notice or grouped cell on the ground.
    static let s2SurfaceContainer = UIColor.secondarySystemBackground
    /// A control or cell on a container: a text field on a card.
    static let s2SurfaceContainerHigh = UIColor.tertiarySystemBackground
    /// A placeholder or skeleton fill.
    static let s2SurfaceFill = UIColor.systemGray5

    /// List and section separators (`rowSeparator`).
    static let s2Separator = UIColor.separator

    static let s2TextPrimary = UIColor.label
    /// Captions and row subtitles that must meet WCAG AA (4.5:1) in light mode too: `secondaryLabel` is only
    /// 3.4:1 on white. Light `#66666B` (5.7:1 on white), dark `#9A9AA0` (5.0:1 on a sheet's `#2C2C2E`).
    static let s2TextSecondary = UIColor(named: "SecondaryText") ?? .secondaryLabel
    /// Placeholder text and disabled labels; never for text a user has to read.
    static let s2TextTertiary = UIColor.tertiaryLabel

    static let s2Success = UIColor.systemGreen
    static let s2Warning = UIColor.systemOrange
    static let s2Error = UIColor.systemRed

    /// The playing row's title and indicator where no artwork tint is in scope.
    static let s2NowPlaying = s2Accent
}

extension ShapeStyle where Self == Color {
    static var s2Accent: Color { Color(uiColor: .s2Accent) }
    static var s2OnAccent: Color { Color(uiColor: .s2OnAccent) }
    static var s2Surface: Color { Color(uiColor: .s2Surface) }
    static var s2SurfaceContainer: Color { Color(uiColor: .s2SurfaceContainer) }
    static var s2SurfaceContainerHigh: Color { Color(uiColor: .s2SurfaceContainerHigh) }
    static var s2SurfaceFill: Color { Color(uiColor: .s2SurfaceFill) }
    static var s2Separator: Color { Color(uiColor: .s2Separator) }
    static var s2TextPrimary: Color { Color(uiColor: .s2TextPrimary) }
    static var s2TextSecondary: Color { Color(uiColor: .s2TextSecondary) }
    static var s2TextTertiary: Color { Color(uiColor: .s2TextTertiary) }
    static var s2Success: Color { Color(uiColor: .s2Success) }
    static var s2Warning: Color { Color(uiColor: .s2Warning) }
    static var s2Error: Color { Color(uiColor: .s2Error) }
    static var s2NowPlaying: Color { Color(uiColor: .s2NowPlaying) }
}

/// The app tint for UIKit-drawn chrome (alerts, menus, share sheets), which reads the window's tint rather than
/// SwiftUI's `.tint`. Applied once at launch (`S2App`).
enum AccentTint {
    static func apply() {
        UIWindow.appearance().tintColor = .s2Accent
    }
}
