#!/usr/bin/env bash
# Stub tests for run-all.sh's per-check failure reporting (#342), run with no device/emulator: a
# temp checks directory holds fake check scripts and a copy of run-all.sh runs against it.
# Run directly: support/scripts/checks/run-all_test.sh
set -euo pipefail

REAL_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

CHECKS="$TMP/support/scripts/checks"
mkdir -p "$CHECKS" "$TMP/bin"
cp "$REAL_ROOT/support/scripts/checks/run-all.sh" "$CHECKS/run-all.sh"
cp "$REAL_ROOT/support/scripts/checks/_lib.sh" "$CHECKS/_lib.sh"
chmod +x "$CHECKS/run-all.sh"

# run-all only calls `adb logcat -c ...`, discarding the result either way.
cat >"$TMP/bin/adb" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
chmod +x "$TMP/bin/adb"
export PATH="$TMP/bin:$PATH"

cat >"$CHECKS/no-crashes.sh" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
chmod +x "$CHECKS/no-crashes.sh"

cat >"$CHECKS/aa-passing.sh" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
chmod +x "$CHECKS/aa-passing.sh"

# Dies under `set -e` with no output and no fail() call -- the exact shape from #342: a failing
# grep inside a $(...).
cat >"$CHECKS/ab-silent-death.sh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
missing="$(echo nope | grep needle)"
echo "unreachable: $missing"
EOF
chmod +x "$CHECKS/ab-silent-death.sh"

# Calls the real fail() (sourcing the copied _lib.sh) -- run-all must not print a second FAIL
# line for a check that already named and explained its own failure.
cat >"$CHECKS/ac-calls-fail.sh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=/dev/null
source "$dir/_lib.sh"
fail "deliberate failure"
EOF
chmod +x "$CHECKS/ac-calls-fail.sh"

pass_count=0 fail_count=0
check() {
    local label="$1" ok="$2"
    if [ "$ok" = "1" ]; then
        echo "ok - $label"; pass_count=$((pass_count + 1))
    else
        echo "not ok - $label"; fail_count=$((fail_count + 1))
    fi
}

status=0
out="$("$CHECKS/run-all.sh" 2>&1)" || status=$?

check "run-all exits non-zero overall" "$([ "$status" -ne 0 ] && echo 1 || echo 0)"
check "names the silently-dying check with its exit code" \
    "$(echo "$out" | grep -qE '^FAIL ab-silent-death \(exit [0-9]+\)$' && echo 1 || echo 0)"
check "does not re-report the check that already called fail()" \
    "$([ "$(echo "$out" | grep -c '^FAIL ac-calls-fail (exit')" -eq 0 ] && echo 1 || echo 0)"
check "fail()'s own FAIL line for ac-calls-fail still appears" \
    "$(echo "$out" | grep -qE '^FAIL ac-calls-fail: deliberate failure$' && echo 1 || echo 0)"
check "summary lists both failing check names" \
    "$(echo "$out" | grep -qE 'check\(s\) failed: .*ab-silent-death.*ac-calls-fail' && echo 1 || echo 0)"
check "the passing check is not named as failed" \
    "$([ "$(echo "$out" | grep -c 'aa-passing')" -eq 0 ] && echo 1 || echo 0)"

echo "-- ${pass_count} passed, ${fail_count} failed --"
[ "$fail_count" -eq 0 ]
