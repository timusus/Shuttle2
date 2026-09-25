#!/usr/bin/env bash
# Stub tests for adb_retry (checks/_lib.sh), run with no device/emulator: a fake `adb` on PATH
# plays back scripted per-call stdout/stderr/exit-status, and a fake `remote-emu.sh` stands in for
# the real reconnect. Run directly: support/scripts/checks/_lib_test.sh
set -euo pipefail

REAL_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
TMP="$(mktemp -d)"
# Test 4 uses $$ as its watchdog duration so its `sleep` is unique to this run (no collision with a
# leftover orphan from a prior run within the same window); clean it up here in case the fallback
# regresses and leaves it running.
trap 'pkill -f "sleep $$\$" 2>/dev/null || true; rm -rf "$TMP"' EXIT

mkdir -p "$TMP/support/scripts/checks" "$TMP/bin" "$TMP/fake-adb"
cp "$REAL_ROOT/support/scripts/checks/_lib.sh" "$TMP/support/scripts/checks/_lib.sh"
cp "$REAL_ROOT/support/scripts/checks/_timeout_fallback.sh" "$TMP/support/scripts/checks/_timeout_fallback.sh"

cat >"$TMP/support/scripts/remote-emu.sh" <<'EOF'
#!/usr/bin/env bash
echo "fake-reconnect: called" >&2
exit 0
EOF
chmod +x "$TMP/support/scripts/remote-emu.sh"

# fake adb: plays back $FAKE_ADB_DIR/call<N>.{out,err,exit}, advancing a call counter each
# invocation. Files are written with printf so byte content (including "no trailing newline") is
# under the test's control. call<N>.sleep execs straight into `sleep <seconds>` (replacing this
# process, not forking a child) to simulate a hung adb call that the watchdog has to kill.
cat >"$TMP/bin/adb" <<'EOF'
#!/usr/bin/env bash
dir="$FAKE_ADB_DIR"
n=$(( $(cat "$dir/count" 2>/dev/null || echo 0) + 1 ))
echo "$n" >"$dir/count"
[ -f "$dir/call${n}.sleep" ] && exec sleep "$(cat "$dir/call${n}.sleep")"
[ -f "$dir/call${n}.out" ] && cat "$dir/call${n}.out"
[ -f "$dir/call${n}.err" ] && cat "$dir/call${n}.err" >&2
exit "$(cat "$dir/call${n}.exit" 2>/dev/null || echo 0)"
EOF
chmod +x "$TMP/bin/adb"

export PATH="$TMP/bin:$PATH"
# shellcheck source=/dev/null
source "$TMP/support/scripts/checks/_lib.sh" # its own BASH_SOURCE-derived CHECKS_ROOT resolves to $TMP

pass_count=0 fail_count=0
check() {
    local label="$1" ok="$2"
    if [ "$ok" = "1" ]; then
        echo "ok - $label"; pass_count=$((pass_count + 1))
    else
        echo "not ok - $label"; fail_count=$((fail_count + 1))
    fi
}

reset_fake_adb() { rm -rf "$FAKE_ADB_DIR"; mkdir -p "$FAKE_ADB_DIR"; }

# Test 1: first call fails offline, reconnect runs, second call succeeds -- the existing
# recover-and-retry path still works after the buffering change.
FAKE_ADB_DIR="$TMP/fake-adb/t1"; export FAKE_ADB_DIR
reset_fake_adb
printf 'error: device offline\n' >"$FAKE_ADB_DIR/call1.err"
echo 1 >"$FAKE_ADB_DIR/call1.exit"
printf 'OK\n' >"$FAKE_ADB_DIR/call2.out"
out="$(adb_retry shell true 2>"$TMP/t1.err")"; status=$?
check "recovers after one reconnect" "$([ "$status" -eq 0 ] && [ "$out" = "OK" ] && echo 1 || echo 0)"
check "reconnect invoked once" "$([ "$(grep -c 'fake-reconnect: called' "$TMP/t1.err")" -eq 1 ] && echo 1 || echo 0)"

