# S2 Design Language

Status: design, 2026-09-25. Nothing here is built yet. Companion to
[`app-shell.md`](../architecture/app-shell.md), which owns layout, navigation and the player state
model; this doc owns how things look, move and are approved.

S2's redesign is **Material 3 Expressive, applied as specified**. Shuttle Podcasts is a reference
for two pieces of plumbing only, its artwork colour extraction and its window-class helpers
(`podcasts/main/mobile/android/core/ui/.../theme/`). Where Podcasts and M3 disagree, M3 wins.

## Sources

Checked 2026-09-25. API facts come from the AARs' bytecode (public/internal, opt-in annotations);
guidance from these pages:

- developer.android.com: [Material Design 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3),
  [window size classes](https://developer.android.com/develop/ui/compose/layouts/adaptive/window-size-classes),
  [use window size classes](https://developer.android.com/develop/ui/compose/layouts/adaptive/use-window-size-classes),
  [canonical layouts](https://developer.android.com/develop/ui/compose/layouts/adaptive/canonical-layouts),
  [fold aware](https://developer.android.com/develop/ui/compose/layouts/adaptive/foldables/make-your-app-fold-aware),
  [material3 releases](https://developer.android.com/jetpack/androidx/releases/compose-material3).
- m3.material.io, the canonical spec, for the owner to read:
  [motion](https://m3.material.io/styles/motion/overview/how-it-works),
  [breakpoints](https://m3.material.io/foundations/layout/applying-layout),
  [panes](https://m3.material.io/foundations/layout/scaffold/panes). These pages render client-side;
  fetching them returned only the title, so nothing below rests on their text alone.
- [Roborazzi](https://github.com/takahirom/roborazzi) README and releases.

### Library baseline

| Need | Where | Status |
|---|---|---|
| `MaterialExpressiveTheme`, emphasized type (15 styles), flexible top bars, `WideNavigationRail`, `ShortNavigationBar`, `SearchBar` | material3 1.4.0 (BOM 2026.09.00) | public, no opt-in |
| `MotionScheme.expressive()`, `Shapes.largeIncreased` / `extraLargeIncreased` / `extraExtraLarge` | 1.4.0: **internal**; 1.5.0-alpha29: public | `MaterialExpressiveTheme` applies the expressive scheme itself in 1.4.0 |
| `ButtonGroup`, `ToggleButton`, `SplitButton`, `Horizontal/VerticalFloatingToolbar`, FAB menu, `LinearWavyProgressIndicator`, `VerticalSlider`, `SegmentedListItem` | **absent from 1.4.0**; 1.5.0-alpha29 | public, no opt-in |
| `MaterialShapes`, `toShape`/`toPath`, `LoadingIndicator`, `ContainedLoadingIndicator`, parts of `MenuKt` and `SliderDefaults` | 1.5.0-alpha29 | `@ExperimentalMaterial3ExpressiveApi` |
| Seeded schemes: `rememberDynamicColorScheme(seed, isDark, style, contrastLevel, specVersion)`, `Contrast.Default/Medium/High`, `SpecVersion.SPEC_2025` | MaterialKolor 5.0.1 | stable, not a dependency |

**Decided (2026-09-25): pin material3 1.5.0-alpha29** over the BOM in `:android:designsystem`
(and the app). The BOM's 1.4.0 lacks most Expressive components. The release freeze means nothing
ships on the alpha until the parity gate, and the pin moves to the first 1.5.0 beta/RC when it
appears. The opt-in for `MaterialShapes` and `LoadingIndicator` stays inside `:android:designsystem`;
screens never see it.

## 1. Principles

### Colour

- **Roles, never raw colours.** Every surface and text colour is a `ColorScheme` role. The pairing
  rule from the Compose M3 guide holds: `onX` on `X`, `onXContainer` on `XContainer`.
- **Root scheme = the user's choice.** S2's existing base theme (day/night, light, dark) and accent
  (Default, Orange, Cyan, Purple, Green, Amber) or Material You dynamic colour on Android 12+.
  Accent schemes are regenerated through MaterialKolor from each accent's seed with
  `SpecVersion.SPEC_2025` and `PaletteStyle.TonalSpot`, so brand and artwork schemes have the same
  role structure. A hand-tuned override is allowed per accent where generation distorts the brand
  hue (Podcasts found the coral pushed to brick red at tone 40); the catalogue shows both.
- **Artwork-seeded schemes are nested, not global** (app-shell §5): the player surface and artwork
  detail screens only. Seed = the MaterialKolor-scored swatches of a small bitmap (quantise, then
  rank by chroma and population), off the main thread, cached by artwork key: the first swatch at
  tone 20 or above, so a dark cover seeds from its colour rather than its near-black background,
  else the first. Style `TonalSpot` for detail screens, `Content` style for the player so the seed
  hue carries into the containers; `Content` builds containers at the seed's own tone, so the
  player lifts a darker seed to tone 30 (#409). The catalogue compares both before approval.
- **Brand fallback.** No artwork, extraction failure, or a seed too grey to carry a hue (chroma
  below a threshold picked on the catalogue's low-chroma seed) → the root accent scheme. Never a
  grey scheme and never the stock M3 purple.
- **Contrast.** `contrastLevel` follows the system setting (`UiModeManager.getContrast()`, API 34+)
  mapped to `Contrast.Default / Medium / High`, for root and artwork schemes alike. The theme board
  shows High for each seed.
- **Crossfades, not jumps.** Scheme changes animate per role with the effects spring
  (below) and keep the previous seed until the next one is ready: old → new, never old → default → new.

### Shape

- **Shape scale** from `Shapes` with the Expressive tokens (`largeIncreased`, `extraLargeIncreased`,
  `extraExtraLarge`). Components take their default shape; overrides go through `S2Theme.shapes`,
  not inline `RoundedCornerShape`s.
- **`MaterialShapes` (the 35 polygon shapes) are for non-content only**: artwork placeholders,
  icon containers (settings, empty states), the loading indicator, the favourite toggle. Album
  artwork is always a rounded rectangle and artist images a circle: art is square and a novelty mask
  crops it. The one exception is an option for playlist art (a user image or a mosaic, never an
  album cover): `ArtworkShape.Scalloped`, a `Cookie12Sided` mask. Text never sits in a novelty shape.
- **Shape morphing where M3 builds it in**: pressed and checked states of `ButtonGroup`,
  `ToggleButton` and `IconButton` shapes (round → square on press), play ⇄ pause, `LoadingIndicator`.
  Not in lists (per-row animation on scroll), not on scroll position, not on artwork.

### Motion

- **`MaterialExpressiveTheme` supplies the expressive `MotionScheme`**; components read
  `MaterialTheme.motionScheme`. No component hard-codes a `tween` or `spring`.
- **Spatial vs effects.** `MotionScheme` has six specs: default, fast and slow for each kind.
  *Spatial* springs move things (position, size, shape morph, sheet settle, pane resize) and may
  overshoot in the expressive scheme. *Effects* springs change colour and opacity and must not
  overshoot. Rule: if it has a position, width or corner radius, spatial; otherwise effects.
- **Speed by distance**: `fast*` for small controls (buttons, toggles, chips), `default*` for
  component-level changes (rows, menus, toolbar in/out), `slow*` for screen-scale moves (sheet
  levels, pane open, scheme crossfade).
- **Gesture-driven motion follows the finger 1:1** (sheet drag, predictive back, reorder) and only
  the settle uses a spring. Progress and seek position are linear with no spring.
- **Reduced motion**: with animator duration scale 0 everything snaps; the catalogue screen has a
  slow-motion toggle to review springs.

### Typography

- The M3 type scale plus its 15 **emphasized** styles, from `Typography` in 1.4.0. The default
  typeface stays until the type board is approved; a brand face is a separate decision.
- **One emphasized element per region**: the now-playing title (`headlineMediumEmphasized` at every size, over a `titleLarge` artist line,
  so a tall phone fills with type rather than gaps), detail screen titles, the flexible top bar title. Body
  text, rows and metadata are never emphasized.
- **Row hierarchy**: title `bodyLarge` on `onSurface`; secondary line `bodyMedium` on
  `onSurfaceVariant`; trailing meta (duration, count) `labelMedium` on `onSurfaceVariant`. Section
  headers `titleSmall` on `primary`.

### Emphasis and containment

- **Tonal layering, not shadows**: surface container roles (`surfaceContainerLowest` … `Highest`)
  separate regions; shadow elevation only for floating things (FAB, floating toolbar, menus).
- **One high-emphasis action per screen**: a filled primary button (Play on a detail screen) beside
  a tonal secondary (Shuffle) in a `ButtonGroup`; everything else tonal, outlined or text.
- **Lists are uncontained** on `surface`; settings group into `SegmentedListItem` containers;
  cards only for grid tiles and Home shelves.
- **Tonal icon containers mark top-level rows only**: a settings row that opens a section or stands
  for a source gets its icon in a tonal container; the rows under it (a folder, "Add folder") get the
  bare icon, so the containers keep the hierarchy readable.
- **Selection** uses `secondaryContainer` for rows and the checked state of the component; never a
  custom highlight colour.

### Adaptive

- Five width classes from `currentWindowAdaptiveInfoV2()` (Compact < 600, Medium 600–839, Expanded
  840–1199, Large 1200–1599, Extra-large ≥ 1600); the pane rules are in app-shell §2.
- Components adapt by **available width, not device**: they take constraints and a class passed
  down, never read the window themselves (so boards and previews can force any class).
- Text and controls never sit in a separating fold; a fold is a pane boundary.
- Touch targets 48 dp minimum at every class; lists cap line length with a max content width on
  Expanded+ single panes.

## 2. Standard components first

A component is built on the M3 component that exists for the job, with its default colours, shape,
motion and semantics; S2 passes content and parameters, not new visuals. A **composite** arranges M3
components (a song row is a `ListItem` with an `Artwork` leading slot). A **custom** component is
allowed only where M3 has nothing, and each one is listed here with its reason:

| Custom component | Why M3 has nothing |
|---|---|
| `PlayerSheet` | M3 bottom sheets have two states and no nav bar tracking; S2 needs Hidden/Mini/NowPlaying/Queue on one drag with the nav bar and mini player following (app-shell §1) |
| `AppShellLayout` | `NavigationSuiteScaffold` has no slot for a sheet between content and nav bar, nor a trailing player pane (app-shell §2) |
| `Artwork` | M3 has no image component; S2 needs a shaped, placeholder-aware, crossfading image with the placeholder drawn from `MaterialShapes` |
| `SeekBar` | M3 `Slider` supplies the thumb, semantics and drag; S2 only replaces its `track` slot with the wavy line that `LinearWavyProgressIndicator` draws, because a wavy *draggable* track does not exist |
| `ReorderableQueue` modifier | `LazyColumn` has no drag-to-reorder; rows stay `ListItem`s with a drag-handle trailing slot |
| `EqualizerCurve` | M3 has no charts; a frequency-response line over the band sliders |
| `FastScroller` | Compose M3 has no scrollbar or fast scroll; long library lists and the queue have one today |

Nothing else is custom. A new custom component needs a row here and the owner's approval before
it is built.

## 3. The component list

Every component lives in `:android:designsystem` and gets a board (§4). IDs are the catalogue keys.
"States" are what the board must show; every board also shows light, dark, brand and three seeds.

### Foundations

| ID | M3 basis | Variants | States |
|---|---|---|---|
| `theme-colour` | `ColorScheme` roles | root accents ×6, dynamic, 3 artwork seeds | Default and High contrast; every role swatch with its `on` pair |
| `theme-type` | `Typography` + emphasized | — | all 30 styles with sample text, at font scale 1.0 and 2.0 |
| `theme-shape` | `Shapes`, `MaterialShapes` | scale tokens; the shapes S2 uses | static, plus a morph strip (start, mid, end) |
| `theme-motion` | `MotionScheme` | 6 specs | curve plots; live in the catalogue screen only |

### Actions

| ID | M3 basis | Variants | States |
|---|---|---|---|
| `button` | `Button`, `FilledTonalButton`, `OutlinedButton`, `TextButton` | XS–XL sizes S2 uses, with and without icon | enabled, pressed, focused, disabled |
| `button-group` | `ButtonGroup` | standard, connected | each button pressed (the morph), checked (connected) |
| `icon-button` | `IconButton`, `FilledIconButton`, `FilledTonalIconButton`, `IconToggleButton` | sizes S2 uses | enabled, pressed, checked, disabled |
| `fab` | `FloatingActionButton`, `ExtendedFloatingActionButton` | Shuffle all on Library and Home only | collapsed, extended, pressed |
| `toolbar-selection` | `HorizontalFloatingToolbar` (compact), docked `FlexibleBottomAppBar` alternative | selection actions, overflow | 1 and many selected, overflow open; floating vs docked side by side; decided: floating (2026-09-25, #382) |

### Navigation and app bars

| ID | M3 basis | Variants | States |
|---|---|---|---|
| `top-bar` | `LargeFlexibleTopAppBar`, `TopAppBar` (detail), `MediumFlexibleTopAppBar` | title, title + subtitle, actions, back | expanded, collapsed, with overflow menu. No collapsing hero |
| `top-bar-contextual` | `TopAppBar` swapped in by `AnimatedContent` | count title, close, actions | 1 selected, many, all |
| `nav-bar` | `ShortNavigationBar` | 3 and 4 items | selected per item, badge, pressed |
| `nav-rail` | `WideNavigationRail` | collapsed, expanded | selected, expanding |
| `search` | `SearchBar` with `SearchBarState`, `ExpandedFullScreenSearchBar` (compact), `ExpandedDockedSearchBar` (expanded+) | — | collapsed, focused empty, typing, results, no results |

### Content

| ID | M3 basis | Variants | States |
|---|---|---|---|
| `artwork` | custom (§2) | square rounded, circle (artist), sizes 40/56/grid/hero | loaded, loading, placeholder per media type (song, album, artist, playlist, genre, folder) |
| `row-song` | `ListItem` + `Artwork` | with artwork, with track number (album), with drag handle (see `queue-row`) | default, playing, selected, disabled (missing file), downloading/offline badge |
| `row-album` | `ListItem` | list row | default, selected |
| `row-artist` | `ListItem` | circle artwork | default, selected |
| `row-playlist` | `ListItem` | user, smart playlist | default, selected, empty |
| `row-genre` | `ListItem` | with song count | default, selected |
| `row-folder` | `ListItem` | folder, file | default, selected |
| `grid-tile` | `Card` (outlined/filled) + `Artwork` | album, artist, playlist; Home shelf item | default, pressed, selected, placeholder |
| `section-header` | `ListItem` headline slot, `titleSmall` | with action (See all), sticky letter header | default |
| `chip-sort-filter` | `FilterChip`, `InputChip`, sort as `AssistChip` + `DropdownMenu`, read-only info as a `SuggestionChip` | sort field and order, filters (downloaded, source), file facts (format, bit rate, sample rate) | selected, unselected, disabled |
| `fast-scroller` | custom (§2) | alphabet, position | idle, dragging with letter bubble |

### Overlays and feedback

| ID | M3 basis | Variants | States |
|---|---|---|---|
| `menu` | `DropdownMenu` (Expressive menu groups) | overflow, sort | items with icon, divider, checked |
| `song-actions-sheet` | `ModalBottomSheet` + `ListItem`s | song, album, playlist targets | header with artwork, destructive item, long list scrolling |
| `dialog` | `AlertDialog`, `BasicAlertDialog` for forms | confirm, destructive, text input (create playlist), choice list | enabled, confirm disabled, error |
| `state-empty` | composite: `MaterialShapes` icon container + text + `Button` | per screen message | with and without action |
| `state-loading` | `LoadingIndicator`, `ContainedLoadingIndicator` | full screen, inline, pull to refresh | indeterminate; determinate (import progress) |
| `state-error` | composite like `state-empty` | retry, provider sign-in | — |
| `snackbar` | `Snackbar` in a `SnackbarHost` | message, with action (Undo) | above mini player, above nav bar |

### Player

| ID | M3 basis | Variants | States |
|---|---|---|---|
| `mini-player` | composite: `Artwork`, text, `IconButton`s, `LinearWavyProgressIndicator` | compact (over nav bar), docked (expanded, bottom of content) | playing, paused, buffering, casting |
| `player-controls` | `ButtonGroup` with `ToggleButton` play/pause, `IconButton` prev/next, shuffle and repeat toggles | compact, expanded, tabletop half | playing, paused (morph), buffering, shuffle on, repeat one/all |
| `seek-bar` | custom `SeekBar` on `Slider` (§2) | wavy while playing, flat when paused | idle, dragging with time label, buffering |
| `progress` | `LinearWavyProgressIndicator` | mini player, row downloading | playing (wave), paused (flat), indeterminate |
| `player-sheet` | custom `PlayerSheet` (§2) | levels Mini, NowPlaying, Queue | each level; mid-drag frames at e = 0.5 and q = 0.5 |
| `player-pane` | `AppShellLayout` trailing pane | Large+ | now playing, queue, collapsed |
| `queue-row` | `ListItem` + drag handle trailing + `ReorderableQueue` | — | current, upcoming, played, dragging, swipe to remove |

### Settings and equalizer

| ID | M3 basis | Variants | States |
|---|---|---|---|
| `setting-row` | `ListItem` / `SegmentedListItem` | link, switch (`Switch`), single choice (dialog), slider (`Slider`), info; tonal or plain icon | enabled, disabled, checked, with summary |
| `eq-band` | `VerticalSlider` | band with frequency and gain labels | 0 dB, boosted, cut, disabled (EQ off) |
| `eq-curve` | custom `EqualizerCurve` (§2) | over the bands | flat, preset, custom |

## 4. The catalogue

### Module

`:android:designsystem` (Android library, Compose), depended on by `:android:app` only:

- `theme/`: `S2Theme` (on `MaterialExpressiveTheme`), `ArtworkTheme`, accent seeds, type and
  shape scales, the contrast mapping.
- `component/`: one file per catalogue ID. Components take plain parameters and slots, never
  ViewModels, repositories or image loaders: `Artwork` takes an image slot, and `:android:app`
  supplies the Coil-backed one.
- `catalog/`: one `@Composable` *board* per ID, rendering the states from §3 in a grid. Boards are
  shared by the screenshot tests and the on-device screen, so both show the same thing.
- It replaces `ui/theme/` and `ui/common/components/` in `:android:app`; those are deleted when the
  module lands.

### Screenshots

- **Roborazzi 1.75.0** (2026-09-21), Gradle plugin `io.github.takahirom.roborazzi`, with
  `roborazzi-compose`. It needs Robolectric ≥ 4.10 with `@GraphicsMode(NATIVE)`; the repo is on
  Robolectric 4.17 at `sdk=34`, so no Robolectric bump.
- Why not the existing Paparazzi 2.0.0-alpha05 preview tests in `:android:app`: Paparazzi runs on
  layoutlib, a second rendering stack beside the Robolectric one the characterisation tests use,
  and is an alpha. Roborazzi runs on that same Robolectric stack, can drive interactions (press,
  focus, drag frames) through the Compose test rule, and records GIF/APNG (`recordRoboVideo`)
  for motion evidence. **Decided (2026-09-25):** when the catalogue lands, move the app's preview
  snapshot test to Roborazzi's Compose Preview Scanner support and delete Paparazzi, so the repo has
  one snapshot tool.
- **Matrix per component**: one PNG per {light, dark} × {compact, expanded}. Inside each PNG,
  columns are the four schemes (brand accent, warm seed, cool seed, low-chroma seed) and rows are
  the states. Compact is `w412dp-h915dp`, expanded `w1000dp-h720dp`; shell components (`nav-rail`,
  `player-pane`) add a large `w1280dp-h800dp` board. Seeds are three fixed bitmaps in test resources
  (warm orange-red, cool blue, grey-beige) so boards are reproducible.
- **Output**: `roborazzi { outputDir.set(rootProject.file("docs/design/catalog/images")) }`, files
  `<id>_<light|dark>_<compact|expanded|large>.png`, recorded at a reduced `resizeScale` so a board
  loads on a phone. `recordRoborazziDebug` writes them; CI runs `verifyRoborazziDebug`.
- **Review pages**: `docs/design/catalog/index.md` plus one `docs/design/catalog/<id>.md` per
  component with its boards inline and the states listed, so the owner reviews on GitHub from a
  phone by tapping through.

### On-device catalogue (motion)

Screenshots cannot show springs, morphs or drags. A **debug-only** `DesignCatalogRoute` (code in
`:android:designsystem` `src/debug` plus a debug entry-provider registration in `:android:app`
`src/debug`, so release builds carry none of it) lists every board live, with:

- toggles for dark, contrast level, font scale, the scheme column (brand or seed, or a seed picked
  from the device's library artwork) and a slow-motion factor for springs;
- interactive demos: press-and-hold on every morphing control, the sheet dragged through its levels,
  the queue reorder, scheme crossfade on a seed change, loading indicator, wavy progress.

It opens from the debug settings screen and by `adb shell am start` with an extra, so a Maestro flow
can open a board for a recording.

## 5. The approval gate (asynchronous, decided 2026-09-25)

`docs/design/catalog/index.md` is the record. One task-list line per component (GitHub renders
task lists as checkboxes; tables do not):

```
- [ ] `row-song`: [boards](row-song.md) · approved: — · boards hash: —
- [x] `button`: [boards](button.md) · approved: 2026-10-02 @ a1b2c3d · boards hash: 9f3e…
```

- **Approval is non-blocking.** Screens may use a catalogue component before its box is ticked;
  building the screen and approving the component are independent. The owner reviews
  `docs/design/catalog/index.md` on their own time, not as a gate any build-order step waits on
  (app-shell §6 build order updated to match). Screens still build their UI only from
  `:android:designsystem` components; a check in `support/scripts/lint` flags
  `androidx.compose.material3` component imports in `ui/screens/**` (theme and token access
  allowed), so a screen cannot slip in an uncatalogued control — catalogued but not yet approved
  is fine, raw Material3 is not.
- **Approve**: the owner ticks the box (on GitHub or by telling a session), and the commit records
  the date, the commit hash and a hash of that component's PNGs.
- **A change to a component flows to every screen that uses it.** Because screens only ever import
  the catalogued component (never a copy), editing `component/<id>.kt` updates every screen using
  it in the same commit; there is nothing to propagate by hand.
- **Changing an approved component re-opens it.** A unit test in `:android:designsystem` compares
  each ticked component's current PNG hash with the recorded one; any mismatch fails the build until
  the box is unticked (hash cleared) in the same commit that re-records the images. The owner then
  reviews the new boards and re-approves — screens already using the component are unaffected while
  its box is unticked; only the recorded-approval date lapses.
- **New components** go through the same line, starting unticked; a custom one also needs its row
  in §2 first.
