#!/usr/bin/env bash
# Tests for land.sh's verify-log matchers (#824), its pre-existing-failure classification (#829), the
# Kotlin/Native test scoping and the push-race recovery (#898). The matchers: ic_failure and verify_env_failure must match on logs
# well over the 64KB pipe buffer (grep -q exiting early must not SIGPIPE a pipeline under pipefail),
# and ic_failure must see the IC line in a build-brief "Raw log:" file when the console dropped it.
# Run directly: support/scripts/land_test.sh
set -uo pipefail

LAND="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/land.sh"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
fails=0

# Pull just the matcher functions out of land.sh (the script itself runs a landing when sourced).
eval "$(awk '/^(verify_env_failure|ic_failure|failure_sig|new_failures|run_verify|verify_changed_files|decl_names|ios_tests_raw|ios_tests_for|checkout_back|ensure_base_sig|verify_blame|verify_step|verify_phases|verify_ios|kmp_native_tasks|run_git|push_once|rebase_onto_new_main|push_landed|reset_after_push_failure)\(\) \{/{p=1} p{print} p&&/^\}/{p=0}' "$LAND")"

check() { # <name> <expected-rc> <cmd...>
  local name=$1 want=$2 rc=0; shift 2
  "$@" || rc=$?
  if [ "$rc" -eq "$want" ]; then echo "ok   $name"; else echo "FAIL $name (rc=$rc, want $want)"; fails=$((fails+1)); fi
}

filler() { head -c 150000 /dev/zero | tr '\0' 'x' | fold -w 100; }

{ echo "w: Incremental compilation failed: could not close caches"; filler; } > "$TMP/ic_big.log"
check "ic_failure matches IC line ahead of 150KB" 0 ic_failure "$TMP/ic_big.log" 1

{ echo "w: Incremental compilation failed"; echo "e: file:///x/Foo.kt:1:1 boom"; filler; } > "$TMP/ic_err.log"
check "ic_failure rejects a real compiler error" 1 ic_failure "$TMP/ic_err.log" 1

{ echo "BUILD FAILED"; filler; } > "$TMP/none.log"
check "ic_failure rejects a log without the IC line" 1 ic_failure "$TMP/none.log" 1

{ echo "Execution failed: JdkImageTransform"; filler; } > "$TMP/env_big.log"
check "verify_env_failure matches env line ahead of 150KB" 0 verify_env_failure "$TMP/env_big.log" 1

echo "w: Incremental compilation failed" > "$TMP/raw.log"
printf 'BUILD FAILED in 3s\nRaw log: %s\n' "$TMP/raw.log" > "$TMP/condensed.log"
check "ic_failure follows build-brief Raw log path" 0 ic_failure "$TMP/condensed.log" 1

# failure_sig (#829): per-phase failures, line/column numbers dropped; only failed phases count.
cat > "$TMP/sig.log" <<'LOG'
noise before
verify: == lint
/r/B.kt:7:1: Unexpected blank line (no-blank-line)
verify: -- lint failed: ktlint
verify: == unit-tests
Failed tests:
com.x.FooTest.bar: expected <a> but was <b>
com.x.FooTest.baz: boom
+3 more
e: file:///r/A.kt:10:5 Unresolved reference
* What went wrong:
Execution failed for task ':android:app:testDebugUnitTest'.
> There were 4 failing tests.

verify: -- unit-tests failed: android unit tests
verify: == assembleDebug
e: file:///r/Ignored.kt:1:1 printed by a phase that passed
verify: == end
LOG
T=$'\t'
want=$(printf '%s\n' "lint${T}FAILED" "lint${T}lint /r/B.kt: Unexpected blank line (no-blank-line)" \
  "unit-tests${T}FAILED" "unit-tests${T}e: file:///r/A.kt Unresolved reference" \
  "unit-tests${T}test com.x.FooTest.bar" "unit-tests${T}test com.x.FooTest.baz" "unit-tests${T}tests +3 more" \
  "unit-tests${T}gradle Execution failed for task ':android:app:testDebugUnitTest'." \
  "unit-tests${T}gradle > There were N failing tests." | sort -u)
