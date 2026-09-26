#!/usr/bin/env bash
# #444/#455: a restore with shuffle on and repeat all comes back on the same song, position,
# shuffle order and repeat mode; the restore logs its per-stage timings ("Queue restored in").
source "$(dirname "$0")/_lib.sh"

start_playback
s2 SHUFFLE --ez enabled true >/dev/null
s2 REPEAT --es mode all >/dev/null
s2 NEXT >/dev/null
s2 NEXT >/dev/null
wait_for 10 "s['state'] == 'Playing' and s['shuffle'] == 'On' and s['repeat'] == 'All'"
s2 SEEK --el ms 15000 >/dev/null
wait_for 5 "s['positionMs'] >= 15000"
s2 PAUSE >/dev/null
wait_for 5 "s['state'] == 'Paused'"
before="$(s2 DUMP_STATE)"
adb_retry logcat -c
adb_retry shell am force-stop "$APP_ID"
launch_app
wait_for 20 "s['queueSize'] == 5"
after="$(s2 DUMP_STATE)"
python3 - "$before" "$after" <<'PY' || fail "restore differs"
import json, sys
b, a = (json.loads(x) for x in sys.argv[1:3])
diffs = [k for k in ("title", "queuePosition", "queueTitles", "shuffle", "repeat") if a[k] != b[k]]
if abs(a["positionMs"] - b["positionMs"]) > 2000:
    diffs.append("positionMs")
for k in diffs:
    print(f"  {k}: before {b[k]!r}, after {a[k]!r}")
print(f"  restored {a['title']} at {a['positionMs']} ms, shuffle {a['shuffle']}, repeat {a['repeat']}, order {a['queueTitles']}")
sys.exit(1 if diffs else 0)
PY
timing="$(adb_retry logcat -d -v raw 2>/dev/null | grep -m1 'Queue restored in' || true)"
[ -n "$timing" ] || fail "no 'Queue restored in' log line after the restore"
echo "  ${timing}"
pass
