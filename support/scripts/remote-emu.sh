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
#   remote-emu.sh status           all lanes: owner, age, qemu alive; box load and free memory
#   remote-emu.sh start [N]        lease the lowest free lane (or lane N), boot it, open its tunnel
#   remote-emu.sh env [N]          print the two exports for this session's lane (eval it)
#   remote-emu.sh install [N] [apk]  assembleDebug locally and adb install it on the lane; given an
#                                  APK, install that without building (build once, install per lane)
#   remote-emu.sh serial [N]       print the lane's serial on the Mac's own adb server
#                                  (localhost:1560N), for Maestro, installDebug and ~/.claude/scripts/adb
#   remote-emu.sh reset [N]        clear the debug app's data and the seeded test media on the lane
#   remote-emu.sh ui-prep [N]      disable window/transition/animator animations on the lane
#   remote-emu.sh tap-text <text> [--desc] [--index N]
#                                  dump the UI hierarchy, tap the centre of the matching node
#   remote-emu.sh dump-texts       list every visible text/content-desc, with bounds
#   remote-emu.sh seed-music [dir] generate + push ~7 tagged mp3s (2 albums) to /sdcard/Music, scan them
#   remote-emu.sh lockscreen on|off   toggle the lane's lockscreen
#   remote-emu.sh stop [N|--all]  kill the lane's emulator and tunnel, drop its lease (idempotent)
#
# Typical run, from the repo root:
#   support/scripts/remote-emu.sh start && eval "$(support/scripts/remote-emu.sh env)"
#   support/scripts/remote-emu.sh install
#   support/scripts/remote-emu.sh ui-prep
#   adb shell am start -n com.simplecityapps.shuttle.dev/com.simplecityapps.shuttle.ui.MainActivity
#   support/scripts/remote-emu.sh tap-text "Up Next"
#   support/scripts/remote-emu.sh stop
#
# UI automation is by text, never by screenshot coordinate: screenshots handed to a model are
# downscaled (device 1280x2856, scale ~1.4286), so a tap computed from one lands in the wrong
# place. `tap-text`/`dump-texts` read real device-pixel bounds from a `uiautomator` dump instead.
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
# The second tunnel goes straight to the emulator's adbd (console port + 1 on the box; the image has
# ro.adb.secure=0, so no key is needed), and the Mac's OWN adb server `adb connect`s it. Tools that
# only talk to the default adb server on 5037 -- Maestro, Gradle's installDebug, Android Studio --
# then see the lane as `localhost:<direct port>`. Kept above 5585 so the Mac's adb server, which
# probes 5555..5585 for local emulators, never mistakes it for one.
direct_port() { echo $((15600 + $1)); }
direct_serial() { echo "localhost:$(direct_port "$1")"; }
direct_pid_file() { echo "${STATE_DIR}/tunnel-$1-adbd.pid"; }
local_adb() { env -u ANDROID_ADB_SERVER_PORT adb "$@"; }
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
    local f; f="${2:-$(tunnel_pid_file "$1")}"
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
    local_adb disconnect "$(direct_serial "$1")" >/dev/null 2>&1 || true
    if pid="$(tunnel_pid "$1" "$(direct_pid_file "$1")")"; then kill "$pid" 2>/dev/null || true; fi
    rm -f "$(direct_pid_file "$1")"
    pkill -f "ssh -N -L $(direct_port "$1"):localhost:" 2>/dev/null || true
}

open_tunnel() {
    local lane="$1" port; port="$(local_port "$lane")"
    kill_tunnel "$lane"
    # Any `adb` run with this port while the tunnel was down auto-started a LOCAL adb server on it,
    # which would shadow the new forward (ssh still binds ::1, so the forward doesn't fail) and
    # list no devices. With the tunnel killed above, whatever answers on the port is that server.
    ANDROID_ADB_SERVER_PORT="$port" adb kill-server >/dev/null 2>&1 || true
    mkdir -p "$STATE_DIR"
    # ExitOnForwardFailure so a busy port surfaces as a dead tunnel, not a silent one.
    ssh -N -L "${port}:localhost:${REMOTE_ADB_PORT}" \
        -o ExitOnForwardFailure=yes -o ServerAliveInterval=15 -o BatchMode=yes "$BOX" \
        >"${STATE_DIR}/tunnel-${lane}.log" 2>&1 &
    echo $! > "$(tunnel_pid_file "$lane")"
    sleep 2
    tunnel_pid "$lane" >/dev/null \
        || { cat "${STATE_DIR}/tunnel-${lane}.log" >&2; echo "remote-emu: tunnel for lane $lane died" >&2; exit 1; }
    open_direct_tunnel "$lane"
}

