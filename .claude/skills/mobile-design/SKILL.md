---
name: mobile-design
description: UI/UX design, critique and audit for Shuttle Music on Android (Compose, Material 3 Expressive) and iOS (SwiftUI, HIG, Liquid Glass) — every form factor (phone, foldable, tablet, desktop window, car, watch) and native surfaces (widgets, Dynamic Island, media session, Auto/CarPlay). Use when designing or reviewing a screen, component or flow, planning a redesign, checking iOS/Android parity, judging whether something "feels native", or before any non-trivial UI change. Triggers on "design", "redesign", "critique", "make it feel native", "polish", "tablet/foldable layout", "widget", "Dynamic Island", "parity".
user_invocable: true
---

# Mobile design (Shuttle Music)

A design skill for a two-platform music player. It is a router: read this file, then load only the
references the task needs. Exact token values (spacing, type scale, radii, colours) live in the repo's
design docs, not here — this skill says *how to decide*, those docs say *what the values are*.

## Ground truth (read before designing)

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

If a reference here conflicts with those docs, the docs win (they record decisions); file a `/note` if
the doc looks wrong.

## Commands

Pick the mode from the request; each lists the references to load.

| Mode | Use when | Load |
|---|---|---|
| **shape** | New screen/feature/flow, or a redesign. Produces a design plan before any code. | craft-floor, platform file(s), adaptive, music, competitors |
| **critique** | "Does this look right / feel native?" on a screen that exists. Screenshot-led. | craft-floor, evaluate, platform file |
| **audit** | Systematic sweep of a module or screen for token, a11y, adaptive and platform-conformance defects. A **whole-app audit is four required passes** — screenshot scorecard, foundations sweep in code against the ground-truth docs, native surfaces, feature expectations — defined in [evaluate.md](evaluate.md); the scorecard alone is not an audit. | craft-floor, evaluate, platform file, adaptive; a whole-app audit also loads native-surfaces, competitors |
| **parity** | Compare one feature across iOS and Android. | parity section below, both platform files |
| **surfaces** | Widgets, Live Activities, controls, tiles, Auto/CarPlay, Wear/Watch, media session. | native-surfaces |
| **polish** | Small refinements to a finished screen (spacing rhythm, motion, optical alignment). | craft-floor, platform file |

References: [craft-floor.md](craft-floor.md) · [android.md](android.md) · [ios.md](ios.md) ·
[adaptive.md](adaptive.md) · [music.md](music.md) · [native-surfaces.md](native-surfaces.md) ·
[evaluate.md](evaluate.md) · [competitors.md](competitors.md)

## Principles (the whole skill in eight lines)

1. **Conventional behaviour, distinctive look.** Follow platform *and* music-genre conventions for
   structure and behaviour (navigation, back, sheets, gestures, insets, a11y) — users read a deviation
   there as broken, not distinctive. Deviate only when you can name the convention, the reason, and the
   accessible standard alternative. Identity lives in the visual and motion layer: artwork-driven colour,
   type, a few signature motions. An Android screen that looks like an iOS port (or vice versa) has
   failed; so has one that looks like a stock Google app.
2. **Parity of intent, not of look.** Same capability and information on both platforms; each built
   from its own idioms (see Parity).
3. **Artwork is the hero, and artwork colour is Shuttle's signature.** Shuttle pioneered theming the
   app from colours extracted from album art and was known for it — it is the brand's one owned idea.
   Covers lead, chrome recedes, colour comes from the art with guaranteed contrast. When deciding where
   to "spend boldness", spend it here first (see music.md → Artwork colour).
4. **Tokens, never literals.** No raw `N.dp`, `Color(0x…)`, `RoundedCornerShape(n.dp)`, `.padding(13)`,
   `Font.system(size:)` in screens. Missing token → add it to the design system, don't inline it.
5. **Every form factor is a first-class layout,** not a stretched phone. Decide per layout tier
   ([adaptive.md](adaptive.md)), at the shell, not ad hoc in screens.
