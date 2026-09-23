#!/usr/bin/env bash
# The desktop-PC emulators: headless Pixel 9 Pro AVDs on the owner's Windows box (WSL2, KVM),
# driven from this Mac over ssh. Runs Shuttle on a device without competing with the Mac for CPU,
# which is what starves a local AVD whenever the Mac is loaded (Xcode, other sessions).
#
# The box is shared with the owner's podcasts repo and CI runners. Several sessions across both
# repos may each take ONE lane (1..3); a lane is an emulator console port, a serial, a local
# tunnel port and a lease on the box:
#
#   lane  console  serial         local adb port   lease
#   1     5554     emulator-5554  5038             /home/tim/.emu-leases/lane-1
#   2     5556     emulator-5556  5039             /home/tim/.emu-leases/lane-2
#   3     5558     emulator-5558  5040             /home/tim/.emu-leases/lane-3
#
#   remote-emu.sh status          all lanes: owner, age, qemu alive; box load and free memory
#   remote-emu.sh start [N]       lease the lowest free lane (or lane N), boot it, open its tunnel
#   remote-emu.sh env [N]         print the two exports for this session's lane (eval it)
#   remote-emu.sh install [N]     assembleDebug locally and adb install it on the lane
#   remote-emu.sh stop [N|--all]  kill the lane's emulator and tunnel, drop its lease (idempotent)
#
# Typical run, from the repo root:
#   support/scripts/remote-emu.sh start && eval "$(support/scripts/remote-emu.sh env)"
#   support/scripts/remote-emu.sh install
#   adb shell am start -n com.simplecityapps.shuttle.dev/com.simplecityapps.shuttle.ui.MainActivity
#   support/scripts/remote-emu.sh stop
#
# How it plugs in: the box runs one adb server on 5037 that sees every lane; `start` forwards it
# to the lane's LOCAL port (the Mac's own adb server keeps 5037), and every `adb` invoked with
# ANDROID_ADB_SERVER_PORT=<that port> ANDROID_SERIAL=<the lane's serial> then talks to that
# emulator. `env` prints exactly those two exports. The lane this session took is remembered in
# ${TMPDIR}/remote-emu/lane.<session>, keyed by CLAUDE_SESSION_ID / CLAUDE_CODE_SESSION_ID, so
# `env`, `install` and `stop` need no argument inside the session that ran `start`.
# REMOTE_EMU_LANE=N overrides that file; REMOTE_EMU_OWNER labels the lease.
#
# Leases: mkdir on the box is the atomic claim. A lease whose console port has no listener and
# whose `since` is older than the boot timeout is stale and gets reclaimed (with a message).
# Nothing here kills another lane's emulator unless you say `stop N` or `stop --all`.
#
# Every lane boots the same AVD with -read-only (emulator 30+: concurrent boots of one AVD, no
# snapshot, disk writes discarded at exit) so the app must be `install`ed on each lane, each time.
#
# ALWAYS `stop` when finished. The box also hosts the CI runner containers, and a stray emulator
# there can be picked up by an instrumentation job instead of its managed device
# (.github/runners/wsl-desktop/EMULATOR-SETUP.md, §5.4).
#
# Off limits on the box: the gh-runner*/segment-acquisition*/acq-vpn-tunnel-us containers, the
# Docker image rebuild, /etc, and the SDK contents (an `sdkmanager` install there mutates what CI
# sees). This script only writes /home/tim/.emu-leases and /home/tim/remote-emu-lane*.log there.
set -euo pipefail

BOX="tim@192.168.50.131"
AVD="pixel_9_pro_api37"
LANES=3
REMOTE_ADB_PORT=5037
LEASE_ROOT="/home/tim/.emu-leases"
STATE_DIR="${TMPDIR:-/tmp}/remote-emu"
BOOT_TIMEOUT=300 # cold boot, no snapshot, software GPU; also the lease grace

