# Component migration

Tracks the move from raw `androidx.compose.material3` components to `:android:designsystem`
(design-language.md §5, #757). Two parts: which components exist and are approved, and which screens
still import raw Material3 components.

Enforced by `DesignSystemRules` in `:android:architecture-tests`: a new raw component import outside
`:android:designsystem` fails the build. The call sites below are its baseline
(`android/architecture-tests/src/test/baselines/raw-material3-components.txt`). Migrate a screen, then
delete its lines from the baseline (`./gradlew :android:architecture-tests:test -PupdateArchitectureBaselines`
rewrites it) and tick its row here; the test also fails if a baseline line no longer violates.

## Components

Status: **approved** = ticked in `catalog/index.md`; **needs work** = built in the designsystem, not yet
approved; **missing** = specified in design-language.md §3 with no catalogue entry or composable.

| ID | Status | Designsystem |
|---|---|---|
| `theme-colour` | approved | `S2Theme`, `ColorSchemes` |
| `theme-type` | approved | `S2Typography` |
| `theme-shape` | needs work | `ContinuousRoundedCornerShape` |
| `button` | approved | `S2Button` |
| `button-group` | approved | `S2ButtonGroup` |
| `icon-button` | approved | `S2IconButton`, `S2IconToggleButton` |
| `artwork` | needs work | `Artwork`, `GeneratedArtwork` |
| `row-song` | approved | `SongRow` |
| `row-album` | approved | `AlbumRow` |
| `row-artist` | needs work | `ArtistRow` |
| `row-playlist` | needs work | `PlaylistRow` |
| `top-bar` | needs work | `S2TopBar`, `S2LargeTopBar`, `S2DetailTopBar` |
| `search` | needs work | `S2SearchField` |
| `nav-bar` | needs work | `S2NavigationBar` |
| `nav-rail` | needs work | `S2NavigationRail` |
| `chip-sort-filter` | needs work | `S2FilterChip`, `S2ChoiceChip`, `S2InputChip`, `S2InfoChip` |
| `menu` | needs work | `S2Menu` |
| `song-actions-sheet` | needs work | `S2ActionsSheet` |
| `dialog` | needs work | `S2Dialog`, `S2ChoiceList` |
| `snackbar` | needs work | `S2Snackbar`, `S2SnackbarHost` |
| `toolbar-selection` | needs work | `S2SelectionToolbar` |
| `mini-player` | needs work | `S2MiniPlayer` |
| `player-controls` | needs work | `S2PlayerControls` |
| `seek-bar` | needs work | `S2SeekBar` |
| `progress` | needs work | `S2PlaybackProgress`, `InlineLoadingIndicator` |
| `queue-row` | needs work | `QueueRow` |
| `setting-row` | needs work | `SettingsGroup`, `SwitchSetting`, `SliderSetting`, `ChoiceSetting`, `LinkSetting`, `InfoSetting` |
| `eq-band` | needs work | `EqBand` |
| `row-genre` | needs work | `GenreRow` |
| `row-folder` | needs work | `FolderRow` |
| `grid-tile` | needs work | `GridTile` |
| `section-header` | needs work | `SectionHeader` |
| `state-empty` | approved | `EmptyState` |
| `state-loading` | approved | `LoadingState` |
| `state-error` | approved | `ErrorState` |
| `theme-motion` | missing | — |
| `fab` | missing | — |
| `top-bar-contextual` | missing | — |
| `fast-scroller` | missing | — |
| `eq-curve` | missing | — |
| `player-sheet` | missing | — |
| `player-pane` | missing | — |

### Raw components with no S2 equivalent

These are flagged by the rule today but have no designsystem component to swap to. Each needs either a
wrapper (add it to the catalogue first) or a decision that screens may use the raw call:
`Icon`, `Surface`, `Scaffold`, `Switch`, `RadioButton`, `OutlinedTextField`, `Card`/`OutlinedCard`/`ElevatedCard`,
`ListItem`, `HorizontalDivider`/`VerticalDivider`, `LinearProgressIndicator`/`CircularProgressIndicator`
(partly `InlineLoadingIndicator`), `SwipeToDismissBox`, `PullToRefreshBox`, `ModalBottomSheet`
(`S2ActionsSheet` covers the song-actions case only). Plain text swaps to `S2Text` (`component/Text.kt`); it is the biggest group still to migrate.

## Screens still to migrate

One row per file. Components are the raw imports the rule flags; tick when the row has none left.

| Done | File (under `com.simplecityapps.shuttle`) | Raw material3 components |
|---|---|---|
| [ ] | `debug.livelog.LiveLogScreen` | Scaffold, Text |
| [ ] | `ui.common.components.DetailScaffold` | Scaffold |
| [ ] | `ui.common.components.FastScroller` | Text |
| [ ] | `ui.common.components.LinearProgressIndicatorWithText` | LinearProgressIndicator, Text |
| [ ] | `ui.common.components.LoadingStatusIndicator` | CircularProgressIndicator, HorizontalDivider, Icon, OutlinedButton, Text |
| [ ] | `ui.common.mediaactions.MediaActionsHost` | OutlinedTextField, Text |
| [ ] | `ui.screens.equalizer.FrequencyResponseChart` | Text |
| [x] | `ui.screens.home.HomeItemTile` | Text |
| [ ] | `ui.screens.home.HomeScreen` | ElevatedCard, Icon, Scaffold, Text, pulltorefresh.PullToRefreshBox |
| [ ] | `ui.screens.home.JumpBackInGrid` | Surface, Text |
| [ ] | `ui.screens.library.AddToPlaylistSubmenu` | DropdownMenu, DropdownMenuItem, Text |
| [x] | `ui.screens.library.LibraryControls` | — |
| [x] | `ui.screens.library.LibraryDetailComponents` | Text |
| [ ] | `ui.screens.library.LibraryEmptyScreen` | LinearWavyProgressIndicator, Text |
| [ ] | `ui.screens.library.LibraryOverflowMenu` | DropdownMenu, DropdownMenuItem, Icon, IconButton, Text |
| [x] | `ui.screens.library.LibraryPages` | — |
| [ ] | `ui.screens.library.LibraryScreen` | ListItem, ModalBottomSheet, Scaffold, Switch |
| [ ] | `ui.screens.library.PlaylistDetailScreen` | Text |
| [ ] | `ui.screens.library.PlaylistDialogs` | Text |
| [ ] | `ui.screens.library.songs.ShuffleListItem` | Text |
| [ ] | `ui.screens.paywall.PaywallScreen` | Card, Icon, OutlinedCard, RadioButton, Scaffold, Surface, Text |
| [x] | `ui.screens.search.SearchScreen` | Icon, Surface, Text |
| [ ] | `ui.screens.settings.SettingsScreens` | Scaffold, Text |
| [ ] | `ui.screens.settings.about.WhatsNewScreen` | Surface, Text |
| [ ] | `ui.screens.settings.equalizer.EqualizerScreen` | Surface, Text, VerticalDivider |
| [ ] | `ui.screens.settings.excluded.ExcludedSongsScreen` | Text |
| [ ] | `ui.screens.songinfo.SongInfoScreen` | Scaffold, Text |
| [ ] | `ui.screens.sources.FolderRulesScreen` | Text |
| [ ] | `ui.screens.sources.SourcesScreen` | Text |
| [ ] | `ui.screens.sources.servers.ServerSignInScreen` | BasicAlertDialog, CircularProgressIndicator, Icon, IconButton, OutlinedTextField, Switch, Text |
| [ ] | `ui.screens.tageditor.TagEditorScreen` | OutlinedTextField, Scaffold, Surface, Text |
| [ ] | `ui.shell.AppShell` | Surface |
| [ ] | `ui.shell.ShellSheetSceneStrategy` | ModalBottomSheet |
| [x] | `ui.shell.player.FullPlayer` | Surface, Text |
| [ ] | `ui.shell.player.NowPlaying` | Text |
| [ ] | `ui.shell.player.NowPlayingPanels` | Icon, Text |
| [x] | `ui.shell.player.PlayerContent` | Text |
| [ ] | `ui.shell.player.PlayerPane` | Surface |
| [ ] | `ui.shell.player.PlayerSheet` | Surface |
| [ ] | `ui.shell.player.QueueList` | Icon, SwipeToDismissBox, Text |
| [ ] | `ui.shell.player.SongActions` | BasicAlertDialog, OutlinedTextField, Text |
