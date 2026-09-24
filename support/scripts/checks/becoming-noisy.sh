#!/usr/bin/env bash
# Unplugging headphones (AUDIO_BECOMING_NOISY) pauses playback. AUDIO_BECOMING_NOISY is a protected
# broadcast adb can't send directly, so the debug-only BECOMING_NOISY action runs the app's real
# NoisyReceiver.onReceive instead of a copy of its logic (see DebugPlaybackReceiver).
source "$(dirname "$0")/_lib.sh"

start_playback
s2 BECOMING_NOISY >/dev/null
wait_for 5 "s['state'] == 'Paused'"
echo "  paused on $(state title) at $(state positionMs) ms"
pass
