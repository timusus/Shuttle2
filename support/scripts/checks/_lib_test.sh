#!/usr/bin/env bash
# Stub tests for adb_retry (checks/_lib.sh), run with no device/emulator: a fake `adb` on PATH
# plays back scripted per-call stdout/stderr/exit-status, and a fake `remote-emu.sh` stands in for
# the real reconnect. Run directly: support/scripts/checks/_lib_test.sh
set -euo pipefail

REAL_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

mkdir -p "$TMP/support/scripts/checks" "$TMP/bin" "$TMP/fake-adb"
cp "$REAL_ROOT/support/scripts/checks/_lib.sh" "$TMP/support/scripts/checks/_lib.sh"

cat >"$TMP/support/scripts/remote-emu.sh" <<'EOF'
#!/usr/bin/env bash
echo "fake-reconnect: called" >&2
exit 0
EOF
chmod +x "$TMP/support/scripts/remote-emu.sh"

# fake adb: plays back $FAKE_ADB_DIR/call<N>.{out,err,exit}, advancing a call counter each
# invocation. Files are written with printf so byte content (including "no trailing newline") is
# under the test's control.
cat >"$TMP/bin/adb" <<'EOF'
#!/usr/bin/env bash
dir="$FAKE_ADB_DIR"
n=$(( $(cat "$dir/count" 2>/dev/null || echo 0) + 1 ))
echo "$n" >"$dir/count"
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

echo "-- ${pass_count} passed, ${fail_count} failed --"
[ "$fail_count" -eq 0 ]