got=$(failure_sig "$TMP/sig.log" 1)
check "failure_sig tags failures by phase, drops passed phases" 0 test "$got" = "$want"
[ "$got" = "$want" ] || diff <(echo "$want") <(echo "$got")
check "failure_sig honours from-byte (nothing after it: incomplete)" 0 test "$(failure_sig "$TMP/sig.log" 99999)" = "verify${T}INCOMPLETE"
printf 'verify: == unit-tests\n' > "$TMP/killed.log"
check "failure_sig marks a run without 'verify: == end' incomplete" 0 test "$(failure_sig "$TMP/killed.log" 1)" = "verify${T}INCOMPLETE"
{ echo "verify: == unit-tests"; echo "BUILD FAILED"; echo "Raw log: $TMP/rawsig.log"; echo "verify: -- unit-tests failed: x"; echo "verify: == end"; } > "$TMP/brief.log"
printf 'com.x.BarTest > qux FAILED\nx/y/BarTest_qux_compare.png written\n' > "$TMP/rawsig.log"
check "failure_sig reads build-brief Raw log files" 0 test "$(failure_sig "$TMP/brief.log" 1)" = "$(printf '%s\n' "unit-tests${T}FAILED" "unit-tests${T}roborazzi BarTest_qux" "unit-tests${T}test com.x.BarTest > qux FAILED" | sort -u)"

# iOS: xcodebuild's "Failing tests:" block and Swift Testing's failure line give per-test signatures.
cat > "$TMP/ios.log" <<'LOG'
verify: == ios
Failing tests:
	LibraryListTests.songAndAlbumRowsDrawTheirArtwork()
	LibrarySortMenuTests.aSongRowShowsItsPlayCountUnderThatSort()

** TEST FAILED **
✘ Test foo() failed after 0.012 seconds with 1 issue.
verify: -- ios failed: ios build/tests
verify: == end
LOG
want=$(printf '%s\n' "ios${T}FAILED" "ios${T}ios test LibraryListTests.songAndAlbumRowsDrawTheirArtwork()" \
  "ios${T}ios test LibrarySortMenuTests.aSongRowShowsItsPlayCountUnderThatSort()" "ios${T}✘ Test foo() failed" | sort -u)
got=$(failure_sig "$TMP/ios.log" 1)
check "failure_sig reads iOS failing tests" 0 test "$got" = "$want"
[ "$got" = "$want" ] || diff <(echo "$want") <(echo "$got")
check "new_failures: iOS failures also on main are pre-existing" 0 test -z "$(new_failures "$got" "$got")"
check "new_failures: a new iOS test failure is new" 0 test -n "$(new_failures "$got" "$(printf '%s\n' "ios${T}FAILED" "ios${T}ios test LibraryListTests.songAndAlbumRowsDrawTheirArtwork()")")"

# iOS test mapping (#857): changed shared sources also select tests that mention a type they declare.
IOS_TESTS_MAX=15
FX="$TMP/fx"; mkdir -p "$FX/ios/S2Tests" "$FX/ios/S2/Artwork" "$FX/shared/src/commonMain"
printf 'import S2\nfinal class LibraryListTests { let a: Album }\n' > "$FX/ios/S2Tests/LibraryListTests.swift"
printf 'final class OtherTests { let r = RemoteArtwork() }\n' > "$FX/ios/S2Tests/OtherTests.swift"
printf 'final class UnrelatedTests { let l = Label(); let v = View() }\n' > "$FX/ios/S2Tests/UnrelatedTests.swift"
printf 'package x\ndata class Album(val id: String)\nenum class Kind { A }\n' > "$FX/shared/src/commonMain/Album.kt"
printf 'package x\nfun helper() = 1\n' > "$FX/shared/src/commonMain/Helpers.kt"
printf 'struct RemoteArtwork { enum Label { } }\nextension View { }\nextension Album { }\n' > "$FX/ios/S2/Artwork/RemoteArtwork.swift"
ios_map() { (cd "$FX" && ios_tests_for "$@" | paste -sd, -); }
check "ios map: shared model selects tests that mention its type" 0 test "$(ios_map shared/src/commonMain/Album.kt)" = "LibraryListTests"
check "ios map: shared Swift source selects tests by its declared types" 0 test "$(ios_map ios/S2/Artwork/RemoteArtwork.swift)" = "OtherTests"
check "ios map: nested and extension declarations select no test" 0 test "$(ios_map ios/S2/Artwork/RemoteArtwork.swift)" != "OtherTests,UnrelatedTests"

# decl_names (#857 follow-up): only unindented declarations, `extension` lines skipped, leading
# @Attr / @Attr(...) tokens allowed before the modifiers.
DN="$TMP/dn"; mkdir -p "$DN"
printf '%s\n' \
  'package x' \
  '@file:JvmName("Fx")' \
  '@Serializable(with = F::class)' \
  'sealed interface Kind' \
  '@Suppress("a") @Deprecated("b") data class Tag(val t: String)' \
  'class Outer(val id: String) {' \
  '    enum class Search { A }' \
  '    object All' \
  '}' \
  'fun helper() = 1' > "$DN/Model.kt"
