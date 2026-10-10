---
paths:
  - ".claude/land.conf"
  - "support/scripts/land-verify"
  - "support/scripts/full-verify.sh"
---

# Landing and full verify

- **Workers verify narrowly; the landing queue verifies lightly; a full verify runs behind a watermark.**
  A worker verifies via `worker-finish.sh`: `unit-test --changed-tests`, plus the architecture tests, the iOS
  test compile of changed KMP modules and `verifyRoborazziDebug` when the diff warrants — no full suite,
  no emulator/simulator lease unless the brief needs a screenshot.
- **Landing is the shared `land` tool** (`land --help`; config `.claude/land.conf`, verify `support/scripts/land-verify`).
  Run it from the landing worktree (`git worktree add --detach .claude/worktrees/landing origin/main`, once), as a detached job,
  never twice for the same batch: `detach start land -- land <branch>... [BRANCH:N ...]` in `.claude/worktrees/landing`,
  then `detach wait land`. `BRANCH:N` closes issue N only when BRANCH landed; a bare `--close N` only when every branch landed.
  The verify is light: Android `lint` (check only), `unit-test --changed`, `:android:architecture-tests:testDebugUnitTest`
  (always, so a layer violation fails the batch that adds it), a compile of dependent modules' test sources when
  domain/shared/core/commonMain sources changed, and assembleDebug; iOS only when the picked commits touch `ios/`, `shared/`,
  `android/domain|presentation|core` or a KMP module's commonMain/commonTest/iosMain/iosTest: framework build,
  `:<module>:iosSimulatorArm64Test` for the KMP modules that changed, app build and the `S2Tests` classes
  `ios-tests-for` maps from the changed files. `land-verify` honours `LAND_PHASES` and `LAND_MODE=confirm` + `LAND_ONLY`
  (reruns just the named failing tests; its `!!` test names are `test:<Class.method>` with `%20` for spaces) and retries an
  `Incremental compilation failed` flake with `-Pkotlin.incremental=false`. After the push, a best-effort iPhone install
  (`post_land_hook`) runs detached when the landing touched what the phone runs and the phone is reachable.
- **Full verify.** `support/scripts/full-verify.sh` (via `detach start full-verify -- ...`) runs the whole suites
  (including `iosSimulatorArm64Test`) at `origin/main` and records the sha as the watermark; `--status` shows how far main
  is past it, SessionStart prints the same, and `/deploy-android` runs it before tagging if the watermark isn't the release commit.
- **Worker worktree pool (#923).** `support/scripts/worktree-pool.sh lease <name>` hands a worker one of `S2_WORKTREE_POOL_SIZE`
  (default 5) locked, reusable slots `.claude/worktrees/pool-<k>` on a fresh `worktree-<name>` branch at `origin/main`, so the Gradle
  configuration cache, `build/`, `ios/build/DerivedData` and Shared.framework stay warm (tracked changes and untracked non-ignored
  files are discarded at lease; ignored files stay). It prints the slot dir on stdout; exit 3 means every slot is leased, so the caller
  falls back to a plain new worktree. Re-leasing a branch already in a slot returns that slot untouched. `land` calls `release`
  (detaches the slot, drops the lease) and then deletes the branch; `worktree-clean.sh` never removes a slot and `reap`s leases whose
  branch is gone. `list` shows slot state, `slot-of <branch>` finds one. If a slot misbehaves (a stale ignored artifact), run
  the recovery command the script prints on failure; the next lease recreates it cold.
