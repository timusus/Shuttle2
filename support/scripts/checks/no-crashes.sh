#!/usr/bin/env bash
# No crash or ANR was recorded for the app during this run. run-all.sh clears the crash/main/system
# log buffers before the suite starts and always runs this check last, so a hit here is scoped to
# this run.
source "$(dirname "$0")/_lib.sh"

crash="$(adb logcat -d -b crash 2>/dev/null | grep -F "$APP_ID" || true)"
[ -z "$crash" ] || fail "crash buffer has an entry for ${APP_ID}: $(echo "$crash" | head -1)"

anr="$(adb logcat -d -b main -b system 2>/dev/null | grep -F "ANR in ${APP_ID}" || true)"
[ -z "$anr" ] || fail "an ANR was recorded for ${APP_ID}: $(echo "$anr" | head -1)"

pass
