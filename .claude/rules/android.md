---
paths:
  - "android/**"
---

# Android

S2 Music Player — Android app for local music playback and streaming via Jellyfin, Emby and Plex.
Kotlin, Compose + ViewModel (UDF) in a single-activity Navigation 3 shell. Build
commands, module layout and code style live in the root `CLAUDE.md` — this file covers testing
judgement, git scope discipline, and the desktop emulator, which do not belong there.

**Use the project's skills proactively** — `/run-android` to build/install/launch, `/commit` when
changes are ready, `/check` after making changes, `/verify-ui` after Compose UI changes,
`/android-coroutine-testing` when writing coroutine tests, `/delegate-verbose` before a Gradle test
or lint sweep.

## Testing

**Bias toward JVM tests (unit tests, Robolectric characterisation tests) over instrumented tests.**
The full Robot/Scenario/Model-factory pattern for Compose characterisation tests is documented in
the root `CLAUDE.md`'s Testing section — this is about *which* test type a change needs.

| Type | Location | When to use |
|------|----------|-------------|
| Unit | `src/test/` | ViewModels, use cases, repositories, mappers, business logic |
| Compose characterisation | `src/test/` (Robolectric) | Compose screens and their ViewModels |
| Instrumented | `src/androidTest/` | Platform integration only — MediaSession/PlaybackService, Android Auto, SAF document access, Chromecast |

Which layer each kind of check belongs in, the measured landing baseline and the speed levers: `docs/testing/strategy.md`.

**The rule for androidTest:** if you're adding one, explain why it can't run on JVM.
Valid: `PlaybackService`/`MediaBrowserServiceCompat` behaviour, real `AudioManager` focus
interaction, SAF tree access. Invalid: "it's a UI change" (Compose screens are Robolectric-testable
via the Robot pattern) or "I want to see the real app".

- **Implementation changes require test updates.** If you change behaviour, update tests to match.
- **Bug fixes start with a failing test.** Reproduce the bug in a test before fixing it.
- **Room version bumps commit the new schema JSON and extend the migration tests** in
  `android/mediaprovider/local/src/test/.../data/room/migrations/` (`MigrationTestHelper` under
  Robolectric) — add the new migration to `ALL_MIGRATIONS` and let the full-chain test cover it.

## Git Conventions

- **Never commit unrelated changes to an existing feature branch.** If the current branch is for
  feature X and you're asked to do task Y, start a new worktree branch (see the root CLAUDE.md's
  Branch Conventions). Even a "simple" rename or config change gets its own branch.

Commit message format, module scopes and changelog upkeep are in the root `CLAUDE.md` and
`.claude/rules/changelog.md`.

## Desktop Emulator (WSL Box)

Prefer a headless Pixel 9 Pro AVD on the owner's desktop (WSL2, KVM) over a local AVD: the 32 GB
Mac starves a local emulator whenever it's loaded (Xcode, other sessions). Launcher:
`support/scripts/remote-emu.sh` (`status` / `start [N] [--api 36|37]` / `env` / `install [N] [apk]` /
`serial [N]` / `reconnect [N]` / `reset [N]` / `ui-prep [N]` / `tap-text` / `dump-texts` /
`seed-music [dir]` / `lockscreen on|off` / `stop [N|--all]`).

Default lane image is the full `google_apis` API 37 image, right-sized to 2560 MB / 3 cores --
`screencap`/Maestro screenshots need it (#390: the lighter `android-36 google_atd x86_64` ATD image
renders solid black frames for both, likely because its stripped-down system image lacks the
hardware composer path `screencap` reads from under swiftshader). `start --api 36` opts into that
ATD image instead (no Play services, fewer system apps, noticeably less idle CPU) for lanes that
never take a screenshot. `start` in a session that
already holds a live lane reuses it; an explicit `--api` naming a different image than the one
running fails with instructions instead of rebooting it -- `stop`, then `start --api N`. `start`
runs `ui-prep` itself once the device has booted, so a raw `start` gets animations disabled
without a separate call; if that fails it warns and the lane is still ready. A lease is reclaimed
on the next `start`/pick not just when its console port has no listener, but also when its
recorded owner process is gone from this Mac -- so a headless worker that dies mid-run no longer
strands its lane. The owner is the nearest Claude Code session process above the caller (never
the `claude remote-control` supervisor), or the calling shell outside Claude; a lease with no
recorded owner PID is only ever reclaimed by the "nothing listening" rule.

