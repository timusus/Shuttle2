#!/usr/bin/env bash
# #461: clear the artwork cache and download all artwork from Settings (artwork-cache.yaml), with no
# errors from the image loader in logcat.
source "$(dirname "$0")/_lib.sh"

adb_retry logcat -c
dc_maestro artwork-cache.yaml
sleep 10
errors="$(adb_retry logcat -d -v brief 2>/dev/null | grep -E '^E/' | grep -iE 'artwork|coil|image' | head -5 || true)"
[ -z "$errors" ] || fail "artwork errors in logcat: ${errors}"
crashes="$(app_crashes)"
[ -z "$crashes" ] || fail "crash after the artwork actions: ${crashes}"
pass
