# Compose App Shell

Status: design, 2026-09-25. Nothing here is built yet.

One Compose `MainActivity` with a Navigation Compose `NavHost` inside a shell layout replaces
`MainActivity`'s XML, `MainFragment`/`MainPresenter`, `MultiSheetView`, `CustomBottomSheetBehavior`,
`res/navigation/main.xml` and `launch.xml`, `bottom_nav_menu.xml` and `BottomDrawerSettingsFragment`.
Until a screen migrates, its Fragment is a destination hosted with `AndroidFragment`; when it
migrates it becomes a plain `composable<Route>` and the Fragment is deleted. No flags, no parallel
app. The shell owns navigation chrome and the player surface; destinations own everything else,
including their top bars.

## API baseline (checked against the Gradle cache, not docs)

| API | Artifact / version | Status |
|---|---|---|
| `AnchoredDraggableState`, `anchoredDraggable`, `DraggableAnchors` | foundation 1.12.1 (BOM 2026.09.00) | stable |
| `PredictiveBackHandler`, `BackHandler` | activity-compose 1.13.0 | stable |
| `LargeFlexibleTopAppBar`, `MediumFlexibleTopAppBar` | material3 1.4.0 (BOM) | public, no opt-in |
| `TopAppBarDefaults.exitUntilCollapsedScrollBehavior` / `pinnedScrollBehavior` | material3 1.4.0 | `@ExperimentalMaterial3Api` |
| `WideNavigationRail`, `ModalWideNavigationRail`, `ShortNavigationBar` | material3 1.4.0 | public, no opt-in |
| `currentWindowAdaptiveInfoV2()`, `Posture` | material3-adaptive 1.3.0 | stable; **not a dependency yet** |
| `ListDetailPaneScaffold` | material3-adaptive-layout 1.3.0 | `@ExperimentalMaterial3AdaptiveApi`; not a dependency |
| `NavHost`, type-safe `composable<T>`, `toRoute()` | navigation-compose 2.9.8 | stable; only transitive today (via hilt-navigation-compose) |
| `AndroidFragment<T>()` | `androidx.fragment:fragment-compose` 1.9.0 | stable; **not a dependency**, not in the local cache |
| `@Serializable` routes | Kotlin serialization plugin + `kotlinx-serialization-core` | **not in the catalog** |
| MaterialKolor (`material-kolor`) | 5.0.1 in Podcasts | **not a dependency**; only `material-color-utilities` is cached |

Slice 1 adds navigation-compose (explicit, aligned with navigation-fragment 2.9.8 while that lives),
fragment-compose and the serialization plugin. Adaptive and MaterialKolor come with their slices.

## 1. Player state model

### Levels

`enum class PlayerLevel { Hidden, Mini, NowPlaying, Queue }`. One shell-owned
`AnchoredDraggableState<PlayerLevel>` drives the sheet on compact and medium widths. Its offset is
the sheet's top edge in shell coordinates. Queue is not a second sheet: it is an anchor *above*
zero, so dragging past Now Playing pushes Now Playing up and pulls the queue panel
in (the Podcasts `ExpandableSheetScaffold` look, driven by the drag rather than a toggle).

```
 offset (y of sheet top)       H = shell height, N = nav bar incl. inset,
                               M = mini player height, Q = queue travel
 Hidden      = H               sheet fully below the window, nav bar at rest
 Mini        = H - N - M       mini player sits on the nav bar
 NowPlaying  = 0               sheet fills the shell, nav bar slid off
 Queue       = -Q              now playing pushed up by Q, queue panel fully in

 ┌────────────────┐  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐
 │                │  │  destination   │  │  now playing   │  │ now playing ↑  │
 │  destination   │  │                │  │  artwork       │  │ (compact head) │
 │                │  │                │  │  transport     │  ├────────────────┤
 │                │  ├────────────────┤  │                │  │ queue list     │
 │                │  │ mini player    │  │ ─ Up next ─    │  │                │
 ├────────────────┤  ├────────────────┤  │                │  │                │
 │ nav bar        │  │ nav bar        │  │                │  │                │
 └────────────────┘  └────────────────┘  └────────────────┘  └────────────────┘
      Hidden               Mini              NowPlaying             Queue
```

The sheet's own `Modifier.offset` clamps at `max(offset, 0)`; everything else is derived from three
fractions, each clamped to 0..1:

