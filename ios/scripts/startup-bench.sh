#!/usr/bin/env bash
# Cold-start benchmark: N cold launches, each streaming the app's "Startup" OSLog category until the Home or Library
# content milestone, then median/min/max per milestone (docs/performance/ios-startup.md "Method").
#
# usage: ios/scripts/startup-bench.sh (--sim [UDID] | --device UDID) [-n 5] [--bundle <id>] [--append] [-- <launch args>]
#   --sim [UDID]    a simulator: UDID, else $S2_SIMULATOR_UDID, else leased from the shared ios-sim pool and
#                   released on exit (S2_SIM_HOLDER leases as another holder; gives up after 5 minutes)
#   --device UDID   a physical device: launched with devicectl, logs streamed with idevicesyslog (libimobiledevice);
#                   nothing is installed, the app must already be on the phone. idevicesyslog prints no category, so
#                   it streams the app's process ("S2") and only the known milestone/step/Home/Database lines are kept
#                   (needs jq to find the pid; untested on hardware)
#   -n N            cold launches (default 5)
#   --bundle ID     bundle id to launch (default com.simplecityapps.shuttle.dev, the Debug build; Release is
#                   com.simplecityapps.shuttle)
#   --append        append the table to docs/performance/ios-startup.md
#   -- ARGS         passed to the launch. Preference overrides are `-pref_<key> <value>` and need the `--` so this
#                   script doesn't parse them; booleans are written `<false/>` / `<true/>`, e.g.
#                   `-- -pref_crash_reporting '<false/>'`
#
# The app must be installed (ios/scripts/build-app.sh, run-sim-server.sh). Each launch is terminate, start the stream,
# launch, wait for "home content" / "library content" (30 s timeout, then 1 s more for stragglers). Rows are ms since
# the kernel started the process, or the step's duration for `step <name> took N ms`. The Kotlin "Home: ..." and
# "Database: ..." lines are rows named after their label, durations (us/ms/s) converted to ms.
#
# Live syslog drops lines: a milestone missing from a run is left out of that run (never counted as 0), and the n
# column says how many of the launches logged it. A run that never reaches a content milestone is reported.
# The app restores its last nav state (@SceneStorage) and no launch arg or pref resets it, so a run that last quit on
# another tab never logs Home content: bring the app to Home once before benchmarking, or expect "library content".
set -euo pipefail

ios_dir="$(cd "$(dirname "$0")/.." && pwd)"
doc="$ios_dir/../docs/performance/ios-startup.md"
mode="" udid="" runs=5 bundle="com.simplecityapps.shuttle.dev" append=0 launch_args=()

usage() { sed -n '2,29p' "$0" | sed 's/^# \{0,1\}//'; }

while [ $# -gt 0 ]; do
  case "$1" in
    -h|--help) usage; exit 0 ;;
    --sim) mode=sim; if [ $# -gt 1 ] && [[ "$2" != -* ]]; then udid="$2"; shift; fi ;;
    --device) mode=device; udid="${2:?--device needs a UDID}"; shift ;;
    -n) runs="${2:?-n needs a count}"; shift ;;
    --bundle) bundle="${2:?--bundle needs an id}"; shift ;;
    --append) append=1 ;;
    --) shift; launch_args=("$@"); break ;;
    *) echo "startup-bench: unknown argument '$1'" >&2; usage >&2; exit 2 ;;
  esac
  shift
done
[ -n "$mode" ] || { usage >&2; exit 2; }
[[ "$runs" =~ ^[1-9][0-9]*$ ]] || { echo "startup-bench: -n wants a positive number" >&2; exit 2; }

work="$(mktemp -d)"
leased=0
stream_pid=""
marker="startup-bench-$$" # makes this run's in-simulator `log stream` findable for cleanup
stop_stream() {
  [ -z "$stream_pid" ] || kill "$stream_pid" 2>/dev/null || true
  # simctl spawn doesn't forward the signal to the in-simulator process
  [ "$mode" != sim ] || pkill -f "$marker" 2>/dev/null || true
  [ -z "$stream_pid" ] || wait "$stream_pid" 2>/dev/null || true
  stream_pid=""
}
cleanup() {
  stop_stream
  if [ "$leased" = 1 ]; then
    CLAUDE_CODE_SESSION_ID="$("$ios_dir/scripts/lease-sim.sh" --holder)" ~/.claude/scripts/ios-sim/sim-lease.sh release >/dev/null 2>&1 || true
  fi
  rm -rf "$work"
}
trap cleanup EXIT