check "decl_names: kotlin top-level only, attributes with args allowed" 0 test "$(decl_names "$DN/Model.kt" | paste -sd, -)" = "Kind,Outer,Tag"
printf '%s\n' \
  'import S2' \
  '@MainActor final class PlayerModel { enum Play { } }' \
  'private struct Hidden { }' \
  'indirect enum Tree { case node(Tree) }' \
  'typealias ID = String' \
  'extension View { func x() {} }' \
  'extension String { }' > "$DN/Swift.swift"
check "decl_names: swift modifiers and attributes, extensions skipped" 0 test "$(decl_names "$DN/Swift.swift" | paste -sd, -)" = "Hidden,ID,PlayerModel,Tree"
printf '    class Indented { }\n\tstruct Tabbed { }\n' > "$DN/NestedOnly.swift"
check "decl_names: only nested declarations declares nothing" 0 test -z "$(decl_names "$DN/NestedOnly.swift")"
check "ios map: shared source declaring no type runs the whole target" 0 test "$(ios_map shared/src/commonMain/Helpers.kt)" = "--all"
check "ios map: a deleted shared file falls back to its stem" 0 test "$(ios_map shared/src/commonMain/Gone.kt)" = ""
check "ios map: Playback adds the package run" 0 test "$(ios_map shared/src/commonMain/Album.kt ios/Playback/X.swift)" = "LibraryListTests,--package"
for i in $(seq 1 16); do printf 'final class T%dTests { let a: Album }\n' "$i" > "$FX/ios/S2Tests/T${i}Tests.swift"; done
check "ios map: more than 15 mapped classes runs the whole target" 0 test "$(ios_map shared/src/commonMain/Album.kt)" = "--all"

# verify_step (#829): a failed phase is recorded and the next one still runs.
VERIFY_FAILED=0
steps=$( { verify_step a "x" false; verify_step b "y" true; echo "rc=$VERIFY_FAILED"; } )
check "verify_step runs on past a failed phase" 0 test "$steps" = "$(printf '%s\n' 'verify: == a' 'verify: -- a failed: x' 'verify: == b' 'rc=1')"

# verify_phases (#829): a layer-rule failure still runs assembleDebug, as its own phase.
VFX="$TMP/vfx"
mkdir -p "$VFX/support/scripts" "$VFX/ios/scripts" "$VFX/bin"
for f in lint unit-test; do printf '#!/bin/sh\nexit 0\n' > "$VFX/support/scripts/$f"; done
printf '#!/bin/sh\necho "gradle $*" >> "%s"\ncase "$*" in *architecture-tests*) exit 1;; esac\n' "$TMP/gradle.calls" \
  > "$VFX/support/scripts/remote-build.sh"
# The iOS link/test call: like Gradle under -q, it prints Kotlin's IC warning only without -q.
printf '#!/bin/sh\nfor a in "$@"; do [ "$a" = -q ] && exit 1; done\necho "w: Incremental compilation failed: caches"\nexit 1\n' \
  > "$VFX/ios/scripts/build-framework.sh"
