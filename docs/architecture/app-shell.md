# Compose App Shell

Status: design 2026-09-25; built. `MainActivity` has hosted the shell since 2026-09-26 (#381), and
the legacy UI is deleted. Section 6 records the plan as it was made.

One Compose `MainActivity` with a Navigation 3 `NavDisplay` inside a shell layout replaces
`MainActivity`'s XML, `MainFragment`/`MainPresenter`, `MultiSheetView`, `CustomBottomSheetBehavior`,
`res/navigation/main.xml` and `launch.xml`, `bottom_nav_menu.xml` and `BottomDrawerSettingsFragment`.
The app moves to Compose in a single cutover: every screen is rebuilt as a Compose destination,
and every Fragment, presenter, XML layout and nav graph is deleted. The shell never hosts a
Fragment; there are no bridges between old and new. The shell owns navigation chrome and the player
surface; destinations own everything else, including their top bars.

## Release freeze

- Nothing intermediate ships. Releases are frozen until the whole app is Compose and the parity
  gate (section 6, step 8) passes.
- No transition period: MVP screens are not kept working beside Compose ones, and nothing is hosted.
- Work lands on `main`, untagged. An urgent fix during the freeze is branched from the last release
  tag, tagged from that branch, and cherry-picked to `main` if it still applies.
- The redesign (Material 3 Expressive, artwork theming) happens in the same pass, not after it.
- Onboarding and library sources are redesigned in
  [`redesign-inventory.md`](redesign-inventory.md), which also carries the parity checklist. This
  doc treats them as ordinary destinations.

## API baseline

Checked against the Gradle cache and Google Maven (`maven-metadata.xml` and the AARs' bytecode for
opt-in annotations), 2026-09-25.

| API | Artifact / version | Status |
|---|---|---|
| `NavDisplay`, `SceneStrategy`, `SinglePaneSceneStrategy`, `DialogSceneStrategy` | `androidx.navigation3:navigation3-ui` 1.2.0 | stable, no opt-in; not cached, not a dependency |
| `NavKey`, `NavBackStack`, `rememberNavBackStack`, `entryProvider`, `rememberSaveableStateHolderNavEntryDecorator` | `androidx.navigation3:navigation3-runtime` 1.2.0 | stable, no opt-in; not a dependency |
| `rememberViewModelStoreNavEntryDecorator` | `androidx.lifecycle:lifecycle-viewmodel-navigation3` 2.11.0 | stable; matches the catalog's lifecycle 2.11.0 |
| `ListDetailSceneStrategy`, `SupportingPaneSceneStrategy` | `androidx.compose.material3.adaptive:adaptive-navigation3` 1.3.0 | stable artifact, but the classes are `@ExperimentalMaterial3AdaptiveApi` |
| `currentWindowAdaptiveInfoV2()`, `Posture`, `calculatePaneScaffoldDirective` | material3-adaptive 1.3.0 | stable; cached, not a dependency. V2 = `currentWindowAdaptiveInfo(supportLargeAndXLargeWidth = true)`, which is deprecated in favour of it; test with `WindowSizeClass.WIDTH_DP_LARGE_LOWER_BOUND` / `WIDTH_DP_EXTRA_LARGE_LOWER_BOUND` (window-core 1.5.0) |
| `hiltViewModel()` with assisted `creationCallback` | `androidx.hilt:hilt-lifecycle-viewmodel-compose` 1.4.0 | stable; cached, not a direct dependency |
| `AnchoredDraggableState`, `anchoredDraggable`, `DraggableAnchors` | foundation 1.12.1 (BOM 2026.09.00) | stable |
| `PredictiveBackHandler`, `BackHandler` | activity-compose 1.13.0 | stable |
| `MaterialExpressiveTheme`, `MotionScheme`, `LargeFlexibleTopAppBar`, `WideNavigationRail`, `ShortNavigationBar` | material3 1.4.0 (BOM) | public, no opt-in; `MotionScheme.expressive()` is internal in 1.4.0 |
| `ButtonGroup`, floating toolbars, `MaterialShapes`, `LoadingIndicator`, wavy progress | material3 1.5.0-alpha29 only | see [`design-language.md`](../design/design-language.md) for the pin and opt-ins |
| `TopAppBarDefaults.exitUntilCollapsedScrollBehavior` | material3 1.4.0 | `@ExperimentalMaterial3Api` |
| `@Serializable` routes | Kotlin serialization plugin + `kotlinx-serialization-core` | **not in the catalog**; `NavBackStack` saving needs it |
| MaterialKolor (`material-kolor`) | 5.0.1 in Podcasts | **not a dependency** |

The shell needs no pre-release (navigation3 1.3.0-alpha01, adaptive 1.4.0-alpha02); the design
system pins material3 1.5.0-alpha29 (owner decision in `design-language.md`).

## 1. Player state model

History: the sheet had a Queue level that pushed a full-height Now Playing up over Up Next; #410
weighed four shapes and built (D), the Shuttle Podcasts panel model described here.

### Levels

`enum class PlayerLevel { Hidden, Mini, NowPlaying, Expanded }`. One shell-owned
`AnchoredDraggableState<PlayerLevel>` (`PlayerSheetState`) drives the sheet below 1200 dp
(section 2). Its offset is the sheet's top edge in shell coordinates.

```
 offset (y of sheet top)       H = shell height, N = nav bar incl. inset,
                               M = mini player height, P = rest offset
 Hidden      = H               sheet fully below the window, nav bar at rest
 Mini        = H - N - M       mini player sits on the nav bar
 NowPlaying  = P               the rest: as tall as handle, artwork, title, transport and bar need
 Expanded    = 0               full height; only where P > 0 (a partial rest)

 ┌────────────────┐  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐
 │                │  │  destination   │  │  destination   │  │ pinned song  ▶ │
 │  destination   │  │                │  ├──── handle ────┤  ├────────────────┤
 │                │  │                │  │  artwork       │  │ open panel     │
 │                │  ├────────────────┤  │  title       ♡ │  │ (queue rows,   │
 │                │  │ mini player    │  │  transport     │  │  sleep timer,  │
 ├────────────────┤  ├────────────────┤  ├────────────────┤  │  sound)        │
 │ nav bar        │  │ nav bar        │  │ bar (pinned)   │  │ bar (pinned)   │
 └────────────────┘  └────────────────┘  └────────────────┘  └────────────────┘
      Hidden               Mini              NowPlaying            Expanded
```

`nowPlayingRest` computes P from the window alone: the artwork takes the full width (up to
`MaxArtworkSize`) and the sheet rises only as far as the chrome and artwork need, leaving the
library above it. Where that would leave under 96 dp of library or under 240 dp of artwork (short
phones, folded foldables) the sheet rests at full height (P = 0), there is no Expanded level, and
the list below the transport shows more queue rows at rest.

The sheet reads its offset only in layout and `graphicsLayer` lambdas, so a drag re-runs layout and
draw, never composition. `PlayerSheetGeometry` is the pure maths (anchors, the reveal and expand
fractions, nav bar and mini fades, the scrim, the corner radius that runs from 28 dp at rest to 0 at
the status bar) with unit tests.

### One list, a pinned bar

Now Playing is one `LazyColumn` (`NowPlayingList`): handle, artwork, title (with the favourite),
transport, then the open panel's items. The queue's rows are items of the same list, reordered by
their handles with `rememberQueueListState(firstRowIndex = NowPlayingItems.FirstQueueRow)`. A filler
after the panel lets any panel's head scroll to the top. The artwork scrolls off the top; it never
shrinks.

`NowPlayingBar` is pinned to the window's bottom edge wherever the sheet is, and the list scrolls
between the status bar and it. It holds Playback & sound (the speed shows when it is not 1×), Sleep
timer (the time left while a timer runs), Queue, Cast where it can start, and the overflow with the
song actions and Clear queue.

**Pinned row: built.** Once the transport scrolls under the list's top, `PinnedSong` (artwork,
title, artist, play/pause) pins over the list; tapping it scrolls back to the title. Checked on the
emulator: without it a long queue or the sleep panel leaves no way to see or pause what is playing
short of scrolling back, and the queue opening at the next song (below) relies on it to show the
current one.