SESSION_KEY="${CLAUDE_SESSION_ID:-${CLAUDE_CODE_SESSION_ID:-default}}"
LANE_FILE="${STATE_DIR}/lane.${SESSION_KEY}"
OWNER="${REMOTE_EMU_OWNER:-$(hostname -s)-$$${CLAUDE_SESSION_ID:+-$CLAUDE_SESSION_ID}${CLAUDE_CODE_SESSION_ID:+-$CLAUDE_CODE_SESSION_ID}}"

console_port() { echo $((5554 + 2 * ($1 - 1))); }
serial_of() { echo "emulator-$(console_port "$1")"; }
local_port() { echo $((REMOTE_ADB_PORT + $1)); }
lease_of() { echo "${LEASE_ROOT}/lane-$1"; }
log_of() { echo "/home/tim/remote-emu-lane$1.log"; }
tunnel_pid_file() { echo "${STATE_DIR}/tunnel-$1.pid"; }
# pgrep pattern with a bracketed first char, so pgrep's own shell command line does not match.
emu_pattern() { echo "[-]port $(console_port "$1") "; }

valid_lane() { [[ "$1" =~ ^[1-9]$ ]] && [ "$1" -le "$LANES" ]; }

# /etc/profile.d/android-sdk.sh is what puts emulator/adb on PATH on the box. Sourced explicitly
# rather than via `bash -l`: a login shell runs ~/.bash_logout on `exit`, whose clear_console
# fails without a tty and turns an explicit `exit 0` into status 1.
box() { ssh -o ConnectTimeout=5 -o BatchMode=yes "$BOX" "bash -c $(printf %q "source /etc/profile.d/android-sdk.sh; $*")"; }
# The remote adb server holds its stdio open when `adb` auto-starts it, which keeps ssh from
# returning; every remote adb call goes through a server started detached first.
box_adb() { box "adb start-server </dev/null >/dev/null 2>&1; adb $*"; }
radb() { ANDROID_ADB_SERVER_PORT="$(local_port "$LANE")" adb -s "$(serial_of "$LANE")" "$@"; }

fallback() { echo "remote-emu: $* -- fall back to the local AVD" >&2; exit 3; }

check_available() {
    ssh -o ConnectTimeout=5 -o BatchMode=yes "$BOX" true 2>/dev/null \
        || fallback "$BOX is not reachable within 5 s"
    local accel
    accel="$(box 'emulator -accel-check 2>&1' | tr -d '\r')" || true
    case "$accel" in
        *"is installed and usable"*) ;;
        *) fallback "KVM is not usable on $BOX ($(echo "$accel" | tail -1))" ;;
    esac
}

# ---- lane bookkeeping (local) -------------------------------------------------------------

own_lane() {
    if [ -n "${REMOTE_EMU_LANE:-}" ]; then echo "$REMOTE_EMU_LANE"; return; fi
    [ -f "$LANE_FILE" ] && cat "$LANE_FILE"
    true
}
remember_lane() { mkdir -p "$STATE_DIR"; echo "$1" > "$LANE_FILE"; }
forget_lane() { [ "$(own_lane)" = "$1" ] && rm -f "$LANE_FILE"; true; }

# The lane an argument-less command means: the explicit arg, else this session's remembered one.
resolve_lane() {
    local lane="${1:-$(own_lane)}"
    [ -n "$lane" ] || { echo "remote-emu: no lane for this session; run start first (or pass N)" >&2; exit 1; }
    valid_lane "$lane" || { echo "remote-emu: lane must be 1..$LANES, got '$lane'" >&2; exit 2; }
    echo "$lane"
}

# ---- tunnel (local) ----------------------------------------------------------------------

tunnel_pid() {
    local f; f="$(tunnel_pid_file "$1")"
    [ -f "$f" ] || return 1
    local pid; pid="$(cat "$f")"
    kill -0 "$pid" 2>/dev/null || return 1
    echo "$pid"
}

kill_tunnel() {
    local pid
    if pid="$(tunnel_pid "$1")"; then kill "$pid" 2>/dev/null || true; fi
    rm -f "$(tunnel_pid_file "$1")"
    # A tunnel from an earlier session whose pid file is gone would keep the local port busy.
    pkill -f "ssh -N -L $(local_port "$1"):localhost:${REMOTE_ADB_PORT}" 2>/dev/null || true
}

