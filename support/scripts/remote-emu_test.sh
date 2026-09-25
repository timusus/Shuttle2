#!/usr/bin/env bash
# Stub tests for remote-emu.sh's tunnel setup (#342), run with no box and no emulator: a fake `ssh`
# stands in for the forward (a silent local listener, or an ssh that dies) and a fake `adb` for the
# Mac's adb server. Uses its own ports (25600+), so real lane tunnels are never touched.
# Run directly: support/scripts/remote-emu_test.sh
set -euo pipefail

REAL_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TMP="$(mktemp -d)"
BUSY_PID=""
cleanup() {
    local f
    for f in "$TMP"/remote-emu/*.pid; do [ -f "$f" ] && kill "$(cat "$f")" 2>/dev/null; done || true
    [ -n "$BUSY_PID" ] && { kill "$BUSY_PID" 2>/dev/null || true; }
    rm -rf "$TMP"
}
trap cleanup EXIT
mkdir -p "$TMP/bin"

# FAKE_SSH=listen: accept and hold connections on the -L port without ever answering, the shape of
# a forward whose adbd never completes the handshake. FAKE_SSH=die: fail the way a busy port does.
cat >"$TMP/bin/ssh" <<'FAKE'
#!/usr/bin/env bash
while [ $# -gt 0 ]; do [ "$1" = "-L" ] && spec="$2"; shift; done
if [ "${FAKE_SSH:-listen}" = "die" ]; then
    echo "bind [127.0.0.1]:${spec%%:*}: Address already in use" >&2; exit 255
fi
exec python3 -c '
import socket, sys
s = socket.socket(); s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
s.bind(("127.0.0.1", int(sys.argv[1]))); s.listen(5); held = []
while True: held.append(s.accept()[0])
' "${spec%%:*}"
FAKE
# FAKE_ADB_STATE is what get-state reports; every call is logged to $TMP/adb.calls.
cat >"$TMP/bin/adb" <<FAKE
#!/usr/bin/env bash
echo "\$*" >>"$TMP/adb.calls"
case "\$*" in
    connect*) [ "\${FAKE_ADB_STATE:-}" = device ] && echo "connected to \$2" || echo "failed to connect to \$2" ;;
    *get-state) [ -n "\${FAKE_ADB_STATE:-}" ] && echo "\$FAKE_ADB_STATE" ;;
esac
exit 0
FAKE
chmod +x "$TMP/bin/ssh" "$TMP/bin/adb"
export PATH="$TMP/bin:$PATH" TMPDIR="$TMP"

# shellcheck source=remote-emu.sh
source "$REAL_ROOT/support/scripts/remote-emu.sh"
direct_port() { echo $((25600 + $1)); }
local_port() { echo $((25610 + $1)); }

fails=0
pass() { echo "ok   $1"; }
fail() { echo "FAIL $1" >&2; fails=$((fails + 1)); }
LOG="$STATE_DIR/t.log" PIDF="$STATE_DIR/t.pid"

# 1. A forward that comes up returns once it listens, and its log names the ssh command; once ssh
#    is killed, the log records how it ended.
if forward "$LOG" "$PIDF" 25621 5555 && grep -q "ssh -N -L 25621:localhost:5555" "$LOG"; then
    pass "forward listens and logs its command"
else fail "forward listens and logs its command: ${FORWARD_ERROR}"; fi
kill "$(cat "$PIDF")"; wait
grep -q "exited with status" "$LOG" && pass "killed forward logs its exit status" || fail "killed forward logs its exit status"

# 2. An ssh that dies is reported as ssh-exited, with its stderr and exit status in the log.
FAKE_SSH=die forward "$LOG" "$PIDF" 25622 5555 && fail "dying ssh reported" || true
wait
if [[ "$FORWARD_ERROR" == ssh-exited:* ]] && grep -q "Address already in use" "$LOG" && grep -q "status 255" "$LOG"; then
    pass "dying ssh reported as ssh-exited with its log"
else fail "dying ssh reported as ssh-exited with its log: '${FORWARD_ERROR}'"; fi

# 3. A foreign listener on the port is named rather than shadowing or failing the forward silently.
python3 -c 'import socket,time; s=socket.socket(); s.bind(("127.0.0.1",25623)); s.listen(1); time.sleep(60)' &
BUSY_PID=$!
until lsof -nP -iTCP:25623 -sTCP:LISTEN >/dev/null 2>&1; do sleep 0.1; done
forward "$LOG" "$PIDF" 25623 5555 && fail "busy port reported" || true
[[ "$FORWARD_ERROR" == port-busy:*"$BUSY_PID"* ]] && pass "busy port reported with its holder" || fail "busy port reported with its holder: '${FORWARD_ERROR}'"
{ kill "$BUSY_PID"; wait "$BUSY_PID"; } 2>/dev/null || true; BUSY_PID=""

# 4. The #342 shape: the forward is up, adbd never answers, the serial stays offline. Each attempt
#    is disconnected, the log is not empty and the warning names the failed check.
: >"$TMP/adb.calls"
err="$(FAKE_ADB_STATE=offline open_direct_tunnel 1 2>&1 >/dev/null)"
dlog="$STATE_DIR/tunnel-1-adbd.log"
if [[ "$err" == *adb-handshake:*offline*"last lines of"* ]] && [ "$(grep -c "attempt" "$dlog")" = 3 ] \
    && [ "$(grep -c '^disconnect localhost:25601' "$TMP/adb.calls")" = 3 ]; then
    pass "stalled handshake retried, disconnected, reported as adb-handshake"
else fail "stalled handshake retried, disconnected, reported as adb-handshake: $err"; fi
kill "$(cat "$(direct_pid_file 1)")"; wait

# 5. A handshake that completes reports the serial for Maestro/Gradle.
out="$(FAKE_ADB_STATE=device open_direct_tunnel 2)"
[[ "$out" == *"as localhost:25602"* ]] && pass "direct tunnel connects" || fail "direct tunnel connects: $out"
kill "$(cat "$(direct_pid_file 2)")"; wait

[ "$fails" -eq 0 ] && echo "remote-emu_test: all passed" || { echo "remote-emu_test: $fails failed" >&2; exit 1; }
