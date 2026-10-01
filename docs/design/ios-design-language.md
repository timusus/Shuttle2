# Shuttle Music iOS Design Language

The iOS counterpart of [design-language.md](design-language.md). It sets the foundations every iOS screen
draws from: the tokens in `ios/S2/Theme`, what each is for, and the rules that pick between them. Phase 1 of
#685 (this doc and the tokens); phase 2 builds the component catalogue (§3–5); phase 3 moves screens onto it.

## Sources

- Apple Human Interface Guidelines: Typography, Color, Materials, Layout, Buttons, Lists and tables,
  Accessibility (44 × 44 pt hit targets).
- iOS 26 Liquid Glass: `glassEffect(_:in:)`, `.glass` / `.glassProminent` button styles, the system's own
  glass tab bar, navigation bar and toolbars.
- The Android doc for intent (neutral chrome, colour from artwork, one type scale, one shape scale).

## 1. Principles

- **Native first.** A screen is built from SwiftUI's own controls (`List`, `TabView`, `NavigationStack`,
  `Menu`, `Button` styles, sheets) and looks like an iOS app before it looks like Shuttle. A custom control
  needs a reason the system one can't serve, written into its component row (§3).
- **Liquid Glass first, fallbacks behind `#available`.** iOS 26 is the design target. The deployment
  target stays iOS 17, so every iOS 26 API sits behind `if #available(iOS 26, *)` with an iOS 17–18
  fallback that keeps the same layout and hierarchy: a `Material` in place of glass, `.borderedProminent`
  in place of `.glassProminent`. The fallback is the same design at a lower fidelity, never a different one.
- **Parity of intent, not of look.** iOS and Android share the product decisions (neutral chrome, colour
  from the artwork, artists as circles, where dividers go, which actions a screen offers) and each
  expresses them in its platform's idiom. iOS does not copy Material shapes, motion or components.
- **Tokens, never literals.** Screens draw with a role from `ios/S2/Theme` (a font, colour, shape, size or
  spacing token). A raw point size, `Color.red` or corner radius in a feature file is a bug to fix.
- **Dynamic Type and dark mode are free.** Every font is relative to a text style; every colour is a system
  semantic colour or an asset with a dark variant, so Increase Contrast and the accessibility sizes work
  without per-screen code.

## 2. Foundations

### Type (`Typography.swift`)

SF Pro throughout; **SF Pro Expanded for display type only**; monospaced digits for every time and count.

| Token | Value | Use |
|---|---|---|
| `NavigationBarType.apply()` | large title, bold, Expanded | Every navigation bar's large title (applied once in `S2App`) |
| `s2LargeTitle` | `.largeTitle` bold Expanded | The first-run welcome |
| `s2HeroTitle` | `.title2` bold Expanded | A detail hero's title, the Now Playing song |
| `s2Title` | `.title2` bold | Empty states, onboarding steps |
| `s2Title3` | `.title3` semibold | A title's supporting line, a sign-in heading |
| `s2SectionTitle` | `.title3` bold | In-content section headers (`SectionHeader`) |
| `s2Headline` | `.headline` | A card's title |
| `s2GroupHeader` | `.caption` semibold | Disc headers, "Now Playing" over the queue |
| `s2Eyebrow` | `.caption` medium | A line above or below a title: "Continue", "artist · year" |
| `s2RowTitle` | `.body` | A list row's title (`MediaRow`, text rows) |
| `s2RowSubtitle` | `.subheadline` | A row's second line |
| `s2RowMeta` | `.subheadline`, mono digits | A row's trailing duration or count |
| `s2Caption` | `.footnote` | Hints and footers |
| `s2Button` | `.headline` | A button label where the style sets none |
| `s2Time` | `.caption`, mono digits | Scrubber times |
| `s2Glyph(size:weight:relativeTo:)` | scaled symbol | A glyph at an `IconSize` that follows Dynamic Type |
| `s2ScaledGlyph(_:weight:)` | fixed symbol | A glyph whose size is already a `@ScaledMetric` |

### Shape (`Shape.swift`)

One `S2Shape` (an `InsettableShape`), continuous corners everywhere. `artworkStyle(_:)` clips artwork and
draws its hairline; `artworkTile(_:shape:)` frames a square first.

| Token | Value | Use |
|---|---|---|
| `artworkRow` | 8 pt | Row artwork (48–56 pt), the mini player's cover |
| `artworkTile` | 16 pt | Shelf and grid tiles |
| `artworkHero` | 20 pt | A detail screen's hero |
| `artworkPlayer` | 20 pt | The Now Playing cover |
| `artist` | circle | **Every** artist picture: rows, tiles, shelves, heroes, skeletons |
| `card` | 16 pt | Cards, notices, the floating mini player |
| `control` | 10 pt | Text fields, icon containers, segmented choices |
| `capsule` | capsule | Prominent buttons, chips, the scrubber, the player's bottom bar |

`S2Shape.artwork(role, for: source)` returns `.artist` for an artist's `ArtworkSource`, so the circle rule
lives in one place and a row given an artist draws a circle without being told.

