# iOS — SwiftUI, HIG and Liquid Glass

Exact values: `docs/design/ios-design-language.md` and `ios/S2/Theme/`. Deployment target is iOS 17;
Liquid Glass applies on iOS 26 with `#available` fallbacks. The iOS app is the current quality
reference for Shuttle — when Android and iOS disagree on intent, iOS is usually right; on idiom, each
platform is right about itself.

## Stance

Native first: system components, system navigation, SF Symbols, Dynamic Type. Brand through artwork
tint, motion and content. If a system component exists, use it and style it lightly.

## Liquid Glass (iOS 26)

- Glass is for the **floating control and navigation layer** only: tab bar, toolbars, the mini player
  accessory, floating transport controls. Never rows, cards, artwork or text blocks.
- Regular variant by default; clear only over rich media (artwork), with a dimming layer (~35%) when the
  art is bright. Group siblings in `GlassEffectContainer`; never nest glass.
- Respect Reduce Transparency and Increase Contrast (fall back to opaque materials).
- Pre-26 fallback: system materials (`.regularMaterial` etc.), not hand-built blur.

## Navigation and structure

- `TabView` with `.tabViewStyle(.sidebarAdaptable)` → sidebar on iPad / wide windows. Tabs navigate,
  never act. Search may be a dedicated tab.
- Mini player = tab bar bottom accessory (minimises inline on scroll, as Apple Music).
- `NavigationStack` per tab; `NavigationSplitView` only where a persistent sidebar/list is right.
- Large titles on library roots, inline on detail; toolbar items in system positions.
- Sheets: `.sheet` with `.presentationDetents`; destructive choices via `.confirmationDialog`.

## Type, colour, symbols

- Text styles (`.title`, `.headline`, `.body`, `.subheadline`, `.footnote`) via the `Typography` tokens;
  test at AX5. Avoid `.caption2` for anything that must be read.
- Semantic colours (`.primary`, `.secondary`, `Color(.systemBackground)`) plus one tint; artwork tint
  from `ArtworkTint`/`PlayerPalette`.
- SF Symbols with meaningful effects: `.contentTransition(.symbolEffect(.replace))` play↔pause,
  `.symbolEffect(.bounce)` on like, variable colour only for changing values (volume, download progress).

## Interaction

- Context menus with previews on album/artist/song/playlist rows (most-used first, destructive last and
  marked `.destructive`).
- Swipe actions on list rows for queue/playlist edits (Play Next, Remove).
- `.sensoryFeedback` on the same moments as Android: toggle, confirm, reorder drop, scrub detents.
- Pull to refresh only where data is remote and refreshable.

## Layout

- Size classes via `@Environment(\.horizontalSizeClass)`; layout tier via `LayoutTier`. Prefer
  `ViewThatFits`, `containerRelativeFrame`, `Grid`/`LazyVGrid(.adaptive)` over `GeometryReader` and screen bounds.
- Extend artwork/backgrounds under bars (`.backgroundExtensionEffect()` on 26) — no hard bands behind
  glass.

## Verification

- Maestro flows in `ios/maestro/*.yaml` via `ios/scripts/maestro-sim.sh` (leases a simulator; never
  `simctl create`). Dark: `xcrun simctl ui <udid> appearance dark`; text size:
  `xcrun simctl ui <udid> content_size accessibility-extra-extra-extra-large`.
- Previews: `#Preview` per component for compact/regular, light/dark, AX5.
- Physical device: `ios-device` skill.
