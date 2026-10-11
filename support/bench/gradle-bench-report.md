# Gradle settings benchmark (devtools #15): partial, contaminated

Status: **inconclusive.** One scenario family ran, on a machine that was not quiet. No setting is recommended on this
data; re-run on a quiet Mac (see "To finish").

## Setup

- Mac, 10 cores, 32 GB. gradle-profiler 0.26.0 (already installed, nothing installed for this). Gradle 9.8.0, daemon JBR 21.
- `support/bench/gradle.scenarios` (scenarios) and `support/bench/gradle-bench.sh` (one `lease --class build` per
  profiler invocation; output in `/tmp/gradle-bench/<scenario>/benchmark.csv`).
- Configs are passed as `--max-workers`, `-Dorg.gradle.jvmargs`, `-Pkotlin.daemon.jvmargs`, `-Ps2.testForks`; these beat
  `~/.gradle/gradle.properties`, which was not touched. Baseline = the machine-wide file: Gradle 5g ParallelGC, Kotlin
  3g ParallelGC, 5 workers, 2 forks.
- Finding: the machine-wide `~/.gradle/gradle.properties` overrides the repo's `gradle.properties` for jvmargs,
  `kotlin.daemon.jvmargs` and `workers.max`, so the repo's 6g/4g/6 values are not what builds on this Mac actually use.
- Test heap (`maxHeapSize`) is hard-coded in `build.gradle.kts`, so the 1g/1.5g/2g axis cannot be varied without an edit; not run.
- Edits: ABI change to `Progress.kt` in `:android:domain`, task `:android:app:assembleDebug`.
- Cut: 1 warm-up, 6 iterations (after a first 3-iteration run). JDK 25 daemon, the non-ABI/test/no-op scenarios and fork
  counts are defined in the scenarios file but not run.

## Results (ABI change, assembleDebug, seconds, measured builds 1-6)

| config | median | min | max |
|---|---|---|---|
| base (5g Par, K3g, w5) | 16.8 | 11.4 | 37.3 |
| heap 4g | 17.1 | 9.4 | 42.1 |
| heap 6g (Kotlin 4g) | 14.1 | 13.7 | 27.0 |
| G1 (Gradle + Kotlin) | 9.4 | 8.1 | 16.3 |
| workers 4 | 16.3 | 10.2 | 34.4 |
| workers 6 | 17.6 | 14.7 | 32.1 |
| workers 8 | not completed | | |

An initial uncontended-looking run of `abi-base` (3 iterations, 11.1 / 8.3 / 7.5 s) was much faster than the base row
above, which shows the noise. The first-ever warm-up took 773 s (empty gradle-profiler Gradle home in the worktree).

## Contamination

`lease stats build` before: 228 leases, slots held 19% of busy time; after: 266 leases, 28%. While the batch ran, the
other build slot was held by `swift test` jobs from other worktrees, and a `verify` and a `device` lease were active; load
average reached ~470. The build lease cap is 2, so one profiler lease is not exclusive; a quiet run needs both slots
held and no verify/device jobs. Within-config spread (up to 4x) exceeds every between-config difference, so the
apparent G1 win is not evidence: it most likely ran in a quieter window. The batch was killed during `abi-w8`.

## To finish

Hold both build slots (`lease --class build -- lease --class build -- ...` is not enough; nested calls reuse the
parent's slot, so use a second holder) when the machine is idle, then run
`support/bench/gradle-bench.sh --iterations 6 abi-base abi-g1 abi-heap4 abi-heap6 abi-w4 abi-w6 abi-w8 nonabi-base nonabi-g1
test-f1 test-f2 test-f3 noop-base`. Add a concurrency run (two worktrees, `memory_pressure` and `sysctl vm.swapusage`
before and after) for the top configs.

Confidence in any recommendation: none yet.
