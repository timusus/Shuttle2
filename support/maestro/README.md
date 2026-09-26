# Emulator checks: debug receivers + Maestro

Scripted end-to-end checks against the debug build on a device or a WSL lane
(`support/scripts/remote-emu.sh`). Playback state is set up and asserted through the debug
receivers (`support/scripts/s2-debug.sh`, the `debug-receivers` skill); Maestro drives only what
has to be tapped. `support/scripts/emu-verify.sh` runs a lane end to end (start, install, seed,
checks/flows, stop) in one call -- see its `--help` or `.claude/skills/emulator-check/SKILL.md`.

**What's here and why:** every device-only check (name, its Maestro wrapper if any, and why a JVM
test can't cover it) is listed in [`CLASSIFICATION.md`](CLASSIFICATION.md), not repeated here --
that's the one place it's kept in sync as flows are added or ported to Robolectric (#450). This
file covers how the checks work, how to run them, and how to write a new one.

**Running the full batch: `emu-verify.sh --suite` once, then read the results file.** With no
`--flows`, it runs the device smoke set (`support/scripts/checks/smoke.txt`, one check per surface)
once each under a per-flow timeout, retries a failure once, keeps going past one, and appends a row
(flow, pass/fail/timeout/skip, duration, screenshot, last error) to `build/maestro/results.md` as
each finishes -- so a cut-short run still leaves a report for every flow that finished, plus an
`interrupted` row for whichever flow was still running when it was cut off. Don't debug flows one
at a time by hand -- that's what left #381's two validation runs (84 and 100 minutes) with no
report to show for them. `--all` runs every check in the run-all set instead of just the smoke set;
`--flows <a,b>` narrows to a subset by name; `--flow-timeout <s>` overrides the 180s default.

`support/scripts/checks/run-all.sh` runs every check directly (`--smoke` for just the smoke set).
Each check prints `PASS <name> in Ns` or `FAIL <name>: <reason>` with the last `DUMP_STATE`, and
exits non-zero on failure.

## Why most checks are shell, not Maestro

Maestro has no shell or adb step (`runScript` is sandboxed JavaScript), so it can't send the
debug broadcasts or read `DUMP_STATE`. Splitting a flow into several `maestro test` runs costs
10-20 s of driver start-up each. So a check whose subject is playback state is a shell script
over `s2-debug.sh`, and a Maestro flow is used where taps are the subject, with its shell wrapper
doing the setup first.

## Running on a WSL lane

For a full batch report in one call, prefer `support/scripts/emu-verify.sh --suite` (see above)
over hand-rolling the steps below, which run `run-all.sh` directly with no per-flow timeout, retry
or results file:

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
  support/maestro/playback-controls.yaml
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
- Before adding a new flow, check `CLASSIFICATION.md`'s criteria: if the behaviour is Compose
  navigation/state a Robolectric test in `:android:app` can drive, it belongs there instead (see
  `.claude/rules/testing.md`) -- a new Maestro flow is for what genuinely needs the device (SAF,
  MediaStore, the playback service and its notification, widgets, shortcuts, Android Auto, voice,
  intents, rotation, process death).

## `nav/` subflows

Reusable navigation, each runnable standalone (`maestro test support/maestro/nav/<name>.yaml`) or
via `runFlow: nav/<name>.yaml` from another flow (a relative `runFlow` path resolves against the
*calling* flow's own directory, so a flow inside `nav/` refers to its siblings by bare filename,
e.g. `runFlow: open-now-playing.yaml`).

| Flow | What it does | Params |
|---|---|---|
| `nav/launch-fresh.yaml` | Cold launch (`stopApp: true`). The building block every other nav flow starts with. | none |
| `nav/open-now-playing.yaml` | Launch fresh, then open the full-screen player by tapping the mini player row (no stable id, so it's selected by the current track's title). | `TITLE`: the current track's title |
| `nav/open-queue.yaml` | Open the full player, then tap "Up Next" to open the queue sheet. | `TITLE`: the current track's title |
| `nav/open-library-tab.yaml` | Tap the Library bottom-nav item, then a sub-tab by name. | `TAB`: `Genres`\|`Playlists`\|`Artists`\|`Albums`\|`Songs` |
| `nav/open-settings.yaml` | Open the More sheet, then tap "Settings". | none |
| `nav/search.yaml` | Open Search and type a query into the auto-focused search field. | `QUERY`: text to type |
| `nav/create-testlist.yaml` | Create a "TestList" playlist from two `many-tracks` songs, if it doesn't already exist (idempotent). | none |
| `nav/pick-saf-folder.yaml` | Drive the already-open system SAF folder picker to a named folder under Music and grant access. | `FOLDER_NAME`: folder to pick |
| `nav/setup-taglib-provider.yaml` | Add a folder under Settings > Sources' "Read these folders directly", then wait for the scan. Wrapped by `checks/_lib.sh`'s `setup_taglib_provider`. | `FOLDER_NAME`: folder to add |

Pass a param with `-e` on the CLI (`maestro test -e TAB=Albums support/maestro/nav/open-library-tab.yaml`)
or `env:` from a parent flow's `runFlow` step:

```yaml
- runFlow:
    file: nav/open-library-tab.yaml
    env:
      TAB: Albums
```

`support/maestro/playback-controls.yaml` is a worked example: it composes `nav/open-library-tab.yaml`
plus a run of taps and screenshots, instead of hand-rolling the library-to-player navigation.

## Adding a new check

1. Decide UI-only vs. device-only against `CLASSIFICATION.md`'s criteria (above). A UI-only
   behaviour goes into a Robolectric test instead, not a new flow.
2. Write the flow (or reuse a `nav/` one via `runFlow`) under `support/maestro/`.
3. Write a `support/scripts/checks/<name>.sh` wrapper (see `playback-controls.sh`): set up
   playback state with the receivers, pause, then run `maestro test --device "$(remote-emu.sh
   serial)"` against the flow, `fail` on a non-zero exit.
4. Add a row to `CLASSIFICATION.md`'s "Device-only (kept)" table.
5. `run-all.sh` and `emu-verify.sh --suite --all` pick it up automatically; run it directly with
   `support/scripts/emu-verify.sh --check <name>`, or run just the flow with `--flow
   support/maestro/<path>.yaml`. If it should also run in the default smoke batch, add its name to
   `support/scripts/checks/smoke.txt`.