### Colour (`Colors.swift`)

Chrome is **neutral**; colour comes from artwork. Each role is a `UIColor` (for UIKit and tests) and a
`Color` (`.s2X`).

| Token | Value (light / dark) | Use |
|---|---|---|
| `s2Accent` | `AccentColor`: #1C1C1E / #F2F2F7 | The app tint: selection, tab bar, slider tracks, prominent fills |
| `s2OnAccent` | `systemBackground` | A label or glyph on an accent fill |
| `artworkTint` / `artworkTintInk` | from the cover | Overrides the accent in the player, mini player and detail heroes |
| `s2SurfaceContainer` | `secondarySystemBackground` | Cards and notices on the ground |
| `s2SurfaceFill` | `systemGray5` | Placeholders, skeletons |
| `s2SurfaceElevated` | `systemBackground` at the elevated level | A sheet's ground and its pinned headers (the queue) |
| `s2TextSecondary` | `SecondaryText`: #66666B / #9A9AA0 | Subtitles and captions (AA 4.5:1; `secondaryLabel` is 3.4:1) |
| `s2Success` / `s2Error` | system green / red | Status: connected, failed; `s2Success` is also every switch's on-track |

The system ignores a neutral asset as the global accent and falls back to system blue, so `S2App` applies
`.tint(.s2Accent)` at the root and `AccentTint.apply()` sets UIKit's window tint; draw with `.s2Accent`, never
`Color.accentColor`. Switches are the HIG green (`s2Success`), applied through `.s2Switch()` on each `Toggle` (a root `toggleStyle` or `UISwitch.appearance()` does not reach the iOS 26 switch): a near-white on-track under the white knob has no contrast in dark mode. Sliders keep the
accent (a near-white track against the grey remainder reads); steppers draw no tint. A prominent button on the plain accent sets `.foregroundStyle(.s2OnAccent)`; `.borderedProminent` otherwise
draws white, which vanishes on the near-white dark-mode accent. Tinted buttons use `capsuleButton`, which
takes `artworkTintInk`.

### Spacing (`Spacing.swift`)

| Token | Value | Use |
|---|---|---|
| `hairline` | 1 | Divider and hairline strokes |
| `tiny` | 2 | Icon insets |
| `xsmall` | 4 | A title and its subtitle, badge padding |
| `small` | 8 | Standard inner padding |
| `smallMedium` | 12 | Artwork to its text, a header to its content |
| `medium` | 16 | Screen margins, content padding |
| `large` | 24 | Between sections |
| `xlarge` | 32 | Above a major block |

### Dividers (`Separator.swift`)

| Token | Use |
|---|---|
| `rowSeparator(.none)` | Rows led by artwork: the artwork already separates them |
| `rowSeparator(.insetToTitle)` | Text-only rows: the system separator, starting at the title |
| `rowSeparator(.system)` | A row that insets its own title (`TrackRow`), where a list mixes it with artwork rows |