```
reveal r = (Hidden - offset) / (Hidden - Mini)        0 hidden → 1 mini
expand e = (Mini - offset) / (Mini - NowPlaying)      0 mini   → 1 now playing
queue  q = (NowPlaying - offset) / (NowPlaying - Queue)   0 → 1 queue
```

| Element | Tracks |
|---|---|
| Nav bar | `translationY = N * e`; it slides down under the rising sheet, as `MultiSheetView` does today |
| Mini player | `alpha = 1 - e / 0.3`; not clickable and cleared from semantics once `e > 0.5` |
| Now playing | `alpha = (e - 0.2) / 0.8`; `translationY = -Q * q` |
| Queue panel | peek header visible at `e = 1`; `translationY = Qh * (1 - q)` |
| Scrim over destinations | `alpha = 0.32 * e`; consumes taps when `e > 0` and taps settle to Mini |
| Destination bottom padding | `N + M * r`: follows reveal only, never `e`, so content never reflows per frame |

All reads of `state.requireOffset()` happen in `Modifier.offset { }` / `graphicsLayer { }` lambdas,
so a drag re-runs layout and draw, never composition. The fraction maths is a pure function
(`PlayerSheetGeometry`) with unit tests.

Gestures: `anchoredDraggable(state, Orientation.Vertical)` on the sheet; a tap on the mini player
animates to NowPlaying; a tap or drag on the "Up next" peek goes to Queue. Hidden is never a
gesture target (see below).

### Nested scroll between sheet and queue list

A `NestedScrollConnection` on the sheet, the pattern M3's bottom sheet uses internally (copy the
pattern, the class is private):

- `onPreScroll`, finger moving up, offset above the Queue anchor: the sheet consumes the delta
  (`dispatchRawDelta`) so a drag on the peek raises the queue before the list scrolls.
- `onPostScroll`, finger moving down, list already at the top: the leftover drags the sheet down
  towards NowPlaying.
- `onPreFling` up while the sheet is not at Queue, and `onPostFling`: `settle(velocity)`.

While the queue is the hosted `QueueFragment`, the source is a `RecyclerView` in `AndroidFragment`.
Interop goes through the `AndroidView` holder (a `NestedScrollingParent3`); the `RecyclerView` should
walk past the `FragmentContainerView`, which declines nested scroll. The spike proves it first.

### Predictive back

`PredictiveBackHandler(enabled = level > Mini)` steps down one level per gesture:
Queue → NowPlaying → Mini, then the handler disables and the `NavHost` pops. During the gesture the
handler drives the same state (`anchoredDrag { dragTo(lerp(current, lower, progress)) }`), so the
nav bar, mini fade and scrim follow the finger for free. Commit animates to the lower anchor; cancel
animates back. Back never reaches Hidden.

Ordering risk: the newest enabled callback runs first, and hosted fragments (library multi-select
back) can register after the sheet. Keying the handler on `level` re-registers it whenever the sheet
leaves Mini. The spike checks that back with a Library selection and Now Playing open steps the sheet.

### State restoration

The state is `rememberSaveable(saver = AnchoredDraggableState.Saver())`, so the level survives
rotation, fold/unfold and process death. Anchors are recomputed from the new size with
`updateAnchors(anchors, newTarget = state.targetValue)`. Hosted player fragments save themselves
through `AndroidFragment`'s saved state.

### Hidden when nothing is queued

`ShellViewModel` (replacing `MainPresenter`) exposes `hasQueue` from `QueueManager.queueStateFlow`.
Empty queue: anchors `{Hidden}` only, padding drops to `N`. Non-empty: `{Mini, NowPlaying, Queue}`,
animating to Mini. Hidden is not user-reachable (decision 4). On cold start the saved level stands
until the first queue emission, so a restoring queue never flashes the mini player.

## 2. Adaptive layout

Tiers come from the Podcasts `LayoutTier` (Compact < 600 dp, Regular 600–839, Wide ≥ 840) and
`FoldPosture`, ported into `:android:app` `ui/shell/adaptive/` and derived from
`currentWindowAdaptiveInfoV2()` at the call site, as Podcasts does. No `isTablet` logic.

