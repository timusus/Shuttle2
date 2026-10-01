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

    /// A card, notice or grouped cell on the ground.
    static let s2SurfaceContainer = UIColor.secondarySystemBackground
    /// A placeholder or skeleton fill.
    static let s2SurfaceFill = UIColor.systemGray5

    /// Captions and row subtitles that must meet WCAG AA (4.5:1) in light mode too: `secondaryLabel` is only
    /// 3.4:1 on white. Light `#66666B` (5.7:1 on white), dark `#9A9AA0` (5.0:1 on a sheet's `#2C2C2E`).
    static let s2TextSecondary = UIColor(named: "SecondaryText") ?? .secondaryLabel

    static let s2Success = UIColor.systemGreen
    static let s2Error = UIColor.systemRed
}

extension ShapeStyle where Self == Color {
    static var s2Accent: Color { Color(uiColor: .s2Accent) }
    static var s2OnAccent: Color { Color(uiColor: .s2OnAccent) }
    static var s2SurfaceContainer: Color { Color(uiColor: .s2SurfaceContainer) }
    static var s2SurfaceFill: Color { Color(uiColor: .s2SurfaceFill) }
    static var s2TextSecondary: Color { Color(uiColor: .s2TextSecondary) }
    static var s2Success: Color { Color(uiColor: .s2Success) }
    static var s2Error: Color { Color(uiColor: .s2Error) }
}

/// The app tint for UIKit-drawn chrome (alerts, menus, share sheets), which reads the window's tint rather than
/// SwiftUI's `.tint`. Applied once at launch (`S2App`).
enum AccentTint {
    static func apply() {
        UIWindow.appearance().tintColor = .s2Accent
    }
}

extension View {
    /// A switch is the HIG green, not the accent: a near-white on-track under the white knob has no contrast in dark
    /// mode. Apply it to every `Toggle`. (A root `toggleStyle` and `UISwitch.appearance()` don't reach SwiftUI's
    /// iOS 26 switch.) Sliders and steppers keep the accent, whose track differs from the knob.
    func s2Switch() -> some View { tint(.s2Success) }
}