# Best effort: the lane works through the server tunnel without it; only tools that ignore
# ANDROID_ADB_SERVER_PORT (Maestro, installDebug) need it.
open_direct_tunnel() {
    local lane="$1" port; port="$(direct_port "$lane")"
    ssh -N -L "${port}:localhost:$(($(console_port "$lane") + 1))" \
        -o ExitOnForwardFailure=yes -o ServerAliveInterval=15 -o BatchMode=yes "$BOX" \
        >"${STATE_DIR}/tunnel-${lane}-adbd.log" 2>&1 &
    echo $! > "$(direct_pid_file "$lane")"
    sleep 2
    if tunnel_pid "$lane" "$(direct_pid_file "$lane")" >/dev/null \
        && local_adb connect "$(direct_serial "$lane")" 2>&1 | grep -q "connected to"; then
        echo "remote-emu: lane $lane also on the Mac's adb server as $(direct_serial "$lane") (for Maestro/Gradle)"
    else
        echo "remote-emu: warning: direct adbd tunnel for lane $lane failed; Maestro/installDebug can't reach it (see ${STATE_DIR}/tunnel-${lane}-adbd.log)" >&2
    fi
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
    local lane_arg="" apk=""
    for arg in "$@"; do
        case "$arg" in
            *.apk) apk="$arg" ;;
            *) lane_arg="$arg" ;;
        esac
    done
    LANE="$(resolve_lane "$lane_arg")"
    tunnel_pid "$LANE" >/dev/null || { echo "remote-emu: no tunnel for lane $LANE; run start first" >&2; exit 1; }
    # assembleDebug + adb install, not :android:app:installDebug: Gradle's install task talks to
    # its own adb server and does not honour ANDROID_ADB_SERVER_PORT. Given an APK, install that
    # as is: build once, then `install <apk>` from each worker's lane without another Gradle run.
    if [ -z "$apk" ]; then
        apk="android/app/build/outputs/apk/debug/app-debug.apk"
        [ -f gradlew ] || { echo "remote-emu: run from the repo root" >&2; exit 2; }
        ./gradlew :android:app:assembleDebug -q
    fi
    [ -f "$apk" ] || { echo "remote-emu: no APK at $apk" >&2; exit 1; }
    radb install -r "$apk"
}

cmd_serial() {
    LANE="$(resolve_lane "${1:-}")"
    direct_serial "$LANE"
}

cmd_env() {
    LANE="$(resolve_lane "${1:-}")"
    echo "export ANDROID_ADB_SERVER_PORT=$(local_port "$LANE")"
    echo "export ANDROID_SERIAL=$(serial_of "$LANE")"
}

# Debug app id: applicationId + the debug build type's ".dev" suffix (android/app/build.gradle.kts).
DEBUG_APP_ID="com.simplecityapps.shuttle.dev"

cmd_reset() {
    LANE="$(resolve_lane "${1:-}")"
    tunnel_pid "$LANE" >/dev/null || { echo "remote-emu: no tunnel for lane $LANE; run start first" >&2; exit 1; }
    radb shell pm clear "$DEBUG_APP_ID" >/dev/null 2>&1 || true # not installed yet on a fresh lane
    radb shell rm -rf /sdcard/Music/s2-seed
    echo "remote-emu: lane $LANE reset -- ${DEBUG_APP_ID} data cleared, /sdcard/Music/s2-seed removed"
}

cmd_ui_prep() {
    LANE="$(resolve_lane "${1:-}")"
    tunnel_pid "$LANE" >/dev/null || { echo "remote-emu: no tunnel for lane $LANE; run start first" >&2; exit 1; }
    local s
    for s in window_animation_scale transition_animation_scale animator_duration_scale; do
        radb shell settings put global "$s" 0
    done
    echo "remote-emu: lane $LANE animations disabled (window/transition/animator scale = 0)"
}

# ---- UI automation (uiautomator XML dump, text/desc lookup) ------------------------------

xml_unescape() {
    local s="$1"
    s="${s//&lt;/<}"; s="${s//&gt;/>}"; s="${s//&quot;/\"}"; s="${s//&apos;/\'}"; s="${s//&amp;/&}"
    echo "$s"
}

