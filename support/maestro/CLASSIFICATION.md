# Maestro flow classification (#450)

Each flow here is either **UI-only** or **device-only**. A UI-only flow checks Compose navigation and state that a
Robolectric test in `:android:app` can drive. A device-only flow needs the real platform: MediaStore or SAF import,
the playback service and its notification, the widget, shortcuts, Android Auto, voice, open-file intents,
rotation or process death.

UI-only flows are ported to JVM tests and deleted along with their `support/scripts/checks/` wrapper, so the
emulator suite only spends time on what a JVM test cannot see. A new UI check belongs in a Robolectric test (see
`.claude/rules/testing.md`), not a new flow.

## Ported to JVM tests (deleted)

| Flow | Wrapper | Covered by |
|---|---|---|
| `genres-sort-by-song-count` (#174) | `genres-sort-by-song-count.sh` | `GenreListIntegrationTest` "sorts genres by song count"; `LibraryScreenTest` "the overflow lists the tab's options above Edit tabs" |
| `library-compose` (#377) | `library-compose.sh` | `LibraryScreenTest` (tabs, pages, selection toolbar); `GenreDetailScreenTest`, `AlbumArtistDetailScreenTest`, `AlbumDetailScreenTest`, `PlaylistDetailScreenTest` "a song plays from its position"; `NewPlaylistFormTest` |
| `library-multiselect-back` (#225) | `library-multiselect-back.sh` | `LibraryScreenTest` "back with a selection clears it rather than leaving the Library", "without a selection back is left to the back stack" (see note 1) |
| `open-queue-by-taps` | `open-queue-by-taps.sh` | `AppShellTest` "tapping the mini player opens Now Playing at rest, and the queue button raises the sheet on the queue" |
| `paywall-settings` (#380) | `paywall-settings.sh` | `SettingsScreenTest` "the root opens S2 Pro"; `PaywallScreenTest` "a Pro user sees their status and no plans", "restore and back reach the caller" |
| `playlists-fast-scroller` (#223) | `playlists-fast-scroller.sh` | `LibraryScreenTest` "the playlists page has a fast scroller at its end edge too" |
| `playlists-multiselect-back` (#185) | `playlists-multiselect-back.sh` | `PlaylistDetailScreenTest` "back while selecting clears the selection rather than leaving the playlist", "without a selection back is left to the back stack" |
| `select-song`, `song-still-selected` (#224) | `song-selected-across-play-pause.sh` | `SongListIntegrationTest` "selection survives a song mutation like play count changing" |
| `settings-compose` | `settings-compose.sh` | `SettingsIntegrationTest` (Pure black, Dark theme stored); `SettingsScreenTest` "playback opens the equalizer"; `EqualizerScreenTest` |
| `shell-home-search` (#378) | `shell-home-search.sh` | `HomeScreenTest`; `SearchScreenTest` (artist, genre and song taps); `SearchLibraryTest` (typo tolerance) |
| `shell-player` (#376) | `shell-player.sh` | `AppShellTest` (play/pause/skip/seek, swipe to remove, drag to move, tap a queue row, sleep timer, speed, Go to artist); `PlayerViewModelTest` |
| `shell-sheet-levels` (#375, #410) | `shell-sheet-levels.sh` | `AppShellTest` (levels, back steps, drag up, pinned song, panels, back at Mini pops the destination) |
| `sleep-timer` | `sleep-timer.sh` | `AppShellTest` sleep timer tests (the flow still drove the pre-shell overflow menu) |
| `songs-fast-scroll-drag` (#222) | `songs-fast-scroll-drag.sh` | `LibraryScreenTest` "dragging the songs page's fast scroller to the bottom scrolls to the last song" |

Note 1: `library-multiselect-back` also asserted that switching tabs clears the selection. The Compose library
keeps each tab's selection in that tab's ViewModel and nothing clears it on a tab switch, and `LibraryDestination`
needs Hilt, so no JVM test reaches it. That part is not ported; see the #450 report.

## Device-only (kept)

| Flow | Wrapper | Why it needs a device |
|---|---|---|
| `first-run`, `first-run-shell` | `first-run.sh` | Runtime permission grant and the MediaStore scan behind onboarding |
| `notification-controls`, `notification-controls-play` | `notification-controls.sh` | The media notification, driven through System UI |
| `playback-controls` | `playback-controls.sh` | Real playback through the service, checked against the notification |
| `playlist-export-m3u` | `playlist-export-m3u.sh` | SAF create-document picker and the written file |
| `queue-actions` | `queue-actions.sh` | Queue edits checked against the real Media3 queue over the debug receiver |
| `queue-shuffle` | `queue-shuffle.sh` | Real Media3 shuffle order and playback |
| `repeat-modes` | `repeat-modes.sh` | Repeat modes observed through real playback crossing track ends |
| `settings-usb-dac-direct-output` | `settings-usb-dac-direct-output.sh` | Audio output routing, API 34+ |
| `shell-tag-editor`, `shell-tag-editor-rescanned` | `shell-tag-editor.sh` | SAF tag write on the `taglib` provider and the rescan that reads it back |
| `tag-edit-playing`, `tag-edit-not-playing` | `tag-edit-queued.sh` | Tag write to a file the player holds open, then playback continuity |

## Helpers (`nav/`, kept)

Subflows run by other flows or wrappers, not checks of their own: `create-testlist`, `launch-fresh`,
`open-library-tab`, `open-now-playing`, `open-queue`, `open-settings`, `pick-saf-folder`, `search`,
`setup-taglib-provider` (the last one also runs from `checks/_lib.sh`).

## Unportable

None. No UI-only flow hit a Robolectric limit. Two limits shaped the tests instead: the fast scroller swallows
`DropdownMenu` popups and a focused `TextField` in a `Dialog` never idles. The tests assert overflow items and the
new-playlist form outside those hosts.