| | Compact | Regular (medium) | Wide (expanded+) |
|---|---|---|---|
| Navigation | `ShortNavigationBar`, bottom | `WideNavigationRail`, collapsed | `WideNavigationRail`, expanded, with secondary items (Settings, EQ) |
| Mini player | sheet at Mini, over the nav bar | sheet at Mini, bottom of the content pane (not under the rail) | strip at the bottom of the content pane when the player pane is collapsed |
| Now playing | sheet, full shell | sheet over the content pane; rail stays visible and live | **persistent trailing pane**, 360 dp (400 dp ≥ 1200 dp) |
| Queue | sheet level Queue | sheet level Queue | inside the pane: "Up next" pushes now playing up, same visual as the sheet |
| Library list-detail | single pane | single pane, wider grids | single pane, wider grids |

No `NavigationSuiteScaffold`: it has no slot for a sheet between content and nav bar, nor for a
nav bar that tracks a drag. The shell lays out rail, content and pane itself, like Podcasts'
`CastNavigationSuiteScaffold`. A nav tap with the sheet above Mini settles it to Mini, then navigates.

### The player pane on wide windows

Wide windows keep browsing context: the player is a supporting pane, not a sheet over the library.
`PlayerLevel` still holds the state; the pane renders it as Mini = collapsed to the strip,
NowPlaying = pane showing now playing, Queue = pane showing the queue. No `AnchoredDraggableState`
gestures drive the pane; its queue push is an `animateFloatAsState` on the level.

### List-detail

No `ListDetailPaneScaffold`: experimental, a second navigator beside the `NavHost`, no use for hosted
fragments, and with the player pane it would squeeze rail + list + detail + player under 1200 dp.
Library grids widen instead. Revisit once library and detail are Compose (decision 6).

### Postures

- **Tabletop** (half-opened, horizontal hinge): Now Playing splits at `foldBounds`, artwork above,
  transport below, nothing interactive in the hinge band; at Queue the queue fills the lower half.
  A layout inside Now Playing; the level never changes.
- **Book** (half-opened, vertical hinge): the Wide layout, player pane on the trailing leaf, hinge
  as the split, whatever the width class.

### Size or posture changes mid-drag or with the sheet open

A size change recomputes anchors with `updateAnchors(newAnchors, newTarget = state.targetValue)`,
which ends any drag in flight and snaps to the level the drag was heading for. Crossing tiers maps
the level:

| From → to | Rule |
|---|---|
| sheet → pane (unfold, resize wider) | Mini and NowPlaying → pane open on now playing; Queue → pane on queue |
| pane → sheet (fold, resize narrower) | always Mini: folding never throws a full-screen sheet over what the user was browsing |
| any posture change | level unchanged; only the now-playing layout changes |

The saveable state sits above the tier branch, so one instance survives the switch.

## 3. Top bars

The shell draws no top bar. Each destination owns a `Scaffold(topBar = …)` and the status-bar
inset through the bar's default `windowInsets`.

- **Top-level and list screens** (Home, Library, Search, Playlists, Settings): `LargeFlexibleTopAppBar`
  with title and subtitle (for example the library count), `exitUntilCollapsedScrollBehavior`
  connected to the screen's list. This is a collapsing *bar*, not a hero.
- **Artwork detail** (album, artist, and later genre and playlist): the existing `DetailScaffold`:
  pinned small bar, artwork as a list item. No collapsing hero; the NestedScrollConnection and
  graphics-layer attempts failed and are not retried.
- **Multi-select**: the destination swaps its bar to a contextual bar with `AnimatedContent` keyed on
  `selectedCount > 0`. Selection is screen state in the UiState, not shell state.

### Hosted fragments during the transition

Every destination fragment already draws its own `Toolbar` (Home, Search, Library, the detail and
settings screens, EQ; album and artist detail draw theirs in `DetailScaffold`). The `HostedFragment`
wrapper adds `statusBarsPadding()` so they look as they do today under `fitsSystemWindows`.
`ToolbarHost` stays private to `LibraryFragment` and dies with it; the shell never lends a toolbar.

### The risk, and the spike

