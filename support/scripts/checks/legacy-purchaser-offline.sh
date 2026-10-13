#!/usr/bin/env bash
# A 1.0.10 purchaser streams from a server before Play Billing has ever answered (#1024): with
# `app_purchased_date` set and the entitlement cache empty, EntitlementRepository seeds Pro. The
# control runs leave the pref unset, and the same stream is refused (Undecided), so the check can fail.
# Order: control, purchaser, control again. The second control proves the purchaser's seed, cache and restored
# queue don't leak into a start that has no pref.
#
#   support/scripts/checks/legacy-purchaser-offline.sh
#
# Needs ~/.config/s2-test/jellyfin.env (SKIPs without it). Resets the lane, signs in to Jellyfin
# (seed-remote-provider.sh) and restores the `playback` fixture on exit (a reset, which also clears the seeded
# pref, the entitlement cache and the persisted queue), so later checks still hold.
#
# Play never answers because com.android.vending is disabled, not because the network is off: the
# stream still needs the network. The debug build always resolves Pro, so the entitlement is switched to
# `store` (as a release build resolves) after each cold start; the seed itself runs at process start.
source "$(dirname "$0")/_lib.sh"

[ -f "$HOME/.config/s2-test/jellyfin.env" ] || {
    echo "SKIP ${CHECK_NAME}: needs ~/.config/s2-test/jellyfin.env"
    exit 0
}

fixture_album="S2 Transcode Test"
play_store="com.android.vending"

cleanup() {
    s2 PAUSE >/dev/null 2>&1 || true
    adb shell pm enable --user 0 "$play_store" >/dev/null 2>&1 || true
    restore_playback_fixture
}
trap cleanup EXIT

# cold_start <true|false>: leaves the stores as an upgrade from 1.0.10 would (purchased pref set or not), kills
# the process so the next one runs the seed, and resolves from the store rather than as a debug build.
cold_start() {
    # s2 returns on the receiver's "ok" line, which comes after both preference files are committed to disk.
    s2 SEED_LEGACY_PURCHASER --ez purchased "$1" >/dev/null
    local prefs
    prefs="$(adb shell "run-as ${APP_ID} grep -rhE 'cached_pro|app_purchased_date' shared_prefs" 2>/dev/null | tr -d '\r' || true)"
    if [ "$1" = false ]; then
        [ -z "$prefs" ] || fail "the preference files still hold the legacy seed after the receiver reported done: ${prefs}"
    else
        case "$prefs" in *app_purchased_date*) ;; *) fail "app_purchased_date isn't on disk after the receiver reported done" ;; esac
    fi
    adb shell am force-stop "$APP_ID"
    # The first broadcast starts the process, so the override is set before any activity or queue restore can start a
    # stream: the debug build resolves Pro until it is, which would load the restored queue's streams ungated.
    s2 SET_ENTITLEMENT --es state store >/dev/null
    launch_app
    s2 SHUFFLE --ez enabled false >/dev/null
}

# Prints PLAY_ALL's reply. Retries only while the seed's background import brings the album in: a refused
# stream is an answer, and replaying it would hide that.
play_fixture_album() {
    local deadline=$(($(date +%s) + 30)) reply
    while :; do
        reply="$(s2 PLAY_ALL --es album "'${fixture_album}'" 2>&1 || true)"
        case "$reply" in *"no songs on album"*) ;; *) break ;; esac
        [ "$(date +%s)" -lt "$deadline" ] || break
        sleep 1
    done
    echo "$reply"
}

# expect_refused <label>: a start without the pref must not stream.
expect_refused() {
    local reply
    cold_start false
    reply="$(play_fixture_album)"
    case "$reply" in
        *"playback failed"*) echo "  $1: refused (${reply})" ;;
        *) fail "$1: the stream wasn't refused without the legacy pref: ${reply}" ;;
    esac
    [ "$(state state)" != "Playing" ] || fail "$1: the stream is playing without the legacy pref"
}

"${CHECKS_ROOT}/support/scripts/remote-emu.sh" reset >/dev/null
"${CHECKS_ROOT}/support/scripts/seed-remote-provider.sh" jellyfin >/dev/null

if ! adb shell pm disable-user --user 0 "$play_store" 2>&1 | grep -q "new state: disabled-user"; then
    fail "couldn't disable ${play_store}, so Play could still answer"
fi

expect_refused "control (no pref)"

cold_start true
reply="$(play_fixture_album)"
wait_for 30 "s['state'] == 'Playing' and s['positionMs'] > 0" || fail "the purchaser's stream didn't play with Play silent (${reply})"
assert_progressing
echo "  purchaser streams ${fixture_album} with Play silent: $(state title)"

expect_refused "control after the purchaser"

pass
