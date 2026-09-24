---
name: emulator-check
description: Verify an S2 change on the WSL desktop emulator in minutes — build once, lease a lane, install, run the scripted playback checks (debug receivers) and Maestro UI flows, add a new check, stop the lane. Use before landing any user-visible change, when briefing an emulator-verification worker, or when writing a new Maestro flow or check script.
---

# Emulator check

Scripted, repeatable verification on a `remote-emu.sh` lane. Drive state with adb broadcasts; for
anything the UI must show or be tapped, write or reuse a Maestro flow — not ad-hoc `tap-text`,
uiautomator dumps or screenshot-and-look loops, which cost many turns and don't survive for the
next change. A full run of the existing checks takes about 95 s; a UI-driven worker used to take 40+
minutes for the same ground.

## Run it

One call, in the foreground, with a generous timeout:

```bash
support/scripts/emu-verify.sh                   # builds/reuses the APK, runs the full check suite
support/scripts/emu-verify.sh --check open-queue-by-taps --flow support/maestro/nav/open-settings.yaml
support/scripts/emu-verify.sh --apk /tmp/s2-apk/<sha>.apk --no-seed --check rapid-skip
```

It starts a lane, installs, seeds the `playback` fixture, runs the named `--check`/`--flow` args
(repeatable; `checks/run-all.sh` if neither is given), and always stops the lane on exit (trap),
even on failure or Ctrl-C, unless `--keep`. Full command output goes to the log file it prints, not
stdout; stdout stays to one line per setup step plus one PASS/FAIL line per check/flow. `--help`
for the rest of the flags (`--apk`, `--no-seed`, `--keep`).

- Run the whole script in the foreground. A headless worker that backgrounds it ends its run (#303).
- Never start a local `emulator` or hand-roll `sleep`/`getprop sys.boot_completed` loops: `start`
  (which `emu-verify.sh` calls) already waits for boot (up to 300 s) and fails loudly. Those loops
  were the top source of 10-minute Bash timeouts in the Sep 2026 token sweep.
- Another job building in the same worktree? Pass `--apk <path>` to skip Gradle there.
- Lane 1 often belongs to another project; `emu-verify.sh` (via `remote-emu.sh start`) picks a free one.

### Manual fallback

For a one-off command outside `emu-verify.sh` (a raw `adb` call, a different fixture, leaving the
lane up to poke at by hand):

```bash
support/scripts/remote-emu.sh start && eval "$(support/scripts/remote-emu.sh env)"
support/scripts/remote-emu.sh reset && support/scripts/remote-emu.sh install   # [N] <apk> for a prebuilt one
support/scripts/seed-test-media.sh playback --skip-onboarding
support/scripts/checks/run-all.sh               # PASS/FAIL per check, non-zero on failure
support/scripts/remote-emu.sh stop              # ALWAYS, even on failure
```

If a command fails with "device offline"/"device not found" mid-run, the tunnel dropped:
`s2-debug.sh` and `checks/*.sh` already reconnect and retry once on their own; for a raw `adb` call
run `support/scripts/remote-emu.sh reconnect` yourself first (no reboot, lease kept).

## Pieces

| Need | Use |
|---|---|
| Play, pause, skip, seek, remove from queue, shuffle/repeat, reimport, read state as JSON | `support/scripts/s2-debug.sh <ACTION>` (the `debug-receivers` skill) |
| Ready-made checks | `support/scripts/checks/*.sh` (queue-remove-current, rapid-skip, restore-position, open-queue-by-taps, folder-art) |
| Taps where the UI is the subject | a Maestro flow in `support/maestro/`, run by a `checks/` wrapper that sets up state first |
| One-off taps, dumps, screenshots | `remote-emu.sh tap-text` / `dump-texts`, or the `android-device` skill with the `env` exports |
| Notification / lock screen | `adb shell cmd statusbar expand-notifications`, `remote-emu.sh lockscreen on` |

Maestro: `brew install mobile-dev-inc/tap/maestro` (plain `brew install maestro` is an unrelated app).
It only talks to the Mac's adb server on 5037, so pass `--device "$(support/scripts/remote-emu.sh serial)"`.

## Adding a check

1. A new `support/scripts/checks/<name>.sh`, sourcing `_lib.sh`: set up state with `s2`, wait with
   `wait_for <secs> "<python expr on s>"`, then assert with `state <field>`, and finish with `pass` or `fail "<why>"`.
   `run-all.sh` picks it up automatically.
2. Only if taps are the subject: add `support/maestro/<name>.yaml`, reusing `support/maestro/nav/`
   subflows via `runFlow` for common navigation (see `support/maestro/README.md`) and selecting by
   visible text otherwise. Call it from the wrapper (see `open-queue-by-taps.sh`), or run it
   directly with `emu-verify.sh --flow support/maestro/<name>.yaml`. Each `maestro test` costs
   10–20 s to start, so use one flow per check.
3. Pause playback (`s2-debug.sh PAUSE`) before any uiautomator or Maestro step. While music plays the UI
   never goes idle and dumps fail.
4. Prefer adding a check that proves the fix over one-off manual steps, so the next change is covered too.

## Reporting

Report PASS/FAIL per check with its key `DUMP_STATE` values, and screenshots only where the UI is the
evidence: Read the PNG so it renders inline. Also confirm that the lane was stopped.
