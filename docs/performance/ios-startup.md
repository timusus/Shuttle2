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

`log show` can't render the Kotlin lines: `OsLogLogger` passes its format string from Kotlin data rather than the
`__oslogstring` section, so the archived entries read `<compose failure>`. `log stream` renders them live, so the method
streams.

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
- `OsLogLogger`'s `<compose failure>` in `log show` (archived Kotlin logs unreadable, sysdiagnoses included).