`.insetToTitle` goes on the title view itself, not on a `Label` (it doesn't reach inside one): a text-only row with a
leading glyph (Recent Searches) is an `HStack` of the glyph and a `Text`.

**Pinned section headers.** A `.plain` `List` pins its headers, and on iOS 26 a pinned header has no background, so
rows scroll visibly under it. Every pinned header takes `pinnedHeader()` (the screen's own surface behind it). A list
on an elevated surface, like the queue sheet, sets the surface on itself and passes it in. Headers stay pinned.

### Icons, touch targets and rows (`Spacing.swift`)

| Token | Value | Use |
|---|---|---|
| `IconSize.small` | 17 | A glyph in a list icon container (Settings' squares) or beside a row title |
| `IconSize.medium` | 20 | A toolbar or card glyph: a source card's icon |
| `IconSize.large` | 24 | A prominent glyph in a large container: a server's icon in Sources |
| `IconSize.hero` | 44 | An empty state's or a first-run step's symbol |
| `TouchTarget.minimum` | 44 | Every tappable control's hit area (`.touchTarget()`) |
| `TouchTarget.disc` | 36 | A visible disc inside a 44 pt target (top-bar buttons, the progress ring) |
| `ArtworkSize.*` | 48 … 420 | Row, shelf, grid, hero and player artwork |

`.touchTarget(visible)` frames a control at `max(visible, 44)` with a full content shape, so a small glyph
still takes a full target.

### Materials and glass (`Glass.swift`)

| Token | iOS 26 | iOS 17–18 | Use |
|---|---|---|---|
| `glassSurface(in:)` | `glassEffect(.regular, in:)` | `GlassFallback.chrome`: `.ultraThinMaterial`, no edge | The player's bottom bar |
| `glassSurface(in:fallback: .disc)` | `glassEffect(.regular, in:)` | `.regularMaterial`, no edge | The player's top-bar discs |
| `glassSurface(in:fallback: .card)` | `glassEffect(.regular, in:)` | `.thickMaterial` + hairline | The floating mini player |
| `capsuleButton(prominent:)` | `.glassProminent` / `.glass` | `.borderedProminent` / `.bordered` capsule | Hero and player actions |

The player's fallbacks reproduce its pre-#685 look (ultra-thin bar, regular discs, no hairline): the backdrop is a
dark, artwork-tinted ground, so the material is legible without an edge, and a hairline there only adds noise. The
mini player floats over a pale list, so it keeps the thick material and hairline.

System bars (tab bar, navigation bar, toolbars) get their glass from the system; never paint over them.
Glass is for chrome floating over content, never for content itself (rows, cards in a list).

### Motion (`Motion.swift`)

System motion by default: pushes, sheets, `matchedGeometryEffect` and the glass morphs come from SwiftUI.
Custom animation takes a `Motion` token, always through `.reduced(reduceMotion)`, which drops movement (or
swaps it for a fade) under Reduce Motion.

| Token | Value | Use |
|---|---|---|
| `press` | spring 0.2 s, damping 0.8 | A control's press feedback, with `pressedScale` 0.96 |
| `coverScale` | spring 0.45 s, damping 0.75 | The Now Playing cover shrinking on pause (`pausedCoverScale` 0.88) |
| `miniPlayerVisibility` | spring 0.35 s, damping 0.85 | The mini player appearing and leaving |
| `disclosure` | snappy 0.3 s | Expanding and collapsing a section |
| `tintChange` | ease-in-out 0.5 s | Crossfading an artwork tint |
| `backdropChange` | ease-in-out 0.8 s | Crossfading the player's backdrop |

## 3. The component list (phase 2)

Each gets an ID, a SwiftUI view in `ios/S2/Components` taking plain values (never a view model), and a
board. System controls are listed where S2 wraps or configures them.

- **Foundations**: `artwork` (tile, hairline, placeholder, artist circle), `cover-mosaic`, `skeleton`.
- **Actions**: `capsule-button` (prominent, secondary; glass and fallback), `icon-button` (44 pt target),
  `menu` (song, album, artist context menus), `shuffle-all`.
- **Navigation**: `tab-shell` (tab bar, sidebar-adaptable), `large-title` (Expanded), `letter-index`,
  `section-header`.
- **Content**: `row-song`, `row-album`, `row-artist`, `row-text`, `tile` (shelf and grid, artist variant),
  `detail-hero` (album, artist, playlist, genre), `jump-back-in`, `empty-state`, `resume-card`.
- **Overlays and feedback**: `player-notice`, `import-activity`, `alert`, `toast`.
- **Player**: `mini-player` (floating card, iOS 26 accessory), `now-playing` (cover, scrubber, transport,
  bottom bar), `queue-row`.
- **Sources and settings**: `source-card`, `sign-in-form` (status lines), `settings-row`, `equalizer`.

## 4. The catalogue (phase 2)

Mirrors §4 of the Android doc:

- **Boards**: one SwiftUI board per ID in `ios/S2/Catalog/` (debug only), rendering its states in a grid;
  the same board feeds the snapshot tests and an on-device screen.
- **Screenshots**: **swift-snapshot-testing** (Point-Free) in S2Tests, one PNG per {light, dark} ×
  {compact, regular} × {iOS 26, iOS 18} into `docs/design/catalog-ios/images/`, recorded on the leased
  simulator profiles, verified in `test.sh`.
- **Review pages**: `docs/design/catalog-ios/index.md` plus one page per ID with its boards inline, read on
  GitHub from a phone.
- **Motion**: a debug-only catalogue screen with toggles for dark, Dynamic Type size, Reduce Motion and
  Increase Contrast, and live demos of the glass morphs and the player transitions.

## 5. The approval gate (phase 2)

Mirrors §5 of the Android doc: `docs/design/catalog-ios/index.md` holds one task-list line per ID
(`- [ ] \`row-song\`: [boards](row-song.md) · approved: — · boards hash: —`). Approval is non-blocking;
the owner ticks a box, the commit records the date, commit and PNG hash, and a unit test re-opens a
ticked component whose PNGs change. Screens build only from catalogued components once phase 3 starts.

## 6. Decisions

| Date | By | Decision |
|---|---|---|
| 2026-10-01 | owner | **Neutral accent.** `AccentColor` is near-black / near-white, as Android's neutral chrome; the artwork tint still overrides it in the player, mini player and heroes. Was blue #006AD1 / #3D9DFF. |
| 2026-10-01 | owner | **SF Pro Expanded for display type only**: the large title, hero titles, the Now Playing song and the welcome. Everything else is SF Pro (the rounded design is gone). |
| 2026-10-01 | owner | **Artists are circles** at every size, reversing 9703ba568 (#624), which had given them albums' corners. |
| 2026-10-01 | owner | **Dividers**: none between rows led by artwork; on text-only rows, the system separator inset to the title. `rowSeparator` (Phase 1) is applied on Library, Playlists, detail and track rows; grouped Forms (Settings, Sources) keep the system style. |
