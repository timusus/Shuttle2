#!/usr/bin/env bash
# An incoming call, through the emulator console (gsm call): playing, S2 pauses while it rings, a
# play during the call doesn't start playback over it, and when the caller hangs up S2 plays on.
# Paused before the call, a call that rings and ends leaves S2 paused, and a play while it rings is
# held and starts when the call ends (API 31+, where the end of a call can be seen; below that it's
# dropped and S2 stays paused). The held play goes through the debug receiver and the notification's
# own button in turn (RS-54): both are held the same way. Not a headset keyevent too: a media key
# dispatched during a call goes to Telecom's global-priority session, not S2's, so it can't exercise
# this path. Also: pausing again before hangup after a held play (RS-54) leaves S2 paused too, not
# playing.
source "$(dirname "$0")/_lib.sh"

NUMBER=5551234

# An image without a dialer (the ATD one) takes no calls.
dialer="$(adb_retry shell cmd telecom get-default-dialer 2>/dev/null | tr -d '\r')"
if [ -z "$dialer" ] || [ "$dialer" = "null" ]; then
    echo "SKIP ${CHECK_NAME}: no dialer on this image to take a call"
    exit 0
fi

# The lane's console is on the WSL box, not the Mac; a local emulator's is reached by adb directly.
console() {
    "${CHECKS_ROOT}/support/scripts/remote-emu.sh" emu "$@" >/dev/null 2>&1 || adb_retry emu "$@" >/dev/null
}

# The audio mode dumpsys audio reports (NORMAL, RINGTONE, IN_CALL, ...), which CallMonitor reads.
audio_mode() {
    adb_retry shell dumpsys audio | sed -n 's/^- Actual mode = MODE_\([A-Z_]*\).*/\1/p' | head -1
}

# wait_for_mode <seconds> <regex>: polls the audio mode until it matches.
wait_for_mode() {
    local deadline=$(($(date +%s) + $1)) mode
    while :; do
        mode="$(audio_mode)"
        grep -qE "^($2)\$" <<<"$mode" && return 0
        [ "$(date +%s)" -lt "$deadline" ] || fail "audio mode is ${mode:-unknown}, not $2, after $1s"
        sleep 0.5
    done
}

ring() {
    console gsm call "$NUMBER"
    wait_for_mode 10 "RINGTONE|IN_CALL|IN_COMMUNICATION|CALL_SCREENING"
}

hang_up() {
    console gsm cancel "$NUMBER"
    wait_for_mode 10 "NORMAL"
}

# Taps the notification shade's Play or Pause button by its accessibility description, the same
# bounds-from-a-dump approach lock-screen-controls.sh uses (the seek bar's ticking position stops a
# Maestro flow from ever settling).
notification_tap() { # "Play" | "Pause"
    adb_retry shell cmd statusbar expand-notifications >/dev/null
    sleep 1
    local texts bounds x y
    texts="$("${CHECKS_ROOT}/support/scripts/remote-emu.sh" dump-texts 2>/dev/null)" || fail "no UI dump of the notification shade"
    bounds="$(sed -n "s/^desc=\"$1\" bounds=\\(.*\\)\$/\\1/p" <<<"$texts" | head -1)"
    [ -n "$bounds" ] || fail "the notification shade has no '$1' button"
    read -r x y < <(python3 -c 'import re,sys; x1,y1,x2,y2=map(int,re.findall(r"\d+",sys.argv[1])); print((x1+x2)//2,(y1+y2)//2)' "$bounds")
    adb_retry shell input tap "$x" "$y" >/dev/null
    adb_retry shell cmd statusbar collapse >/dev/null
}

# Hang up a call left ringing by a failure, so later checks aren't paused under it.
trap 'console gsm cancel "$NUMBER" 2>/dev/null || true' EXIT

sdk="$(adb_retry shell getprop ro.build.version.sdk | tr -d '\r')"

# Playing when the call comes in.
start_playback
ring
wait_for 5 "s['state'] == 'Paused' and s['title'] == 'Playback One'"
echo "  ringing: paused at $(state positionMs) ms"
screenshot g-phone-call-ringing
s2 PLAY >/dev/null
sleep 3
[ "$(state state)" = "Paused" ] || fail "a play during the call started playback over it"
echo "  play pressed while ringing: still paused"
hang_up
wait_for 10 "s['state'] == 'Playing' and s['title'] == 'Playback One'"
assert_progressing
echo "  call ended: playing at $(state positionMs) ms"

# Paused when the call comes in: its focus coming back when it ends doesn't start S2.
s2 PAUSE >/dev/null
wait_for 5 "s['state'] == 'Paused'"
ring
hang_up
sleep 3
[ "$(state state)" = "Paused" ] || fail "paused before a call, S2 started when it ended"
echo "  paused through a call: still paused after it"

# A play pressed while it rings waits for the call to end.
ring
s2 PLAY >/dev/null
sleep 3
[ "$(state state)" = "Paused" ] || fail "a play while paused during the call started playback over it"
echo "  paused, play pressed while ringing: still paused"
hang_up
if [ "$sdk" -ge 31 ]; then
    wait_for 10 "s['state'] == 'Playing' and s['title'] == 'Playback One'"
    assert_progressing
    echo "  call ended: the held play started"
else
    sleep 3
    [ "$(state state)" = "Paused" ] || fail "below API 31 the play during the call should be dropped"
    echo "  call ended: API ${sdk} drops the held play, still paused"
fi
s2 PAUSE >/dev/null
screenshot g-phone-call-ended

# RS-50 (doc line 139, "pause S2 from its notification during the call") isn't reachable as a
# distinct action here: ringing alone drops S2's audio focus and auto-pauses it (asserted above,
# "ringing: paused at ... ms"), so by the time a tap could land the notification already shows Play,
# not Pause -- there's no Pause button to tap, and DUMP_STATE doesn't distinguish a user-initiated
# pause from an audio-focus-loss auto-pause to test the two paths separately. The behaviour the
# bullet cares about (stays paused after hangup) is the same auto-pause case already covered above.

# RS-54 (API 31+): paused, ring, press play -- from the app (debug receiver) and from the
# notification -- held through the call, starts when it ends.
for source in app notification; do
    s2 PAUSE >/dev/null
    wait_for 5 "s['state'] == 'Paused'"
    ring
    if [ "$source" = app ]; then
        s2 PLAY >/dev/null
    else
        notification_tap Play
    fi
    sleep 3
    [ "$(state state)" = "Paused" ] || fail "a play from ${source} while ringing started playback over the call"
    hang_up
    if [ "$sdk" -ge 31 ]; then
        wait_for 10 "s['state'] == 'Playing' and s['title'] == 'Playback One'"
        assert_progressing
        echo "  RS-54, play held from ${source}: started after the call ended"
    else
        sleep 3
        [ "$(state state)" = "Paused" ] || fail "below API 31 a play held from ${source} should be dropped"
        echo "  RS-54, play held from ${source}: API ${sdk} dropped it, still paused"
    fi
done

# RS-54: paused, ring, press play (held), pause again before hangup -- stays paused after the call.
s2 PAUSE >/dev/null
wait_for 5 "s['state'] == 'Paused'"
ring
s2 PLAY >/dev/null
sleep 2
s2 PAUSE >/dev/null
hang_up
sleep 3
[ "$(state state)" = "Paused" ] || fail "pausing again before hangup should leave S2 paused after the call"
echo "  play then pause again before hangup: still paused after the call"
pass
