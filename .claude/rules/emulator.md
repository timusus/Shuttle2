---
paths:
  - "support/scripts/remote-emu.sh"
  - "support/scripts/emu-verify.sh"
  - "support/scripts/seed-*.sh"
  - "support/scripts/s2-debug.sh"
  - "support/scripts/checks/**"
  - "support/maestro/**"
---

# Desktop emulator (WSL box)

Prefer a headless Pixel 9 Pro AVD on the owner's desktop (WSL2, KVM) over a local AVD: the Mac starves a local emulator
whenever it's loaded. Launcher `support/scripts/remote-emu.sh`: `status` / `start [N] [--api 36|37]` / `env` /
`install [N] [apk]` / `serial [N]` / `reconnect [N]` / `reset [N]` / `ui-prep [N]` / `tap-text` / `dump-texts` /
`seed-music [dir]` / `lockscreen on|off` / `emu <console cmd>` / `stop [N|--all]`. Scripted verification is the
`emulator-check` skill (`emu-verify.sh`); this file is the lane protocol and gotchas.

## Lanes

- The box is shared with the podcasts repo and CI runners: **one lane each, up to 3** (lane N = `emulator-555{4,6,8}`,
  local adb port `5038..5040`, lease `/home/tim/.emu-leases/lane-N` on the box). Run `status` first; `start` leases the
  lowest free lane, reuses one this session already holds, and refuses a held lane. If `status` exits non-zero the box is
  unreachable: fall back to a local AVD.
- A lease is reclaimed on the next `start` when nothing listens on its console port, or its recorded owner process (the
  nearest Claude Code session above the caller, never the `claude remote-control` supervisor) is gone from this Mac.
