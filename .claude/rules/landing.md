---
paths:
  - "support/scripts/land.sh"
  - "support/scripts/full-verify.sh"
---

# Landing and full verify

- **Workers verify narrowly; the landing queue verifies lightly; a full verify runs behind a watermark.**
  A worker verifies via `worker-finish.sh`: `unit-test --changed-tests`, plus the architecture tests, the iOS
  test compile of changed KMP modules and `verifyRoborazziDebug` when the diff warrants — no full suite,
  no emulator/simulator lease unless the brief needs a screenshot. `lint` covers changed files only
  (`land.sh` passes `--base` so it lints just the batch's commits). `support/scripts/land.sh <branch>... [--close N|BRANCH:N ...]`
  cherry-picks each approved branch onto `origin/main`, runs a light verify once under `machine-lock`
  (Android: `lint` (check only), `unit-test --changed`, `:android:architecture-tests:testDebugUnitTest` (always, so a layer violation fails the batch that adds it; its own phase, so a failure there still runs assembleDebug), a compile of dependent modules' test sources when domain/shared/core/commonMain sources changed, + assembleDebug; one automatic retry per verify run (so per bisect iteration) with `-Pkotlin.incremental=false` on an `Incremental compilation failed` flake; iOS, only when the picked commits touch `ios/`,
  `shared/`, `android/domain|presentation|core`, or a KMP module's commonMain/commonTest/iosMain/iosTest: framework build, `:<module>:iosSimulatorArm64Test` for just the KMP modules whose commonMain/commonTest/iosMain/iosTest changed (none changed, no Kotlin/Native tests) + app build and just the `S2Tests` classes
  mapped from the changed files), pushes (if another session pushed meanwhile, it rebases onto the new `origin/main` and pushes again, re-verifying once only when the incoming commits touch the batch's files; a push that still fails resets the checkout to `origin/main`; so does any other exit after the picks and before a successful push, via an EXIT trap, #867; `--no-push`/`--dry-run` deliberately keep the picks for inspection, so `git reset --hard origin/main` before the next landing), closes issues and cleans up the landed worktrees (`--close
  BRANCH:N` closes only when BRANCH landed; a bare `--close N` only when every branch landed). Run it as a
  `longjob.sh` batch, never twice for the same batch. `support/scripts/full-verify.sh` (via `longjob.sh
  start full-verify -- ...`) runs the whole suites (including `iosSimulatorArm64Test`) at `origin/main` and records the sha as the watermark;
  `--status` shows how far main is past it, SessionStart prints the same, and `/deploy-android` runs it
  before tagging if the watermark isn't the release commit.
- **Worker worktree pool (#923).** `support/scripts/worktree-pool.sh lease <name>` hands a worker one of `S2_WORKTREE_POOL_SIZE`
  (default 5) locked, reusable slots `.claude/worktrees/pool-<k>` on a fresh `worktree-<name>` branch at `origin/main`, so the Gradle
  configuration cache, `build/`, `ios/build/DerivedData` and Shared.framework stay warm (tracked changes and untracked non-ignored
  files are discarded at lease; ignored files stay). It prints the slot dir on stdout; exit 3 means every slot is leased, so the caller
  falls back to a plain new worktree. Re-leasing a branch already in a slot returns that slot untouched. `land.sh` calls `release`
  (detaches the slot, drops the lease) and then deletes the branch; `worktree-clean.sh` never removes a slot and `reap`s leases whose
  branch is gone. `list` shows slot state, `slot-of <branch>` finds one. If a slot misbehaves (a stale ignored artifact), run
  the recovery command the script prints on failure; the next lease recreates it cold.
