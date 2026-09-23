---
name: emulator-check
description: Verify an S2 change on the WSL desktop emulator in minutes — build once, lease a lane, install, run the scripted playback checks (debug receivers) and Maestro UI flows, add a new check, stop the lane. Use before landing any user-visible change, when briefing an emulator-verification worker, or when writing a new Maestro flow or check script.
---

# Emulator check

Scripted, repeatable verification on a `remote-emu.sh` lane. Drive state with adb broadcasts, tap
only when the UI is the subject. A full run of the existing checks takes about 95 s; a UI-driven
worker used to take 40+ minutes for the same ground.

## Run it

```bash
./gradlew :android:app:assembleDebug            # once, FOREGROUND; skip if an APK for HEAD exists
support/scripts/remote-emu.sh start && eval "$(support/scripts/remote-emu.sh env)"
support/scripts/remote-emu.sh reset && support/scripts/remote-emu.sh install   # [N] <apk> for a prebuilt one
support/scripts/seed-test-media.sh playback --skip-onboarding
support/scripts/checks/run-all.sh               # PASS/FAIL per check, non-zero on failure
support/scripts/remote-emu.sh stop              # ALWAYS, even on failure
```

- Every command in the foreground. A headless worker that backgrounds a wait ends its run (#303).
- Another job building in the same worktree? Copy the APK to /tmp and `install <apk>`; never run a
  second Gradle there.
- Lane 1 often belongs to another project; `start` picks a free lane.

## Pieces

| Need | Use |
|---|---|
| Play, pause, skip, seek, remove from queue, shuffle/repeat, reimport, read state as JSON | `support/scripts/s2-debug.sh <ACTION>` (the `debug-receivers` skill) |
| Ready-made checks | `support/scripts/checks/*.sh` (queue-remove-current, rapid-skip, restore-position, open-queue-by-taps) |
| Taps where the UI is the subject | a Maestro flow in `support/maestro/`, run by a `checks/` wrapper that sets up state first |
| One-off taps, dumps, screenshots | `remote-emu.sh tap-text` / `dump-texts`, or the `android-device` skill with the `env` exports |
| Notification / lock screen | `adb shell cmd statusbar expand-notifications`, `remote-emu.sh lockscreen on` |

Maestro: `brew install mobile-dev-inc/tap/maestro` (plain `brew install maestro` is an unrelated app).
It only talks to the Mac's adb server on 5037, so pass `--device "$(support/scripts/remote-emu.sh serial)"`.

## Adding a check

1. A new `support/scripts/checks/<name>.sh`, sourcing `_lib.sh`: set up state with `s2`, wait with
   `wait_for <secs> "<python expr on s>"`, then assert with `state <field>`, and finish with `pass` or `fail "<why>"`.
   `run-all.sh` picks it up automatically.
2. Only if taps are the subject: add `support/maestro/<name>.yaml`, starting with `launchApp: stopApp: true`
   and selecting by visible text. Call it from the wrapper (see `open-queue-by-taps.sh`). Each `maestro test`
   costs 10–20 s to start, so use one flow per check.
3. Pause playback (`s2-debug.sh PAUSE`) before any uiautomator or Maestro step. While music plays the UI
   never goes idle and dumps fail.
4. Prefer adding a check that proves the fix over one-off manual steps, so the next change is covered too.

## Reporting

Report PASS/FAIL per check with its key `DUMP_STATE` values, and screenshots only where the UI is the
evidence: Read the PNG so it renders inline. Also confirm that the lane was stopped.
