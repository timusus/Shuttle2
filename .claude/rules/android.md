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
`support/scripts/remote-emu.sh` (`status` / `start [N]` / `env` / `install` / `stop [N|--all]`).

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