if [ "$mode" = sim ] && [ -z "$udid" ]; then
  udid="${S2_SIMULATOR_UDID:-}"
  if [ -z "$udid" ]; then
    leased=1 # the lease can land just as the timeout fires; releasing when none is held is harmless
    "$ios_dir/scripts/lease-sim.sh" >"$work/lease" 2>/dev/null &
    lease_pid=$!
    for _ in $(seq 300); do kill -0 "$lease_pid" 2>/dev/null || break; sleep 1; done
    if kill -0 "$lease_pid" 2>/dev/null; then
      kill "$lease_pid" 2>/dev/null || true
      echo "startup-bench: no simulator lease within 5 minutes; skipping" >&2
      exit 1
    fi
    wait "$lease_pid" || { echo "startup-bench: no leased simulator available; pass a UDID or set \$S2_SIMULATOR_UDID" >&2; exit 1; }
    udid="$(cat "$work/lease")"
  fi
fi

# Swift logs under com.simplecityapps.shuttle, Kotlin (os.Logger sink) under com.simplecityapps.shuttle2.
predicate='subsystem BEGINSWITH "com.simplecityapps.shuttle" AND category == "Startup"'

stop_app() {
  if [ "$mode" = sim ]; then
    xcrun simctl terminate "$udid" "$bundle" >/dev/null 2>&1 || true
  else
    # The app's install path (from its bundle id) prefixes its executable in the process list.
    local app_url pid
    xcrun devicectl device info apps --device "$udid" --bundle-id "$bundle" --json-output "$work/apps.json" >/dev/null 2>&1 || true
    app_url="$(jq -r '.result.apps[0].url // empty' "$work/apps.json" 2>/dev/null || true)"
    [ -n "$app_url" ] || return 0
    xcrun devicectl device info processes --device "$udid" --json-output "$work/procs.json" >/dev/null 2>&1 || true
    pid="$(jq -r --arg u "${app_url%/}/" '[.result.runningProcesses[]? | select((.executable // "") | contains($u))][0].processIdentifier // empty' "$work/procs.json" 2>/dev/null || true)"
    [ -z "$pid" ] || xcrun devicectl device process terminate --device "$udid" --pid "$pid" >/dev/null 2>&1 || true
  fi
}

if [ "$mode" = device ]; then
  command -v idevicesyslog >/dev/null || { echo "startup-bench: --device needs idevicesyslog (brew install libimobiledevice)" >&2; exit 1; }
  command -v jq >/dev/null || { echo "startup-bench: --device needs jq (brew install jq)" >&2; exit 1; }
fi

start_stream() { # $1 = output file
  : >"$1"
  if [ "$mode" = sim ]; then
    xcrun simctl spawn "$udid" log stream --level debug --style compact --predicate "$predicate AND eventMessage != \"$marker\"" >"$1" 2>&1 & # the header goes to stderr
  else
    idevicesyslog -u "$udid" -p S2 >"$1" 2>/dev/null &
  fi
  stream_pid=$!
  # The stream prints a header line once attached; bounded wait, then carry on.
  for _ in $(seq 100); do [ -s "$1" ] && return 0; sleep 0.1; done
  echo "startup-bench: log stream printed no header in 10 s; continuing" >&2
}

launch_app() {
  if [ "$mode" = sim ]; then
    xcrun simctl launch "$udid" "$bundle" ${launch_args[@]+"${launch_args[@]}"} >/dev/null
  else
    xcrun devicectl device process launch --device "$udid" "$bundle" ${launch_args[@]+"${launch_args[@]}"} >/dev/null
  fi
}

[ "$mode" != sim ] || xcrun simctl boot "$udid" 2>/dev/null || true

