import SwiftUI
import UIKit

// MARK: - Environment

/// The artwork tint in scope, after Shuttle Podcasts' `ArtworkTintEnvironment`. Outside any
/// `.artworkTint(from:)` it is the app accent, so a view can always draw with `\.artworkTint`.
///
/// - `artworkTint`: the scheme-safe tint (4.5:1 against the scheme's chrome ground), for glyphs, small labels,
///   the scrubber, a playing row, washes (`.opacity`) and fills.
/// - `artworkTintInk`: the label ink for text or a glyph drawn *on* a tint fill (the play circle, a prominent
///   Play button): white or near-black, whichever clears AA on the fill.
/// - `isArtworkTinted`: whether the tint came from artwork, rather than being the accent fallback.
/// - `artworkTintSource`: the raw extracted colour, nil for the accent fallback. Never draw it; a surface that
///   isn't the scheme's ground derives its own colours from it (Now Playing's `PlayerPalette`).
extension EnvironmentValues {
    @Entry var artworkTint: Color = .accentColor
    @Entry var artworkTintInk: Color = TintedChromeInk.onAccent
    @Entry var isArtworkTinted: Bool = false
    @Entry var artworkTintSource: ContrastSafeTint.RGB? = nil
}

// MARK: - Provider

extension View {
    /// Tints this subtree from `source`'s artwork: extracts the cover's dominant colour, makes it scheme-safe
    /// and provides it as `\.artworkTint` (plus `\.artworkTintInk` and `\.isArtworkTinted`). With no source,
    /// or a cover with no dominant hue, the tint is the app accent.
    ///
    /// Scope it to what the artwork belongs to: the player and mini player take the playing song's
    /// (`ContentView`), an album or artist hero takes its own. It sets the environment only; apply `.tint(tint)`
    /// yourself where controls should pick it up, since a tint above a `TabView` would recolour the tab bar.
    func artworkTint(from source: ArtworkSource?, extractor: ArtworkColorExtractor? = nil) -> some View {
        modifier(ArtworkTintModifier(source: source, extractor: extractor))
    }
}

struct ArtworkTintModifier: ViewModifier {
    let source: ArtworkSource?
    let extractor: ArtworkColorExtractor?

    /// The raw extracted colour. The tint is derived from it per scheme on every body, so switching to dark
    /// mode re-derives at once instead of waiting for the next cover.
    @State private var extracted: ContrastSafeTint.RGB?

    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func body(content: Content) -> some View {
        let tint = ArtworkTintValues(extracted: extracted, isDarkScheme: colorScheme == .dark)
        content
            .environment(\.artworkTint, tint.tint)
            .environment(\.artworkTintInk, tint.ink)
            .environment(\.isArtworkTinted, tint.isTinted)
            .environment(\.artworkTintSource, extracted)
            .task(id: source?.id) {
                // The old tint stays until the new one is known, so a skip doesn't flash the accent.
                let found: ContrastSafeTint.RGB? = if let source { await (extractor ?? .shared).color(for: source) } else { nil }
                if Task.isCancelled || found == extracted { return }
                withAnimation(Motion.tintChange.reduced(reduceMotion)) { extracted = found }
            }
    }
}

/// What `.artworkTint(from:)` provides for an extracted colour and scheme, as plain values so it's testable
/// without a view.
struct ArtworkTintValues {
    let tint: Color
    let ink: Color
    let isTinted: Bool
    /// The scheme-safe RGB, nil for the accent fallback.
    let safeRGB: ContrastSafeTint.RGB?

    init(extracted: ContrastSafeTint.RGB?, isDarkScheme: Bool) {
        guard let extracted else {
            tint = .accentColor
            ink = TintedChromeInk.onAccent
            isTinted = false
            safeRGB = nil
            return
        }
        let safe = ContrastSafeTint.safeTint(for: extracted, isDarkScheme: isDarkScheme)
        tint = ContrastSafeTint.color(safe)
        ink = ContrastSafeTint.color(ContrastSafeTint.labelColor(onFill: safe))
        isTinted = true
        safeRGB = safe
    }
}

// MARK: - Tinted chrome ink

/// Ink for tinted chrome.
enum TintedChromeInk {
    /// The label on an accent fill, per scheme: white on the light-scheme `#006AD1`, near-black on the
    /// dark-scheme `#3D9DFF` (white there is 2.9:1).
    static let onAccent = Color(uiColor: UIColor { traits in
        let accent = UIColor(named: "AccentColor") ?? .systemBlue
        let rgb = ContrastSafeTint.rgb(from: accent, isDarkScheme: traits.userInterfaceStyle == .dark)
        let label = ContrastSafeTint.labelColor(onFill: rgb)
        return UIColor(red: label.red, green: label.green, blue: label.blue, alpha: 1)
    })
}