printf '#!/bin/sh\nexit 1\n' > "$VFX/ios/scripts/lease-sim.sh"
printf '#!/bin/sh\nexit 0\n' > "$VFX/bin/xcodegen"
chmod +x "$VFX"/support/scripts/* "$VFX"/ios/scripts/* "$VFX"/bin/*
phases=$( cd "$VFX" && verify_phases base 0; echo "rc=$?" )
check "verify_phases: architecture failure is its own phase" 0 grep -qx 'verify: -- architecture failed: layer rules (:android:architecture-tests, #871)' <<< "$phases"
check "verify_phases: assembleDebug still runs after it" 0 grep -q ':android:app:assembleDebug' "$TMP/gradle.calls"
check "verify_phases: every phase ran, and failed overall" 0 test "$(grep -e '^verify: == assembleDebug' -e '^verify: == end' -e '^rc=' <<< "$phases")" = "$(printf '%s\n' 'verify: == assembleDebug' 'verify: == end' 'rc=1')"
# verify_ios (#824): the framework link's IC warning reaches the log ic_failure reads.
( cd "$VFX" && HOME="$TMP/home" PATH="$VFX/bin:$PATH" verify_ios ) > "$TMP/ios.log" 2>&1
check "verify_ios: IC warning from the framework link reaches the log" 0 ic_failure "$TMP/ios.log" 1

# new_failures (#829): per-phase comparison, failing safe.
sig() { printf '%s\n' "$@"; }
BASE=$(sig "unit-tests${T}FAILED" "unit-tests${T}test com.x.FooTest.bar")
check "new_failures: same failure on main is pre-existing" 0 test -z "$(new_failures "$BASE" "$BASE")"
check "new_failures: empty batch signature is new" 0 test -n "$(new_failures "" "$BASE")"
check "new_failures: incomplete batch run is new" 0 test -n "$(new_failures "verify${T}INCOMPLETE" "verify${T}INCOMPLETE")"
check "new_failures: same phase, different error is new" 0 test -n "$(new_failures "$(sig "unit-tests${T}FAILED" "unit-tests${T}test com.x.FooTest.baz")" "$BASE")"
check "new_failures: failed phase with only its marker is new" 0 test -n "$(new_failures "unit-tests${T}FAILED" "$BASE")"
check "new_failures: same error in another phase is new" 0 test -n "$(new_failures "$(sig "assembleDebug${T}FAILED" "assembleDebug${T}test com.x.FooTest.bar")" "$BASE")"
check "new_failures: a subset of main's failures is pre-existing" 0 test -z "$(new_failures "$BASE" "$(sig "$BASE" "lint${T}FAILED" "lint${T}lint x")")"
check "new_failures: anything vs a passing main is new" 0 test -n "$(new_failures "$BASE" "")"

# verify_blame end to end (#829), with verify_once stubbed: the batch's output and origin/main's go
# to the same log, so the batch's signature must not be read from origin/main's offset.
log() { printf '%s\n' "$*" >> "$LOG"; }
say() { log "$@"; }
verify_once() {  # <files-file|""> ...: "" = the batch, otherwise origin/main for the batch's tasks
  if [ -n "$1" ]; then cat "$BASE_OUT" >> "$LOG"; return "$BASE_RC"; fi
  cat "$BATCH_OUT" >> "$LOG"; return "$BATCH_RC"
}
REPO="$TMP/repo"
git init -q -b work "$REPO"
gitc() { git -C "$REPO" -c core.hooksPath=/dev/null -c commit.gpgsign=false -c user.name=t -c user.email=t@t "$@"; }
echo a > "$REPO/README"; gitc add README; gitc commit -qm base
ORIGIN_MAIN_SHA=$(git -C "$REPO" rev-parse HEAD)
mkdir -p "$REPO/android"; echo b > "$REPO/android/x.kt"; gitc add android; gitc commit -qm batch
CUR_BRANCH=work TIMED_OUT_RC=125 REPO_ROOT=$REPO BASE_SIG_DONE=0 BASE_SIG=""
out() { printf '%s\n' "verify: == unit-tests" "$@" "verify: -- unit-tests failed: tests" "verify: == assembleDebug" "verify: == end" > "$1.tmp"; mv "$1.tmp" "$1"; }
blame_case() {  # <batch-rc> <base-rc>: verify_blame's rc, in a subshell so BASE_SIG starts unset
  ( cd "$REPO" && LOG="$TMP/land.log" && : > "$LOG" && BATCH_RC=$1 BASE_RC=$2 && verify_blame >/dev/null; )
}
BATCH_OUT="$TMP/batch.out" BASE_OUT="$TMP/base.out"
out "$BATCH_OUT" "Failed tests:" "com.x.NewTest.a: boom" ""
out "$BASE_OUT" "Failed tests:" "com.x.OldTest.a: boom" ""
check "verify_blame: new failure blamed though main fails too (offsets independent)" 1 blame_case 1 1
out "$BATCH_OUT" "Failed tests:" "com.x.OldTest.a: boom" ""
check "verify_blame: only main's failure, every phase ran: not blamed" 0 blame_case 1 1
check "verify_blame: failure with main passing is blamed" 1 blame_case 1 0
: > "$BATCH_OUT"
check "verify_blame: killed verify (rc 143, no output) is blamed" 1 blame_case 143 1
check "verify_blame: HEAD is back on the batch branch" 0 test "$(git -C "$REPO" rev-parse --abbrev-ref HEAD)" = work

# Kotlin/Native test scoping: only KMP modules whose commonMain/commonTest/iosMain/iosTest changed,
# mapped by unit-test's path -> module helpers against this repo's real settings and build files.
REPO_ROOT="$(cd "$(dirname "$LAND")/../.." && pwd)"
kmp() { kmp_native_tasks "$@" | paste -sd, -; }
check "kmp tasks: a commonMain change maps to its module" 0 test "$(kmp android/scrobbling/src/commonMain/kotlin/X.kt)" = ":android:scrobbling:iosSimulatorArm64Test"
check "kmp tasks: nested module wins over its parent dir" 0 test "$(kmp android/playback/core/src/commonTest/kotlin/X.kt)" = ":android:playback:core:iosSimulatorArm64Test"
check "kmp tasks: iosMain and iosTest count" 0 test "$(kmp shared/src/iosTest/kotlin/A.kt android/presentation/src/iosMain/kotlin/B.kt)" = ":android:presentation:iosSimulatorArm64Test,:shared:iosSimulatorArm64Test"
check "kmp tasks: androidMain/androidHostTest and build files map to nothing" 0 test -z "$(kmp android/domain/src/androidMain/kotlin/A.kt android/domain/src/androidHostTest/kotlin/B.kt android/domain/build.gradle.kts)"
check "kmp tasks: a non-KMP module maps to nothing" 0 test -z "$(kmp android/playback/src/main/java/A.kt android/app/src/main/java/B.kt)"
check "kmp tasks: docs, scripts and nothing at all map to nothing" 0 test -z "$(kmp docs/a.md support/scripts/land.sh; kmp)"
check "kmp tasks: one task per module, sorted" 0 test "$(kmp android/domain/src/commonTest/kotlin/B.kt android/domain/src/commonMain/kotlin/A.kt android/core/src/commonMain/kotlin/C.kt)" = ":android:core:iosSimulatorArm64Test,:android:domain:iosSimulatorArm64Test"

# run_verify hands the changed KMP modules to the iOS phase, which a KMP-only change now triggers.
verify_args() {  # <changed path>...: the args run_verify gives verify_once (after the files-file)
  ( cd "$TMP" && LOG="$TMP/rv.log" && : > "$LOG" && printf '%s\n' "$@" > "$TMP/rv.files" \
    && verify_once() { shift; echo "$*"; } && run_verify "$TMP/rv.sig" "$TMP/rv.files" )
}
check "run_verify: a KMP module's commonMain alone runs the iOS phase with its task" 0 test "$(verify_args android/scrobbling/src/commonMain/kotlin/X.kt)" = "1 --kmp=:android:scrobbling:iosSimulatorArm64Test"
check "run_verify: an androidMain change skips the iOS phase" 0 test "$(verify_args android/scrobbling/src/androidMain/kotlin/X.kt)" = "0"
check "run_verify: domain androidMain still runs the iOS phase, without Kotlin/Native tests" 0 test "$(verify_args android/domain/src/androidMain/kotlin/ZzNoSuchType.kt)" = "1"

# verify_ios passes just those tasks to the framework build's Gradle call.
VI="$TMP/vi"; mkdir -p "$VI/ios/scripts" "$VI/bin"
printf '#!/bin/sh\necho "$*" > "%s"\nexit 1\n' "$TMP/framework.args" > "$VI/ios/scripts/build-framework.sh"
printf '#!/bin/sh\nexit 1\n' > "$VI/ios/scripts/lease-sim.sh"
printf '#!/bin/sh\nexit 0\n' > "$VI/bin/xcodegen"
chmod +x "$VI"/ios/scripts/* "$VI"/bin/*
( cd "$VI" && HOME="$TMP/home" PATH="$VI/bin:$PATH" verify_ios --kmp=:a:iosSimulatorArm64Test --kmp=:b:iosSimulatorArm64Test SomeTests ) > /dev/null 2>&1
check "verify_ios: runs only the given Kotlin/Native tasks" 0 test "$(cat "$TMP/framework.args")" = ":a:iosSimulatorArm64Test :b:iosSimulatorArm64Test"
( cd "$VI" && HOME="$TMP/home" PATH="$VI/bin:$PATH" verify_ios SomeTests ) > /dev/null 2>&1
check "verify_ios: no KMP task means no iosSimulatorArm64Test" 0 test -z "$(cat "$TMP/framework.args")"

# Push race (#898): origin/main moves during the verify. A bare origin, the landing checkout and a
# second session's clone, fresh for each case; verify_blame is stubbed to count calls.
RACE_N=0 RACE=""
race_cfg() { git -C "$1" config core.hooksPath /dev/null; git -C "$1" config user.name t; git -C "$1" config user.email t@t; git -C "$1" config commit.gpgsign false; }
race_setup() {  # <batch-line>: the landing checkout has one batch commit changing that line of android/a.kt
  RACE_N=$((RACE_N + 1)); RACE="$TMP/race$RACE_N"; mkdir -p "$RACE"
  git init -q --bare -b main "$RACE/origin.git"
  git clone -q "$RACE/origin.git" "$RACE/seed" 2>/dev/null; race_cfg "$RACE/seed"
  mkdir -p "$RACE/seed/android"; seq 1 20 > "$RACE/seed/android/a.kt"
  git -C "$RACE/seed" add -A; git -C "$RACE/seed" commit -qm base; git -C "$RACE/seed" push -q origin HEAD:main
  git clone -q "$RACE/origin.git" "$RACE/lander"; git clone -q "$RACE/origin.git" "$RACE/other"
  race_cfg "$RACE/lander"; race_cfg "$RACE/other"
  sed -i.bak "${1}s/.*/batch/" "$RACE/lander/android/a.kt"
  git -C "$RACE/lander" commit -qm batch android/a.kt
}
race_push_other() {  # <file> [<line>]: the second session lands a commit changing <file> (line <line> of it)
  if [ -n "${2:-}" ]; then sed -i.bak "${2}s/.*/other/" "$RACE/other/$1"
  else mkdir -p "$(dirname "$RACE/other/$1")"; echo other > "$RACE/other/$1"; fi
  git -C "$RACE/other" add "$1"; git -C "$RACE/other" commit -qm other; git -C "$RACE/other" push -q origin HEAD:main
}
race_run() {  # <verify_blame rc>: push_landed, then reset_after_push_failure on a failure, in the landing checkout
  ( cd "$RACE/lander" && LOG="$RACE/land.log" && : > "$LOG" && VB_RC=$1 && VB_N=0 && IN_PLACE=${IN_PLACE:-0} \
    && CUR_BRANCH=main && ORIGIN_MAIN_SHA=$(git -C "$RACE/seed" rev-parse HEAD) \
    && verify_blame() { VB_N=$((VB_N + 1)); return "$VB_RC"; } \
    && { rc=0; push_landed || rc=$?; [ "$rc" -eq 0 ] || reset_after_push_failure; echo "rc=$rc verifies=$VB_N"; } )
}
origin_has() { git -C "$RACE/origin.git" show main:"$1" 2>/dev/null | grep -qx "$2"; }
lander_unpushed() { git -C "$RACE/lander" fetch -q origin; git -C "$RACE/lander" rev-list --count origin/main..HEAD; }