6. **Accessibility is the floor, not a feature:** 48dp/44pt targets, 4.5:1 text contrast, 200% text,
   screen reader labels, reduced motion and transparency honoured.
7. **Fewer, better controls.** Each screen has one primary action; settings and customisation are
   pruned, not accumulated (see the anti-patterns in [competitors.md](competitors.md)).
8. **Evidence over opinion.** No design is "done" without screenshots at the matrix in
   [evaluate.md](evaluate.md), judged by a separate strict pass.

## shape — the design plan

Write the plan (in the issue or a scratch file) before code. Then re-read it against the brief and the
craft floor, revise, and only then build. The plan has:

1. **Job and context** — who, doing what, on which surfaces; the one primary action.
2. **Reference** — the closest prior art in [competitors.md](competitors.md) and in our own goldens, and
   what we take or reject from each.
3. **Information hierarchy** — what is seen first, second, third; what is hidden behind a menu.
4. **Layout per tier** — an ASCII wireframe for Compact, Medium, Expanded+ and tabletop (Android) /
   compact and regular widths (iOS). Name the canonical layout used ([adaptive.md](adaptive.md)).
5. **Platform translation** — a two-column table: Android component/idiom | iOS component/idiom.
6. **States** — loading, empty, error, offline/unavailable-source, partial (downloading), long text,
   huge library, no artwork.
7. **Motion** — what moves, which motion token/spring, and the reduced-motion fallback.
8. **Tokens needed** — existing tokens used, and any new token proposed (with the reason).
9. **Verification** — which goldens/flows will capture it (see [evaluate.md](evaluate.md)).

Spend boldness in one place per screen (usually the artwork treatment or one signature motion); keep
everything else quiet and standard.

## Parity

For one feature, fill this table from code and screenshots of both apps; every row must be "same
intent, native idiom" or an explicit, recorded divergence.

| Aspect | Android idiom | iOS idiom |
|---|---|---|
| Top-level navigation | Navigation bar → rail (NavigationSuite) | Tab bar → sidebar (`sidebarAdaptable`) |
| Mini player | Docked above nav bar; expands via sheet | Tab bar bottom accessory; minimises on scroll |
| Full player dismiss | Drag down + predictive back collapses into mini player | Drag down / swipe; no back button |
| Secondary actions | Overflow menu / modal bottom sheet | Context menu with preview / `.confirmationDialog` |
| Row actions | Long-press → selection mode; swipe only where Material allows | Swipe actions + context menu |
| Sheets | Modal bottom sheet with drag handle | `.sheet` with detents |
| Primary emphasis | Filled/tonal buttons, button groups, FAB/toolbar | Prominent/glass buttons, toolbar items |
| Search | Search bar / SearchView expanding to full screen | `.searchable`, search tab |
| Typography | Google Sans Flex roles, `sp` | SF / Dynamic Type text styles |
| Icons | Material Symbols (rounded, filled when selected) | SF Symbols with effects |
| Colour from art | MaterialKolor scheme from seed, tonal surfaces | Artwork tint/gradient, glass over art |
| Haptics | `HapticFeedbackConstants` on confirm/toggle/drag | `.sensoryFeedback` on the same moments |
| Feedback | Snackbar | Inline confirmation / HUD-free state change |

Flag in a parity report: missing capability on one side; an idiom borrowed from the other platform; the
same idiom done less well (the usual Android problem — compare screenshots side by side).

## Output contract

- **shape**: the plan above, then implementation briefs (via `/brief`) per platform; never one brief
  spanning both platforms' UI code.
- **critique / audit**: findings table — `severity | file:line or screenshot | rule | fix` — ranked,
  only genuine problems, plus the [evaluate.md](evaluate.md) scorecard. A whole-app audit reports all
  four passes and its feature-gap list. File each non-trivial finding with `/note` (label `design`),
  with cropped screenshot evidence embedded in the issue (evaluate.md → Filing findings).
- **parity**: the table above with a verdict per row, and filed gaps.
- Visual claims cite the screenshot they came from and whether it was a golden, emulator/simulator or
  device.