- Default image: full `google_apis` API 37 at 2560 MB / 3 cores. `screencap`/Maestro screenshots need it: the lighter
  `android-36 google_atd` image renders black frames (#390). `start --api 36` opts into ATD for lanes that never
  screenshot (less idle CPU); an explicit `--api` naming a different image than the running one fails: `stop`, then
  `start --api N`. `start` runs `ui-prep` (animations off) once booted.
- `eval "$(support/scripts/remote-emu.sh env)"` in every shell that runs `adb` or a build against the box (sets
  `ANDROID_ADB_SERVER_PORT` + `ANDROID_SERIAL`; Mac adb keeps 5037; exports don't persist across tool calls).
  `install` runs `:android:app:assembleDebug` and installs over the tunnel (`installDebug` ignores the port); `install [N]
  <apk>` installs a prebuilt APK without Gradle, so parallel workers share one build. Tools that ignore the port
  (Maestro, `installDebug`) use `remote-emu.sh serial` (`localhost:1560N`) on the Mac's adb server.
- **Always `remote-emu.sh stop` when done**, including after failures; it kills only your lane. Never `stop N`/`--all` on
  a lane you did not lease unless `status` shows its qemu dead. Never touch `gh-runner*`, `segment-acquisition*` or
  `acq-vpn-tunnel-us` on the box: a stray emulator can hijack a CI instrumentation job.
- **Tunnel drops** ("device offline"/"not found"): `remote-emu.sh reconnect` re-opens it without rebooting or losing the
  lease; `s2-debug.sh` and `checks/_lib.sh` do it automatically (`adb_retry`), so only raw `adb` needs it by hand. A
  failure names its check (`port-busy`, `ssh-exited`, `listen-timeout`, `adb-handshake`) and the tail of
  `$TMPDIR/remote-emu/tunnel-N[-adbd].log` (#342).
- Box setup, lanes and reversal: `/home/tim/gh-runner-image/EMULATOR-SETUP.md` on the box (shared infra written up from
  the podcasts repo).

## Batch and standard start state

- One-shot: `emu-verify.sh` does start/install/reset/seed, the named checks or flows, stop. `--no-reset` skips the reset
  on a lane seeded this session (#412); keep the default for landing verification.
- **Full device-check pass (queued batches like #452):** `emu-verify.sh --suite` (`--suite --flows a,b` for a subset;
  `--all` for the run-all set) once, then read `build/maestro/results.md`; don't debug flows one at a time. Per-flow
  timeout `--flow-timeout` (default 180 s), one retry, keeps going past failures, appends a row per flow as it finishes
  (plus `interrupted` for a flow cut short); exits non-zero on fail/timeout; `skip` (no dialer, no Photos on ATD) isn't a
  failure.
- A reused lane has stale app data and media. Before validating a UI or playback change:

```bash
support/scripts/remote-emu.sh reset               # pm clear the debug app, drop /sdcard/Music/s2-seed
support/scripts/remote-emu.sh install
support/scripts/seed-test-media.sh two-disc --skip-onboarding
adb shell am start -n com.simplecityapps.shuttle.dev/com.simplecityapps.shuttle.ui.MainActivity
```

- `seed-test-media.sh <fixture> [--skip-onboarding] [--if-needed]` generates tagged mp3/FLAC with ffmpeg (cached under
  `build/test-media/<fixture>`), pushes to `/sdcard/Music/s2-seed/<fixture>` and triggers a MediaStore scan. Fixtures:
  `two-disc` (2 discs x 3 tracks, one FLAC), `many-tracks` (3 artists x 2 albums x 8), `playlist-basic` (5 songs + `.m3u`),
  `playback` (5 x 60 s), `library` (the screenshot tests' sample library, 16 albums with generated covers).
  `--skip-onboarding` writes the debug app's SharedPreferences via `run-as` (local provider selected) and broadcasts to a
  debug-only receiver (`android/app/src/debug`) that calls `MediaImporter.import()` directly: the real `MediaStore`
  `ContentObserver` import path is dead code, so nothing else imports once onboarding's Scanner page is skipped. Run it
  after `install`, before first launch. `--if-needed` skips work when the lane's manifest says this fixture is already
  seeded; `reset` removes the manifest.
- **Remote providers:** `seed-remote-provider.sh jellyfin|emby|plex` signs the debug app in without a password: reads
  `~/.config/s2-test/<server>.env` (`URL=`, `API_KEY=`; for Plex the owner's plex.tv token), looks up the `shuttle-test`
  user's Id (Jellyfin/Emby), and broadcasts to the DUMP-guarded debug `DebugRemoteProviderReceiver`, which enables the
  provider, marks onboarding done, imports and launches. Run after `reset` + `install`, one server per reset. The key
  never reaches a command line or output. `media-server-stream-probe.sh` checks the same servers at HTTP level.

## Driving the app

- **Playback checks don't need the UI.** `support/scripts/s2-debug.sh <ACTION>` (`debug-receivers` skill) plays, skips,
  seeks, edits the queue and dumps state as JSON. Ready-made checks live in `support/scripts/checks/`;
  `support/maestro/README.md` covers writing more and `support/maestro/CLASSIFICATION.md` lists device-only vs ported to
  Robolectric (#450).
- **An outside controller** (queue edits, browse, old-session play-by-id/search that adb can't send): the debug-only
  `:android:testing:media-controller` app, driven by `am start ... --es cmd <cmd>` and answering on logcat tag `S2CTRL`.
  Checks source `checks/_controller.sh` (`install_controller`, `ctl`); it builds the APK on first use (#423).
- **Taps, dumps, screenshots:** the `android-device` skill's scripts (`~/.claude/scripts/adb/`) work on a lane with the
  `env` exports; with `ANDROID_ADB_SERVER_PORT` set they require `ANDROID_SERIAL`. **Pause playback before any
  uiautomator step** (`s2-debug.sh PAUSE`): while music plays the UI never goes idle and dumps fail with "could not get
  idle state" (`tap-text`/`dump-texts` give up after two and say so).
- **Tap by text, never by screenshot coordinate.** Screenshots handed to a model are downscaled (device 1280x2856, scale
  ~1.4286). `remote-emu.sh tap-text <text> [--desc] [--index N]` dumps the hierarchy, finds the node by text or
  content-description and taps its centre in device pixels; `dump-texts` lists everything visible. **Never swipe near the
  bottom edge** (system home gesture); every screen is reachable by tap.
- `seed-music [dir]` generates ~6 short mp3s plus a 6-minute track into `/sdcard/Music/emu-seed`; the app still needs
  Settings -> Media -> Rescan (same import gap as above). `lockscreen on|off` runs `locksettings set-disabled`.
- Navigation recipes (tap-text, no swipes): full player = tap the mini player's title (the playing track's name); queue =
  full player then `"Queue"` (again or back to close); `"Collapse player" --desc` closes the player; overflow menu =
  `"More options" --desc` (sleep timer, speed, song info, edit tags); queue item actions = long-press the row
  (`adb shell input swipe <cx> <cy> <cx> <cy> 800`, bounds from `dump-texts`), then e.g. `"Remove from Queue"`.
