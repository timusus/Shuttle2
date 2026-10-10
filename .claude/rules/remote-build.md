---
paths:
  - "support/scripts/remote-build*.sh"
  - "support/scripts/unit-test"
---

# Remote Gradle builds (WSL box)

`support/scripts/remote-build.sh [--box|--local] <gradle args...>` is a thin wrapper over devtools' `remote-gradle` (see its
header and README for routing, slots, sync and env knobs; tune with `REMOTE_GRADLE_*`). With no flag it routes by job type:
long jobs (tests, `check`, `lint*`, `verify*`, more than 4 tasks) go to the box whenever a slot is free, Roborazzi record,
`connected*`, iOS/Xcode and `install*` stay on the Mac, and quick builds stay on the Mac unless it is loaded or busy. A
probe comes first, so an unreachable or full box builds on the Mac instead. `--box` forces the box and waits for a slot
(exit 3 if unreachable); `--local` forces the Mac. One line says where it ran and why.

On the box it rsyncs the worktree to `~/s2-builds/<worktree name>`, takes a slot (`flock` on `~/s2-builds/.slots/N`, cap 2),
runs `./gradlew` under `nice` with a box-side JDK (Temurin 21, `~/opt/jdk-21`) and Gradle home (`~/s2-builds/.gradle-home`)
at `--max-workers=6` (4 when a lane is leased), streams a condensed log (full: `build/remote-gradle/gradle.log`), then
syncs back APKs, test results/reports and Roborazzi outputs. Box runs add `-Ps2.testForks=2` (`REMOTE_GRADLE_BOX_ARGS`) and
are logged for `lease stats remote-build`.

The wrapper itself keeps the version tag (read on the Mac, passed as `-PversionCode`/`-PversionName` so both hosts get it)
and, on a non-zero exit, names each failing test as `Class.method: first line` (cap 20, #468). Exit code is Gradle's.
Different worktrees build side by side; two calls from one worktree queue on a local lock. One-time box setup:
`support/scripts/remote-build-setup.sh` (idempotent; only checks the CI-shared SDK at `/opt/android-sdk`).

Opt in with `S2_REMOTE_BUILD=1`, or `--remote-build` as the first argument to `support/scripts/unit-test` / an argument to
`emu-verify.sh`. Line for briefs:

> Build with `support/scripts/unit-test --remote-build [module]`, `support/scripts/emu-verify.sh --remote-build ...`, or
> `support/scripts/remote-build.sh <tasks>` for anything else (foreground, generous timeout): it picks the Mac or the box
> itself. The landing verify is one Mac invocation: `support/scripts/remote-build.sh --local -q testDebugUnitTest
> :android:app:assembleDebug :android:app:verifyRoborazziDebug :android:designsystem:verifyRoborazziDebug`.

**`recordRoborazziDebug` stays on the Mac** (the wrapper's `REMOTE_GRADLE_MAC_ONLY`). The `docs/design/**` goldens are
recorded on macOS; #458 added a Linux anti-aliasing tolerance (`s2.roborazzi.changeThreshold`), but all 7
`HomeScreenshotTest` shots were still failing on Linux with a colour diff when last measured, so landing pins its verify
to the Mac with `--local` (#539). Plain `testDebugUnitTest` is unaffected: the `docs/design` shots are a no-op outside
`verify`/`record` (#538). Roborazzi switches a module's test task into verify mode when its `verifyRoborazziDebug` is in
the graph (#552), so landing runs the verify tasks in the same invocation as `testDebugUnitTest`.

Measured 2026-09-26, full verify: cold 3m29s on the box (5m40s fresh daemon) vs 4m21s on the Mac at `--max-workers=2`;
warm after a one-line change 2m04s vs 2m16s. The first build into an empty box Gradle home took 10m48s. Sync costs 1-5 s
each way.
