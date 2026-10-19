# shellcheck shell=bash
# Maestro flow runner and device helpers for support/scripts/release-compare.sh. Sourced, not run.

# scrub: stdin to stdout with the credential values replaced (awk, so they stay off every command line).
scrub() {
    RC_SECRETS="${URL}"$'\n'"${USER_NAME}"$'\n'"${PASSWORD}" awk 'BEGIN { n = split(ENVIRON["RC_SECRETS"], s, "\n") }
        { for (i = 1; i <= n; i++) { v = s[i]; if (v == "") continue; while ((j = index($0, v)) > 0) $0 = substr($0, 1, j - 1) "***" substr($0, j + length(v)) } print }'
}

# run_flow <version> <id> [creds|optional]: runs <id>'s flow (old/new override, else shared); 2 if the version has
# none. creds hands it the server credentials; optional means a failure is expected sometimes, so no warning.
run_flow() {
    local ver="$1" id="$2" mode="${3:-}" creds="" flow rc args
    [ "$mode" = creds ] && creds=1
    if [ -f "${FLOWS}/${ver}/${id}.yaml" ]; then flow="${FLOWS}/${ver}/${id}.yaml"
    elif [ -f "${FLOWS}/${id}.yaml" ]; then flow="${FLOWS}/${id}.yaml"
    else return 2; fi
    args=(-e "APP_ID=${PKG}" -e "ARTIST=${ARTIST}" -e "ALBUM=${ALBUM}" -e "ALBUM_TRACK=${ALBUM_TRACK}" -e "PLAYING_ARTIST=${PLAYING_ARTIST}" -e "PLAYING_ALBUM=${PLAYING_ALBUM}" -e "PLAYING_TRACK=${PLAYING_TRACK}" -e "QUERY=${QUERY}"
        -e "GENRE=${GENRE}" -e "PLAYLIST=${PLAYLIST}")
    # MAESTRO_-prefixed env vars reach flows as ${MAESTRO_*}; set only for this one invocation, never on argv
    if [ -n "$creds" ]; then
        MAESTRO_URL="$URL" MAESTRO_USER_NAME="$USER_NAME" MAESTRO_PASSWORD="$PASSWORD" \
            run_with_timeout "$FLOW_TIMEOUT" maestro_run "$flow" "${WORK}/scratch" "${args[@]}" 2>&1 | scrub >"${WORK}/maestro-${ver}-${id}.log"
        rc=${PIPESTATUS[0]}
    else
        run_with_timeout "$FLOW_TIMEOUT" maestro_run "$flow" "${WORK}/scratch" "${args[@]}" 2>&1 | scrub >"${WORK}/maestro-${ver}-${id}.log"
        rc=${PIPESTATUS[0]}
    fi
    rm -rf -- "${WORK}/scratch"
    if [ "$rc" -ne 0 ]; then
        [ "$mode" = optional ] || warn "$ver" "$id" "flow failed (rc ${rc}), see ${WORK}/maestro-${ver}-${id}.log"
        return 1
    fi
}

start_app() {
    adb shell am force-stop "$PKG" >/dev/null 2>&1
    # explicit component: debug builds also have LeakCanary's launcher, which monkey may pick
    adb shell am start -n "${PKG}/com.simplecityapps.shuttle.ui.MainActivity" >>"$LOG" 2>&1
    sleep 3
}

# local_avd_up: the running local emulator, else a headless boot of $LOCAL_AVD; exports ANDROID_SERIAL. Explicit
# serials throughout, since a phone may be plugged in too. The AVD's data persists, so installed apps survive a restart.
local_emulator() { adb devices | awk '$1 ~ /^emulator-/ && $2 == "device" {print $1; exit}'; }
local_avd_up() {
    local serial deadline
    serial="$(local_emulator)"
    if [ -z "$serial" ]; then
        nohup "$HOME/Library/Android/sdk/emulator/emulator" -avd "$LOCAL_AVD" -no-window -no-audio -no-boot-anim \
            -no-snapshot-save </dev/null >"${WORK}/emulator.log" 2>&1 &
        disown
    fi
    deadline=$(($(date +%s) + 420))
    until [ -n "$serial" ] && [ "$(adb -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do
        [ "$(date +%s)" -lt "$deadline" ] || { echo "local AVD didn't boot in 420 s"; return 1; }
        sleep 5
        serial="$(local_emulator)"
    done
    export ANDROID_SERIAL="$serial"
    local s
    for s in window_animation_scale transition_animation_scale animator_duration_scale; do adb shell settings put global "$s" 0; done
}

# dump_texts: every visible text and content-desc, one per line (the import poll compares these).
dump_texts() {
    adb shell uiautomator dump /sdcard/rc-dump.xml >/dev/null 2>&1 || return 1
    adb exec-out cat /sdcard/rc-dump.xml | grep -oE '(text|content-desc)="[^"]+"'
}