open_tunnel() {
    local lane="$1" port; port="$(local_port "$lane")"
    kill_tunnel "$lane"
    mkdir -p "$STATE_DIR"
    # ExitOnForwardFailure so a busy port surfaces as a dead tunnel, not a silent one.
    ssh -N -L "${port}:localhost:${REMOTE_ADB_PORT}" \
        -o ExitOnForwardFailure=yes -o ServerAliveInterval=15 -o BatchMode=yes "$BOX" \
        >"${STATE_DIR}/tunnel-${lane}.log" 2>&1 &
    echo $! > "$(tunnel_pid_file "$lane")"
    sleep 2
    tunnel_pid "$lane" >/dev/null \
        || { cat "${STATE_DIR}/tunnel-${lane}.log" >&2; echo "remote-emu: tunnel for lane $lane died" >&2; exit 1; }
}

# ---- leases (on the box, one ssh round trip each) ------------------------------------------

# Prints one line per lane: "N owner since alive" (owner/since are "-" without a lease; alive
# is 1 when something listens on the console port).
lease_table() {
    box "for n in \$(seq 1 $LANES); do
            port=\$((5554 + 2 * (n - 1))); d=${LEASE_ROOT}/lane-\$n
            alive=0; ss -ltn 2>/dev/null | grep -q \":\$port \" && alive=1
            if [ -d \$d ]; then echo \"\$n \$(cat \$d/owner 2>/dev/null || echo '?') \$(cat \$d/since 2>/dev/null || echo 0) \$alive\"
            else echo \"\$n - - \$alive\"; fi
        done"
}

