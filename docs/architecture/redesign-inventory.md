# Redesign Inventory: keep, change or drop, screen by screen

Status: inventory, 2026-09-25. Companion to [`app-shell.md`](app-shell.md), which owns the shell,
player sheet, navigation and theming. This doc owns *what each screen does* and what survives the
Compose cutover. Everything below was read from code on `fb8b8a13`; nothing here is built.

Verdict per area: **Keep** (same capabilities, restyled as Compose), **Change** (capabilities kept
or moved, flow reworked), **Drop** (capability removed on purpose). Capability-level drops are
called out inside Change areas. "Maestro" names the `support/maestro` flow that covers the area
today (`nav/*` = the reusable navigation subflows); "none" means no on-device check exists.

## Direction already set by the owner

- **One local scanner.** TagLib ("S2") becomes the only local provider. MediaStore stops being a
  user-chosen mode and is used only to *discover* files under `READ_MEDIA_AUDIO` /
  `READ_EXTERNAL_STORAGE`; KTagLib reads their tags through content URIs. SAF folder picking
  becomes an optional include/exclude setting. Needs spike 1.
- **Zero-step onboarding.** The app opens to the library. The empty state offers "Allow access to
  music on this device" and "Connect Jellyfin / Plex / Emby"; permission is asked in context and
  the scan runs in the background.
- **No analytics screen.** Crash reporting on by default, opt-out in settings. Usage analytics:
  see owner decision 3.

## 1. Library

