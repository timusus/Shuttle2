# iOS cold start: where the time goes

Measured 2026-10-05, before any startup fix, to see where the 1-2 s before Home shows content goes. The instrumentation
stays in the app (Release too); rerun the method below after a change and compare.

## Instrumentation

Everything logs under subsystem `com.simplecityapps.shuttle`, category `Startup`.

- **Swift** (`ios/S2/Platform/Diagnostics/StartupTrace.swift`): signposts for Instruments, plus a notice per milestone
  with its time since the kernel started the process (`kinfo_proc.p_starttime`), so pre-`main` work (dyld, static
  initialisers, UIKit's launch) counts. `S2App.init` start and end, each `AppGraph.initialize` step as a signposted
  interval (`step <name> took N ms`), `ContentView`'s first body, the first frame (the shell's first `onAppear`), and
  for Home, the Library root, each Library category (songs, albums, album artists, playlists, genres) and Search: first
  body, first `onAppear` and first non-loading state. Each milestone logs once per process.
- **Kotlin** (`Logger.tagged("Startup")`, info level): the database's build and first open (`DatabaseProvider`), Home's
  `songCount` and `eventCount` (`ObserveHomeSections`), each `LoadHomeSections` stage, its first emission and the full
  load, and the search index warm-up (songs query, index ready). These carry durations; their log timestamps place them
  on the same timeline.

Kotlin lines reach OSLog through Swift (`KotlinLogSink`, a literal `"\(message, privacy: .public)"` format), so they
render in both `log stream` and `log show`. The measurements below were taken by streaming, before that fix.

## Method

- **Environment:** iPhone 16 simulator, iOS 18.5 (shared lease pool), on the M-series dev Mac. Release configuration
  (`build-framework.sh --release`, `xcodebuild -configuration Release`, bundle `com.simplecityapps.shuttle`). No
  physical iPhone was attached, so there are no device numbers yet; expect a device to be slower in absolute terms.
