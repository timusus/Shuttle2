---
paths:
  - "android/app/**/ui/**"
  - "android/designsystem/**"
  - "ios/S2/**"
---

# Design

For any UI design, critique or parity work use the global `mobile-design` skill (HIG, Material 3
Expressive, adaptive layouts, native surfaces, evaluation rubric). This file holds the Shuttle facts it
defers to. If the skill and these docs disagree, the docs win; file a `/note` if a doc looks wrong.

## Ground truth

| What | Where |
|---|---|
| Android design language (M3 Expressive spec, tokens, component list, approval gate) | `docs/design/design-language.md` |
| iOS design language (HIG-first tokens, glass, motion) | `docs/design/ios-design-language.md` |
| App shell, navigation, player state model, layout tiers | `docs/architecture/app-shell.md` |
| Redesign inventory and parity checklist | `docs/architecture/redesign-inventory.md` |
| Android tokens and components | `android/designsystem/.../theme/`, `.../component/`, debug `catalog/` |
| iOS tokens and components | `ios/S2/Theme/`, `ios/S2/Components/` |
| Goldens (per component and per screen) | `docs/design/catalog/**`, `docs/design/<screen>/**` |
| Layout tiers in code | Android `ui/shell/adaptive/ShellLayout.kt`; iOS `ios/S2/Theme/LayoutTier.swift` |
| Competitor teardown, feature-expectations checklist, anti-patterns | `docs/design/competitors.md` |

Tokens: `S2Theme`, `S2Spacing`, `S2Shapes`, `S2Preview` (Android); `Spacing` (iOS). Screens never use
literal `dp`/`Color(0x…)`/`Font.system(size:)`.

## Shuttle principles

- **Artwork colour is the signature.** Shuttle pioneered theming the app from album-art colours and was
  known for it; it is the brand's one owned idea. Spend boldness there first and keep it more refined
  than competitors' copies (judge side by side in critiques).
- Artwork-derived colour on media screens beats dynamic colour (dynamic colour for chrome only).
- iOS is the current quality reference for intent; Android's usual gap is screens bypassing the design
  system, so most Android polish is moving screens onto tokens.
- Whole-app audits load `docs/design/competitors.md` for the feature-expectations pass.
- Screenshot evidence: `support/scripts/design-shots.sh` (via `longjob.sh`), goldens in `docs/design/**`,
  `emulator-check` and `ios/scripts/maestro-sim.sh` for ad hoc states. Issue evidence images go on the
  orphan `design-evidence` branch of `timusus/Shuttle2` (raw URLs); file findings with `/note` (label
  `design`).
- Native-surface state: CarPlay, Wear/watchOS and a QS tile are absent; the Android widget is Glance
  `NowPlayingWidget`, iOS is `S2Widgets`, shortcuts are `IntentPerformers`.
