# Android — Compose and Material 3 Expressive

Exact values and the list of wrapped Expressive components: `docs/design/design-language.md` and
`android/designsystem`. This file is the decision layer.

## Stance

**Material structure and behaviour strictly; Expressive styling selectively.**

- Strict: navigation suite, standard components, insets, predictive back, touch targets, contrast,
  font scaling, the motion system. This is what "native" means on Android.
- Selective: Expressive is a toolkit, not a costume. One or two signature moments per screen (e.g. play
  button shape morph, wavy progress while playing); everything else standard and quiet. Morphing,
  springs and emphasized type on everything is a new kind of slop.
- Shuttle's look, not Google's: artwork-derived colour on media screens beats dynamic colour (dynamic
  colour for chrome only); continuous corners on artwork; restraint. Historically Shuttle was known as
  "the beautiful one" — that identity is worth more than spec conformance.
- Check on One UI as well as Pixel: if it only feels at home on a Pixel, it's too Expressive.
- Expressive APIs are alpha (material3 pinned to an alpha): screens never import them; only
  `designsystem` wrappers do, so API churn stays in one module.

Shuttle's Android problem is rarely the spec — it is screens that bypass the design system (≈290 literal
`dp` in main source vs a few dozen `S2Spacing` uses when this skill was written). Most Android polish
work is **moving screens onto tokens and standard components**, then adding expressive moments
deliberately.

## Theme and colour

- Theme entry is `S2Theme` (`MotionScheme.expressive()`); screens never construct `MaterialTheme` or
  read opt-in Expressive APIs directly — wrappers live in `designsystem`.
- Colour roles only: `primary/onPrimary`, `*Container`, `surface*` (surfaceContainerLowest…Highest for
  elevation-by-tone), `onSurfaceVariant` for secondary text, `outlineVariant` for dividers.
- Scheme sources: neutral default; user accent; Material You dynamic (12+); **artwork scheme on player
  and detail screens** (MaterialKolor, `SPEC_2025`). Never mix sources on one screen.
- Elevation is tonal (surface containers), not shadows, except for floating elements.

## Type

- Google Sans Flex roles (`MaterialTheme.typography.*`). Use **emphasized** roles for the one thing that
  should land (album title on detail, track title on Now Playing); regular roles elsewhere.
- Always `sp`; test at font scale 2.0 (Android 14+ scales nonlinearly — large text grows less).

## Shape

- `MaterialTheme.shapes` / `S2Shapes`; continuous corners (`ContinuousRoundedCornerShape`) for artwork
  to match iOS feel.
- Expressive shape morphing (e.g. play button square↔round on state change, selected chip shape) is a
  signature moment — use for state changes on primary controls, not decoration.

## Motion

- Spatial springs (position, size) for movement; effects springs (colour, alpha) for fades — from
  `MaterialTheme.motionScheme`. Duration/easing tokens only for enter/exit/shared-axis transitions.
- Shared element / container transform: mini player → full player, album tile → album detail.
- Predictive back (on by default at targetSdk 36): the full player, sheets and detail screens must
  animate with the gesture (`PredictiveBackHandler` / Navigation 3 scene transitions) — a player that
  snaps closed on back feels broken.

## Components — standard first

- Navigation: `NavigationSuiteScaffold` semantics (bar → rail → expanded rail), via the shell.
- Top app bars: large/medium flexible app bars collapsing on scroll for detail and library roots; small
  for settings.
- Actions: button groups (connected) for transport and segmented choices; split button for "Play ▾";
  FAB / floating toolbar only for a screen's single primary action (e.g. Shuffle on album detail).
- Lists: one-, two-, three-line list items with leading artwork; expressive list styles (segmented
  grouping) for settings.
- Loading: `LoadingIndicator` (expressive) or skeletons, never a bare `CircularProgressIndicator` in content.
- Menus/sheets: dropdown menu for ≤ 6 actions anchored to a button; modal bottom sheet for richer
  song actions (with artwork header).
- Feedback: snackbar with Undo for destructive-but-reversible actions.

## Edge-to-edge and system UI

- `enableEdgeToEdge()` is on; every screen consumes insets: lists get `contentPadding` from
  `WindowInsets.safeDrawing`/`systemBars`, text fields `imePadding()`, FABs/toolbars sit above the gesture bar.
- Transparent nav bar; no scrims unless content behind the gesture bar needs it.
- Display cut-outs: no interactive content under them in landscape.

## Haptics

Use `LocalHapticFeedback`/`HapticFeedbackConstants` for: toggle (like/shuffle/repeat), confirm
(added to queue), drag start/drop (queue reorder), scrub detents at track start. None on plain taps.

## Previews and verification

- Every component/screen has `@Preview`s via `S2Preview` at phone, foldable, tablet; light/dark;
  font scale 2.0.
- Goldens are Roborazzi, recorded on the Mac (`recordRoborazziDebug` / `verifyRoborazziDebug`) into
  `docs/design/**`; characterisation tests per `verify-ui` skill. Linux renders colour differently — never
  record there.
- Live checks: `emulator-check` skill (Maestro + `takeScreenshot`); dark mode `adb shell cmd uimode night yes`;
  text `adb shell settings put system font_scale 2.0`.