### Panels

The open panel is `PlayerUiState.panel` (`NowPlayingPanel.Queue | SleepTimer | PlaybackSound`, or
null for none), held by `PlayerViewModel` in its `SavedStateHandle`, so it survives rotation,
resizing and process death beside the saved level. With none open the list shows the queue.

`NowPlayingPanels` opens and closes them. A bar button toggles its panel: opening raises the sheet
to Expanded where there is one and scrolls the panel under the pinned row (the queue to the song
after the current one, the others to the title so the transport heads them); closing scrolls back to
the top and lowers the sheet to its rest. Sleep timer and Playback & sound are ported from Shuttle
Podcasts and restyled to S2 (speed persistence is #408).

### Nested scroll

`PlayerSheetNestedScrollConnection`, M3's bottom sheet pattern:

- `onPreScroll`, finger moving up: the sheet takes the delta until it reaches Expanded, then the
  list scrolls.
- `onPostScroll`, finger moving down: the list scrolls back to its top first, then the leftover
  lowers the sheet to its rest and on to Mini.
- `onPreFling` up while below Expanded, and `onPostFling`: fling the sheet to an anchor.

Settling at Expanded with nothing open opens the queue, which is how a drag up opens it; settling at
a partial rest or Mini closes the panel and scrolls back to the top (`PanelSettleEffect`). Queue
reorder drags start on a row's handle, so they never reach the connection.

### Predictive back

Open panel → rest → Mini, then the handler disables and `NavDisplay` pops. On a partial-rest sheet
Expanded → NowPlaying is the panel step, lerped by `PredictiveBackHandler` (`anchoredDrag { dragTo(lerp(...)) }`)
so the nav bar, mini fade and scrim follow the finger; commit animates to the lower anchor, cancel
animates back. Where there is no Expanded level (full-height rest, Medium and Expanded widths) a
plain `BackHandler` closes the panel first. Back never reaches Hidden. The handler is keyed on the
settled level, so it re-registers, and outranks destination handlers, whenever the sheet rests
above Mini.

### State restoration

The level is `rememberSaveable(saver = AnchoredDraggableState.Saver())` and the panel is in the
view model's `SavedStateHandle`, so both survive rotation, fold/unfold and process death. Anchors
are recomputed from the new size with `updateAnchors(anchors, newTarget = state.targetValue)`. The
back stacks are `rememberNavBackStack`, saved through the `@Serializable` route keys.

### Hidden when nothing is queued

`ShellViewModel` exposes `hasQueue` from `QueueOperations.queueStateFlow`. Empty queue: anchors
`{Hidden}` only, padding drops to `N`, and no panel shows. Non-empty: `{Mini, NowPlaying}` plus
Expanded on a partial rest, animating to Mini. Hidden is not user-reachable (decision 3). On cold
start the saved level stands until the first queue emission, so a restoring queue never flashes the
mini player.

### Dark mode

The sheet is `ArtworkTheme(Player)` `surfaceContainer`, one tonal step up (`surfaceContainerHigh`)
in dark mode, where the container barely parts from the library behind the sheet's edge
(`PlayerSheetColor`).

## 2. Adaptive layout

The five M3 width classes come from `currentWindowAdaptiveInfoV2()` (material3-adaptive 1.3.0):
Compact < 600, Medium 600–839, Expanded 840–1199, Large 1200–1599, Extra-large ≥ 1600. Podcasts'
`FoldPosture` is ported into `ui/shell/adaptive/`; its `LayoutTier` is not (it stops at 840). No
`isTablet` logic. Checked 2026-09-25 against the
[window size classes](https://developer.android.com/develop/ui/compose/layouts/adaptive/window-size-classes)
and [canonical layouts](https://developer.android.com/develop/ui/compose/layouts/adaptive/canonical-layouts)
guides (list-detail shows both panes from Expanded; a supporting pane sits alongside from Medium, in
a sheet on Compact; no two panes at compact height) and 1.3.0's `calculatePaneScaffoldDirective`
(1 pane at Compact and Medium, 2 at Expanded, 3 from Large; pane width 360 dp, 412 dp at XL):

| Width | Navigation | Library | Player |
|---|---|---|---|
| Compact | `ShortNavigationBar` | one pane | sheet: Mini → NowPlaying → Expanded |
| Medium | `WideNavigationRail`, collapsed | one pane | sheet; expanded, player and queue side by side |
| Expanded | `WideNavigationRail`, collapsed | list-detail, two panes at most | mini player docked at the bottom of the content area; expanded, the player takes the full window as two panes, artwork and controls beside the queue. No persistent player pane |
| Large, Extra-large | `WideNavigationRail`, collapsed at Large, expanded at XL | list-detail | persistent now-playing/queue supporting pane on the trailing side, 360 dp (412 dp at XL), collapsible |

The rail stays collapsed at Large because 1200 − 96 (rail) − 360 (pane) leaves 744 dp, two 360 dp
panes. Compact height (< 480 dp) keeps the library to one pane at any width. No
`NavigationSuiteScaffold`: it has no slot for a sheet between content and a drag-tracking nav bar,
nor for a trailing pane; the shell lays out rail, `NavDisplay` and player itself, like Podcasts'
`CastNavigationSuiteScaffold`. A nav tap with the player above Mini settles it to Mini, then navigates.

### The player by class

**Below 1200 dp, one sheet** (section 1). A compact sheet's anchors are `{Mini, NowPlaying,
Expanded}`, or `{Mini, NowPlaying}` where it rests at full height. On Medium and Expanded,
Now Playing shows the player and its bar beside the open panel (the queue when none is), so the
anchors are `{Mini, NowPlaying}` and a saved Expanded level opens NowPlaying with the panel kept.
On Medium the sheet covers the content pane and the rail stays live; on Expanded the mini player
docks across the content area and the expanded player covers the whole window, rail included.

**From 1200 dp, a pane**, to keep browsing context. `PlayerLevel` still holds the state; the pane
renders Mini = collapsed (the docked mini player) and NowPlaying = the pane open on the same
`NowPlayingList` and bar as the compact sheet, with no drag gestures. It sits beside `NavDisplay`,
not in it as a `SupportingPaneSceneStrategy` scene: scenes are built from back stack entries, and
the player is not a route (section 4).

### List-detail

`ListDetailSceneStrategy` (adaptive-navigation3) shows a list entry and its detail side by side
when there is room, else single pane, from the one back stack. Library tabs carry `listPane()`
metadata, detail routes `detailPane()`; the opt-in stays in the shell. The strategy gets the
shell's directive: `calculatePaneScaffoldDirective(adaptiveInfo)` with `maxHorizontalPartitions`
capped at 2, and 1 below Expanded or at compact height; the player pane is the third partition,
outside `NavDisplay`. This resolves decision 5: at 840–1199 dp there is no persistent pane, so
list-detail has the whole content area; from 1200 dp the pane takes its fixed width.

### Folds and postures

A separating fold (`isSeparating`, from `Posture.hingeList`) is the pane boundary at any width;
nothing interactive sits in the hinge band. An unfolded book-style foldable is Medium in portrait
and Expanded in landscape, so it gets the sheet, never the pane.

- **Flat or book** (vertical fold): list and detail split at the fold (the directive excludes the
  hinge); the expanded player puts artwork and controls on one leaf, the queue on the other.
- **Tabletop** (horizontal fold): the compact sheet rests at full height with the artwork and
  title above the fold and the transport below it; the artwork's list item stretches to the fold,
  and the list still scrolls to the panel. The level never changes.

### Size or posture changes mid-drag or with the player open

`updateAnchors(newAnchors, newTarget = state.targetValue)` on a size change ends any drag in
flight and snaps to the level it was heading for. Crossing classes maps the level:

| From → to | Rule |
|---|---|
| sheet → pane (resize to ≥ 1200 dp) | any level above Hidden → pane open; the open panel stays |
| pane → sheet (fold, resize below 1200 dp) | always Mini: folding never throws a full-window player over what the user was browsing |
| Compact → Medium or Expanded | Expanded → NowPlaying with the panel kept; other levels unchanged |
| any posture change | level unchanged; only the now-playing layout changes |

The saveable state sits above the class branch, so one instance survives the switch.

## 3. Top bars

The shell draws no top bar. Each destination owns a `Scaffold(topBar = …)` and the status-bar
inset through the bar's default `windowInsets`.

- **Top-level and list screens** (Home, Library, Search, Playlists, Settings): `LargeFlexibleTopAppBar`
  with title and subtitle (for example the library count), `exitUntilCollapsedScrollBehavior`
  connected to the screen's list. This is a collapsing *bar*, not a hero.
- **Artwork detail** (album, artist, genre, playlist): the existing `DetailScaffold`: pinned small
  bar, artwork as a list item. No collapsing hero; the NestedScrollConnection and graphics-layer
  attempts failed and are not retried.
- **Multi-select**: the destination swaps its bar to a contextual bar with `AnimatedContent` keyed on
  `selectedCount > 0`. Selection is screen state in the UiState, not shell state.

### The risk: Library

Library's tabs share one bar and one contextual bar (the #344 ownership bug), each tab brings its
own options menu, and the collapsing bar must follow whichever page is scrolling. Design: one
`LargeFlexibleTopAppBar` over a `HorizontalPager`, per-page `nestedScroll(…)`, the contextual bar and
actions read from the visible page's UiState. `ToolbarHost` and `ComposeContextualToolbarHelper` are
deleted, not ported. Step 5A proves the bar follows the page and the selection counts per page.

## 4. Navigation

Navigation 3, not Navigation Compose. Navigation Compose was only chosen to host Fragments
(`AndroidFragment` destinations, a `findNavController()` shim: both rejected now). Navigation 3's
back stack is an app-owned, snapshot-backed list of keys, so per-tab stacks and the start-tab rule
are plain list code with plain tests; scenes give list-detail from the same stack; runtime, UI and
ViewModel integration are all stable.

### Routes and entries

`@Serializable` route keys implementing `NavKey`, in `ui/shell/Routes.kt`. Routes carry keys, never
Parcelable models:

```kotlin
@Serializable data object HomeRoute : NavKey   // likewise LibraryRoute, SearchRoute
@Serializable data class AlbumRoute(val albumKey: String?, val albumArtistKey: String?) : NavKey  // AlbumGroupKey
@Serializable data class AlbumArtistRoute(val albumArtistKey: String?) : NavKey
@Serializable data class PlaylistRoute(val id: Long) : NavKey   // GenreRoute(name: String) likewise
@Serializable data class SmartPlaylistRoute(val id: SmartPlaylistId) : NavKey  // enum of the built-ins
@Serializable data object SettingsRoute : NavKey   // + one per settings screen, EqualizerRoute
// onboarding and library-source routes: see redesign-inventory.md
```

`NavDisplay(backStack, entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(),
rememberViewModelStoreNavEntryDecorator()), sceneStrategy = listDetail then single pane,
entryProvider = entryProvider { entry<AlbumRoute> { key -> AlbumDetailRoute(key, navigator) } … })`.
Each entry gets its own `ViewModelStore`, cleared when the entry leaves the stack.

ViewModels receive their key by Hilt assisted injection:
`hiltViewModel<AlbumDetailViewModel, AlbumDetailViewModel.Factory> { it.create(key) }`, then load
from the repository. No `SavedStateHandle.toRoute()`, no Safe Args. `AlbumGroupKey` is two nullable
strings and the route mirrors it; an album with no group key cannot open detail today either.

### Back stacks and the navigator

`AppNavigator` (in `ui/shell/`) owns one `NavBackStack` per top-level tab, each from
`rememberNavBackStack(tabRoot)`, and the selected tab. `NavDisplay` shows the start tab's stack
followed by the selected tab's, so back at the root of a non-start tab returns to the start tab, as
`NavigationUI` does today; re-selecting a tab restores its stack; re-selecting the current tab pops
it to its root. Its API is `open(route)`, `selectTab(tab)`, `back()`. Screens never see it: route
composables take lambdas (`onOpenAlbum`) that the entry provider wires to it. The rules are unit
tests on the navigator, with no Compose.

The start tab (Home or Library by `showHomeOnLaunch`) comes from `ShellViewModel`, read once per
activity, so a change in Settings applies from the next launch.

The settings drawer (`BottomDrawerSettingsFragment`, a `<dialog>` destination today) becomes a
shell-owned `ModalBottomSheet` (Shuffle all, Sleep timer, Equalizer, Settings); on rail widths the
same entries are the rail's secondary items. Screen dialogs (tag editor, song info, create playlist,
delete confirmations) are Compose dialogs owned by the screen that raises them; `DialogSceneStrategy`
is only for a dialog that must survive as its own back stack entry, and none does yet.

**The player is not a route.** `PlayerLevel` is shell state: it overlays every route and must not
pop with the back stack. Anything that wants to open the player sends an intent extra the shell maps
to a level.

### Activity, deep links and shortcuts

`MainActivity` becomes a `FragmentActivity`-based Compose activity with no Fragment destinations:
mediarouter's Cast button shows its chooser as a `DialogFragment` and needs a `FragmentActivity`.
No URI deep links exist and none are added; navigation3 1.2's `DeepLinkMatcher` is the mechanism
later. `MainActivity` keeps its intents as is (`MEDIA_PLAY_FROM_SEARCH` starts the service, `VIEW`
plays through the media session, `MUSIC_PLAYER`/`APP_MUSIC` launch). `ShortcutHandlerActivity` and
the toggle-playback shortcut only start the service, so they are unchanged.

Android Auto, Cast and the widgets are untouched: they talk to `:android:playback`, never the UI.

### Edge-to-edge (#242)

The shell closes #242 at the root: `enableEdgeToEdge()`, and `activity_main.xml` with its
`fitsSystemWindows` is deleted. With no View screens left there is no per-fragment inset work.
Insets are split by owner:

| Inset | Owner |
|---|---|
| status bar | the destination's top bar |
| navigation bar | the nav bar or rail; with the nav bar slid away, the sheet's own bottom padding |
| IME | destination content (`imePadding()`) |
| display cutout, rail side | the rail and the pane |

## 5. Theming

`AppTheme` (`ui/theme/Theme.kt`) becomes `S2Theme` in `:android:designsystem`: a
`MaterialExpressiveTheme` (which applies the expressive motion scheme itself), the user's base theme
and accent, and the type and shape scales of `design-language.md`. It wraps the whole shell. The artwork scheme is a second, nested `MaterialTheme` around
specific subtrees, never a swap of the root scheme:

```
AppTheme(theme, accent)                       ← user's accent, everywhere
 └─ AppShell
     ├─ NavDisplay
     │   └─ AlbumRoute → ArtworkTheme(seed = album art)   ← detail screens, own seed
     └─ PlayerSurface
         └─ ArtworkTheme(seed = now-playing art)          ← mini, now playing, queue, pane
```

`ArtworkTheme(seed)` is specified in `design-language.md` §1 (MaterialKolor seed from a small Coil
bitmap off the main thread, cached by artwork key; crossfade that keeps the last seed until the
next is ready). The now-playing seed comes from `ShellViewModel` (current song → artwork key →
seed); detail screens extract in their ViewModel.

**Recommendation (decision 1): player surface and artwork detail screens only**, with a setting
"Colour from artwork", default on. Not the whole app: every track change would recolour the library
the user is browsing and override the accent they chose, and animating the root scheme recomposes
every colour reader.

## 6. Build order

One cutover in eight steps. Each is verifiable on its own and lands on `main`; none is released.
From step 2, `main` runs the new shell and an unbuilt destination is a `NotBuiltYet(route)`
placeholder entry. Each step deletes the legacy code it replaces (Fragments, presenters, contracts,
layouts, menus) in the same change and moves its Maestro flows to test tags (`testTagsAsResourceId`).

1. **Build deps, design system and component catalogue.** Catalog: navigation3 runtime and ui,
   lifecycle-viewmodel-navigation3, material3-adaptive (adaptive, layout, navigation3),
   hilt-lifecycle-viewmodel-compose, the serialization plugin and core, MaterialKolor, material3
   1.5.0-alpha29, Roborazzi. The `:android:designsystem` module with `S2Theme`, `ArtworkTheme` and
   every component, board and catalogue page in [`design-language.md`](../design/design-language.md).
   Verify: lint, colour-extraction unit tests, `verifyRoborazziDebug`, the approval-hash test.
   **Not a gate (decided 2026-09-25):** later steps start using a component as soon as it is
   catalogued, whether or not its box in `docs/design/catalog/index.md` is ticked yet — the owner
   reviews and ticks it in their own time (design-language.md §5), and a later change to a ticked
   component flows to every screen already using it.
2. **Shell and player sheet.** First a branch-only spike covering just the sheet drag, the scroll
   handoff from a stub `LazyColumn` queue, and predictive back (with section 1's ordering case);
   findings amend this doc. Then `setContent { AppTheme { AppShell(start) } }`: `NavDisplay` with
   placeholder entries, `AppNavigator`, the section 1 state model with placeholder player content,
   adaptive chrome (rail, player pane, class mapping, postures), the list-detail scene, the settings
   sheet, edge-to-edge, and `ShellViewModel` taking over `MainPresenter` (queue visibility,
   changelog, trial and thank-you dialogs, review prompt, crash-reporting nag). Deletes the old shell.
   Verify: unit tests for `PlayerSheetGeometry`, `ShellViewModel`, `AppNavigator`; Robolectric robot
   tests on `AppShell` (mini tap → NowPlaying, peek → Queue, back Queue → NowPlaying → Mini → pop,
   empty queue hides the sheet, `StateRestorationTester`); a new `sheet-levels-back.yaml` on the
   emulator's phone, foldable and tablet profiles.
3. **Player.** Mini player and Now Playing (with the tabletop split) on ViewModels over the playback
   flows, player artwork theming, sleep timer and playback menu actions, the Cast button. Deletes the
   playback Fragments and presenters. Verify: UDF test layers (principle 14); `playback-controls`,
   `repeat-modes` and `sleep-timer` flows.
4. **Queue.** `LazyColumn` with drag-to-reorder, remove and play-next, in the sheet and the pane.
   Deletes `QueueFragment`, its presenter and binders. Verify: UDF test layers; reorder never moves
   the sheet; `queue-actions` and `queue-shuffle` flows; `AppShellTest`.
5. **Screens,** after a short serial 5.0 for the shared song actions every group uses (playlist menu
   as a use case and Compose menu; tag editor, song info and create-playlist dialogs). Then groups
   with disjoint files, one worker each:
   - A. Library: pager and top bar (section 3); songs, albums, artists, genres, playlists lists.
   - B. Detail: album, artist, genre, playlist, smart playlist.
   - C. Home and Search. D. Equalizer.
   - E. Onboarding and library sources, per `redesign-inventory.md`.
   Verify per group: UDF test layers; its `nav/*.yaml`, multi-select and list flows; its
   `NotBuiltYet` entries gone.
6. **Settings.** `androidx.preference` is Fragment-based, so settings are rebuilt: Compose setting
   rows (switch, list, slider, link) over the existing preference managers, one route per screen.
   Deletes every `PreferenceFragmentCompat`, the preference XMLs and the dependency. Verify: a
   ViewModel test per screen that each change writes its preference; `settings-*` flows.
7. **Delete the legacy.** Remove `:android:recyclerview-adapter`, `BasePresenter`/`BaseContract`,
   the ViewBinders, unused layouts, menus, drawables and strings, navigation-fragment, navigation-ui,
   Safe Args, hilt-navigation-compose, and the AppCompat XML themes beyond the launch theme. Verify:
   build, lint with `UnusedResources` clean, unit tests; no `Fragment()`, `Presenter` or `R.layout`
   left outside an agreed keep list; APK size against the last release.
   **Done (#381).** Kept, because still reachable with no Compose replacement: the Jellyfin, Emby and
   Plex sign-in `DialogFragment`s and the paywall `DialogFragment`, with the AppCompat/Material View
   themes, `CircularLoadingView` and the helpers they use. The paywall has since moved to a Compose
   `PaywallHost` in `MainActivity` (#443).
8. **Parity gate, then tag.** Every Maestro flow green on phone, foldable and tablet profiles; the
   `redesign-inventory.md` checklist ticked; the `docs/testing/device-checks.md` batch done on the
   owner's device (predictive back, 3-button and gesture nav, light and dark, Cast, Android Auto).
   Only then is a release tagged. The checklist lives in [`docs/testing/parity.md`](../testing/parity.md).

## 7. Decisions for the owner (decided 2026-09-25, epic #382; each can still be revisited)

1. **Artwork theming scope. Decided: player surface + artwork detail screens**, behind a
   default-on setting; not the whole app (section 5).
2. **Player on wide windows.** Resolved by the section 2 rule: the sheet below 1200 dp, a
   persistent trailing pane from Large up.
3. **Swipe the mini player away** (to Hidden, clearing or stopping the queue). **Decided: no**;
   keep Hidden programmatic, as today.
4. **Fourth nav item. Superseded.** The settings bottom sheet is dropped, not kept — see
   redesign-inventory.md owner decision 1, which supersedes the "keep it for parity" recommendation
   here. Settings becomes a top-bar action, not a nav item or rail entry.
5. **List-detail beside the player pane.** Resolved by the section 2 rule: at 840–1199 dp there is
   no persistent pane, so list-detail has two panes; from 1200 dp list, detail and player fit.
6. **Design system choices** (material3 alpha pin, Roborazzi): decided, see `design-language.md`
   (the Sources preamble) and §4. The selection toolbar (`design-language.md` §3) is **decided:
   floating** (`HorizontalFloatingToolbar`); the docked board stays in the catalogue for comparison.

## 8. Changes to the UDF principles

Made in [`compose-viewmodel-udf.md`](compose-viewmodel-udf.md) with this doc:

- **Principle 9** (Fragment stays as a thin lifecycle host) is **deleted**. Its duties are gone or
  moved: toolbar and options menus → the destination's `TopAppBar`; navigation → lambdas the entry
  provider wires to `AppNavigator`; dialogs → Compose dialogs owned by the screen;
  `PlaylistMenuPresenter` → a use case and a Compose menu; Context work → the route's effect collector.
- **Principle 12** (no Fragment base class) is **deleted**: no Fragments are left to share one.
- **Principle 13** becomes a test-first *build* order; step 7 becomes "register the route in the
  entry provider, wire it to the navigator, collect effects, delete the old screen's code".
- Principles 10 and 11, and the effect and `Context` notes in 4 and 8b, name the route composable
  instead of the Fragment. Numbering is kept so existing references stay valid.