# Test 2 (finding 2): the first attempt writes partial stdout before failing, e.g. a screencap
# that drops mid-transfer. adb_retry must emit only the retry's output, byte-exact, never the
# partial bytes from the failed attempt.
FAKE_ADB_DIR="$TMP/fake-adb/t2"; export FAKE_ADB_DIR
reset_fake_adb
printf 'PARTIAL-BINARY-\x01\x02' >"$FAKE_ADB_DIR/call1.out"
printf 'error: device offline\n' >"$FAKE_ADB_DIR/call1.err"
echo 1 >"$FAKE_ADB_DIR/call1.exit"
printf 'SECOND-ATTEMPT-OUTPUT-NO-TRAILING-NEWLINE' >"$FAKE_ADB_DIR/call2.out"
adb_retry exec-out screencap >"$TMP/t2.out" 2>"$TMP/t2.err"
printf 'SECOND-ATTEMPT-OUTPUT-NO-TRAILING-NEWLINE' >"$TMP/t2.expected"
check "no partial-attempt bytes leak into output" "$(cmp -s "$TMP/t2.out" "$TMP/t2.expected" && echo 1 || echo 0)"

# Test 3 (finding 3): both attempts fail -- the error must be printed exactly once, not once per
# attempt.
FAKE_ADB_DIR="$TMP/fake-adb/t3"; export FAKE_ADB_DIR
reset_fake_adb
printf 'error: device offline\n' >"$FAKE_ADB_DIR/call1.err"
echo 1 >"$FAKE_ADB_DIR/call1.exit"
cp "$FAKE_ADB_DIR/call1.err" "$FAKE_ADB_DIR/call2.err"
cp "$FAKE_ADB_DIR/call1.exit" "$FAKE_ADB_DIR/call2.exit"
status=0
adb_retry shell true >"$TMP/t3.out" 2>"$TMP/t3.err" || status=$?
check "still-failing path exits nonzero" "$([ "$status" -ne 0 ] && echo 1 || echo 0)"
check "error printed exactly once" "$([ "$(grep -c 'device offline' "$TMP/t3.err")" -eq 1 ] && echo 1 || echo 0)"

# Test 4 (#412, #416 round 3): the bash watchdog fallback (used when timeout/gtimeout are missing)
# must not leave its `sleep $ADB_CALL_TIMEOUT` child running once a fast call returns -- forced
# here regardless of what's actually on the host PATH.
FAKE_ADB_DIR="$TMP/fake-adb/t4"; export FAKE_ADB_DIR
reset_fake_adb
printf 'OK\n' >"$FAKE_ADB_DIR/call1.out"
status=0
( _ADB_TIMEOUT_BIN=""; ADB_CALL_TIMEOUT="$$"; _run_adb shell true >"$TMP/t4.out" 2>"$TMP/t4.err" ) || status=$?
sleep 0.5 # give a just-killed sleep a moment to actually exit, to avoid a race false-fail
check "fast call under the fallback succeeds" "$([ "$status" -eq 0 ] && echo 1 || echo 0)"
check "no orphaned watchdog sleep child survives" "$(pgrep -f "sleep $$\$" >/dev/null 2>&1 && echo 0 || echo 1)"

# Test 5: a genuinely hung call under the fallback still times out with exit 124 (the watchdog
# fix must not break the timeout it exists to enforce).
FAKE_ADB_DIR="$TMP/fake-adb/t5"; export FAKE_ADB_DIR
reset_fake_adb
echo 30 >"$FAKE_ADB_DIR/call1.sleep"
status=0
# shellcheck disable=SC2034 # consumed by _run_adb, sourced from _lib.sh above
( _ADB_TIMEOUT_BIN=""; ADB_CALL_TIMEOUT=1; _run_adb shell true >"$TMP/t5.out" 2>"$TMP/t5.err" ) || status=$?
check "hung call under the fallback times out with 124" "$([ "$status" -eq 124 ] && echo 1 || echo 0)"

echo "-- ${pass_count} passed, ${fail_count} failed --"
[ "$fail_count" -eq 0 ]
