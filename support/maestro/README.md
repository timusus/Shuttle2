# Emulator checks: debug receivers + Maestro

Scripted end-to-end checks against the debug build on a device or a WSL lane
(`support/scripts/remote-emu.sh`). Playback state is set up and asserted through the debug
receivers (`support/scripts/s2-debug.sh`, the `debug-receivers` skill); Maestro drives only what
has to be tapped. `support/scripts/emu-verify.sh` runs a lane end to end (start, install, seed,
checks/flows, stop) in one call -- see its `--help` or `.claude/skills/emulator-check/SKILL.md`.

| Check | Driver | What it proves | Time on a lane |
|---|---|---|---|
| `support/scripts/checks/queue-remove-current.sh` | receivers | Removing the playing item plays the next track from its start, no stall | ~4 s |
| `support/scripts/checks/rapid-skip.sh` | receivers | Three back-to-back NEXTs settle on track 4, playing, no load pending | ~3 s |
| `support/scripts/checks/restore-position.sh` | receivers | Seek 0:20, play 5 s, force-stop, relaunch, play: resumes at ~0:25 | ~12 s |
| `support/scripts/checks/folder-art.sh` | receivers + taps | One-song album with a magenta `cover.jpg` and no embedded art: Library > Albums shows the cover (artwork pixel check); removes the album after | ~20 s |
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
  restores the queue the wrapper left. `nav/launch-fresh.yaml` does exactly this -- reuse it via
  `runFlow` rather than repeating the `launchApp` step.
- Select by visible text, as the user sees it, except where a nav item has no label (the Settings
  overflow icon) -- there, select by `id:` instead (a regex against the Android resource id).
  `takeScreenshot` paths must stay inside the output directory.
- Reuse a `nav/` subflow with `runFlow` instead of re-typing common navigation. Each one starts
  with `runFlow: launch-fresh.yaml`, so it's safe to run standalone (`--flow`) or nested inside
  another flow (`runFlow`) regardless of what screen the app was left on -- at the cost of a cold
  relaunch each time it's entered, which is cheap next to Maestro's own ~10-20 s driver start-up.

## `nav/` subflows

Reusable navigation, each runnable standalone (`maestro test support/maestro/nav/<name>.yaml`) or
via `runFlow: nav/<name>.yaml` from another flow (a relative `runFlow` path resolves against the
*calling* flow's own directory, so a flow inside `nav/` refers to its siblings by bare filename,
e.g. `runFlow: open-now-playing.yaml`).

| Flow | What it does | Params |
|---|---|---|
| `nav/launch-fresh.yaml` | Cold launch (`stopApp: true`). The building block every other nav flow starts with. | none |
| `nav/open-now-playing.yaml` | Launch fresh, then open the full-screen player from the mini player (selects by id, since the title is the current track). | none |
| `nav/open-queue.yaml` | Open the full player, then tap "Up Next" to open the queue sheet. | none |
| `nav/open-library-tab.yaml` | Tap the Library bottom-nav item, then a sub-tab by name. | `TAB`: `Genres`\|`Playlists`\|`Artists`\|`Albums`\|`Songs` |
| `nav/open-settings.yaml` | Tap the bottom-nav overflow sheet (by id, no label), then "Settings". | none |
| `nav/search.yaml` | Open Search and type a query into the auto-focused search field. | `QUERY`: text to type |

Pass a param with `-e` on the CLI (`maestro test -e TAB=Albums support/maestro/nav/open-library-tab.yaml`)
or `env:` from a parent flow's `runFlow` step:

```yaml
- runFlow:
    file: nav/open-library-tab.yaml
    env:
      TAB: Albums
```

`support/maestro/open-queue-by-taps.yaml` is a worked example: it composes `nav/open-queue.yaml`
plus one assertion and a screenshot, instead of hand-rolling the mini-player and "Up Next" taps.

## Adding a new check that uses them

1. Write the flow (or reuse a `nav/` one via `runFlow`) under `support/maestro/`.
2. Write a `support/scripts/checks/<name>.sh` wrapper (see `open-queue-by-taps.sh`): set up
   playback state with the receivers, pause, then run `maestro test --device "$(remote-emu.sh
   serial)"` against the flow, `fail` on a non-zero exit.
3. `run-all.sh` picks it up automatically; run it directly with `support/scripts/emu-verify.sh
   --check <name>`, or run just the flow with `--flow support/maestro/<path>.yaml`.
