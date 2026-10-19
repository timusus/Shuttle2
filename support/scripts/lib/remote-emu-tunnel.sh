# shellcheck shell=bash
# Local SSH tunnels for support/scripts/remote-emu.sh. Sourced, not run.

tunnel_pid() {
    local f; f="${2:-$(tunnel_pid_file "$1")}"
    [ -f "$f" ] || return 1
    local pid; pid="$(cat "$f")"
    kill -0 "$pid" 2>/dev/null || return 1
    echo "$pid"
}

kill_tunnel() {
    close_tunnel "${STATE_DIR}/tunnel-$1.log" "$(tunnel_pid_file "$1")" \
        "^ssh -N -L $(local_port "$1"):localhost:${REMOTE_ADB_PORT} "
    local_adb disconnect "$(direct_serial "$1")" >/dev/null 2>&1 || true
    close_tunnel "${STATE_DIR}/tunnel-$1-adbd.log" "$(direct_pid_file "$1")" "^ssh -N -L $(direct_port "$1"):localhost:"
}

# close_tunnel <log> <pid-file> <pattern>: kill the tunnel's ssh, and any other ssh forwarding the
# same local port (a tunnel from an earlier session whose pid file is gone would keep the port busy),
# noting each in the log first: a tunnel ssh exits 0 on SIGTERM, so without the note a deliberate
# close and a tunnel the box dropped read the same. The pattern is anchored at `ssh` so it never
# matches forward()'s watcher, whose command line merely contains the ssh command.
close_tunnel() {
    local log="$1" pid_file="$2" pattern="$3" pid p
    pid="$(tunnel_pid - "$pid_file")" || pid=""
    rm -f "$pid_file"
    for p in $(pgrep -f "$pattern" 2>/dev/null || true); do
        [ "$p" = "$pid" ] && continue
        echo "$(date '+%F %T') remote-emu: killing stale ssh[$p] from another run" >>"$log" 2>/dev/null || true
        kill "$p" 2>/dev/null || true
    done
    [ -n "$pid" ] || return 0
    echo "$(date '+%F %T') remote-emu: closing ssh[$pid]" >>"$log" 2>/dev/null || true
    kill "$pid" 2>/dev/null || true
}

# "command pid" of whatever listens on local TCP <port>, empty if nothing does.
port_holder() { lsof -nP -iTCP:"$1" -sTCP:LISTEN 2>/dev/null | awk 'NR == 2 {print $1, $2}'; }

# forward <log> <pid-file> <local-port> <remote-port>: an `ssh -N -L` to the box, verified to be
# listening before it returns. The log always records the ssh command line and, once ssh ends,
# its exit status, so a tunnel that dies (or is killed) never leaves an empty log behind. Returns
# non-zero with FORWARD_ERROR naming the check that failed:
#   port-busy        another process still listens on the local port once our tunnels are gone
#   ssh-exited       ssh died before its listener came up (ExitOnForwardFailure, auth, network)
#   listen-timeout   ssh is alive but no listener appeared within 15 s (box or Mac under load)
FORWARD_ERROR=""
forward() {
    local log="$1" pid_file="$2" port="$3" remote="$4" holder pid=""
    mkdir -p "$STATE_DIR"
    rm -f "$pid_file"
    # kill_tunnel only signals the old ssh; give it a moment to exit and free the port.
    for _ in $(seq 1 10); do holder="$(port_holder "$port")"; [ -z "$holder" ] && break; sleep 0.3; done
    if [ -n "$holder" ]; then
        FORWARD_ERROR="port-busy: localhost:${port} is held by ${holder}"
        echo "$(date '+%F %T') not starting: $FORWARD_ERROR" >"$log"
        return 1
    fi
    local cmd=(ssh -N -L "${port}:localhost:${remote}"
        -o ExitOnForwardFailure=yes -o ServerAliveInterval=15 -o BatchMode=yes "$BOX")
    echo "$(date '+%F %T') ${cmd[*]}" >"$log"
    # The watcher records ssh's own PID (what kill_tunnel signals) and outlives it to log how it
    # ended. Lines carry the PID: a killed tunnel's last line can land after the next one's header.
    # A fresh `bash -c`, not a ( ) subshell: a forked subshell would inherit bash's saved copies of
    # the caller's stdout and keep a `$(remote-emu.sh ...)` or a pipe open for the tunnel's lifetime.
    # shellcheck disable=SC2016 # expanded by the watcher
    bash -c '"${@:3}" </dev/null >>"$1" 2>&1 &
        pid=$!; echo "$pid" >"$2"; wait "$pid"; rc=$?
        echo "$(date "+%F %T") ssh[$pid] exited with status $rc" >>"$1"' \
        _ "$log" "$pid_file" "${cmd[@]}" </dev/null >/dev/null 2>&1 &
    # ExitOnForwardFailure makes ssh exit rather than run without its listener, so this settles
    # as soon as ssh has either bound the port or given up; the cap only matters on a stalled box.
    for _ in $(seq 1 50); do
        pid="$(cat "$pid_file" 2>/dev/null || true)"
        if [ -n "$pid" ]; then
            kill -0 "$pid" 2>/dev/null || { FORWARD_ERROR="ssh-exited: ssh for localhost:${port} died before listening"; return 1; }
            lsof -nP -a -p "$pid" -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1 && return 0
        fi
        sleep 0.3
    done
    FORWARD_ERROR="listen-timeout: ssh for localhost:${port} not listening after 15 s"
    return 1
}

