# Adaptive layout — form factors, size classes, foldables

The shell owns adaptivity (Android `ui/shell/adaptive/ShellLayout.kt`, iOS `LayoutTier.swift`); screens
receive a tier/size class as state and choose a layout. Screens never read window metrics or device type
themselves, and never branch on "is tablet".

## Breakpoints

**Android window size classes** (width; height in brackets)
| Class | Width dp | Notes |
|---|---|---|
| Compact | < 600 | phones portrait; [height Compact < 480 = phone landscape] |
| Medium | 600–840 | small tablets portrait, unfolded book foldables, large phones landscape |
| Expanded | 840–1200 | tablets landscape, unfolded foldables landscape |
| Large | 1200–1600 | large tablets, desktop windows (`supportLargeAndXLargeWidth = true`) |
| Extra-large | ≥ 1600 | desktop/external display |

**iOS size classes**: horizontal × vertical, each compact or regular. iPad windows are freely resizable
(iPadOS 26) and Stage Manager/Slide Over can make an iPad horizontally compact — design for the class,
never the device.

## Tier mapping (design once per row)

| Tier | Android | iOS (h / v) | Shell | Music layout |
|---|---|---|---|---|
| Phone portrait | Compact | compact / regular | Nav bar + mini player | Single pane; full-screen player sheet |
| Phone landscape | Compact/Medium, height Compact | compact / compact (Pro Max: regular / compact) | Rail / hidden bars | Player: art left, controls right; lists single pane |
| Narrow window / split | Compact–Medium | compact / regular | Bar | Single pane |
| Tablet portrait, unfolded book | Medium | regular / regular | Rail / sidebar | List-detail; queue as sheet |
| Tablet landscape, desktop | Expanded / Large / XL | regular / regular | Expanded rail / sidebar | List-detail + docked player pane with queue (supporting pane) |
| Tabletop posture | `isTabletop` (any class) | n/a | — | Art + metadata above fold, transport + scrubber + queue below |

## Canonical layouts and where Shuttle uses them

- **List-detail** (`ListDetailPaneScaffold` / `NavigationSplitView`): Library → album/artist/playlist/genre
  detail. Two panes from Expanded (or wherever a separating fold divides the window); one pane below.
  Back steps through panes on compact (`BackHandler`).
- **Supporting pane** (`SupportingPaneScaffold`): Now Playing + queue/lyrics. ~2/3 + 1/3 on Expanded+;
  supporting content becomes a sheet/tab below that.
- **Feed** (`LazyVerticalGrid(GridCells.Adaptive(min))` / `LazyVGrid(.adaptive)`): Home, album grids.
  Grid minimum widths are tokens; never a fixed column count.
- **Navigation suite**: bar < Medium; rail Medium–Expanded; expanded rail/permanent drawer at
  Extra-large (or sidebar on iPad).

## Rules

- **Content width caps:** reading text ≤ ~600dp/pt; settings and forms centred in a ≤ 840 column or
  laid out as list-detail; no row wider than useful (detail panes, not edge-to-edge rows).
- **Artwork scales by tier,** not linearly with width: cap the hero art (e.g. Now Playing art ≤ min(height
  share, ~520dp/pt)) and spend extra space on queue/lyrics/metadata.
- **Continuity:** resizing, folding/unfolding, rotation and multi-window keep scroll position, selection,
  open sheet and playback UI state (state hoisted above the tier switch; `rememberSaveable`/scene storage).
- **Input:** Expanded+ assumes keyboard/mouse/trackpad may exist — hover states, right-click =
  context menu/long-press, keyboard shortcuts for space (play/pause), arrows (seek/skip), ⌘F/Ctrl+F (search).
- **Foldables:** collect `FoldingFeature` (state FLAT/HALF_OPENED, orientation, `isSeparating`,
  `occlusionType`). Never put controls or text across a separating/occluding hinge; split panes at the
  hinge. Tabletop = HALF_OPENED + horizontal (media's canonical posture — Shuttle should support it).
  Book = HALF_OPENED + vertical → list-detail at the hinge.
- **Landscape phone:** the player must not scroll; nav bar becomes a rail or hides.
- **Car/desk/TV-like large screens** are template-driven (Auto/CarPlay) — see native-surfaces.md.

## Quality bar (Android large-screen tiers)

- Tier 3 Adaptive-ready: full screen in all sizes, no letterboxing, state kept across configuration
  changes, basic keyboard/mouse.
- Tier 2 Optimized: a deliberate layout for every size class (above table) — Shuttle's target.
- Tier 1 Differentiated: multi-window, drag and drop (e.g. drag a song into a playlist pane), postures
  (tabletop), stylus where relevant.

## Verification matrix

Android previews/goldens: phone 411×891, phone landscape, foldable unfolded 841×701 (book posture
emulated), tablet 1280×800 and 800×1280, desktop 1920×1080. iOS: iPhone SE-class compact, iPhone Pro Max
(portrait + landscape), iPad 1/3 split (compact), iPad full landscape. Tabletop: emulator posture
controls (Pixel Fold AVD) or a `FoldingFeature` test override.
