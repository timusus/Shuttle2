#!/usr/bin/env bash
# Local-import benchmark on a device. Usage: support/bench/bench.sh <taglib|mediastore> <a|b|c|d> [runs=3]
#   a: pm clear (.dev app only) + provider setup + first import (TagLib: walks the picked folder)
#   b: no-change routine import (the background sync)   c: touch 50 S2Bench files, routine import
#   d: no-change rescan (an import, which walks the picked folders on TagLib)
# Prints one line per run: "<provider> <scenario> run N: <ms> ms, peak PSS <MB> MB". Run from the repo root. See docs/testing/import-benchmark.md.
export PATH=$PATH:/Users/tim/Library/Android/sdk/platform-tools
export ANDROID_SERIAL=${ANDROID_SERIAL:-37211FDJG009GS}
P=com.simplecityapps.shuttle.dev
UI=support/bench/ui.sh
set +m
provider=$1; scen=$2; runs=${3:-3}
PREFS=shared_prefs/${P}_preferences.xml

start_app() { adb shell am start -n $P/com.simplecityapps.shuttle.ui.MainActivity >/dev/null; }

sampler_start() { # background PSS sampler writing the max to /tmp/bench.peak
  echo 0 >/tmp/bench.peak
  ( while :; do
      pid=$(adb shell pidof $P | tr -d '\r'); [ -n "$pid" ] && {
        kb=$(adb shell dumpsys meminfo "$pid" | awk '/TOTAL PSS:/{print $3; exit} /^ *TOTAL +[0-9]/{print $2; exit}')
        [ -n "$kb" ] && [ "$kb" -gt "$(cat /tmp/bench.peak)" ] && echo "$kb" >/tmp/bench.peak; }
      sleep 1; done ) &
  SAMPLER=$!
}
sampler_stop() { kill $SAMPLER 2>/dev/null; wait $SAMPLER 2>/dev/null; echo $(( $(cat /tmp/bench.peak) / 1024 )); }

# wait for the Nth "Import complete" since logcat -c (max 300 s); echoes the Nth ms value
wait_import() {
  local n=${1:-1} i
  for i in $(seq 1 150); do
    ms=$(adb logcat -d | grep "MediaImporter" | grep -o 'Import complete in [0-9]*' | awk '{print $4}' | sed -n "${n}p")
    [ -n "$ms" ] && { echo "$ms"; return 0; }
    sleep 2
  done
  echo TIMEOUT; return 1
}

pick_folder() {
  $UI tap Settings; sleep 1; $UI tap Sources; sleep 1
  $UI tap "This device"; sleep 1; $UI tap "Turn off"; sleep 2   # drop MediaStore
  $UI tap "This device"; sleep 3                                # enable Shuttle (TagLib)
  $UI tap "Folder rules"; sleep 1; $UI tap "Add folder"; sleep 2
  $UI tap "Music"; sleep 2; $UI tap "S2Bench"; sleep 2; $UI tap "Use this folder"; sleep 2
  adb logcat -c; sampler_start
  $UI tap "Allow"
}

setup_mediastore() {
  adb shell run-as $P sh -c "mkdir -p shared_prefs; printf '%s' \"<?xml version='1.0' encoding='utf-8' standalone='yes' ?><map><string name='media_providers'>1</string><boolean name='changelog_show_on_launch' value='false' /></map>\" > $PREFS"
}

for r in $(seq 1 "$runs"); do
  case $scen in
    a)
      adb shell pm clear $P >/dev/null; adb shell pm grant $P android.permission.READ_MEDIA_AUDIO
      if [ "$provider" = mediastore ]; then
        setup_mediastore; adb logcat -c; sampler_start; start_app; sleep 3
        support/scripts/s2-debug.sh IMPORT >/dev/null; wait_import 2 >/dev/null; ms=$(wait_import 1) # launch import = the full pass; the IMPORT broadcast's pass follows
      else
        start_app; sleep 6; pick_folder; ms=$(wait_import 1)
      fi ;;
    b)
      start_app; sleep 2; adb logcat -c; sampler_start
      support/scripts/s2-debug.sh SYNC >/dev/null; ms=$(wait_import 1) ;;
    c)
      adb shell "find /sdcard/Music/S2Bench -type f | head -50 | while read f; do touch \"\$f\"; done"
      [ "$provider" = mediastore ] && { adb shell content call --uri content://media/ --method scan_volume --arg external_primary >/dev/null; sleep 20; }
      start_app; sleep 2; adb logcat -c; sampler_start
      support/scripts/s2-debug.sh SYNC >/dev/null; ms=$(wait_import 1) ;;
    d)
      start_app; sleep 2; adb logcat -c; sampler_start
      support/scripts/s2-debug.sh IMPORT >/dev/null; ms=$(wait_import 1) ;;
  esac
  echo "$provider $scen run $r: $ms ms, peak PSS $(sampler_stop) MB"
done
