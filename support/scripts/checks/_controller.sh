# Driving the test MediaController app (android/testing/media-controller) from a check. Sourced after
# _lib.sh, not run. install_controller builds the APK when it isn't there yet.

CONTROLLER_ID="com.simplecityapps.shuttle.testing.controller"
CONTROLLER_APK="${CHECKS_ROOT}/android/testing/media-controller/build/outputs/apk/debug/media-controller-debug.apk"

install_controller() {
    [ -f "$CONTROLLER_APK" ] || "${CHECKS_ROOT}/support/scripts/remote-build.sh" -q :android:testing:media-controller:assembleDebug >&2
    [ -f "$CONTROLLER_APK" ] || fail "no controller APK at ${CONTROLLER_APK}"
    adb_retry install -r -t "$CONTROLLER_APK" >/dev/null
}

# ctl <cmd> [am extras...]: runs one controller command and prints its S2CTRL result line. Tagged with a
# fresh id so an earlier run's line is never mistaken for this one's.
ctl() {
    local cmd="$1" id="$$-$RANDOM" deadline line
    shift
    adb_retry shell am start -n "${CONTROLLER_ID}/.ControllerActivity" --es cmd "$cmd" --es id "$id" "$@" >/dev/null
    deadline=$(($(date +%s) + 20))
    while :; do
        line="$(adb_retry logcat -d -s S2CTRL:I | grep -F " id=${id} " | tail -1 || true)"
        [ -z "$line" ] || break
        [ "$(date +%s)" -lt "$deadline" ] || fail "no S2CTRL result for '${cmd}' within 20s"
        sleep 0.5
    done
    echo "  controller: ${line#*S2CTRL  : }" >&2
    printf '%s\n' "$line"
}

# expect_in <result line> <text>...: every text appears in the line.
expect_in() {
    local line="$1" text
    shift
    for text in "$@"; do
        case "$line" in *"$text"*) ;; *) fail "controller result lacks '${text}': ${line}" ;; esac
    done
}

# song_id <queue index>: that queue entry's song id, which is also its media id.
song_id() {
    s2 DUMP_STATE | python3 -c 'import json,sys; print(json.load(sys.stdin)["queueSongIds"][int(sys.argv[1])])' "$1"
}