### Library container (`LibraryFragment`) — Change
- Today: pager of enabled tabs from `pref_library_tabs_all` (order) and `pref_library_tabs_enabled`
  (visibility); Folders is opt-in; last tab restored from `library_tab_current`; "no tabs enabled"
  empty state; one shared contextual toolbar driven by the resumed tab (#344); each tab contributes
  its own options menu; a hidden "Trial" action view (days-left ring / expired multiplier) that
  opens the trial dialog and hosts the promo-code result (see §8).
- Redesign: `LargeFlexibleTopAppBar` with the library count as subtitle, M3 `PrimaryScrollableTabRow`
  over a `HorizontalPager`, contextual bar swap on selection (app-shell §3). Tab order and
  visibility move to an "Edit tabs" sheet reached from the Library overflow (drag handles, switches),
  instead of living in Settings > Display. Trial ring leaves the toolbar (§8).
- Maestro: `nav/open-library-tab`, `library-multiselect-back`.

### Songs tab (Compose) — Keep
- Tap plays the visible list from that row; synthetic "Shuffle" first row; long-press multi-select.
- Sort: name, artist, album, year, duration, date modified (`sort_order_song_list`); fast scroller
  with section popup (no popup for duration/date sorts).
- Row menu: Add to queue, Add to playlist, Play next, Song info, Exclude (no confirm), Edit tags
  (hidden if the provider can't tag-edit), Delete (only if `canBeDeleted()`, with confirm).
- Multi-select bar: Add to queue, Add to playlist, Edit tags (sanitised per provider).
- States: loading, "scan in progress" bar, `song_list_empty`.
- Redesign: already Compose; restyle rows, Shuffle becomes an extended FAB or header button.
- Maestro: `select-song` + `song-still-selected`, `songs-fast-scroll-drag`, `library-multiselect-back`,
  `queue-actions` (Play next / Add to queue from this tab), `nav/create-testlist`.

### Albums tab (Compose) — Keep
- List or 3-column grid (`pref_album_view_mode`); sort by name, artist, year, Random (reseeded only
  when Random is re-picked); album-grouped shuffle; tap opens detail; long-press multi-select.
- Menu: Play, Add to queue, Add to playlist, Play next, Exclude, Edit tags (only if every provider
  in the album supports it). Multi-select as Songs.
- Redesign: adaptive grid column count by width (app-shell §2) instead of fixed 3.
- Maestro: `library-multiselect-back`.

### Album artists tab (Compose) — Keep
- List or grid (`pref_artist_view_mode`); **no sort menu**; tap opens detail; multi-select; menu:
  Play, Add to queue, Add to playlist, Play next, Exclude, Edit tags.
- Redesign: same; consider name / album-count sort for parity with Genres (cheap, optional).
- Maestro: none.

### Genres tab (Compose) — Keep
- List only; sort by name or song count (`sort_order_genre_list`); no multi-select; menu: Play,
  Add to queue, Add to playlist, Play next, Exclude, Edit tags.
- Maestro: `genres-sort-by-song-count`.

### Playlists tab (Compose) — Change
- User playlists (sort by name or date created, `sort_order_playlist_list`) plus two hardcoded smart
  playlists: Recently Added and Most Played (≥2 plays). User playlist menu: Play, Add to queue,
  Play next, Rename, Clear (confirm), Delete (confirm). Smart playlists get no menu.
- Favorites is an ordinary playlist created lazily by the first heart tap in Now Playing, so it
  only appears once used.
- Redesign: pin a row of auto playlists at the top (Favorites always, Recently added, Most played,
  History), then user playlists; "New playlist" FAB (today a playlist can only be created from an
  "Add to playlist" menu). Rename/clear/delete stay in the row menu.
- Maestro: `playlists-fast-scroller`, `playlist-export-m3u`, `nav/create-testlist`.

### Folders tab (Compose, opt-in) — Keep
- Drill-down browsing of the local folder tree with back popping one level; falls back to the
  nearest ancestor after a rescan removes a folder. Folder menu (recursive): Play, Shuffle, Add to
  queue, Add to playlist, Play next. Song rows get the full song menu.
- Redesign: breadcrumb chips in the top bar. Still off by default.
- Maestro: none.

### Album detail (Compose in a Fragment) — Keep
- Artwork header, "year · N songs · duration", disc groups. Overflow: Shuffle, Add to queue, Play
  next, Add to playlist, Edit tags. Song rows: full song menu, Exclude **with** confirm.
- Redesign: `DetailScaffold` as app-shell §3; artwork colour theme (app-shell §5); route by key.
- Maestro: none.

### Album artist detail (Compose in a Fragment) — Keep
- Albums section (year desc) and songs; **tapping an album expands its tracks inline**, "View album"
  in its menu navigates. Artist actions: Play all, Shuffle all, Shuffle albums, Add all to queue,
  Play all next, Edit all tags. Album menu adds Shuffle and Album shuffle.
- Redesign: keep inline expand (it is distinctive); M3 Expressive split button for Play / Shuffle.
- Maestro: none.

### Genre detail (MVP) — Change
- Songs with derived albums; Shuffle, Add to queue, Play next at genre, album and song level;
  Exclude / Edit tags / Delete at song and album level; play one album from within the genre.
- Redesign: migrate to Compose on `DetailScaffold`, same actions; add Add to playlist at genre level.
- Maestro: none.

### Playlist detail (MVP) — Change
- Sort: Custom, name, artist, album, year, duration, date modified, plus a Descending toggle.
  Drag-to-reorder only under Custom + ascending (the handle just vanishes otherwise).
- Toolbar: Shuffle, Add to queue, Export as m3u (SAF create-document; empty-playlist error),
  Rename, Clear (confirm), Delete (confirm, then closes).
- Multi-select bar: Add to queue, Add to playlist, Edit tags — **no batch "Remove from playlist"**.
- Song menu adds Remove (from playlist, file kept).
- Redesign: Compose `LazyColumn` with reorder; show "Sorted by X — switch to custom order to
  reorder" when drag is off; add batch Remove; Export moves to overflow unchanged.
- Maestro: `playlists-multiselect-back`, `playlist-export-m3u`.

### Smart playlist detail (MVP) — Change
- Read-only list; toolbar Shuffle and Add to queue; song menu has queue, Play next, Song info,
  Exclude, Edit tags, Add to playlist, Delete (hidden when not deletable).
- Redesign: one "auto playlist" detail shared with Favorites/History/Recently added; add Save as
  playlist. Smart playlists stay built-in only (owner decision 7).
- Maestro: none.

## 2. Search — Change
- Search-as-you-type (500 ms debounce) over artists, albums and songs with Jaro-similarity fuzzy
  ranking; three filter chips persisted (`search_filter_artists|albums|songs`); tap artist/album
  opens detail with a shared-element transition; tap song plays the song results as a queue.
  Song menu includes Delete; artist/album menus: Play, Queue, Play next, Exclude, Edit tags,
  Add to playlist. Empty results show an empty list, no message.
- Redesign: M3 `SearchBar` / expanded `SearchView` reachable from Home and Library top bars, so
  Search can leave the bottom bar (owner decision 5); recent searches; a "No results for …" state;
  playlists and genres as result types.
- Maestro: `nav/search` (opens and types only; results unasserted).

## 3. Home — Change
- Buttons: History (last completed), Latest (recently added), Favorites (toast if empty), Shuffle
  all. Sections, each hidden when empty: Recent albums, Most played (play-count badge), This year,
  Something different (unplayed artists, seeded shuffle). Tile long-press menu: Play, Add to queue,
  Play next, Exclude, Edit tags, Add to playlist. `pref_show_home_on_launch` picks Home or Library
  as the start screen.
- Gap: an empty or unplayed library gives a **blank screen** — no empty state.
- Redesign: Home is where the zero-step empty state lives (§6). With a library: carousels
  (M3 `HorizontalMultiBrowseCarousel`) for Recently played and Recently added, a "Jump back in"
  hero for the last queue, then Most played / Something different. History/Latest/Favorites move
  to Playlists' auto-playlist row. See owner decision 4.
- Maestro: none.

## 4. Player

### Mini player — Keep
- Artwork, title, "artist • album", linear progress, play/pause, skip next; long-press skip seeks
  forward in repeated steps. **No swipe gesture.** Tap expands; hidden while the queue is empty.
- Redesign: Compose in the sheet (app-shell slice 3); M3 Expressive wavy `LinearWavyProgressIndicator`
  while playing; optional horizontal swipe to skip (owner decision 9).
- Maestro: `nav/open-now-playing`, `open-queue-by-taps`, `playback-controls`.

### Now playing — Change
- Artwork carousel: **swipe artwork to skip** to that queue position. Play/pause, prev/next with
  long-press seek, shuffle, repeat (off/all/one); seek bar with times. Audiobook and podcast songs
  swap prev/next for seek-back/forward buttons.
- Tap artist or album name opens that detail and collapses the sheet.
- Menu: Cast route button (when Cast available), Sleep timer, Favorite (heart), Lyrics overlay
  (only if lyrics exist), Song info, Edit tags, Clear queue.
- No rating, no playback-speed control. The trial-expiry speed penalty that used to change playback
  speed here is removed (§8, decision 6 superseded). The redesign adds a speed control (#400, below).
- Redesign: artwork-themed surface (app-shell §5); Expressive `FilledIconButton` shapes that morph
  on press for play/pause; `Slider` with wavy active track; lyrics as a full panel; an Equalizer
  entry in the overflow (owner decision 2); tabletop split (app-shell §2). Done (#377, #400): the
  overflow's "Playback & sound" sheet sets speed (0.5–2×, pitch kept) and ReplayGain mode and links
  to the Equalizer and Settings > Playback & sound; a speed other than 1× shows as a header chip.
- Maestro: `playback-controls`, `repeat-modes`, `queue-shuffle`, `sleep-timer`, `nav/open-now-playing`.

### Queue — Change
- Tap a row to play it; drag-to-reorder; **no swipe-to-remove**; long-press row menu: Play next,
  Remove from queue, Add to playlist, Exclude, Edit tags. Top bar (expanded only): Scroll to current,
  Save queue to playlist, Clear queue. Collapsed header shows the trial ring during Trial/Expired.
  No multi-select.
- Redesign: Compose `LazyColumn` with reorder (app-shell slice 3); add swipe-to-remove with an Undo
  snackbar; keep the row menu; auto-scroll to current on open.
- Maestro: `queue-actions`, `queue-shuffle`, `open-queue-by-taps`, `nav/open-queue`.

## 5. Actions, dialogs and tools

### Shared context actions — Change
- The same verbs appear in ~12 menus built three ways (Compose `*Menu.kt`, `PopupMenu` XML,
  toolbar XML): Play, Shuffle, Add to queue, Play next, Add to playlist (with "Create playlist…"),
  Song info, Exclude, Edit tags (provider-gated), Delete (SAF, deletable only), Remove (playlist).
  No Share, no Set as ringtone.
- Inconsistency: Exclude confirms in the legacy screens, Home, Search, Queue and Album detail, but
  not from the Compose library tabs.
- Redesign: one `MediaActionsSheet` (modal bottom sheet with artwork header) used everywhere;
  Exclude and Remove become immediate with an Undo snackbar; Delete keeps its confirm.
- Maestro: `queue-actions` (Play next, Add to queue), `nav/create-testlist` (Add to playlist).

### Add to playlist, create playlist, duplicates — Change
- Submenu lists "Create playlist…" then every playlist; create validates non-empty only (duplicate
  names allowed). Adding checks duplicate songs unless `playlist_ignore_duplicates`; the duplicates
  dialog has a "don't ask again" switch that writes the same key. Toast "N added to playlist".
  Sources: songs, albums, artists, genres, folders, the queue.
- Redesign: a picker sheet with "New playlist" at the top and recent playlists first; duplicates
  handled as "2 already in playlist — Add anyway" in a snackbar, which lets the
  `playlist_ignore_duplicates` setting go (owner decision 8).
- Maestro: `nav/create-testlist`.

### Tag editor — Keep
- Fields: Title, Artists, Album, Album artist, Year, Track #/total, Disc #/total, Genres, Lyrics.
  Batch mode hides Title and Track #, shows mixed values as blank. Unreadable songs are silently
  skipped but still counted in the "N of M updated" toast. Writes only SAF `content://` files;
  always updates Room and the live queue.
- Redesign: full-screen Compose editor, "mixed" placeholders; list uneditable songs before saving.
  Done (#377): `ui/screens/tageditor`, a detail-pane screen with write progress and a discard prompt;
  the toast now counts only songs it tried to write.
- Maestro: `shell-tag-editor` (on the `taglib` provider).

### Song info — Keep
- Title, track, duration, album artist, artists, album, year, disc, play count, genres, path
  (decoded), MIME, size, bit rate, sample rate, channels, lyrics.
- Redesign: modal bottom sheet, copy-path action. Done (#377) as a detail-pane screen rather than a
  sheet, adding bit depth and ReplayGain; `ui/screens/songinfo`. Maestro: `shell-tag-editor`.

### Sleep timer — Keep
- 5/15/30/60 min, "Play to end of track" switch (`sleepTimerPlayToEnd`); while active: live
  countdown or "waiting for track to end", Stop, Set new time.
- Redesign: bottom sheet with M3 Expressive button group of durations plus a custom duration;
  countdown chip on Now Playing. Reached from Now Playing (and the drawer today). Done (#377): the
  running timer's sheet offers "Add 5 min" and Stop; Stop reopens the presets, replacing Set new time.
- Maestro: `sleep-timer`.

### Equalizer (`DspFragment`) — Change
- Master switch (`equalizer_enabled`), presets (editing switches to Custom, persisted), band editor,
  ReplayGain mode (Track/Album/Off, `replaygain_mode`), pre-amp slider, frequency-response chart dialog.
- Redesign: Compose "Sound" screen: EQ, ReplayGain, pre-amp, plus the USB DAC direct-output switch
  from Settings > Playback. Reached from Now Playing overflow and Settings (owner decision 2).
- Maestro: none (`settings-usb-dac-direct-output` covers only the USB switch).

## 6. Onboarding, sources and the scanner

### Onboarding wizard (`OnboardingParentFragment`) — Drop
- Today: non-swipeable pager of Storage permission (if not granted), Analytics (first run, once),
  Media provider selection, Scanner (auto-scans; closes on completion during onboarding). The
  `MusicDirectories` page is defined but never added. Sets `has_onboarded` on exit. Settings >
  Media > "Media providers" reopens it with `isOnboarding=false` (providers + scanner only).
- Redesign: gone. First launch lands on Home (or Library) with an empty state: "Allow access to
  music on this device" (runtime permission in context; rationale on second ask; "Open settings"
  after permanent denial) and "Connect Jellyfin / Plex / Emby". Scan starts on grant and shows
  progress in place (§6 scanner). `has_onboarded` and the launch nav graph go.
- Maestro: `nav/launch-fresh` assumes onboarding is already done; none covers it.

### Analytics / privacy step — Drop
- Today: two switches, both default off; the ViewModel never hydrates from stored values (harmless
  only because it shows once, `onboarding_analytics_dialog_viewed`).
- Redesign: crash reporting defaults on with opt-out in Settings > Privacy; usage analytics per
  owner decision 3. Also drop the alpha/beta crash-reporting nag in `MainPresenter`.
- Maestro: none.

### Media provider selection and options — Change
- Five types: S2 (TagLib), Basic (MediaStore), Jellyfin, Emby, Plex; one of each; grouped Local /
  Remote; "+" opens an options sheet, then that type's config. Row menu: Configure, Remove (confirm
  outside onboarding; removal pauses playback if needed, strips the queue, deletes that provider's
  songs and playlists). Onboarding auto-adds MediaStore when empty.
- Redesign: **Settings > Sources**: "This device" (always present once permission is granted, with
  folder include/exclude) and one card per connected server with status, last sync, Configure,
  Sign out. The Basic/S2 choice disappears.
- Maestro: none.

### Local scanner mode (MediaStore "Basic" vs TagLib "S2") — Change
- Today TagLib walks persisted SAF trees, opens each file with `openFileDescriptor`, detaches the fd
  and calls `KTagLib.getAudioFile(fd, …)`. MediaStore mode reads the `Audio.Media` cursor and
  genre joins, and already backfills ReplayGain by opening `content://media/external/audio/media/<id>`
  and feeding KTagLib the same way (`MediaStoreReplayGainReader`) — a shipped precedent for spike 1.
- Only TagLib gives: multi-value artists and genres, track/disc totals, lyrics, grouping, bit rate,
  sample rate, channels; tag editing and Delete need SAF document URIs.
- Redesign: MediaStore discovery + KTagLib via content URIs for everything; SAF trees become an
  optional "Add folder" for files MediaStore misses. Open: tag writing and Delete need a write path
  (`MediaStore.createWriteRequest` on 30+, SAF below) — spike 2.
- Spike 1 (#370): **go for API 30+**, and the discovery half has landed. `TaglibMediaProvider`
  queries `Audio.Media`, filters by folder (`FolderFilter`) and reads each content URI with
  KTagLib. Tags match the SAF build on every stored column; imports ran 4–8x faster than the SAF
  walk on API 36/37. Songs are keyed by file path now, so S2-provider users need spike 3 before
  release. Evidence in [`spike-taglib-mediastore.md`](spike-taglib-mediastore.md).
- Maestro: none (emulator fixtures seed files, not provider choice).

### Directory selection (SAF) — Change
- Lists persisted tree grants (tree walk streamed; Done disabled until every walk finishes); Add
  launches `ACTION_OPEN_DOCUMENT_TREE` and persists the grant ("no document provider" dialog if
  none); Remove releases it. No handling of revoked grants here.
- Redesign: Settings > Sources > This device > Folders: include extra folders, exclude folders
  (replacing path-level exclusion by song). Flag revoked grants inline.
- Maestro: none.

### Scanner UI — Change
- Per-provider rows with separate song and playlist progress (in progress / complete / failed with
  message); Back cancels the scan; Close exits mid-scan. Settings > Media > Rescan shows the same
  list in a dialog with only Close. Automatic rescan via `MediaImportWorker`
  (`pref_media_rescan_frequency`: never/daily/weekly; last scan date in the summary).
- Redesign: scanning is ambient: an M3 Expressive `LinearWavyProgressIndicator` under the Library
  top bar with "Scanning… 1,204 songs", tappable for a per-source detail sheet with errors and
  Rescan. Pull-to-refresh on Library triggers a rescan.
- Maestro: none.

### Jellyfin and Emby configuration — Change
- One shared layout: Address, Username, Password, "Remember password" (unchecked clears stored
  credentials). Address and username required. Authenticate shows a loading state; success
  auto-closes after 1 s; failure shows the error with Retry back to the form. Reopening pre-fills.
  No sign-out, no library selection. Settings > Media > "Report playback to server"
  (`pref_report_playback`) applies to all three servers.
- Redesign: full-screen Compose form with URL validation, inline errors, "Sign out" on the
  source card, and Quick Connect for Jellyfin as a later enhancement. Offline downloads
  (`:android:downloads`, `pref_download_wifi_only`) have **no UI at all** today; the source card
  is where "Download over Wi-Fi only" and storage used would live (owner decision 10).
- Maestro: none.

### Plex configuration — Change
- As above plus an "Auth code" field (a 2FA code appended to the password); password required.
- Redesign: plex.tv PIN link (`plex.tv/link`) as the primary path, username/password as fallback.
- Maestro: none.

## 7. Settings

The root lists nine screens (`preferences.xml`). Redesign: one Compose settings list with
Expressive grouped sections and search, five destinations instead of nine.

| Screen | Capabilities today (keys) | Verdict | Redesign | Maestro |
|---|---|---|---|---|
| Display (custom View) | Theme Light/Dark/System (`pref_theme`), Extra dark (`pref_theme_extra_dark`), 6 accents (`pref_theme_accent`), Show Home on launch, library tab order/visibility; theme change recreates the activity | Change | "Appearance": add Dynamic colour (Material You) as an accent choice, "Colour from artwork" (app-shell decision 1), rename Extra dark to "Pure black"; tabs move to Library (§1); no recreate | `nav/open-settings` only |
| Playback | Keep shuffle on new queue (`pref_retain_shuffle_on_new_queue`), USB DAC direct output (API 34+, `pref_bit_perfect_usb`) | Change | Merge into "Playback & sound" with the EQ screen's settings | `settings-usb-dac-direct-output` |
| Media | Media providers (reopens onboarding), Report playback, Rescan (dialog), Rescan frequency, Excluded songs (list, remove one, clear all) | Change | Becomes "Sources" (§6) + "Library": rescan, frequency, excluded items | none |
| Artwork | Wi-Fi only (`artwork_wifi_only`), Local only (`artwork_local_only`), Clear cache (confirm), Download all (starts `ArtworkDownloadService`), Media session artwork (`media_session_artwork`) | Change | Fold into "Library" as an Artwork group | none |
| Widgets | Background opacity slider (`widget_background_opacity`), live | Change | Move to "Appearance"; also offer it from the widget's configure activity later | none |
| Playlists | Ignore duplicates (`playlist_ignore_duplicates`) | Drop | Snackbar "Add anyway" replaces it (owner decision 8) | none |
| App info | View changelog, Show changelog on launch, Open-source licences | Change | "About": version, What's new, licences, rate, contact | none |
| Privacy | Crash reporting (`pref_crash_reporting`, default off), Firebase Analytics (`pref_firebase_analytics`, default off) | Change | Crash reporting default on; analytics per owner decision 3; explain Remote Config dependency (below) | none |
| Debug | File logging (`pref_file_logging`), Copy debug logs (size-guarded) | Keep | Under About > Advanced. Delete `DebugPreferenceFragment`'s dead `pref_crash_reporting` restart dialog (key isn't in its XML) | none |

- **Settings bottom sheet (`BottomDrawerSettingsFragment`) — Drop (contested).** Four entries:
  Shuffle all (error if empty library), Sleep timer, Equalizer, Settings. Each has a better home:
  Shuffle all on Library/Home, Sleep timer and Equalizer on Now Playing, Settings as a top-bar
  action on Home and Library. This departs from app-shell decision 5 (keep the sheet for parity);
  see owner decision 1.
- **Debug drawer** (debug builds only; `DebugDrawerFragment` + live `LoggingFragment` in the debug
  `activity_main.xml`) — Change: `activity_main.xml` is deleted in shell slice 1, so give it a
  debug-only Settings entry "Live log" or a debug notification. Maestro: none.

## 8. Changelog, trial and purchase

Decided model (owner, 2026-09-25; detail in [`monetisation.md`](../product/monetisation.md)): **no
playback penalty, no nag dialogs.** A 14-day, no-card trial starts at the first server connection;
the paywall appears only at add-server, trial end and Settings > S2 Pro. All five legacy product
IDs are grandfathered to Pro forever; new server downloads need Pro, existing downloads keep
playing. `:android:trial` (#380) has landed with the penalty code deleted, and `ServerAccessGate`
is wired in: adding a server after the trial, resolving a server song for playback (local player,
Cast and Auto alike, in `AggregateMediaInfoProvider`) and downloading from a server all open the
Compose paywall (`ui/screens/paywall/`), which Settings > S2 Pro opens too.

| Flow | Today | Verdict | Redesign |
|---|---|---|---|
| Changelog | Bottom sheet on launch when the version changed and `changelog_show_on_launch`; newest entries expanded; its own "show on launch" switch; also from App info | Change | No launch popup: a dismissible "What's new in 2026.10" card at the top of Home, full list under About |
| Server trial | No server-scoped trial exists; the whole app degraded instead, via the penalty removed below | Decided: new | A 14-day, no-card trial starts at the first server connection, recorded server-side on the existing device backend so reinstalling doesn't reset it. Every current non-payer gets one fresh trial at cutover |
| Trial nag dialog / ring | Dialog every 3 days in Trial, daily when Expired (`last_viewed_trial_dialog`); days-left ring in the Library toolbar and collapsed Queue header | Drop | No nag dialogs. A trial chip in the Library top bar for the last 3 days of the trial only; the paywall itself surfaces only at add-server, trial end and Settings > S2 Pro |
| Expiry penalty | `TrialInitializer` raised playback speed by 2 %/day after expiry, capped at 1.5× | **Dropped** (owner decision 6, superseded) | No penalty, no reduced quality. At trial end, remote libraries stay visible and browsable with a small lock; tapping play on a remote song opens the paywall and remote queue items are skipped with a snackbar. Local playback, settings and downloaded songs are untouched |
| Grandfathering | Five products (`s2_subscription_full_version_monthly/_yearly/_yearly_low`, `s2_iap_full_version`, `_low`) each independently unlock everything | Decided | Any of the five legacy SKUs grants Pro forever, whichever the user holds; legacy subscribers keep their existing renewal price. The products stop being sold but stay in the entitlement set indefinitely |
| Downloads | `:android:downloads` has no UI; nothing is gated | Change | New downloads from a connected server require Pro; songs already downloaded before or during Pro keep playing after a trial or subscription ends |
| Purchase | Lists Play Billing products from a hardcoded SKU list (monthly, yearly, lifetime; `_low` variants by Remote Config `pricing_tier`) | Change | One modal sheet, three Expressive plan cards: Lifetime ($14.99, preselected, "Best value"), Annual ($5.99/yr), Monthly ($1.49/mo) |
| Thank you | Once after purchase (`thank_you_dialog_viewed`), auto-closes after 8 s | Change | Snackbar or a one-off card; no dialog. Existing legacy-SKU holders get a one-off "You already own S2 Pro" message instead |
| Promo code | Hidden: five taps on the trial dialog icon in 2 s → email prompt → `PromoCodeService` → `PromoCodeDialogFragment` (shown by `LibraryFragment`, which owns the listener) | Change | Made visible in Settings > S2 Pro (the five-tap easter egg is dropped); owned by the paywall sheet, not `LibraryFragment` |
| Review prompt | Play in-app review ≥7 days after purchase, at most every 30 days | Keep | Unchanged, from `ShellViewModel` |
| Crash nag | Alpha/beta only, once, if crash reporting is off | Drop | Crash reporting defaults on |

Note: Remote Config (trial length, pricing tier, snowfall) only refreshes when Firebase Analytics
is enabled, so the analytics decision changes pricing and trial behaviour too.

Maestro: `paywall-settings.yaml` opens the paywall from Settings > S2 Pro (a debug build is always Pro, so it
shows the Pro status); the other states are covered by the Roborazzi recordings in `docs/design/paywall/`.

## 9. System surfaces (unaffected)

- **Activity intents — Keep.** `MEDIA_PLAY_FROM_SEARCH`, `VIEW` of `audio/*` (plays via the media
  session), `MUSIC_PLAYER`, `APP_MUSIC` category. Unchanged by the shell.
- **Shortcut — Keep.** One dynamic "Toggle playback" shortcut via `ShortcutHandlerActivity`, which
  only starts the service. Opportunity: add "Shuffle all" and "Search" static shortcuts.
- **Widgets — Keep, unaffected.** Glance `WidgetProvider41`/`42` with Row/Card/Split/Hero layouts,
  controls through `PlaybackService` intents. They read only `widget_background_opacity` from the UI
  side, which moves screens but keeps its key.
- **Android Auto and Cast — Keep, untouched.** Both live in `:android:playback`. The only UI touch
  point is the Cast route button in Now Playing, which must survive into the Compose player
  (`MediaRouteButton` in an `AndroidView`, or the Cast Compose button).
- **Snowfall — Keep.** Remote Config `snow_forecast` easter egg; becomes an `AndroidView` overlay in
  slice 1, a Compose `Canvas` later.

## Owner decisions

All 12 taken as written on 2026-09-25 (epic #382); each can still be revisited.

1. **Settings bottom sheet. Decided: drop it** (each entry has a better home, §7); this
   supersedes app-shell decision 4 ("Fourth nav item" — this doc previously mis-cited it as
   decision 5). Settings then sits as a top-bar action, not a nav item.
2. **Where the equalizer lives. Decided: a "Playback & sound" screen** reachable from Now Playing
   overflow and Settings, merging EQ, ReplayGain, pre-amp and USB DAC output.
3. **Usage analytics. Decided: (c) ask once, later, in a small non-blocking prompt** (e.g. after
   the tenth play). This is a legal question (GDPR/ePrivacy consent for non-essential analytics,
   UK PECR); get advice before shipping. Crash reporting on by default is also worth a one-line
   check under the same advice.
4. **Home's content. Decided: Home = empty state + "What's new"/trial cards + carousels** (recent,
   added, most played, something different); move History/Latest/Favorites buttons to Playlists'
   auto-playlist row.
5. **Nav bar items. Decided: Home and Library as tabs, Search as a top-bar `SearchBar`** on both,
   leaving a two-item bar (or three with Playlists promoted from a Library tab). Needs a call with
   app-shell §2.
6. **Trial expiry speed penalty. Superseded: no penalty.** The mechanism is dropped entirely, not
   softened or shown honestly — see the decided monetisation model in §8 and
   [`monetisation.md`](../product/monetisation.md). Enforcement moves from a whole-app playback
   penalty to server access itself (locked remote items, paywall on tap).
7. **Smart playlists. Decided: keep the built-ins** (Recently added, Most played, plus History
   and Favorites merged in as "auto playlists") and don't build user-defined rules now.
8. **Duplicate-song handling. Decided: an "Add anyway" snackbar**, dropping the
   `playlist_ignore_duplicates` setting and the Playlists settings screen.
9. **Mini player swipe. Decided: horizontal swipe to skip** (matches Now Playing's artwork swipe);
   vertical swipe down stays "no hide" per app-shell decision 3.
10. **Offline downloads UI. Decided: in scope for the redesign**, as a per-server toggle and a
    "Download" action in the shared actions sheet; not a parity blocker. Downloading from a server
    needs Pro, but a song already downloaded is never taken away (§8).
11. **Exclude confirm. Decided: immediate exclude with Undo everywhere** (today it is confirm in
    half the screens and silent in the other half).
12. **Changelog on launch. Decided: drop the auto-popup and its switch** in favour of a Home card.

## Parity checklist (tick before the first post-freeze release)

- [x] Library tabs: Songs, Albums, Artists, Genres, Playlists, Folders (opt-in); reorder and hide tabs; last tab restored
- [x] Songs sort ×6, Albums sort ×4 incl. Random, Genres sort ×2, Playlists sort ×2
- [x] Album and artist list/grid toggle, persisted
- [x] Fast scroller with section popup on every long list
- [x] Multi-select on Songs, Albums, Artists, Playlist detail; back clears selection first
- [x] Batch Add to queue, Add to playlist, Edit tags
- [ ] Album detail: disc groups, Shuffle, Queue, Play next, Add to playlist, Edit tags
- [ ] Artist detail: inline album expand, Play/Shuffle all, Shuffle albums, Play next, Edit all tags
- [x] Genre detail actions at genre, album and song level
- [x] Playlist detail: 7 sorts + descending, drag reorder (custom), Remove, Rename, Clear, Delete, Export m3u
- [ ] Auto playlists: Recently added, Most played, History, Favorites (heart in Now Playing)
- [x] Folders: drill down, back one level, recursive Play/Shuffle/Queue/Playlist
- [x] Song actions: Play next, Add to queue, Add to playlist, Song info, Exclude, Edit tags, Delete, Remove
- [x] Create, rename, clear, delete playlists; duplicate-song handling
- [x] Tag editor: all 11 fields, batch mode, provider gating
- [x] Song info: all 17 fields
- [ ] Search: artists/albums/songs, fuzzy ranking, filter chips persisted, shared-element open
- [ ] Home sections and Shuffle all
- [ ] Mini player: progress, play/pause, skip, long-press seek
- [ ] Now Playing: artwork swipe skip, shuffle, repeat ×3, seek, long-press seek, audiobook seek buttons, artist/album links, Cast, lyrics, favorite, clear queue
- [ ] Queue: tap to play, reorder, remove, Play next, scroll to current, save as playlist, clear
- [x] Sleep timer: presets, play to end of track, live countdown, stop
- [ ] EQ: on/off, presets, custom bands, ReplayGain mode, pre-amp, frequency response
- [ ] USB DAC direct output (API 34+), Keep shuffle on new queue
- [ ] Permission in context on API 23–32 (`READ_EXTERNAL_STORAGE`) and 33+ (`READ_MEDIA_AUDIO`), incl. permanent denial
- [ ] Local scan without folder picking; optional include/exclude folders; revoked grant surfaced
- [ ] Rescan now, rescan frequency, last scan date; scan progress and failures visible
- [ ] Excluded items: view, restore one, clear all
- [ ] Jellyfin, Emby, Plex: connect, edit, remember password, errors with retry, remove source (cleans queue and library), report playback
- [ ] Theme, pure black, accent, dynamic colour, Home-or-Library on launch
- [ ] Artwork: Wi-Fi only, local only, clear cache, download all, media session artwork
- [ ] Widget opacity, both widget sizes still update
- [ ] Crash reporting and analytics toggles; Remote Config still refreshes as decided
- [ ] File logging, copy logs; debug live log reachable in debug builds
- [ ] Changelog reachable; licences
- [ ] Purchase (Lifetime, Annual, Monthly plan cards), thank-you, promo code path (visible in Settings > S2 Pro), review prompt
- [x] Grandfathering: all 5 legacy product IDs (monthly, yearly, yearly_low, iap_full_version, iap_full_version_low) still grant Pro
- [ ] Server trial: starts on first server connection, 14 days no card, trial chip in Library top bar for the last 3 days
- [x] Paywall entry points: add-server (before connecting), trial end (on tapping play on a remote song), Settings > S2 Pro
- [ ] Intents: play-from-search, VIEW audio file, default music app; Toggle playback shortcut
- [ ] Android Auto browse and playback; Cast connect from Now Playing
- [ ] Every `support/maestro` flow ported to Compose test tags and green

## Spikes needed

1. **KTagLib through MediaStore content URIs, API 23–36.** Discover via `MediaStore.Audio.Media`,
   open `content://media/external/audio/media/<id>` with `openFileDescriptor` + `detachFd`, read
   all tags (reuse `MediaStoreReplayGainReader`'s pattern). Measure: full-library time vs today's
   SAF walk on 10k tracks; SD-card (secondary volume, `VOLUME_EXTERNAL` vs named volumes on 29+)
   files; files MediaStore has not indexed (`.nomedia` folders, fresh copies, unsupported
   extensions such as `.opus`/`.dsf` on older APIs) and whether a `MediaScannerConnection.scanFile`
   nudge or an optional SAF include covers them; scoped storage on 29 (`requestLegacyExternalStorage`)
   vs 30+; OEM MediaStore quirks (missing `DISC_NUMBER`, stale rows). Pass: tag parity with today's
   TagLib provider on the emulator fixtures plus one real SD-card device.
   **Result: go for API 30+** ([`spike-taglib-mediastore.md`](spike-taglib-mediastore.md)).

   | | API 36 ATD | API 37 google_apis |
   |---|---|---|
   | Read via content URI | 102/102, 0 failures | 102/102, 0 failures |
   | Tags, non-ASCII, ReplayGain | same as the SAF build | same as the SAF build |
   | Artwork | read (run a) | shown in Albums |
   | Import time, SAF walk → MediaStore | ~1.0 s → ~0.25 s | ~2.3 s → ~0.3 s |
   | `.nomedia`, unrecognised `.wv` | skipped | skipped |
   | Secondary volume | virtual SD read (run a) | not run |

   Folder mapping: the #207 SAF tree grants become include folders (`primary:Music` →
   `/storage/emulated/0/Music`; no grants means the whole device); folder excludes come from
   Settings > Sources (#379); the per-song exclude list stays per song, keyed by path. Still open:
   API 23–29 and a real SD card (in `docs/testing/device-checks.md`), m3u import without a grant,
   an optional SAF "Add folder" for `.nomedia` and unrecognised files, path migration (spike 3).
2. **Writes without SAF.** Tag editing and Delete for MediaStore-discovered files:
   `MediaStore.createWriteRequest` / `createDeleteRequest` on 30+, `RecoverableSecurityException`
   on 29, plain file access on 23–28. Decide whether tag editing requires a folder grant below 30.
3. **Migration of existing users.** Users on MediaStore mode or TagLib with SAF trees: map song ids,
   play counts, playlists and excluded items onto the unified provider without losing history.
4. **Ambient scan progress.** WorkManager foreground import with progress surfaced to the Library
   bar and a notification, cancellable, surviving process death.
5. **Plex PIN linking.** Confirm the plex.tv PIN flow works with the existing
   `PlexAuthenticationManager` token storage.
6. **Analytics consent.** Region detection (option b); Remote Config independent of the opt-in.

## Findings outside the redesign (bugs to file)

- `DebugPreferenceFragment` wires a "requires restart" dialog to `pref_crash_reporting`, which is
  not in `preferences_debug.xml`: dead code.
- `AnalyticsPermissionViewModel` starts both switches at `false` instead of reading preferences.
- ~~Tag editor counts silently skipped songs in its "N of M updated" toast.~~ Fixed in #377.
