---
paths:
  - "android/**"
---

# Android

S2 Music Player — Android app for local music playback and streaming via Jellyfin, Emby and Plex.
Kotlin, mixed legacy MVP (Fragment/Presenter/ViewBinder) and Compose + ViewModel (UDF). Build
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
| Unit | `src/test/` | Presenters, use cases, repositories, mappers, business logic |
| Compose characterisation | `src/test/` (Robolectric) | Compose screens and their ViewModels |
| Instrumented | `src/androidTest/` | Platform integration only — MediaSession/PlaybackService, Android Auto, SAF document access, Chromecast |

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
`support/scripts/remote-emu.sh` (`status` / `start [N]` / `env` / `install` / `reset [N]` /
`ui-prep [N]` / `tap-text` / `dump-texts` / `seed-music [dir]` / `lockscreen on|off` /
`stop [N|--all]`).

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
- `seed-test-media.sh <fixture> [--skip-onboarding]` generates short, tagged mp3/FLAC files with
  ffmpeg (cached under `build/test-media/<fixture>`), pushes them to
  `/sdcard/Music/s2-seed/<fixture>` on the current lane, and triggers a MediaStore scan. Fixtures:
  `two-disc` (one album, 2 discs x 3 tracks, one FLAC), `many-tracks` (3 artists x 2 albums x 8
  tracks), `playlist-basic` (5 songs + an `.m3u`). `--skip-onboarding` writes the debug app's
  SharedPreferences directly via `run-as` so it opens straight to the library with the local
  provider selected, then broadcasts to a debug-only receiver (`android/app/src/debug`) that calls
  `MediaImporter.import()` directly — the app's real `MediaStore` `ContentObserver` import path is
  dead code (never wired into the `AppInitializer` set), so nothing else triggers a library import
  once onboarding's Scanner page is skipped. Run it only after `install`, before the first launch.
- Respects `ANDROID_SERIAL` / `ANDROID_ADB_SERVER_PORT` the same way `remote-emu.sh env` sets
  them — `eval` that first in any shell that calls either script.

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
  `animator_duration_scale` to 0 on the lane. Run it once per lane after `start`, before the first
  `tap-text`/`dump-texts` call. Even with animations off, `uiautomator dump` can still fail with
  "could not get idle state" while music plays — the playback screen's progress bar keeps ticking —
  so `tap-text`/`dump-texts` retry the dump (`--compressed`, several attempts with a short sleep)
  internally; callers don't need their own retry loop, but **never background that retry loop and
  end the turn** — run `tap-text`/`dump-texts` in the foreground like any other emulator command.
- `remote-emu.sh seed-music [dir]` generates ~6 short mp3s plus one 6-minute track (ffmpeg,
  distinct title/artist/album tags across 2 albums), pushes them to `/sdcard/Music/emu-seed`, and
  scans each file into MediaStore. The app's own library still needs a manual Settings -> Media ->
  Rescan to import them (same `MediaImporter` reimport gap noted above for `seed-test-media.sh`).
- `remote-emu.sh lockscreen on|off` runs `locksettings set-disabled false|true` on the lane.
- Navigation recipes (tap-text only, no swipes):
  - Full player: `tap-text` the mini player's title text (dynamic — the currently playing track's
    title, e.g. a seeded track name from `seed-music`).
  - Queue sheet: open the full player, then `tap-text "Up Next"` (`QueueFragment.kt` ~146, which
    calls `expandSheet(SECOND)`) — never swipe up for this.
  - Full player overflow menu: `menu_playback.xml` puts `sleepTimer` as `ifRoom` and
    `lyrics`/`songInfo`/`editTags`/`clearQueue` as `never`, so most of it lives behind the overflow
    icon (AppCompat's default content-desc is "More options"; confirm with `dump-texts` first).
  - Sleep timer: `tap-text "Sleep Timer"` if visible directly on the toolbar, else open the
    overflow menu first, then `tap-text "Sleep Timer"`.
  - Queue item actions (remove, play next, add to playlist, exclude): the queue has no per-row
    overflow icon — long-press the row (`adb shell input swipe <cx> <cy> <cx> <cy> 800`, bounds
    from `dump-texts`) to open its popup menu, then `tap-text "Remove from Queue"` (or the other
    item titles from `menu_queue_item.xml`). This is a long-press in the middle of the screen, not
    a bottom-edge swipe, so it's safe.

- The box is shared with the owner's podcasts repo and CI runners — sessions may run concurrently,
  **one lane each, up to 3** (lane N = `emulator-555{4,6,8}`, local adb port `5038..5040`, lease
  `/home/tim/.emu-leases/lane-N` on the box). Run `status` before assuming a lane is free; `start`
  leases the lowest free one and refuses a held lane.
- `remote-emu.sh start` then `eval "$(support/scripts/remote-emu.sh env)"` in every shell that runs
  `adb` or a build against the box (it sets `ANDROID_ADB_SERVER_PORT` + `ANDROID_SERIAL`; the Mac's
  adb keeps 5037; the exports don't persist across tool calls, so re-eval per shell). `install`
  runs `:android:app:assembleDebug` and installs over the tunnel (`:android:app:installDebug`
  ignores the port).
- If `status` exits non-zero, the box is unreachable: fall back to the local AVD.
- **Always `remote-emu.sh stop` when done**, including after failures: it kills only your lane.
  Never `stop N`/`--all` on a lane you did not lease unless `status` shows its qemu dead. Never
  touch `gh-runner*`, `segment-acquisition*`, or `acq-vpn-tunnel-us` on the box. A stray emulator
  can hijack a CI instrumentation job.
- Setup, lanes, gotchas and reversal: on the box at `/home/tim/gh-runner-image/EMULATOR-SETUP.md`
  (box setup is shared infra written up from the podcasts repo, not duplicated here).