reached=0
for i in $(seq "$runs"); do
  out="$work/run$i.log"
  stop_app
  sleep 1
  start_stream "$out"
  launch_app || { echo "startup-bench: launch failed (is $bundle installed?)" >&2; exit 1; }
  for _ in $(seq 300); do
    grep -Eq '(home|library) content at [0-9]+ ms' "$out" 2>/dev/null && break
    sleep 0.1
  done
  if grep -Eq '(home|library) content at [0-9]+ ms' "$out"; then reached=$((reached + 1)); else echo "run $i: no content milestone in 30 s" >&2; fi
  sleep 1
  stop_stream
  # One "<ms> <milestone>" line per logged milestone, first occurrence only, in log order. idevicesyslog has no
  # category, so device lines are taken after the "<Level>: " prefix and filtered by the known shapes below.
  awk -v dev="$([ "$mode" = device ] && echo 1 || echo 0)" '
    function ms(d,   u, n) { # Kotlin Duration.toString() token (12.3ms, 1.23s, 345us) to ms
      u = d; sub(/^[0-9.]+/, "", u); n = d; sub(/(ns|us|ms|s)$/, "", n)
      return n * (u == "s" ? 1000 : u == "ms" ? 1 : u == "us" ? 0.001 : 0.000001)
    }
    dev ? match($0, />: .*/) : match($0, /Startup\] .*/) {
      msg = substr($0, RSTART + (dev ? 3 : 9))
      if (msg ~ /^(Home|Database|Search index): / && match(msg, / [0-9.]+(ns|us|ms|s)( |$)/)) {
        dur = substr(msg, RSTART + 1, RLENGTH - 1); sub(/ $/, "", dur)
        name = substr(msg, 1, RSTART - 1) " " substr(msg, RSTART + RLENGTH)
        sub(/ +$/, "", name); sub(/ in$/, "", name); gsub(/  +/, " ", name)
        v = ms(dur)
      }
      else if (msg ~ / at [0-9]+ ms$/) { name = msg; sub(/ at [0-9]+ ms$/, "", name); v = msg; sub(/.* at /, "", v); sub(/ ms$/, "", v) }
      else if (msg ~ /^step .* took [0-9.]+ ms$/) { name = msg; sub(/^step /, "", name); sub(/ took .*/, "", name); name = "step " name " (duration)"; v = msg; sub(/.* took /, "", v); sub(/ ms$/, "", v) }
      else next
      if (!(name in seen)) { seen[name] = 1; print v "\t" name }
    }' "$out" >"$work/run$i.tsv"
done
stop_app

cat "$work"/run*.tsv >"$work/all.tsv"
[ -s "$work/all.tsv" ] || { echo "startup-bench: no Startup lines logged in $runs launches" >&2; exit 1; }

# Milestones in first-seen order; median (min-max) of the runs that logged each.
table="$(awk -F'\t' -v runs="$runs" '
  !($2 in n) { order[++k] = $2 }
  { vals[$2, ++n[$2]] = $1 + 0 }
  END {
    print "| Milestone | Median (min–max) ms | n/" runs " |"
    print "|---|---|---|"
    for (j = 1; j <= k; j++) {
      m = order[j]; c = n[m]
      for (a = 1; a <= c; a++) s[a] = vals[m, a]
      for (a = 2; a <= c; a++) { x = s[a]; b = a - 1; while (b >= 1 && s[b] > x) { s[b + 1] = s[b]; b-- } s[b + 1] = x }
      med = (c % 2) ? s[(c + 1) / 2] : (s[c / 2] + s[c / 2 + 1]) / 2
      printf "| %s | %.1f (%.1f–%.1f) | %d |\n", m, med, s[1], s[c], c
    }
  }' "$work/all.tsv")"

header="$bundle on $mode $udid, $runs cold launches ($reached reached a content milestone), $(date +%Y-%m-%d)"
echo "$header"
echo "$table"
if [ "$append" = 1 ]; then
  { printf '\n### Startup bench: %s\n\n%s\n' "$header" "$table"; } >>"$doc"
  echo "appended to docs/performance/ios-startup.md"
fi
