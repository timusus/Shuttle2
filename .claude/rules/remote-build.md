---
paths:
  - "support/scripts/remote-build*.sh"
  - "support/scripts/unit-test"
---

# Remote Gradle builds (WSL box)

`support/scripts/remote-build.sh [--box|--local] <gradle args...>` picks the host itself (#546), so it never queues for a
box slot while the Mac could build: if the Mac's 1-min load is under `REMOTE_BUILD_LOAD_RATIO` x cores (default 0.8) it
runs `./gradlew` on the Mac with `--max-workers` capped at idle cores. Otherwise it goes to the box (#451) only if a probe
sees a free build slot, falling back to the Mac if the box is unreachable, full, or fills during the sync (the box side
exits 75 instead of waiting). `--box` forces the box and waits for a slot (exit 3 if unreachable); `--local` forces the
Mac. One line says where it ran and why.

On the box it rsyncs the worktree to `~/s2-builds/<worktree name>`, takes a slot (`flock` on `~/s2-builds/.slots/N`, at
most `REMOTE_BUILD_SLOTS`, default 2; the lock lives on the remote shell's fd so it releases on exit or disconnect), runs
`./gradlew` under `nice` with a box-side JDK (Temurin 21, `~/opt/jdk-21`) and Gradle home (`~/s2-builds/.gradle-home`)
at `--max-workers=6` (4 when a lane is leased; `REMOTE_BUILD_MAX_WORKERS`), streams a condensed log (full:
`build/remote-build/gradle.log`), then syncs back APKs, test results/reports and Roborazzi outputs (dropping report dirs
the box no longer has). On a failing test task, either host, the final output names each failure as
`Class.method: first line` (cap 20); a box run's `file:///home/...` report line is rewritten to the local copy (#468).
Exit code is Gradle's. The version tag is read on the Mac and passed as `-PversionCode`/`-PversionName`. Different
worktrees build side by side; two calls from one worktree queue on a local lock. One-time box setup:
`support/scripts/remote-build-setup.sh` (idempotent; only checks the CI-shared SDK at `/opt/android-sdk`).

Opt in with `S2_REMOTE_BUILD=1`, or `--remote-build` as the first argument to `support/scripts/unit-test` / an argument to
`emu-verify.sh`. Line for briefs:

> Build with `support/scripts/unit-test --remote-build [module]`, `support/scripts/emu-verify.sh --remote-build ...`, or
> `support/scripts/remote-build.sh <tasks>` for anything else (foreground, generous timeout): it builds on the Mac unless
> the Mac is loaded, and uses the box only when a slot is free. Keep `verifyRoborazziDebug` on the Mac. The landing verify
> is one Mac invocation: `support/scripts/remote-build.sh --local -q testDebugUnitTest :android:app:assembleDebug
> :android:app:verifyRoborazziDebug :android:designsystem:verifyRoborazziDebug`.

**Keep `verifyRoborazziDebug`/`recordRoborazziDebug` on the Mac.** The `docs/design/**` goldens are recorded on macOS; #458
added a Linux anti-aliasing tolerance (`s2.roborazzi.changeThreshold`) that fixed every other screenshot class, but all 7
`HomeScreenshotTest` shots still fail on Linux with a colour diff (the LFS preview goldens under
`android/app/src/test/snapshots` verify identically). Plain `testDebugUnitTest` is unaffected: the `docs/design` shots
are a no-op outside `verify`/`record` (#538). Landing runs the verify tasks in the same invocation as
`testDebugUnitTest` (#552): Roborazzi switches a module's test task into verify mode when its `verifyRoborazziDebug` is in
the graph, so each suite runs once. #539 keeps that invocation off the box.

Measured 2026-09-26, full verify: cold 3m29s on the box (5m40s fresh daemon) vs 4m21s on the Mac at `--max-workers=2`;
warm after a one-line change 2m04s vs 2m16s. The first build into an empty box Gradle home took 10m48s. Sync costs 1-5 s
each way.
