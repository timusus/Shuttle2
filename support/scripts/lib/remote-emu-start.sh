# shellcheck shell=bash
# `remote-emu.sh start` and ui-prep, in steps. Sourced, not run.

# Assumes $LANE is set and its tunnel is up; shared by cmd_ui_prep and cmd_start (which runs this
# once the lane has booted, so a raw `start` gets it without a separate `ui-prep` call).
ui_prep_lane() {
    local s
    for s in window_animation_scale transition_animation_scale animator_duration_scale; do
        radb shell settings put global "$s" 0 || return 1
    done
    echo "remote-emu: lane $LANE animations disabled (window/transition/animator scale = 0)"
}

# start_reuse_lane <lane> <api-or-empty> <avd>: this session already holds <lane> with qemu alive.
start_reuse_lane() {
    local mine="$1" api="$2" avd="$3"
    LANE="$mine"
    # An explicit --api must not be dropped silently. Restarting in place would throw away the
    # session's installed app and state, so ask for a deliberate stop + start instead.
    local booted; booted="$(lane_avd "$LANE")"
    if [ -n "$api" ] && [ "$booted" != "$avd" ]; then
        echo "remote-emu: lane $LANE is already running ${booted:-an unidentified AVD}, not $avd (--api $api);" \
            "run \`$0 stop\` then \`$0 start --api $api\` to switch" >&2
        exit 1
    fi
    echo "remote-emu: this session already holds lane $mine (${booted:-AVD unknown}) with qemu alive; reusing it"
}

# start_claim_lane <wanted-lane-or-empty>: leases a lane, reclaiming stale ones, and sets $LANE.
start_claim_lane() {
    local out
    if ! out="$(claim_lane "$1")"; then
        local line; line="$(echo "$out" | grep -m1 -E '^(HELD|BUSY|NONE)' || true)"
        set -- $line
        case "${1:-}" in
            HELD) echo "remote-emu: lane $2 is held by $3 (${4}s ago) -- \`stop $2\` only if you know it is dead" >&2 ;;
            BUSY) echo "remote-emu: lane $2 has a listener on $(console_port "$2") but no lease -- \`stop $2\` to clear" >&2 ;;
            *) echo "remote-emu: all $LANES lanes are taken; \`status\` shows who holds them" >&2 ;;
        esac
        exit 1
    fi
    local n owner
    while read -r _ n owner; do
        echo "remote-emu: reclaiming stale lease on lane $n (was $owner, nothing listening)"
    done < <(echo "$out" | grep '^STALE' || true)
    while read -r _ n owner; do
        echo "remote-emu: reclaiming lease on lane $n (was $owner, owner PID dead on this Mac)"
    done < <(echo "$out" | grep '^DEAD' || true)
    LANE="$(echo "$out" | awk '/^TAKEN/{print $2}')"
    remember_lane "$LANE"
    echo "remote-emu: leased lane $LANE ($(serial_of "$LANE")) as $OWNER"
}

# start_boot_emulator <avd>: launches the emulator for $LANE on the box.
start_boot_emulator() {
    local avd="$1" port log; port="$(console_port "$LANE")"; log="$(log_of "$LANE")"
    echo "remote-emu: booting $avd headless on $BOX, console port $port (${LANE_MEMORY}MB, ${LANE_CORES} cores) ..."
    # setsid + nohup + closed stdio, or the emulator's inherited fds keep this ssh open forever.
    box "setsid nohup emulator -avd ${avd} -port ${port} -read-only -no-window -no-audio \
        -no-boot-anim -gpu swiftshader_indirect -no-snapshot -memory ${LANE_MEMORY} -cores ${LANE_CORES} \
        >${log} 2>&1 </dev/null &"
}

start_wait_booted() {
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
}

# start_connect: tunnel, device check and UI prep for the booted $LANE.
start_connect() {
    local serial; serial="$(serial_of "$LANE")"
    # Keep ANR/crash dialogs from covering the UI that tests read.
    box_adb "-s ${serial} shell settings put global hide_error_dialogs 1" >/dev/null || true
    open_tunnel "$LANE"
    if ! ANDROID_ADB_SERVER_PORT="$(local_port "$LANE")" adb devices | grep -q "^${serial}[[:space:]]*device"; then
        echo "remote-emu: ${serial} not listed through the tunnel:" >&2
        ANDROID_ADB_SERVER_PORT="$(local_port "$LANE")" adb devices >&2
        cmd_stop "$LANE"; exit 1
    fi
    ui_prep_lane || echo "remote-emu: warning: ui-prep failed on lane $LANE; animations may be on (retry \`$0 ui-prep\`)" >&2
    echo "remote-emu: lane $LANE ready. $(radb shell getprop ro.product.model | tr -d '\r'), sdk $(radb shell getprop ro.build.version.sdk | tr -d '\r'), $(radb shell wm size | tr -d '\r')"
    echo "remote-emu: now  eval \"\$($0 env)\""
}

cmd_start() {
    check_available
    local want="" api=""
    while [ $# -gt 0 ]; do
        case "$1" in
            --api) api="${2:?remote-emu: --api needs 36 or 37}"; shift 2 ;;
            *) want="$1"; shift ;;
        esac
    done
    [ -z "$want" ] || valid_lane "$want" || { echo "remote-emu: lane must be 1..$LANES" >&2; exit 2; }
    local avd
    case "${api:-37}" in
        36) avd="$AVD_ATD" ;;
        37) avd="$AVD_API37" ;;
        *) echo "remote-emu: --api must be 36 or 37" >&2; exit 2 ;;
    esac
    local mine; mine="$(own_lane)"
    if [ -n "$mine" ] && [ -z "$want" ] \
        && box "test -d $(lease_of "$mine") && ss -ltn | grep -q ':$(console_port "$mine") '"; then
        start_reuse_lane "$mine" "$api" "$avd"
    else
        start_claim_lane "$want"
        start_boot_emulator "$avd"
    fi
    start_wait_booted
    start_connect
}
