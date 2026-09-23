# Emulator checks: debug receivers + Maestro

Scripted end-to-end checks against the debug build on a device or a WSL lane
(`support/scripts/remote-emu.sh`). Playback state is set up and asserted through the debug
receivers (`support/scripts/s2-debug.sh`, the `debug-receivers` skill); Maestro drives only what
has to be tapped.

| Check | Driver | What it proves | Time on a lane |
|---|---|---|---|
| `support/scripts/checks/queue-remove-current.sh` | receivers | Removing the playing item plays the next track from its start, no stall | ~4 s |
| `support/scripts/checks/rapid-skip.sh` | receivers | Three back-to-back NEXTs settle on track 4, playing, no load pending | ~3 s |
| `support/scripts/checks/restore-position.sh` | receivers | Seek 0:20, play 5 s, force-stop, relaunch, play: resumes at ~0:25 | ~12 s |
| `support/scripts/checks/open-queue-by-taps.sh` | receivers + `open-queue-by-taps.yaml` | Mini player -> full player -> "Up Next" opens the queue | 30-75 s (Maestro driver start-up varies) |

`support/scripts/checks/run-all.sh` runs them all. Each prints `PASS <name> in Ns` or
`FAIL <name>: <reason>` with the last `DUMP_STATE`, and exits non-zero on failure.

## Why most checks are shell, not Maestro

Maestro has no shell or adb step (`runScript` is sandboxed JavaScript), so it can't send the
debug broadcasts or read `DUMP_STATE`. Splitting a flow into several `maestro test` runs costs
10-20 s of driver start-up each. So a check whose subject is playback state is a shell script
over `s2-debug.sh`, and a Maestro flow is used where taps are the subject, with its shell wrapper
doing the setup first.

## Running on a WSL lane

```bash
support/scripts/remote-emu.sh start && eval "$(support/scripts/remote-emu.sh env)"
support/scripts/remote-emu.sh reset && support/scripts/remote-emu.sh install
support/scripts/seed-test-media.sh playback --skip-onboarding
support/scripts/checks/run-all.sh
support/scripts/remote-emu.sh stop
```

Maestro (`brew install mobile-dev-inc/tap/maestro`; plain `brew install maestro` is an unrelated app) only
talks to the Mac's own adb server on 5037, not the lane's tunnelled one. `remote-emu.sh start`
also tunnels the emulator's adbd and `adb connect`s it there; `remote-emu.sh serial` prints that
serial (`localhost:1560N`), which the wrapper passes as `--device`. Set `MAESTRO_DEVICE` to run
against another device. Screenshots and logs land in `tmp/maestro/`.

To run a flow by hand:

```bash
maestro --device "$(support/scripts/remote-emu.sh serial)" test --test-output-dir tmp/maestro \
  support/maestro/open-queue-by-taps.yaml
```

## Writing a flow

- Set up state with the receivers in the wrapper, not with taps.
- Pause before a UI flow. While music plays the progress bar keeps the UI from ever going idle,
  and uiautomator-based tools (`remote-emu.sh tap-text`, `~/.claude/scripts/adb`) fail on it.
- Start from `launchApp: stopApp: true` so an earlier run's screen doesn't leak in; the cold launch
  restores the queue the wrapper left.
- Select by visible text, as the user sees it. `takeScreenshot` paths must stay inside the
  output directory.