The risk is not hosted fragments; it is the first Compose screen that replaces a `ToolbarHost`:
Library. Its tabs share one bar and one contextual bar (#344 ownership bug), its options menus come
from each tab, and the collapsing bar must follow whichever page's list is scrolling. Mixing that
with edge-to-edge, hosted neighbours and predictive back between Fragment and Compose destinations
is where this can go wrong.

**Spike (slice 0, not landed):** a branch with the shell, a rough sheet hosting `QueueFragment`, and:
1. hosted `HomeFragment` (View toolbar + `statusBarsPadding`) under `enableEdgeToEdge()`;
2. `AlbumDetail` as a plain `composable<AlbumRoute>` (DetailScaffold) reached from Home, loading by key;
3. a Compose Library prototype: `LargeFlexibleTopAppBar` over a `HorizontalPager` of the existing
   Songs and Albums composables (their ViewModels, no fragments), with the contextual bar swap and
   per-page `nestedScroll(scrollBehavior.nestedScrollConnection)`.

Pass: the bar follows the visible page; the contextual bar counts and clears per page; no double
status inset; predictive back animates both ways across Compose/Fragment; hosted `findNavController()`
resolves (section 4); the queue list hands scroll to the sheet; `nav/*.yaml` and
`library-multiselect-back.yaml` pass. Findings amend this doc before slice 1 is briefed.

## 4. Navigation

### Routes

Navigation Compose 2.9 with `@Serializable` routes in `ui/shell/Routes.kt`. Routes carry keys, never
Parcelable models:

```kotlin
@Serializable data object HomeRoute   // likewise LibraryRoute, SearchRoute
@Serializable data class AlbumRoute(val albumKey: String?, val albumArtistKey: String?)  // AlbumGroupKey
@Serializable data class AlbumArtistRoute(val albumArtistKey: String?)
@Serializable data class PlaylistRoute(val id: Long)   // GenreRoute(name: String) likewise
@Serializable data class SmartPlaylistRoute(val id: SmartPlaylistId)  // enum of the built-ins
@Serializable data object SettingsRoute   // + one per preference screen, EqualizerRoute
@Serializable data class OnboardingRoute(val isOnboarding: Boolean)
```

Detail screens load their model by key (`SavedStateHandle.toRoute<AlbumRoute>()` then the
repository). The five detail fragments that read a Parcelable today switch to key lookup in slice 1;
that code is what their Compose versions use, so it is not throwaway. `AlbumGroupKey` is two
nullable strings and the route mirrors it; an album with no group key cannot open detail today either.

Top-level tabs keep separate back stacks with the standard
`navigate(tab) { popUpTo(start) { saveState = true }; launchSingleTop = true; restoreState = true }`.
The start destination (Onboarding, else Home or Library by `showHomeOnLaunch`) is computed in
`MainActivity.onCreate` from preferences, as now.

**Hosted fragments keep `findNavController()`.** The shell calls
`Navigation.setViewNavController(contentView, navController)`; `NavHostFragment.findNavController()`
falls back to walking the fragment's view parents, which reach it through the `AndroidFragment`
container. `popBackStack()` and `currentDestination` keep working. The ~30
`navigate(R.id.action_…, bundle)` calls become `navigate(AlbumRoute(…))`, and
`currentDestination?.id == R.id.x` becomes `currentDestination?.hasRoute<X>()`. Migrated Compose
screens take lambdas (`onOpenAlbum`) and never see a controller. Safe Args and its Gradle plugin go
when the six `navArgs()` users switch to route keys.

`BottomDrawerSettingsFragment` is a `<dialog>` destination whose `findNavController()` would resolve
through the dialog window, not the shell. It becomes a Compose `ModalBottomSheet` owned by the shell
(Shuffle all, Sleep timer, Equalizer, Settings) in slice 1; on rail tiers the same entries are the
rail's secondary items.

**The player is not a route.** `PlayerLevel` is shell state: it overlays every route and must not
pop with the back stack. Anything that wants to open the player sends an intent extra the shell maps
to a level.

### Deep links and shortcuts

No URI deep links exist and none are added; later, `navDeepLink<AlbumRoute>(basePath)` is the
mechanism. `MainActivity` keeps its intents as is (`MEDIA_PLAY_FROM_SEARCH` starts the service,
`VIEW` plays through the media session, `MUSIC_PLAYER`/`APP_MUSIC` launch). `ShortcutHandlerActivity`
and the toggle-playback shortcut only start the service, so they are unchanged.

Android Auto, Cast and the widgets are untouched: they talk to `:android:playback`
(`MediaBrowserServiceCompat`, media session, Cast session), never to the UI.

### Edge-to-edge (#242)

The shell resolves #242 at the root: `enableEdgeToEdge()`, `activity_main.xml` and its root
`fitsSystemWindows` are deleted. Insets are split by owner:

| Inset | Owner |
|---|---|
| status bar | the destination's top bar; hosted fragments via the wrapper's `statusBarsPadding()` |
| navigation bar | the nav bar or rail; with the nav bar slid away, the sheet's own bottom padding |
| IME | destination content (`imePadding()`) |
| display cutout, rail side | the rail and the pane |

This drops #242's "shared inset helper for ~55 View fragments": hosted fragments get parity, not
edge-to-edge, and each goes truly edge-to-edge when it becomes Compose (decision 3).

## 5. Theming hook

`AppTheme` (base theme + accent, `ui/theme/Theme.kt`) wraps the whole shell. The artwork scheme is a
second, nested `MaterialTheme` around specific subtrees, never a swap of the root scheme:

```
AppTheme(theme, accent)                       ← user's accent, everywhere
 └─ AppShell
     ├─ NavHost
     │   └─ AlbumRoute → ArtworkTheme(seed = album art)   ← detail screens, own seed
     └─ PlayerSurface
         └─ ArtworkTheme(seed = now-playing art)          ← mini, now playing, queue, pane
```

`ArtworkTheme(seed)` ports Podcasts' pieces: `ExtractArtworkColor` (MaterialKolor
`themeColorOrNull()` on a small Glide bitmap, off the main thread, cached by artwork key), a
MaterialKolor dynamic scheme from the seed in the current light/dark mode, and
`rememberAnimatedColorScheme` so track changes crossfade. It keeps the last seed while the next
extracts, so colours go old → new, never old → default → new. The now-playing seed comes from
`ShellViewModel` (current song → artwork key → seed); detail screens extract in their ViewModel.

