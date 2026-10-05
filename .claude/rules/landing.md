---
paths:
  - "support/scripts/land.sh"
  - "support/scripts/full-verify.sh"
---

# Landing and full verify

- **Workers verify narrowly; the landing queue verifies lightly; a full verify runs behind a watermark.**
  A worker verifies with `unit-test --changed-tests` via `worker-finish.sh` and nothing wider — no full suite,
  no emulator/simulator lease unless the brief needs a screenshot. `lint` covers changed files only
  (`land.sh` passes `--base` so it lints just the batch's commits). `support/scripts/land.sh <branch>... [--close N|BRANCH:N ...]`
  cherry-picks each approved branch onto `origin/main`, runs a light verify once under `machine-lock`
  (Android: `lint` (check only), `unit-test --changed`, `:android:architecture-tests:testDebugUnitTest` (always, so a layer violation fails the batch that adds it; its own phase, so a failure there still runs assembleDebug), a compile of dependent modules' test sources when domain/shared/core/commonMain sources changed, + assembleDebug; one automatic retry per verify run (so per bisect iteration) with `-Pkotlin.incremental=false` on an `Incremental compilation failed` flake; iOS, only when the picked commits touch `ios/`,
  `shared/`, `android/domain|presentation|core`, or a KMP module's commonMain/commonTest/iosMain/iosTest: framework build, `:<module>:iosSimulatorArm64Test` for just the KMP modules whose commonMain/commonTest/iosMain/iosTest changed (none changed, no Kotlin/Native tests) + app build and just the `S2Tests` classes
  mapped from the changed files), pushes (if another session pushed meanwhile, it rebases onto the new `origin/main` and pushes again, re-verifying once only when the incoming commits touch the batch's files; a push that still fails resets the checkout to `origin/main`), closes issues and cleans up the landed worktrees (`--close
  BRANCH:N` closes only when BRANCH landed; a bare `--close N` only when every branch landed). Run it as a
  `longjob.sh` batch, never twice for the same batch. `support/scripts/full-verify.sh` (via `longjob.sh
  start full-verify -- ...`) runs the whole suites (including `iosSimulatorArm64Test`) at `origin/main` and records the sha as the watermark;
  `--status` shows how far main is past it, SessionStart prints the same, and `/deploy-android` runs it
  before tagging if the watermark isn't the release commit.