**If the tunnel drops mid-run** (adb reports "device offline" or "device not found"):
`remote-emu.sh reconnect` re-opens the tunnel for this session's lane without rebooting the
emulator or losing the lease; it's a fast no-op if the tunnel is already healthy. `s2-debug.sh`
and `checks/_lib.sh` call it automatically through their shared `adb_retry` wrapper (one
reconnect, one retry, then a clear failure) — you only need it by hand for a raw `adb` call.
A tunnel failure names the check that failed (`port-busy` with the holder, `ssh-exited`,
`listen-timeout`, or `adb-handshake`: the forward is up but the lane's adbd never answered, after
three connects) and prints the tail of `$TMPDIR/remote-emu/tunnel-N[-adbd].log`, which records
the ssh command, each failed connect, every close and how ssh exited (#342).

**One-shot verification:** `support/scripts/emu-verify.sh` runs start/install/reset/seed, the named
checks or Maestro flows (or the full suite), and stop as a single foreground call -- see the
`emulator-check` skill. The steps below are what it automates; use them directly only for a
one-off command it doesn't cover (a different fixture, a raw `adb` call, leaving the lane up).
`--no-reset` skips the reset step for a fast re-run of the same flow/check on a lane you already
seeded this session (#412) -- keep the default (reset every time) for the landing verification.

**Batch device validation: `emu-verify.sh --suite` once, then read the results file.** For a full
device-check pass (queued batches like #452), run `support/scripts/emu-verify.sh --suite` (or
`--suite --flows <a,b>` for a subset) and read `build/maestro/results.md` -- don't debug flows one
at a time by hand. With no `--flows`, it runs the device smoke set (`smoke.txt`, #450); `--all`
runs every check in the run-all set instead. Each runs under a per-flow timeout (`--flow-timeout`,
default 180s, falling back to a bash watchdog when neither `timeout` nor `gtimeout` is on PATH,
#454), retries a failure once, keeps going past one, and appends a row (flow, pass/fail/timeout/
skip, duration, screenshot, last error) as each finishes -- plus an `interrupted` row for whatever
flow was still running if the run itself gets cut short -- so a run that gets cut short still
leaves a report for every flow, not just the ones that had already finished; #381's two validation
runs (84 and 100 minutes) left none. It exits non-zero if any flow failed or timed out; a `skip`
row (no dialer, no Photos on the ATD image, ...) isn't a failure.

**Standard start state for validation:** a lane that's been reused inherits stale app data and
media from a previous run. Before validating a UI or playback change, reset the lane and reseed
known media instead of hand-rolling ffmpeg + adb push + a manual onboarding pass:

```bash
support/scripts/remote-emu.sh reset               # pm clear the debug app, drop /sdcard/Music/s2-seed
support/scripts/remote-emu.sh install
support/scripts/seed-test-media.sh two-disc --skip-onboarding
adb shell am start -n com.simplecityapps.shuttle.dev/com.simplecityapps.shuttle.ui.MainActivity
```

- `remote-emu.sh reset [N]` clears the debug app's data (`pm clear`) and removes
  `/sdcard/Music/s2-seed` on the lane's serial. Run it before reseeding to avoid mixing fixtures
  from a previous validation run into the new one.
- `seed-test-media.sh <fixture> [--skip-onboarding] [--if-needed]` generates short, tagged mp3/FLAC files with
  ffmpeg (cached under `build/test-media/<fixture>`), pushes them to
  `/sdcard/Music/s2-seed/<fixture>` on the current lane, and triggers a MediaStore scan. Fixtures:
  `two-disc` (one album, 2 discs x 3 tracks, one FLAC), `many-tracks` (3 artists x 2 albums x 8
  tracks), `playlist-basic` (5 songs + an `.m3u`), `playback` (5 x 60 s tracks, for playback checks
  that must finish before a track ends on its own), `library` (the screenshot tests' invented
  sample library, 16 albums with embedded generated covers -- for checks that need realistic content). `--skip-onboarding` writes the debug app's
  SharedPreferences directly via `run-as` so it opens straight to the library with the local
  provider selected, then broadcasts to a debug-only receiver (`android/app/src/debug`) that calls
  `MediaImporter.import()` directly — the app's real `MediaStore` `ContentObserver` import path is
  dead code (never wired into the `AppInitializer` set), so nothing else triggers a library import
  once onboarding's Scanner page is skipped. Run it only after `install`, before the first launch.
  `--if-needed` skips the push/scan/prefs work entirely when this fixture (and `--skip-onboarding`
  state) is already what a manifest file on the lane records as last seeded -- `reset` removes that
  manifest with the rest of `/sdcard/Music/s2-seed`, so it always forces a real reseed (#412).
- Respects `ANDROID_SERIAL` / `ANDROID_ADB_SERVER_PORT` the same way `remote-emu.sh env` sets
  them — `eval` that first in any shell that calls either script.
- **Remote providers (Jellyfin/Emby/Plex):** `support/scripts/seed-remote-provider.sh
  jellyfin|emby|plex` signs the debug app in without a password. It reads
  `~/.config/s2-test/<server>.env` (`URL=`, `API_KEY=` -- for Plex, `API_KEY` is the server owner's
  plex.tv token), looks up the `shuttle-test` user's Id through the API for Jellyfin/Emby (Plex
  needs no lookup -- its API calls only use the token), and broadcasts the address, user Id and API
  key (as the access token) to the debug-only `DebugRemoteProviderReceiver` (DUMP-guarded,
  `android/app/src/debug`), which enables the provider and marks onboarding done, then triggers an
  import and launches the app. Run it after `reset` + `install`, one server per reset (all three
  servers carry the same "S2 Transcode Test" album). The key never reaches a command line or the
  output. `support/scripts/media-server-stream-probe.sh` checks the same servers at the HTTP level.

**Playback checks don't need the UI.** The debug build's `DebugPlaybackReceiver` plays the
library, skips, seeks, removes queue items and dumps playback state as JSON over `adb` broadcasts:
`support/scripts/s2-debug.sh <ACTION>`, documented in the `debug-receivers` skill. Set up state
with it and tap only when the UI is the thing under test. Ready-made checks (queue removal, rapid
skip, position restore after a force-stop, shuffle and repeat driven by Maestro) live in
`support/scripts/checks/`; `support/maestro/README.md` covers them and how to write more, and
`support/maestro/CLASSIFICATION.md` lists which are device-only versus ported to Robolectric (#450).

**Taps, dumps and screenshots once a lane is up: the `android-device` skill.** Its scripts
(`~/.claude/scripts/adb/`: `tap-by-text.sh`, `find-element.sh`, `screenshot.sh`, `safe-zone.sh`, ...)
work on a lane with the `remote-emu.sh env` exports in the same shell; with
`ANDROID_ADB_SERVER_PORT` set they require `ANDROID_SERIAL` and skip the local emulator lease.
Tools that ignore `ANDROID_ADB_SERVER_PORT` (Maestro, `:android:app:installDebug`) use the lane's
serial on the Mac's own adb server instead: `remote-emu.sh serial` (`localhost:1560N`, tunnelled to
the emulator's adbd by `start`). **Pause playback before any uiautomator-based step** (`s2-debug.sh
PAUSE`): while music plays the UI never goes idle, so every dump fails with "could not get idle
state"; the scripts now fail fast with that hint instead of retrying.

**UI checks are scriptable — tap by text, never by screenshot coordinate.** Screenshots handed to
a model are downscaled (device is 1280x2856, scale ~1.4286), so a tap computed from a screenshot's
coordinates lands in the wrong place on the real device. `remote-emu.sh tap-text <text> [--desc]
[--index N]` dumps the UI hierarchy via `uiautomator`, finds the node by visible text or
content-description, and taps the centre of its bounds in real device pixels; it exits non-zero
with a list of visible text/desc when nothing matches. `dump-texts` lists every visible
text/content-desc with its bounds, for exploring a screen before scripting a tap. **Never swipe
near the bottom edge** — it triggers the system home gesture; every screen in this app is reachable
by tap.

- `remote-emu.sh ui-prep [N]` sets `window_animation_scale`/`transition_animation_scale`/
  `animator_duration_scale` to 0 on the lane; `start` already runs it once the device has booted,
  so this is only needed by hand if something re-enables animations later. Even with animations
  off, `uiautomator dump` fails with "could not get idle state" while music plays (the progress bar
  keeps ticking): `tap-text`/`dump-texts` give up after two such failures and say to pause. Run
  them in the foreground like any other emulator command.
- `remote-emu.sh seed-music [dir]` generates ~6 short mp3s plus one 6-minute track (ffmpeg,
  distinct title/artist/album tags across 2 albums), pushes them to `/sdcard/Music/emu-seed`, and
  scans each file into MediaStore. The app's own library still needs a manual Settings -> Media ->
  Rescan to import them (same `MediaImporter` reimport gap noted above for `seed-test-media.sh`).
- `remote-emu.sh lockscreen on|off` runs `locksettings set-disabled false|true` on the lane.
- Navigation recipes (tap-text only, no swipes):
  - Full player: `tap-text` the mini player's title text (dynamic — the currently playing track's
    title, e.g. a seeded track name from `seed-music`).
  - Queue: open the full player, then `tap-text "Show queue" --desc` — never swipe up for this.
    `tap-text "Collapse player" --desc` closes the player.
  - Full player overflow menu: `tap-text "More options" --desc` (confirm with `dump-texts` first);
    it holds the sleep timer, speed, song info, edit tags and the rest.
  - Sleep timer: open the overflow menu, then `tap-text "Sleep timer"`.
  - Queue item actions (play next, add to playlist, remove): long-press the row
    (`adb shell input swipe <cx> <cy> <cx> <cy> 800`, bounds from `dump-texts`) to open its menu,
    then `tap-text "Remove from Queue"` (or another item). This is a long-press in the middle of
    the screen, not a bottom-edge swipe, so it's safe.

- The box is shared with the owner's podcasts repo and CI runners — sessions may run concurrently,
  **one lane each, up to 3** (lane N = `emulator-555{4,6,8}`, local adb port `5038..5040`, lease
  `/home/tim/.emu-leases/lane-N` on the box). Run `status` before assuming a lane is free; `start`
  leases the lowest free one and refuses a held lane.
- `remote-emu.sh start` then `eval "$(support/scripts/remote-emu.sh env)"` in every shell that runs
  `adb` or a build against the box (it sets `ANDROID_ADB_SERVER_PORT` + `ANDROID_SERIAL`; the Mac's
  adb keeps 5037; the exports don't persist across tool calls, so re-eval per shell). `install`
  runs `:android:app:assembleDebug` and installs over the tunnel (`:android:app:installDebug`
  ignores the port). **Build once, install per lane:** `install [N] <apk>` installs a prebuilt APK
  without running Gradle, so parallel workers share one `assembleDebug`.
- If `status` exits non-zero, the box is unreachable: fall back to the local AVD.
- **Always `remote-emu.sh stop` when done**, including after failures: it kills only your lane.
  Never `stop N`/`--all` on a lane you did not lease unless `status` shows its qemu dead. Never
  touch `gh-runner*`, `segment-acquisition*`, or `acq-vpn-tunnel-us` on the box. A stray emulator
  can hijack a CI instrumentation job.
- Setup, lanes, gotchas and reversal: on the box at `/home/tim/gh-runner-image/EMULATOR-SETUP.md`
  (box setup is shared infra written up from the podcasts repo, not duplicated here).

## Remote Gradle Builds (WSL Box, opt-in)

`support/scripts/remote-build.sh [--box|--local] <gradle args...>` picks the host itself (#546),
so it never queues for a box slot while the Mac could build: it reads the Mac's 1-min load against
its core count and, under `REMOTE_BUILD_LOAD_RATIO` x cores (default 0.8), runs `./gradlew` on the
Mac with `--max-workers` capped at the idle cores. When the Mac is loaded it goes to the WSL box
(#451) only if a probe sees a free build slot, and falls back to the Mac if the box is unreachable,
every slot is busy, or the slots fill up during the sync (the box side exits 75 instead of
waiting). One line says where it ran and why. `--box` forces the box and waits for a slot (exit 3
if it's unreachable); `--local` forces the Mac.

On the box it rsyncs the worktree to `~/s2-builds/<worktree name>`, takes a box-side build slot
(`flock` on `~/s2-builds/.slots/N`, at most `REMOTE_BUILD_SLOTS` concurrent builds, default 2 --
with `--box`, a caller past the limit prints one line, then rescans all slots every few seconds
and takes whichever frees first; the lock lives on an fd the remote shell holds, so it releases
itself on exit or ssh disconnect and never wedges) then runs `./gradlew` there under `nice` with a
box-side JDK (Temurin 21 in `~/opt/jdk-21`) and Gradle user home (`~/s2-builds/.gradle-home`, whose
`gradle.properties` caps the daemon heap and worker count for remote runs without touching this
worktree's own) at `--max-workers=6` (4 when `remote-emu.sh` shows a lane leased on the box) unless
the args say otherwise (`REMOTE_BUILD_MAX_WORKERS` changes the default), streams a condensed log
(full log: `build/remote-build/gradle.log`), prints the slot wait and Gradle wall time, then syncs
back APKs, test results, test reports and Roborazzi outputs and drops any of those report dirs the
box no longer has so a stale one can't linger. On a failing test task, either host, the script's
final output names each failing test as `Class.method: first line of the failure message` (cap 20,
then `+N more`), read from fresh `TEST-*.xml` under `build/test-results/`; a box run's own "See the
report at: file:///home/..." line is rewritten to the local synced copy (#468). Either host exits
with Gradle's exit code. The version tag is read on the Mac and passed as
`-PversionCode`/`-PversionName`. Different worktrees
build side by side (bounded by the box-side slot above); two calls from one worktree queue on a
separate local lock. One-time box setup: `support/scripts/remote-build-setup.sh` (idempotent; it
only checks the CI-shared SDK at `/opt/android-sdk` and never installs into it).

Opt in with `S2_REMOTE_BUILD=1`, or `--remote-build` as the first argument to
`support/scripts/unit-test` / an argument to `emu-verify.sh` (which installs the APK wherever it
was built -- a box build is synced back first). Line for briefs:

> Build with `support/scripts/unit-test --remote-build [module]`, `support/scripts/emu-verify.sh
> --remote-build ...`, or `support/scripts/remote-build.sh <tasks>` for anything else (foreground,
> generous timeout): it builds on the Mac unless the Mac is loaded, and uses the box only when a
> slot is free. Keep `verifyRoborazziDebug` on the Mac.

**Keep `verifyRoborazziDebug`/`recordRoborazziDebug` on the Mac.** The `docs/design/**` screenshot
goldens are recorded on macOS; #458 added a Linux anti-aliasing tolerance
(`s2.roborazzi.changeThreshold`) that fixed every other screenshot class, but all 7
`HomeScreenshotTest` shots still fail there with a colour diff, not anti-aliasing (the LFS preview
goldens under `android/app/src/test/snapshots` verify identically). Plain `testDebugUnitTest` is
unaffected: the `docs/design` shots are a no-op outside `verify`/`record` (#538). **#539 is the one
remaining blocker** to folding `verifyRoborazziDebug` into the box landing run instead of a second
Mac build — fix `HomeScreenshotTest`'s goldens there first.

Measured 2026-09-26, the full verify (`testDebugUnitTest :android:architecture-tests:test
:android:app:verifyRoborazziDebug :android:app:assembleDebug --continue`): cold (no build outputs,
`--no-build-cache`) 3m29s on the box (5m40s with a fresh daemon) vs 4m21s on the Mac at `--max-workers=2` (Mac load average
10 → 41 during the run); warm after a one-line app change, 2m04s vs 2m16s (Mac load 44 → 125).
The first build into an empty box-side Gradle home (downloads) took 10m48s for `assembleDebug`.
The sync costs 1-5 s each way.