**Recommendation (owner decision 1): player surface and artwork detail screens only**, with a
setting "Colour from artwork", default on. Not the whole app: every track change would recolour
the library the user is browsing and override the accent they chose; animating the root scheme
recomposes every colour reader; and hosted View fragments read XML theme attributes, so a whole-app
scheme cannot even reach them until the last Fragment is gone. The player's artwork theme only
becomes possible once the player surface is Compose (slice 3).

## 6. Slice plan

**Slice 0, spike (branch only):** section 3's spike, which also covers the two shell checks
(`setViewNavController` resolution, `RecyclerView` → sheet nested scroll). Output: edits to this doc.

### Slice 1: Compose shell hosting every current fragment

The smallest slice with no throwaway: it deletes the old shell outright and every line it adds is the
final shell, except the two bridges named below, each with a deletion trigger.

- `MainActivity` stays an `AppCompatActivity` (the fragments need it) and calls
  `enableEdgeToEdge()` then `setContent { AppTheme { AppShell(startRoute) } }`. `SnowfallView` becomes
  an `AndroidView` overlay in the shell.
- `NavHost` with a `composable<Route>` for every destination in `main.xml` and `launch.xml`, each
  `HostedFragment<XFragment>(arguments)`, including album and artist detail: their content is
  already Compose, but their Fragments still own dialogs and the playlist menu (principle 9).
- `PlayerSheet`: the full section 1 state model (levels, fractions, nested scroll, predictive back,
  restoration, Hidden) with `MiniPlaybackFragment`, `PlaybackFragment` and `QueueFragment` hosted
  in it. `ShortNavigationBar` on every width for now.
- `ShellViewModel` takes over `MainPresenter`: queue visibility, changelog, trial and thank-you
  dialogs, review prompt, crash-reporting nag (as one-off effects).
- Settings drawer → Compose `ModalBottomSheet`.
- Navigation call sites rewritten to routes; detail fragments load by key.
- **Bridge 1, `PlayerSheetHost`**: `PlaybackFragment` and `QueueFragment` call
  `findParentMultiSheetView()` today. They get `(requireActivity() as PlayerSheetHost)` exposing
  `level: StateFlow<PlayerLevel>` and `goTo(level)`. Deleted in slice 3.
