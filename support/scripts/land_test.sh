#!/usr/bin/env bash
# Tests for land.sh's verify-log matchers (#824) and its pre-existing-failure classification (#829): ic_failure and verify_env_failure must match on logs
# well over the 64KB pipe buffer (grep -q exiting early must not SIGPIPE a pipeline under pipefail),
# and ic_failure must see the IC line in a build-brief "Raw log:" file when the console dropped it.
# Run directly: support/scripts/land_test.sh
set -uo pipefail

LAND="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/land.sh"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
fails=0

# Pull just the matcher functions out of land.sh (the script itself runs a landing when sourced).
eval "$(awk '/^(verify_env_failure|ic_failure|failure_sig|new_failures|run_verify|verify_changed_files|decl_names|ios_tests_raw|ios_tests_for|checkout_back|ensure_base_sig|verify_blame|verify_step)\(\) \{/{p=1} p{print} p&&/^\}/{p=0}' "$LAND")"

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

[ "$fails" -eq 0 ] || { echo "$fails failed"; exit 1; }