# claim_lane [N]: mkdir-claims lane N, or the lowest free lane. Prints "TAKEN N" on success;
# "STALE N owner" lines precede it when a dead lease was reclaimed. Exits 1 with the holder
# otherwise. Runs as one remote script so the check-and-claim is a single round trip.
claim_lane() {
    local want="${1:-}"
    box "set -e; mkdir -p ${LEASE_ROOT}; now=\$(date +%s)
        lanes=\"${want:-\$(seq 1 $LANES | tr '\n' ' ')}\"
        for n in \$lanes; do
            port=\$((5554 + 2 * (n - 1))); d=${LEASE_ROOT}/lane-\$n
            alive=0; ss -ltn 2>/dev/null | grep -q \":\$port \" && alive=1
            if [ -d \$d ]; then
                owner=\$(cat \$d/owner 2>/dev/null || echo '?'); since=\$(cat \$d/since 2>/dev/null || echo 0)
                if [ \$alive = 0 ] && [ \$((now - since)) -gt $BOOT_TIMEOUT ]; then
                    echo \"STALE \$n \$owner\"; rm -rf \$d
                else
                    [ -n '$want' ] && { echo \"HELD \$n \$owner \$((now - since))\"; exit 1; }
                    continue
                fi
            elif [ \$alive = 1 ]; then
                [ -n '$want' ] && { echo \"BUSY \$n\"; exit 1; }
                continue
            fi
            if mkdir \$d 2>/dev/null; then
                echo '$OWNER' > \$d/owner; echo \$now > \$d/since; echo \"TAKEN \$n\"; exit 0
            fi
        done
        echo NONE; exit 1"
}

release_lane() { box "rm -rf $(lease_of "$1")"; }

# ---- commands ----------------------------------------------------------------------------

cmd_status() {
    check_available
    echo "remote-emu: $BOX reachable, KVM usable"
    box "uptime | sed 's/.*load average/load average/'; free -m | awk '/^Mem:/{print \"free \" \$7 \" MB available of \" \$2}'" \
        | sed 's/^/remote-emu: box /'
    local now; now="$(date +%s)"
    local n owner since alive
    while read -r n owner since alive; do
        local state="no lease" age=""
        if [ "$owner" != "-" ]; then
            state="leased by $owner"; age=", $(( (now - since) / 60 )) min"
        fi
        local qemu="qemu down"; [ "$alive" = 1 ] && qemu="qemu ALIVE"
        local tun="no tunnel"; tunnel_pid "$n" >/dev/null && tun="tunnel localhost:$(local_port "$n")"
        local mine=""; [ "$(own_lane)" = "$n" ] && mine="  <- this session"
        echo "remote-emu: lane $n  $(serial_of "$n")  ${state}${age}  ${qemu}  ${tun}${mine}"
    done < <(lease_table)
}

cmd_start() {
    check_available
    local want="${1:-}"
    [ -z "$want" ] || valid_lane "$want" || { echo "remote-emu: lane must be 1..$LANES" >&2; exit 2; }
    local mine; mine="$(own_lane)"
    if [ -n "$mine" ] && [ -z "$want" ] \
        && box "test -d $(lease_of "$mine") && ss -ltn | grep -q ':$(console_port "$mine") '"; then
        echo "remote-emu: this session already holds lane $mine with qemu alive; reusing it"
        LANE="$mine"
    else
        local out
        if ! out="$(claim_lane "$want")"; then
            local line; line="$(echo "$out" | grep -m1 -E '^(HELD|BUSY|NONE)' || true)"
            set -- $line
            case "${1:-}" in
                HELD) echo "remote-emu: lane $2 is held by $3 (${4}s ago) -- \`stop $2\` only if you know it is dead" >&2 ;;
                BUSY) echo "remote-emu: lane $2 has a listener on $(console_port "$2") but no lease -- \`stop $2\` to clear" >&2 ;;
                *) echo "remote-emu: all $LANES lanes are taken; \`status\` shows who holds them" >&2 ;;
            esac
            exit 1
        fi
        while read -r _ n owner; do
            echo "remote-emu: reclaiming stale lease on lane $n (was $owner, nothing listening)"
        done < <(echo "$out" | grep '^STALE' || true)
        LANE="$(echo "$out" | awk '/^TAKEN/{print $2}')"
        remember_lane "$LANE"
        echo "remote-emu: leased lane $LANE ($(serial_of "$LANE")) as $OWNER"
        local port log; port="$(console_port "$LANE")"; log="$(log_of "$LANE")"
        echo "remote-emu: booting $AVD headless on $BOX, console port $port ..."
        # setsid + nohup + closed stdio, or the emulator's inherited fds keep this ssh open forever.
        box "setsid nohup emulator -avd ${AVD} -port ${port} -read-only -no-window -no-audio \
            -no-boot-anim -gpu swiftshader_indirect -no-snapshot -memory 4096 \
            >${log} 2>&1 </dev/null &"
    fi
    local serial pattern log; serial="$(serial_of "$LANE")"; pattern="$(emu_pattern "$LANE")"; log="$(log_of "$LANE")"
    echo "remote-emu: waiting for sys.boot_completed on $serial (up to ${BOOT_TIMEOUT}s) ..."
    local waited=0
    until [ "$(box_adb "-s ${serial} shell getprop sys.boot_completed 2>/dev/null" | tr -d '\r')" = "1" ]; do
        sleep 5
        waited=$((waited + 5))
        if [ "$waited" -ge "$BOOT_TIMEOUT" ]; then
            echo "remote-emu: boot timed out; tail of ${log}:" >&2
            box "tail -20 ${log}" >&2 || true
            cmd_stop "$LANE"; exit 1
        fi
        if ! box "pgrep -f '${pattern}' >/dev/null"; then
            echo "remote-emu: emulator process died; tail of ${log}:" >&2
            box "tail -20 ${log}" >&2 || true
            cmd_stop "$LANE"; exit 1
        fi
    done
    echo "remote-emu: booted after ~${waited}s"
    # Keep ANR/crash dialogs from covering the UI that tests read.
    box_adb "-s ${serial} shell settings put global hide_error_dialogs 1" >/dev/null || true
    open_tunnel "$LANE"
    if ! ANDROID_ADB_SERVER_PORT="$(local_port "$LANE")" adb devices | grep -q "^${serial}[[:space:]]*device"; then
        echo "remote-emu: ${serial} not listed through the tunnel:" >&2
        ANDROID_ADB_SERVER_PORT="$(local_port "$LANE")" adb devices >&2
        cmd_stop "$LANE"; exit 1
    fi
    echo "remote-emu: lane $LANE ready. $(radb shell getprop ro.product.model | tr -d '\r'), sdk $(radb shell getprop ro.build.version.sdk | tr -d '\r'), $(radb shell wm size | tr -d '\r')"
    echo "remote-emu: now  eval \"\$($0 env)\""
}