# dump_xml_to <local-file>: uiautomator dump (compressed, retried) saved to <local-file>. Retries
# because "could not get idle state" is common while music plays -- the playback screen's progress
# bar keeps ticking even with `ui-prep`'s animation scales at 0. Assumes $LANE is set.
dump_xml_to() {
    local out_file="$1" remote="/sdcard/s2-dump.xml" tries=8 n out
    for n in $(seq 1 "$tries"); do
        out="$(radb shell uiautomator dump --compressed "$remote" 2>&1 | tr -d '\r')"
        if ! echo "$out" | grep -qi error && radb pull "$remote" "$out_file" >/dev/null 2>&1 && [ -s "$out_file" ]; then
            return 0
        fi
        # Each idle-state failure costs ~10 s, and a screen that keeps updating (the mini player's
        # progress while music plays) never goes idle: give up after two rather than eight.
        if echo "$out" | grep -q "could not get idle state" && [ "$n" -ge 2 ]; then
            echo "remote-emu: the UI never went idle (music playing?): pause with support/scripts/s2-debug.sh PAUSE, or use a Maestro flow" >&2
            return 1
        fi
        sleep 1
    done
    echo "remote-emu: uiautomator dump failed after ${tries} attempts (last: ${out})" >&2
    return 1
}

# Field separator for list_nodes' output. Not a tab/space: bash `read` and `cut` treat IFS
# whitespace specially (leading/trailing separators are stripped, runs of them collapse), which
# silently shifts columns whenever the first field (text) is empty -- as it usually is for a
# content-desc-only node. \x1f (unit separator) never appears in UI text.
NODE_SEP=$'\x1f'