race_setup 1
check "push race: an unraced push goes straight through" 0 test "$(race_run 0)" = "rc=0 verifies=0"

race_setup 1; race_push_other docs/b.md
check "push race: disjoint incoming commits push without verifying again" 0 test "$(race_run 0)" = "rc=0 verifies=0"
check "push race: origin/main has both the batch and the incoming commit" 0 eval 'origin_has android/a.kt batch && origin_has docs/b.md other'

race_setup 1; race_push_other android/a.kt 15
check "push race: incoming commits on the batch's files verify once, then push" 0 test "$(race_run 0)" = "rc=0 verifies=1"
check "push race: the rebased batch kept both edits" 0 eval 'origin_has android/a.kt batch && origin_has android/a.kt other'

race_setup 1; race_push_other android/a.kt 15
check "push race: a failing re-verify pushes nothing" 0 test "$(race_run 1)" = "rc=1 verifies=1"
check "push race: origin/main lacks the batch" 1 origin_has android/a.kt batch
check "push race: final failure resets the checkout to origin/main" 0 test "$(lander_unpushed)" = 0

race_setup 1; race_push_other android/a.kt 1
check "push race: a conflicting rebase pushes nothing and verifies nothing" 0 test "$(race_run 0)" = "rc=1 verifies=0"
check "push race: no rebase left in progress" 1 test -e "$RACE/lander/.git/rebase-merge"
check "push race: a conflict also resets the checkout" 0 test "$(lander_unpushed)" = 0

race_setup 1; race_push_other android/a.kt 1
IN_PLACE=1 race_run 0 > /dev/null
check "push race: in place, a failed push leaves HEAD's commits alone" 0 test "$(lander_unpushed)" = 1

[ "$fails" -eq 0 ] || { echo "$fails failed"; exit 1; }
