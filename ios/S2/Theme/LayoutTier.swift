import SwiftUI

/// The width tier the root layout is built for, mirroring Android's shell (docs/architecture/app-shell.md)
/// and Shuttle Podcasts' `LayoutTier`, so briefs and reviews line up across platforms.
///
/// - `compact`: `horizontalSizeClass == .compact`. iPhone, Slide Over, narrow Split View. The tab bar.
/// - `regular`: regular width, container narrower than `wideThreshold`. iPad portrait, half Split View.
///   A sidebar in place of the tab bar.
/// - `wide`: regular width at or above `wideThreshold`. iPad landscape. The same sidebar; later phases
///   add a persistent player column here, as Android's expanded layout has its player pane.
///
/// Keyed off size class and measured container width only, never the device idiom, the screen's bounds
/// or orientation: Split View, Stage Manager and foldables resize the window without touching those.
enum LayoutTier: Hashable {
    case compact
    case regular
    case wide

    /// Regular-width containers at or above this width resolve to `.wide`.
    static let wideThreshold: CGFloat = 1000

    /// A `nil` size class (no window yet) resolves to `.compact`, the layout every device renders
    /// correctly. A zero width (first layout pass) resolves to `.regular` rather than `.wide`.
    static func resolve(horizontalSizeClass: UserInterfaceSizeClass?, containerWidth: CGFloat) -> LayoutTier {
        guard horizontalSizeClass == .regular else { return .compact }
        return containerWidth >= wideThreshold ? .wide : .regular
    }
}

private struct LayoutTierKey: EnvironmentKey {
    static let defaultValue: LayoutTier = .compact
}

extension EnvironmentValues {
    /// Resolved once in `ContentView`; child views read this instead of `horizontalSizeClass`, so the
    /// breakpoint lives in one file.
    var layoutTier: LayoutTier {
        get { self[LayoutTierKey.self] }
        set { self[LayoutTierKey.self] = newValue }
    }
}
