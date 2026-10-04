#!/usr/bin/env bash
# Tests for land.sh's verify-log matchers (#824): ic_failure and verify_env_failure must match on logs
# well over the 64KB pipe buffer (grep -q exiting early must not SIGPIPE a pipeline under pipefail),
# and ic_failure must see the IC line in a build-brief "Raw log:" file when the console dropped it.
# Run directly: support/scripts/land_test.sh
set -uo pipefail

LAND="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/land.sh"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
fails=0

# Pull just the two matcher functions out of land.sh (the script itself runs a landing when sourced).
eval "$(awk '/^(verify_env_failure|ic_failure)\(\) \{/{p=1} p{print} p&&/^\}/{p=0}' "$LAND")"

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

[ "$fails" -eq 0 ] || { echo "$fails failed"; exit 1; }