- **Library:** the leased simulator's own library was empty (0 songs), measured once as a floor. The main numbers use
  an 8,196-song library (the Jellyfin test server's, Room schema 53, migrated to 55 on the discarded warm-up launch),
  copied into the Release app's Application Support with `sqlite3 ".backup"`. It has no play history, so Home takes
  its cold-start path (no Around This Time or Heavy Rotation) and Jump Back In is empty.
- **Runs:** install, one discarded launch, then 5 cold launches: `simctl terminate`, start the stream, `simctl launch`,
  wait 10 s, stop the stream. Start tab Home, so Library and Search weren't on screen at launch and have no numbers.
- **Collecting:**

  ```bash
  xcrun simctl spawn <udid> log stream --level debug --style compact \
    --predicate 'subsystem == "com.simplecityapps.shuttle" AND category == "Startup"'
  ```

  In Instruments, the os_signpost instrument with the same subsystem shows the `AppGraph.initialize` intervals.
- **Ablations** were temporary, uncommitted edits, each measured the same way and reverted: (a) no
  `librarySearchIndex.warmUp()`; (b) crash reporting and analytics consent off (`pref_crash_reporting`,
  `pref_firebase_analytics` = NO in the app's defaults); (c) `homeViewModel.onVisibilityChanged(true)` in `HomeModels.init`
  instead of `onAppear`; (d) the Room database opened (`SELECT 1` on a reader connection) on a background coroutine as
  soon as `IosPersistenceModule` builds it.
- **Noise:** the simulator shares the Mac. The first 8k baseline ran with more host load than the rest (every milestone
  is 20-60 ms later and Home content 220 ms later); the rerun matches the ablations' conditions, so the comparisons use it.

## Results (8,196 songs unless noted)

Median (min–max) in ms over 5 cold launches. Rows marked *duration* are how long that step took; every other row is ms since the kernel started the process.

| Milestone | Baseline (rerun) | Baseline (first run) | (a) no search warm-up | (b) telemetry consent off |
|---|---|---|---|---|
| Process start → S2App.init | 263 (261–272) | 285 (269–300) | 273 (261–542) | 262 (255–274) |
| AppGraph: audio engine (duration) | 113 (109–115) | 133 (114–143) | 124 (107–126) | 109 (106–125) |
| AppGraph: IosAppDependencies (duration) | 136 (131–140) | 173 (138–179) | 155 (127–159) | 130 (127–150) |
| AppGraph: telemetry start (duration) | 58 (56–60) | 66 (62–71) | 64 (57–73) | 0 (0–0) |
| S2App.init done | 473 (466–482) | 539 (481–562) | 495 (469–777) | 402 (398–442) |
| ContentView first body | 520 (512–524) | 601 (531–626) | 554 (513–832) | 445 (439–488) |
| First frame (shell onAppear) | 541 (533–545) | 632 (553–658) | 587 (533–852) | 467 (460–509) |
| Home first body | 642 (619–654) | 761 (648–784) | 677 (609–945) | 548 (540–614) |
| Home onAppear | 656 (630–683) | 788 (660–795) | 688 (619–955) | 557 (551–625) |
| **Home first content** | 846 (807–1001) | 1066 (829–1126) | 1045 (966–1317) | 765 (747–878) |
| Home songCount (duration) | 103 (72–222) | 142 (79–188) | 267 (257–278) | 112 (103–133) |
| Home eventCount (duration) | 2 (1–4) | 36 (1–44) | 1 (1–2) | 2 (1–6) |
| LoadHomeSections first emission (duration) | 14 (12–18) | 22 (13–55) | 13 (12–14) | 15 (12–19) |
| All Home sections loaded | 1121 (1097–1139) | 1306 (1212–1345) | 1153 (1051–1411) | 1018 (976–1225) |
| DB open after build began (duration) | 2 (2–6) | 2 (2–3) | 2 (2–5) | 2 (2–2) |
| DB opened | 402 (394–410) | 459 (411–480) | 422 (395–699) | 390 (386–424) |
| Search warm-up: songs query (duration) | 345 (307–369) | 338 (315–345) | — | 355 (338–430) |
| Search index ready | 1259 (1222–1300) | 1399 (1272–1424) | — | 1201 (1186–1418) |

| Milestone | (c) Home visible at init | (d) eager background DB open | (a)+(b) together | Empty library |
|---|---|---|---|---|
| Process start → S2App.init | 265 (261–268) | 266 (260–269) | 270 (266–1911) | 270 (263–272) |
| AppGraph: audio engine (duration) | 112 (106–121) | 109 (108–114) | 120 (113–168) | 118 (107–131) |
| AppGraph: IosAppDependencies (duration) | 137 (126–149) | 133 (130–136) | 149 (134–206) | 146 (138–162) |
| AppGraph: telemetry start (duration) | 59 (58–62) | 58 (55–59) | 0 (0–0) | 56 (53–58) |
| S2App.init done | 476 (462–483) | 469 (457–481) | 433 (418–2080) | 482 (462–497) |
| ContentView first body | 525 (505–540) | 515 (503–528) | 492 (461–2135) | 528 (506–551) |
| First frame (shell onAppear) | 547 (526–564) | 536 (525–550) | 520 (480–2155) | 555 (525–579) |
| Home first body | 661 (620–690) | 631 (612–642) | 615 (560–2242) | 641 (599–663) |
| Home onAppear | 678 (630–701) | 655 (622–665) | 625 (570–2251) | 651 (609–672) |
| **Home first content** | 875 (782–990) | 830 (803–877) | 1003 (892–2605) | 742 (675–768) |
| Home songCount (duration) | 94 (68–180) | 86 (59–104) | 278 (250–300) | 1 (1–4) |
| Home eventCount (duration) | 1 (1–3) | 4 (1–6) | 1 (1–3) | — |
| LoadHomeSections first emission (duration) | 16 (14–19) | 14 (13–43) | 13 (12–16) | — |
| All Home sections loaded | 1164 (1073–1214) | 1092 (1016–1124) | 1112 (972–2698) | — |
| DB open after build began (duration) | 2 (2–2) | 2 (2–2) | 2 (2–2) | 2 (2–2) |
| DB opened | 404 (391–416) | 400 (392–406) | 419 (405–2066) | 419 (402–432) |
| Search warm-up: songs query (duration) | 333 (305–416) | 335 (310–353) | — | 2 (2–3) |
| Search index ready | 1310 (1220–1385) | 1249 (1233–1258) | — | 480 (461–495) |


## Where the time goes (baseline rerun, Home content at ~846 ms)

1. **Before `S2App.init`: ~263 ms.** dyld, static initialisers and UIKit launch for a binary with the whole Kotlin
   framework linked in. Nothing in the app's code runs yet; this is the floor to beat with a smaller or lazier binary.
2. **`AppGraph.initialize` on the main thread: ~210 ms**, of which the audio engine (`MusicPlaybackController` in
   `IosAppDependencies`) is ~113 ms and Sentry + PostHog start-up ~58 ms; the other steps are under 2 ms each, the graph
   itself is lazy (0.2 ms) and the search warm-up only launches a coroutine. Turning telemetry consent off brought Home's
   content ~80 ms earlier, more than the step itself: the SDKs keep working off the main thread after `start()`.
3. **Home's appear to content: ~190 ms.** `songCount` is `getSongs(SongQuery.All()).map { it.size }`: it loads all
   8,196 songs to count them (~100 ms while the search warm-up's identical query is in flight, ~270 ms without it). It
   waits ~65 ms after `onAppear` to start (ViewModel flow start-up and dispatch); `eventCount` is ~1 ms and the first
   sections ~14 ms. Genre Picks is the slowest section (~260 ms) but lands after first content.
4. **SwiftUI to the first Home body: ~170 ms.** `S2App.init` done → first frame ~68 ms, first frame → Home's first
   body ~100 ms (the tab view and navigation stacks).
5. **The search index warm-up competes in the background:** the songs query takes ~345 ms from ~460 ms and the index is
   ready at ~1.26 s. Skipping it made Home *later*, not earlier: Home's `songCount` then runs the full songs query on
   its own.

Ruled out: the database open (built ~0.1 ms, opened ~2 ms after, during `AppGraph.initialize`, so ablation (d) changed
nothing), Home's visibility timing (ablation (c) moves the start ~15 ms; its median is within the noise), `eventCount`
and the per-item resume-point loop (Jump Back In is empty here; recheck with play history).

## Open questions

- Device numbers, and a library with play history (Jump Back In's resume points, Around This Time, Heavy Rotation).
- Library and Search first content: relaunch with each as the start tab (Settings) and rerun.

## Physical device (Debug build)

Measured 2026-10-05 on the owner's iPhone 16 (iOS 26), Debug configuration (`com.simplecityapps.shuttle.dev`, `S2.debug.dylib`), with the real library: 8,196 Emby songs, 190 play events, 14 resume points, telemetry consent on. Debug is what the owner runs day to day.

**Method:** `devicectl device process terminate`, `pymobiledevice3 syslog live --tunnel <udid> -m S2` streamed to a file, then `devicectl device process launch --device <udid> com.simplecityapps.shuttle.dev -- <args>` (the `--` is needed or devicectl parses `-pref_…` as its own option). 5 cold launches per variant, one 22 s capture each. Launch arguments only (`-pref_show_home_on_launch '<false/>'`; `-pref_crash_reporting '<false/>' -pref_firebase_analytics '<false/>'`), nothing changed on the phone. `syslog live` doesn't print the category and drops some lines under load (n below is the number of runs in which a milestone was captured). It shows the Kotlin `Database` and `Search index` lines but not the Home section lines (`songCount`, `eventCount`, `LoadHomeSections`).

Median (min–max) in ms; *duration* rows are step lengths, the others ms since the kernel started the process.

| Milestone | Home start tab | Library start tab | Telemetry off (Home) | Sim Release (baseline) |
|---|---|---|---|---|
| Process start → S2App.init | 24 (21–86) | 28 (21–150) | 32 (26–36) | 263 |
| AppGraph: audio engine (duration) | 44 (37–96) | 43 (38–67) | 45 (45–121) | 113 |
| AppGraph: dependencies (duration) | 82 (78–173) | 73 (68–153) | 80 (72–149) | 136 |
| AppGraph: telemetry start (duration) | 38 (37–52) | 39 (37–52) | 0 (0–0) | 58 |
| AppGraph: playback system (duration) | 7 (5–8) | 10 (6–11) | 17 (11–20) | <2 |
| S2App.init done | 150 (145–329) | 153 (144–377) | 142 (140–194) | 473 |
| First frame | 198 (194–425) | 202 (191–480) | 187 (168–240) | 541 |
| Home body / onAppear | 231 / 241 | — | 223 / 225 | 642 / 656 |
| **Home first content** | never logged (see below) | — | never logged | 846 |
| Library body / onAppear | — | 234 / 247 | — | — |
| **Library first content** | — | 3531 (3521–4042) | — | — |
| Album artists first content | — | 3839 (3729–4489) | — | — |
| DB open after build began (duration) | 15 (14–20) | 12 (7–25) | 7 (4–10) | 2 |
| Search warm-up: songs loaded (duration) | 4547 (4244–4571) | 3768 (3410–6804) | 4434 (4334–4607) | 345 |
| Search index ready (duration) | 5867 (5588–5976) | 8064 (7657–8613) | 5831 (5687–5871) | ~800 after songs |

Telemetry off: `telemetryStartup` 38 → 0 ms, first frame ~11 ms earlier (198 → 187). Smaller than the simulator's ~75 ms, and the search warm-up is unchanged.

### Findings

- **Home first content never fires.** In all 15 launches `home body` and `home appear` log, but `home content` doesn't, and no Home Kotlin lines appear in the 22 s window. The app had been left on an artist detail page (Bicep) and restores onto it, so Home sits under the pushed screen. Whether Home is still loading or is just not started while hidden is not settled; it needs a clean tab state to measure (see open questions).
- **Pre-main and `AppGraph.initialize` are not the problem on device.** The process reaches `S2App.init` in ~25 ms and the first frame in ~200 ms, about a third of the simulator Release time. Caveat: the first launch after install and any pre-warmed process may hide cost in `p_starttime`.
- **The wait is the 8,196-song query.** `getSongs(SongQuery.All())` takes ~4.5 s in Debug on the device against 345 ms in simulator Release. Library's first content lands at ~3.5 s, in step with the warm-up's songs query finishing.

### Ranked causes of the 1-2 s (and worse) before content

1. **The all-songs query in Debug, ~3.4-4.6 s** (13× the simulator Release time). Library content waits on it (~3.5 s); the warm-up and anything else that loads every song shares it. Debug Kotlin/Native and Room are far slower than Release, so confirm on a Release build before optimising.
2. **Search warm-up after the songs query, ~1.3-3.8 s more** (index ready at 5.6-8.6 s), competing for the same cores when the user opens Library or Search.
3. **Main-thread `AppGraph.initialize`, ~130 ms** (audio engine ~44 ms, Sentry + PostHog ~38 ms, playback system ~7-17 ms). Telemetry off saves ~11 ms of first frame. Small next to 1 and 2.

### Open questions

- Home content on device from a clean Home tab (the app was parked on an artist page); Home Kotlin timings need `log stream`-class capture, which `syslog live` doesn't give for the Home lines.
- ~~A Release build on the device~~ Done, see "Physical device (Release build)".

## Physical device (Release build)

Measured 2026-10-05 on the same iPhone 16 and library (8,196 Emby songs, 190 play events, 14 resume points, telemetry on) with a Release build of HEAD installed as `com.simplecityapps.shuttle.dev`, so the owner's data is kept. Same method as the Debug section, except each run kills the previous process first (`devicectl device process terminate --pid <pid> --kill`; `--bundle-identifier` isn't accepted, and a launch without a kill only foregrounds the old process). 5 cold launches per variant, one 22 s `syslog live` capture each, launch arguments only (`-pref_show_home_on_launch '<false/>'` for Library). Home started on its root tab this time, so Home content is measured. Unlike the Debug run, `syslog live` captured every Kotlin Startup line, including the Home sections. The only gaps are `firstFrame` and `contentViewBody` in one run each (n=4). Median (min–max), ms; *duration* rows are step lengths, the others ms since process start.

| Milestone | Home start (Release) | Library start (Release) | Debug (Home / Library) |
|---|---|---|---|
| Process start → S2App.init | 22 (19–29) | 29 (20–35) | 24 / 28 |
| AppGraph: audio engine (duration) | 46 (44–48) | 45 (42–95) | 44 / 43 |
| AppGraph: dependencies (duration) | 77 (73–84) | 78 (74–126) | 82 / 73 |
| AppGraph: telemetry start (duration) | 40 (36–49) | 34 (33–46) | 38 / 39 |
| AppGraph: playback system (duration) | 12 (7–62) | 8 (7–12) | 7 / 10 |
| S2App.init done | 153 (150–214) | 156 (142–209) | 150 / 153 |
| First frame | 210 (195–260) | 214 (188–260) | 198 / 202 |
| Home body / onAppear | 252 / 257 | — | 231 / 241 |
| **Home first content** | **731 (690–792)** | — | never logged |
| Library body / onAppear | — | 240 / 252 | — / 234 / 247 |
| **Library first content** | — | **594 (549–741)** | 3531 |
| Album artists first content | — | 679 (662–768) | 3839 |
| DB build / open after build (duration) | 0.2 / 17 (11–22) | 0.2 / 14 (6–17) | 15 / 12 |
| Home: songCount (duration) | 200 (184–226) | — | — |
| Home: eventCount (duration) | 0.8 | — | — |
| Home sections: Rediscover / Recently added / Around this time | 3 / 32 / 83 | — | — |
| Home sections: Jump back in / Heavy rotation / Genre picks / Resume points | 116 / 111 / 112 / 116 | — | — |
| Home: first sections = sections loaded (duration) | 249 (158–273) | — | — |
| **Search warm-up: songs loaded (duration)** | **354 (335–370)** | 433 (410–539) | 4547 / 3768 |
| Search index ready (duration) | 805 (781–883) | 773 (726–982) | 5867 / 8064 |

The section durations are measured from the start of the sections load, so they overlap (they run concurrently); only the longest sets "sections loaded".

Debug → Release for the all-songs query: **~13× faster** (4547 → 354 ms Home; 3768 → 433 ms Library). Library first content improves ~6× (3531 → 594 ms), Home's search index ready ~7× (5867 → 805 ms). Release on the phone is in the same range as the simulator Release baseline for the songs query (345 ms) and first content (846 ms for Home, which is slower there).

### Is pre-main credible?

Partly. `StartupTrace.processStart` reads `kp_proc.p_starttime` from `sysctl(KERN_PROC_PID)`, the kernel's timestamp when the process was created, and subtracts it from `Date()` (wall clock), so it covers dyld, static initialisers and the Swift runtime up to `S2App.init`. 20-30 ms is plausible for a Release binary with no `S2.debug.dylib`, and it's stable across 10 launches (19-35 ms). It's a lower bound, not the user-visible launch cost: anything before the process exists (the tap, SpringBoard/RunningBoard, launchd spawning the process, the app-launch snapshot) isn't counted. The simulator's 263 ms is mostly the simulator's own spawn cost and a slower dyld, not a like-for-like figure. Don't read 22 ms as "launch takes 22 ms"; read it as "our binary's load cost is small". Instruments' App Launch template is the way to measure the full launch.

### Ranked causes that still matter in Release

1. **Home content waits on sections and the song count, ~730 ms to first content** (first frame at ~210 ms, so ~520 ms of empty Home). `songCount` takes ~200 ms and the sections load takes ~250 ms after it; the slowest sections (Jump back in, Heavy rotation, Genre picks, Resume points, ~110 ms each) set the end. Rendering the sections that are ready instead of waiting for all of them would help; `songCount` could be a cheap `COUNT(*)` or cached.
2. **All-songs query and search warm-up, ~350-430 ms for the songs, ~800 ms to index ready.** It runs at launch regardless of tab, and competes with Home/Library for cores. Library first content (~590 ms) lands soon after it finishes (~430 ms). Smaller than in Debug, but the largest single step. A lighter first-page query for Library would remove the dependency.
3. **Main-thread `AppGraph.initialize`, ~130 ms before first frame** (audio engine ~46 ms, dependencies ~77 ms, telemetry start ~35-40 ms, playback system ~8-12 ms). Pre-main is ~22 ms, so `S2App.init` done at ~153 ms and first frame ~210 ms are mostly this. Moving the audio engine and telemetry off the first frame's critical path would give ~80 ms.

## Changes made (2026-10-05)

Not yet measured on the device; the effects below are expected from the measurements above, not observed.

- **Resume points in one query.** Home's Resume points section read each context's resume point with its own query
  (and its own album-index check), serially after Jump back in. `PlayHistoryRepository.resumePointsFor` reads them
  all in one query, so that section no longer adds a round trip per item before the first sections emit.
- **Search index warm-up after the first content.** It no longer starts in `AppGraph.initialize`; it starts when Home
  or a Library list first shows content, or when Search opens, whichever is first (`AppGraph.warmUpSearch()`). Its
  all-songs query no longer competes with the first content (cause 2). Search opened before it has run builds the
  index on demand, as it always could.
- **PostHog after the first frame.** `TelemetryConsentGate` starts crash reporting and analytics separately. iOS
  applies the crash reporting choice (Sentry) in `AppGraph.initialize` as before, and the analytics choice (PostHog's
  set-up and super properties) on the main queue after `ContentView`'s first `onAppear`
  (`AppGraph.startAfterFirstFrame()`, logged as step `analyticsStartup`). Expected: PostHog's share of the ~35-40 ms
  telemetry step leaves the first frame's path. Trade-off: PostHog's lifecycle integration only hears
  `didBecomeActive` once it is set up, so a cold launch's "Application Opened" may no longer be captured. Installs,
  updates and later opens still are. Android is unchanged.
- **Instrumentation.** Home's `Startup` timings log at info once per process and at debug after that;
  `StartupTrace.mark` repeats no longer allocate; the database line reads "builder set up" (`build()` only sets Room
  up; the open is the "opened" line).
- **Not changed: the audio engine (~46 ms).** `EngineAudioPlayer` is a constructor dependency of the Kotlin graph, the
  playback system coordinator and the player binding, and `playbackSystem.start()` restores the queue, Now Playing and
  the remote commands at launch. Deferring it would mean a lazy stand-in player and a second start-up order for
  playback, so it stays on the launch path.
- **Not changed here: Home's song count.** The `COUNT(*)` change (#900) landed separately with #882.

## After the changes (Release, iPhone 16, 2026-10-05)

Release build of `51a053f7d` installed as `com.simplecityapps.shuttle.dev`, same library and method as the Release
section (5 cold launches per start tab, `syslog live`, launch arguments only). Median (min–max), ms.

| Milestone | Before | After |
|---|---|---|
| AppGraph: telemetry start (duration) | 40 (36–49) | 25 (22–77) |
| S2App.init done | 153 | 144 (136–212) |
| First frame | 210 (195–260) | 190 (184–272), Library start 182 |
| Home: songCount (duration) | 200 (184–226) | 9 (6–20) |
| Home: first sections (duration) | 249 (158–273) | 224 (214–312) |
| Home: Resume points loaded (duration) | 116 | 80 (63–92) |
| **Home first content** | **731 (690–792)** | **580 (516–609)** |
| **Library first content** | **594 (549–741)** | **719 (634–818)** |
| Search warm-up: songs loaded / index ready (duration) | 354 / 805 | 107 / 419 |

- Home is ~150 ms faster, mostly from the song count (#900) and search warm-up no longer competing for the DB.
- Library got slower (n=5, ranges overlap, so possibly noise). Both start tabs now log
  `Album index of 8196 songs built in ~215 ms` (`LibraryAlbumIndex`), which sits on the first-content path; it wasn't
  logged in the earlier run.
- The audio engine (48 ms) and dependencies (76 ms) still run on main before the first frame.
- Album index (#963): the build was already launched on `Dispatchers.IO`, but only when Metro first made
  `LibraryAlbumIndex`, which on iOS was Home's or Library's own first injection, so the ~215 ms ran on the first-content
  path. `AppGraph.initialize` now reads `albumIndexProvider` (step `albumIndex`), so the build starts at launch and
  first content awaits the in-flight build. Not yet re-measured: the simulator library is too small to show the build,
  so the 8,196-song numbers need an iPhone run (compare Library first content against 719 ms).