# tunnel_failure <what> <log> <detail>: the failure message, with the tail of the tunnel's log.
tunnel_failure() {
    echo "remote-emu: $1 failed -- $3" >&2
    echo "remote-emu: last lines of $2:" >&2
    tail -5 "$2" 2>/dev/null | sed 's/^/    /' >&2 || true
}

open_tunnel() {
    local lane="$1" port log; port="$(local_port "$lane")"; log="${STATE_DIR}/tunnel-${lane}.log"
    kill_tunnel "$lane"
    # Any `adb` run with this port while the tunnel was down auto-started a LOCAL adb server on it,
    # which would shadow the new forward (ssh still binds ::1, so the forward doesn't fail) and
    # list no devices. With the tunnel killed above, whatever answers on the port is that server.
    ANDROID_ADB_SERVER_PORT="$port" adb kill-server >/dev/null 2>&1 || true
    if ! forward "$log" "$(tunnel_pid_file "$lane")" "$port" "$REMOTE_ADB_PORT"; then
        tunnel_failure "tunnel for lane $lane" "$log" "$FORWARD_ERROR"
        exit 1
    fi
    open_direct_tunnel "$lane"
}

# Best effort: the lane works through the server tunnel without it; only tools that ignore
# ANDROID_ADB_SERVER_PORT (Maestro, installDebug) need it.
#
# A connect whose TCP path works but whose adb handshake never completes (the forward accepts and
# reaches the emulator's adbd port, adbd never answers) prints "failed to connect" after ~10 s and
# leaves the serial `offline` on the Mac's adb server, retrying on its own; the ssh log stays clean
# because ssh saw nothing wrong (#342). Such an offline entry is disconnected before each retry, and
# after the last one, so the Mac server never keeps a dead transport for the lane.
open_direct_tunnel() {
    local lane="$1" port serial log attempt out state=""
    port="$(direct_port "$lane")"; serial="$(direct_serial "$lane")"; log="${STATE_DIR}/tunnel-${lane}-adbd.log"
    if ! forward "$log" "$(direct_pid_file "$lane")" "$port" "$(($(console_port "$lane") + 1))"; then
        tunnel_failure "direct adbd tunnel for lane $lane" "$log" \
            "$FORWARD_ERROR; Maestro/installDebug can't reach the lane"
        return 0
    fi
    for attempt in 1 2 3; do
        out="$(local_adb connect "$serial" 2>&1 | tr -d '\r' || true)"
        state="$(local_adb -s "$serial" get-state 2>/dev/null | tr -d '\r' || true)"
        if [ "$state" = "device" ]; then
            echo "remote-emu: lane $lane also on the Mac's adb server as $serial (for Maestro/Gradle)"
            return 0
        fi
        echo "$(date '+%F %T') adb connect $serial attempt $attempt: ${out:-no output}; state ${state:-absent}" >>"$log"
        local_adb disconnect "$serial" >/dev/null 2>&1 || true
        if ! tunnel_pid "$lane" "$(direct_pid_file "$lane")" >/dev/null; then
            tunnel_failure "direct adbd tunnel for lane $lane" "$log" \
                "ssh-exited during adb connect; Maestro/installDebug can't reach the lane"
            return 0
        fi
    done
    tunnel_failure "direct adbd tunnel for lane $lane" "$log" \
        "adb-handshake: $serial stayed ${state:-absent} after 3 connects (tunnel up, adbd on box port $(($(console_port "$lane") + 1)) not answering); Maestro/installDebug can't reach the lane"
}
