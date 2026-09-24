#!/usr/bin/env bash
# The lock screen's media controls: locked while playing, the lock screen shows "Playback One" by
# "Playback Artist", and its play/pause button pauses and plays. Turns the lane's keyguard on for
# the check and off again after.
#
# adb taps, not Maestro: while music plays the seek bar never settles, and Maestro spends ~40 s
# before each tap waiting for it, long enough for the song to end. The button's bounds come from a
# dump taken while paused (uiautomator can't dump while the seek bar moves either); it doesn't
# move between play and pause.
source "$(dirname "$0")/_lib.sh"

emu="${CHECKS_ROOT}/support/scripts/remote-emu.sh"
# The keyguard takes a moment to notice it's been disabled, so the first dismiss can be ignored.
unlock() {
    "$emu" lockscreen off >/dev/null 2>&1 || true
    adb_retry shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
    for _ in 1 2 3 4 5; do
        adb_retry shell wm dismiss-keyguard >/dev/null 2>&1 || true
        sleep 1
        adb_retry shell dumpsys window 2>/dev/null | grep -q "isKeyguardShowing=true" || return 0
    done
    echo "lock-screen-controls: the keyguard is still showing" >&2
}
trap unlock EXIT

"$emu" lockscreen on >/dev/null
start_playback
adb_retry shell input keyevent KEYCODE_SLEEP >/dev/null
adb_retry shell input keyevent KEYCODE_WAKEUP >/dev/null
adb_retry shell dumpsys window | grep -q "isKeyguardShowing=true" || fail "the keyguard isn't showing after sleep and wake"
wait_for 5 "s['state'] == 'Playing'"
sleep 1
screenshot b-lock-screen-playing

s2 PAUSE >/dev/null
wait_for 5 "s['state'] == 'Paused'"
texts="$("$emu" dump-texts)" || fail "no UI dump of the lock screen"
grep -q 'desc="Lock screen"' <<<"$texts" || fail "the lock screen isn't showing"
grep -q 'text="Playback One"' <<<"$texts" || fail "the lock screen doesn't show Playback One"
grep -q 'text="Playback Artist"' <<<"$texts" || fail "the lock screen doesn't show Playback Artist"
bounds="$(sed -n 's/^desc="Play" bounds=\(.*\)$/\1/p' <<<"$texts" | head -1)"
[ -n "$bounds" ] || fail "the lock screen has no Play button"
read -r x y < <(python3 -c 'import re,sys; x1,y1,x2,y2=map(int,re.findall(r"\d+",sys.argv[1])); print((x1+x2)//2,(y1+y2)//2)' "$bounds")

adb_retry shell input tap "$x" "$y" >/dev/null
wait_for 5 "s['state'] == 'Playing' and s['title'] == 'Playback One'"
assert_progressing
adb_retry shell input tap "$x" "$y" >/dev/null
wait_for 5 "s['state'] == 'Paused' and s['title'] == 'Playback One'"
screenshot b-lock-screen-paused-by-tap
adb_retry shell input tap "$x" "$y" >/dev/null
wait_for 5 "s['state'] == 'Playing' and s['title'] == 'Playback One'"
s2 PAUSE >/dev/null
pass