- **Bridge 2, `HostedFragment`**: `AndroidFragment` plus `statusBarsPadding()` plus the shell's
  bottom padding (`N + M * r`, today's `navHostFragment` bottom margin). Deleted with the last Fragment.
- Deleted: `MainFragment`, `MainPresenter`/`MainContract`, `MultiSheetView`,
  `CustomBottomSheetBehavior`, `fragment_main.xml`, `activity_main.xml`, `main.xml`, `launch.xml`,
  `bottom_nav_menu.xml`, `BottomDrawerSettingsFragment` and its layout, the
  `BottomNavigationView.setupWithNavController` extension.
- `docs/architecture/compose-viewmodel-udf.md` principles 9, 12 and step 7 of 13 are rewritten
  (section 8 below).

Verification:
- Unit: `PlayerSheetGeometry`; `ShellViewModel` (empty queue → Hidden, first emission, dialogs);
  route round-trips through `toRoute()`.
- Robolectric robot tests on `AppShell` with fake destinations: per-tab back stacks; mini tap →
  NowPlaying; peek → Queue; back steps Queue → NowPlaying → Mini → pop; empty queue hides the sheet;
  `StateRestorationTester` keeps the level.
- `unit-test` and `lint` green; `InstrumentedTest`, `NavigationSmokeTest`, `SmokeTestSuite` updated
  (they find `onboardingNavHostFragment` and `bottomNavigationView` by id).
- Emulator (`emulator-check`): every `support/maestro` flow plus a new `sheet-levels-back.yaml`;
  View-id flows keep working because the player is still hosted fragments.
- `docs/testing/device-checks.md`: sheet predictive back, 3-button and gesture nav, light and dark.

### Follow-up slices, in order

2. **Adaptive chrome.** material3-adaptive 1.3.0; port `LayoutTier`/`FoldPosture`; `WideNavigationRail`
   on Regular/Wide; the Wide player pane; tier-change level mapping; tabletop split and book posture.
   Verify on the emulator's foldable and tablet profiles.
3. **Player surface to Compose.** Mini player, Now Playing and Queue (`LazyColumn` with reorder)
   composables and ViewModels; the sheet's nested scroll becomes Compose-native. Deletes the three
   player Fragments and `PlayerSheetHost`. Maestro flows move from View ids to test tags
   (`testTagsAsResourceId`).
4. **Artwork theming** on the player surface and detail screens (section 5); adds MaterialKolor.
5. **Library** as a Compose destination with the spike's top bar; deletes `LibraryFragment`, the tab
   Fragments, `ToolbarHost`, `findToolbarHost`, `ComposeContextualToolbarHelper`.
6. **Remaining screens**, one per slice, via `migrate-screen`: Album and Artist detail (drop the
   Fragment hosts), Home, Search, Genre/Playlist/Smart playlist detail, Equalizer, Settings, Onboarding.
7. **Last Fragment gone:** `MainActivity` becomes a `ComponentActivity`; drop `HostedFragment`,
   fragment-compose, navigation-fragment, navigation-ui, Safe Args, AppCompat themes.

## 7. Open decisions for the owner

1. **Artwork theming scope.** Recommend player surface + artwork detail screens, behind a
   default-on setting; not the whole app (section 5).
2. **Player on wide windows.** Recommend the persistent trailing pane. Alternative: the same sheet at
   every width, simpler but covers the library on tablets and unfolded foldables.
3. **#242 scope.** Recommend closing it through the shell: hosted fragments get status-bar padding
   (today's look), no per-fragment inset helper; each screen goes edge-to-edge when it migrates.
4. **Swipe the mini player away** (to Hidden, clearing or stopping the queue). Recommend no; keep
   Hidden programmatic, as today.
5. **Fourth nav item.** Recommend keeping the settings bottom sheet on compact for parity, with the
   entries as rail secondary items on wider tiers. Alternative: Settings as a top-level tab.
6. **Navigation 3.** Recommend staying on Navigation Compose 2.9 for the transition (hosted
   fragments, `findNavController()` bridge, NavHost predictive back). Reconsider Navigation 3 with
   adaptive scenes, and list-detail with it, after slice 7.
7. **`MaterialExpressiveTheme` and motion scheme.** Recommend adopting in slice 5 with the first
   `LargeFlexibleTopAppBar` screen, not in the shell slice, so it is judged on a real screen.

## 8. Changes to the UDF principles

- **Principle 9** becomes: only unmigrated screens have a Fragment, as a `HostedFragment`
  destination; a migrated screen is a `composable<Route>`. Fragment duties move: options menu → the
  `TopAppBar` actions; navigation → lambdas wired to the `NavHost`; dialogs → Compose (a legacy
  `DialogFragment` may use the activity's fragment manager); `PlaylistMenuPresenter` → a use case
  and Compose menu; Context work → the route composable's effect collector.
- **Principle 12** keeps "no base class"; the boilerplate moves to the route entry composable.
- **Principle 13, step 7** becomes "register the destination, rewrite callers, delete the Fragment".
