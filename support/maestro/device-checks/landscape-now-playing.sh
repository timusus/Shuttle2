#!/usr/bin/env bash
# #381: Now Playing in landscape (landscape-now-playing.yaml), with the fixture queued and paused.
source "$(dirname "$0")/_lib.sh"

start_playback
s2 PAUSE >/dev/null
wait_for 5 "s['state'] == 'Paused'"
dc_maestro landscape-now-playing.yaml
pass
