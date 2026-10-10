#!/usr/bin/env bash
# Tests for land-verify's confirm-mode name mapping (the pure helpers, no builds).
# Run directly: support/scripts/land-verify_test.sh
set -uo pipefail

SCRIPT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/land-verify"
fails=0

# Pull just the helper functions out of land-verify (the script itself runs a verify when sourced).
eval "$(awk '/^(test_class|module_for_path|tracked_files|test_module|ios_class|plan_confirm)\(\) \{/{p=1} p{print} p&&/^\}/{p=0}' "$SCRIPT")"
confirm=0 unit_ok=1 ios_ok=1 unit_plan="" arch_plan="" ios_plan=()

tracked_files() {
  printf '%s\n' \
    android/playback/src/test/java/com/x/queue/QueueTest.kt \
    android/architecture-tests/src/test/kotlin/com/x/LayerTest.kt \
    android/mediaprovider/core/src/test/java/com/x/ImporterTest.kt \
    android/app/src/main/java/com/x/QueueTest.kt
}

expect() { # <name> <want> <got>
  if [ "$2" = "$3" ]; then echo "ok   $1"; else echo "FAIL $1: want '$2', got '$3'"; fails=$((fails+1)); fi
}

expect "test_class: plain" "com.x.queue.QueueTest" "$(test_class 'com.x.queue.QueueTest.adds a song')"
expect "test_class: nested keeps its outer" 'com.x.A$Inner' "$(test_class 'com.x.A$Inner.works')"
expect "test_class: dots inside the method name" "com.x.QueueTest" "$(test_class 'com.x.QueueTest.plays 1.5 seconds')"
expect "test_class: no method is unmappable" "" "$(test_class 'com.x.QueueTest')"
expect "module_for_path: nested module" ":android:mediaprovider:core" "$(module_for_path android/mediaprovider/core/src/test/Foo.kt)"
expect "test_module: finds the test source, not main" ":android:playback" "$(test_module com.x.queue.QueueTest)"
expect "test_module: nested class uses the outer file" ":android:mediaprovider:core" "$(test_module 'com.x.ImporterTest$Inner')"
expect "test_module: unknown class" "" "$(test_module com.x.GhostTest)"
expect "ios_class" "LibraryListTests" "$(ios_class 'LibraryListTests.songRowDrawsArtwork()')"

LAND_MODE=confirm LAND_ONLY='test:com.x.queue.QueueTest.adds%20a%20song test:com.x.LayerTest.noCycles ios-test:FooTests.bar()' plan_confirm
expect "plan_confirm: unit plan decodes spaces" "$(printf ':android:playback\tcom.x.queue.QueueTest.adds a song')" "$(printf '%s' "$unit_plan")"
expect "plan_confirm: architecture split out" "$(printf ':android:architecture-tests\tcom.x.LayerTest.noCycles')" "$(printf '%s' "$arch_plan")"
expect "plan_confirm: ios class" "FooTests" "${ios_plan[*]}"
expect "plan_confirm: all mapped" "1 1 1" "$confirm $unit_ok $ios_ok"

confirm=0 unit_ok=1 ios_ok=1 unit_plan="" arch_plan="" ios_plan=()
LAND_MODE=confirm LAND_ONLY='test:com.x.GhostTest.nope ios-test:???' plan_confirm
expect "plan_confirm: unmapped names run their phases whole" "1 0 0" "$confirm $unit_ok $ios_ok"

confirm=0 unit_ok=1 ios_ok=1 unit_plan="" arch_plan="" ios_plan=()
LAND_MODE=batch LAND_ONLY='test:com.x.queue.QueueTest.a' plan_confirm
expect "plan_confirm: other modes do nothing" "0" "$confirm"

[ "$fails" -eq 0 ] && echo "all passed" || { echo "$fails failed"; exit 1; }