# list_nodes <xml-file>: one line per node with a text or content-desc: "text<NODE_SEP>desc<NODE_SEP>bounds".
list_nodes() {
    local node text desc bounds
    while IFS= read -r node; do
        text="$(grep -oE ' text="[^"]*"' <<<"$node" | head -1 | sed -E 's/ text="(.*)"/\1/')"
        desc="$(grep -oE ' content-desc="[^"]*"' <<<"$node" | head -1 | sed -E 's/ content-desc="(.*)"/\1/')"
        bounds="$(grep -oE ' bounds="[^"]*"' <<<"$node" | head -1 | sed -E 's/ bounds="(.*)"/\1/')"
        [ -z "$text" ] && [ -z "$desc" ] && continue
        printf '%s%s%s%s%s\n' "$(xml_unescape "$text")" "$NODE_SEP" "$(xml_unescape "$desc")" "$NODE_SEP" "$bounds"
    done < <(grep -oE '<node[^>]*>' "$1")
}

cmd_dump_texts() {
    LANE="$(resolve_lane)"
    tunnel_pid "$LANE" >/dev/null || { echo "remote-emu: no tunnel for lane $LANE; run start first" >&2; exit 1; }
    local tmp; tmp="$(mktemp)"
    if ! dump_xml_to "$tmp"; then rm -f "$tmp"; exit 1; fi
    while IFS="$NODE_SEP" read -r text desc bounds; do
        [ -n "$text" ] && echo "text=\"${text}\" bounds=${bounds}"
        [ -n "$desc" ] && echo "desc=\"${desc}\" bounds=${bounds}"
    done < <(list_nodes "$tmp")
    rm -f "$tmp"
}

cmd_tap_text() {
    LANE="$(resolve_lane)"
    tunnel_pid "$LANE" >/dev/null || { echo "remote-emu: no tunnel for lane $LANE; run start first" >&2; exit 1; }
    local field=text index=0 target=""
    while [ $# -gt 0 ]; do
        case "$1" in
            --desc) field=desc; shift ;;
            --index) index="${2:?remote-emu: --index needs a value}"; shift 2 ;;
            *) target="$1"; shift ;;
        esac
    done
    [ -n "$target" ] || { echo "remote-emu: usage: tap-text <text> [--desc] [--index N]" >&2; exit 2; }

    local tmp; tmp="$(mktemp)"
    if ! dump_xml_to "$tmp"; then rm -f "$tmp"; exit 1; fi

    local col=1; [ "$field" = "desc" ] && col=2
    local matches; matches="$(list_nodes "$tmp" | awk -F"$NODE_SEP" -v c="$col" -v t="$target" '$c == t')"
    if [ -z "$matches" ]; then
        echo "remote-emu: no node with ${field}=\"${target}\" -- visible text/desc:" >&2
        list_nodes "$tmp" | cut -d "$NODE_SEP" -f1,2 | tr "$NODE_SEP" '\n' | grep -v '^$' | sort -u >&2
        rm -f "$tmp"
        exit 1
    fi
    local chosen; chosen="$(echo "$matches" | sed -n "$((index + 1))p")"
    rm -f "$tmp"
    if [ -z "$chosen" ]; then
        echo "remote-emu: only $(echo "$matches" | wc -l | tr -d ' ') match(es) for ${field}=\"${target}\"; index ${index} out of range" >&2
        exit 1
    fi
    local bounds x1 y1 x2 y2 cx cy
    bounds="$(echo "$chosen" | cut -d "$NODE_SEP" -f3)"
    read -r x1 y1 x2 y2 <<<"$(echo "$bounds" | sed -E 's/\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]/\1 \2 \3 \4/')"
    cx=$(( (x1 + x2) / 2 )); cy=$(( (y1 + y2) / 2 ))
    echo "remote-emu: tapping ${field}=\"${target}\" at (${cx},${cy}) bounds=${bounds}"
    radb shell input tap "$cx" "$cy"
}

cmd_seed_music() {
    LANE="$(resolve_lane)"
    tunnel_pid "$LANE" >/dev/null || { echo "remote-emu: no tunnel for lane $LANE; run start first" >&2; exit 1; }
    command -v ffmpeg >/dev/null 2>&1 || { echo "remote-emu: ffmpeg not found on PATH" >&2; exit 1; }
    [ -f gradlew ] || { echo "remote-emu: run from the repo root" >&2; exit 2; }

    local dir="${1:-build/test-media/ui-seed}"
    mkdir -p "$dir"
    echo "remote-emu: generating seed tracks in ${dir} (cached files reused) ..."

    _seed_gen() {
        local out="$1" dur="$2" title="$3" artist="$4" album="$5" track="$6"
        [ -f "$out" ] && return 0
        ffmpeg -nostdin -loglevel error -f lavfi -i "anullsrc=r=44100:cl=mono" -t "$dur" \
            -metadata title="$title" -metadata artist="$artist" -metadata album_artist="$artist" \
            -metadata album="$album" -metadata track="$track" \
            -c:a libmp3lame -b:a 32k -y "$out" >/dev/null
    }
    _seed_gen "${dir}/a1_t1.mp3" 2   "Seed Song One"    "Seed Artist One" "Seed Album One" 1
    _seed_gen "${dir}/a1_t2.mp3" 2   "Seed Song Two"    "Seed Artist One" "Seed Album One" 2
    _seed_gen "${dir}/a1_t3.mp3" 2   "Seed Song Three"  "Seed Artist One" "Seed Album One" 3
    _seed_gen "${dir}/a2_t1.mp3" 2   "Seed Track Alpha" "Seed Artist Two" "Seed Album Two" 1
    _seed_gen "${dir}/a2_t2.mp3" 2   "Seed Track Beta"  "Seed Artist Two" "Seed Album Two" 2
    _seed_gen "${dir}/a2_t3.mp3" 2   "Seed Track Gamma" "Seed Artist Two" "Seed Album Two" 3
    _seed_gen "${dir}/a2_t4.mp3" 360 "Seed Long Player" "Seed Artist Two" "Seed Album Two" 4

    local remote_dir="/sdcard/Music/emu-seed" f pushed=0
    radb shell mkdir -p "$remote_dir"
    for f in "$dir"/*.mp3; do
        radb push "$f" "${remote_dir}/$(basename "$f")" >/dev/null
        pushed=$((pushed + 1))
    done
    echo "remote-emu: pushed ${pushed} track(s) to ${remote_dir}"

    # scan_volume only registers placeholder rows for new files -- scan_file is what actually runs
    # the metadata extractor per file (same finding as seed-test-media.sh).
    echo "remote-emu: scanning each file so MediaStore extracts tags ..."
    for f in "$dir"/*.mp3; do
        radb shell content call --uri content://media/ --method scan_file \
            --arg "${remote_dir}/$(basename "$f")" >/dev/null 2>&1 || true
    done
    radb shell content call --uri content://media --method scan_volume --arg external_primary >/dev/null 2>&1 || true

    echo "remote-emu: MediaStore updated -- the app itself still needs Settings -> Media -> Rescan to import these tracks into its library"
}

cmd_lockscreen() {
    LANE="$(resolve_lane)"
    tunnel_pid "$LANE" >/dev/null || { echo "remote-emu: no tunnel for lane $LANE; run start first" >&2; exit 1; }
    case "${1:-}" in
        on) radb shell locksettings set-disabled false >/dev/null; echo "remote-emu: lane $LANE lockscreen enabled" ;;
        off) radb shell locksettings set-disabled true >/dev/null; echo "remote-emu: lane $LANE lockscreen disabled" ;;
        *) echo "remote-emu: usage: lockscreen on|off" >&2; exit 2 ;;
    esac
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
    install) shift; cmd_install "$@" ;;
    serial) cmd_serial "${2:-}" ;;
    env) cmd_env "${2:-}" ;;
    reset) cmd_reset "${2:-}" ;;
    ui-prep) cmd_ui_prep "${2:-}" ;;
    tap-text) shift; cmd_tap_text "$@" ;;
    dump-texts) cmd_dump_texts ;;
    seed-music) cmd_seed_music "${2:-}" ;;
    lockscreen) cmd_lockscreen "${2:-}" ;;
    stop) cmd_stop "${2:-}" ;;
    *) sed -n '2,25p' "$0" >&2; exit 2 ;;
esac