cmd_install() {
    LANE="$(resolve_lane "${1:-}")"
    tunnel_pid "$LANE" >/dev/null || { echo "remote-emu: no tunnel for lane $LANE; run start first" >&2; exit 1; }
    # assembleDebug + adb install, not :android:app:installDebug: Gradle's install task talks to
    # its own adb server and does not honour ANDROID_ADB_SERVER_PORT.
    local apk="android/app/build/outputs/apk/debug/app-debug.apk"
    [ -f gradlew ] || { echo "remote-emu: run from the repo root" >&2; exit 2; }
    ./gradlew :android:app:assembleDebug -q
    radb install -r "$apk"
}

cmd_env() {
    LANE="$(resolve_lane "${1:-}")"
    echo "export ANDROID_ADB_SERVER_PORT=$(local_port "$LANE")"
    echo "export ANDROID_SERIAL=$(serial_of "$LANE")"
}

stop_lane() {
    local lane="$1" serial pattern; serial="$(serial_of "$lane")"; pattern="$(emu_pattern "$lane")"
    kill_tunnel "$lane"
    # `adb emu kill` is the clean shutdown; pkill is the belt for a wedged one. Both run ON the
    # box, so this works even if the tunnel never came up. Only THIS lane's port is matched.
    # emu kill hangs forever against a serial adb does not know, hence the liveness check + timeout.
    if box "ss -ltn | grep -q ':$(console_port "$lane") '"; then
        box_adb "-s ${serial} emu kill >/dev/null 2>&1 || true" &
        local killer=$!
        ( sleep 15; kill "$killer" 2>/dev/null ) &
        wait "$killer" 2>/dev/null || true
        sleep 2
    fi
    box "pkill -f '${pattern}' 2>/dev/null || true"
    if box "pgrep -f '${pattern}' >/dev/null"; then
        sleep 3
        box "pkill -9 -f '${pattern}' 2>/dev/null || true"
    fi
    release_lane "$lane"
    forget_lane "$lane"
    echo "remote-emu: lane $lane stopped on $BOX, lease dropped, tunnel closed"
}

cmd_stop() {
    if ! ssh -o ConnectTimeout=5 -o BatchMode=yes "$BOX" true 2>/dev/null; then
        for n in $(seq 1 "$LANES"); do kill_tunnel "$n"; done
        echo "remote-emu: $BOX unreachable; tunnels closed, emulator and lease state unknown" >&2
        exit 1
    fi
    if [ "${1:-}" = "--all" ]; then
        for n in $(seq 1 "$LANES"); do stop_lane "$n"; done
        rm -f "${STATE_DIR}"/lane.* 2>/dev/null || true
        return
    fi
    local lane
    if [ -z "${1:-}" ] && [ -z "$(own_lane)" ]; then
        echo "remote-emu: this session holds no lane; nothing to stop (\`stop N\` or \`stop --all\` for cleanup)"
        return
    fi
    lane="$(resolve_lane "${1:-}")"
    stop_lane "$lane"
}

case "${1:-}" in
    status) cmd_status ;;
    start) cmd_start "${2:-}" ;;
    install) cmd_install "${2:-}" ;;
    env) cmd_env "${2:-}" ;;
    stop) cmd_stop "${2:-}" ;;
    *) sed -n '2,25p' "$0" >&2; exit 2 ;;
esac
