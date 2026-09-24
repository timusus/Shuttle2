#!/bin/sh
# longjob.sh — run a long command detached so an agent never blocks on it.
#
#   support/scripts/longjob.sh start <name> -- <command...>   # nohup, returns at once
#   support/scripts/longjob.sh status <name>                  # running/exited, code, last 5 lines
#   support/scripts/longjob.sh wait <name> [--timeout SECS]   # block until exit, short summary
#   support/scripts/longjob.sh tail <name> [N]                # last N log lines (default 20)
#
# State lives in .claude/longjobs/<name>.{log,pid,exit,start} at the repo root
# (resolved from this script's location, so it works from any worktree).
set -u

here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../.." && pwd)
dir="$root/.claude/longjobs"

usage() { sed -n '2,8p' "$0" >&2; exit 2; }

is_running() { # $1 = pid
  [ -n "$1" ] && kill -0 "$1" 2>/dev/null
}

cmd=${1:-}; [ -n "$cmd" ] || usage
name=${2:-}; [ -n "$name" ] || usage
case "$name" in */*|.*) echo "longjob: bad name '$name'" >&2; exit 2;; esac
log="$dir/$name.log"; pidf="$dir/$name.pid"; exitf="$dir/$name.exit"; startf="$dir/$name.start"

case "$cmd" in
start)
  shift 2
  [ "${1:-}" = "--" ] && shift
  [ $# -gt 0 ] || usage
  mkdir -p "$dir"
  if [ -f "$pidf" ] && is_running "$(cat "$pidf")"; then
    echo "longjob: '$name' is still running (pid $(cat "$pidf")); wait or pick another name" >&2
    exit 1
  fi
  rm -f "$exitf"
  : > "$log"
  date +%s > "$startf"
  # The subshell runs the command, then records its exit code; nohup keeps it alive
  # after this shell (and the agent's Bash call) has returned.
  nohup sh -c 'cd "$1"; shift; "$@" ; echo $? > "$0"' "$exitf" "$root" "$@" >> "$log" 2>&1 &
  echo $! > "$pidf"
  echo "started '$name' (pid $!)"
  echo "log: $log"
  echo "exit file: $exitf"
  ;;
status)
  [ -f "$log" ] || { echo "longjob: no job named '$name'" >&2; exit 1; }
  if [ -f "$exitf" ]; then
    code=$(cat "$exitf")
    echo "$name: exited with code $code"
  elif [ -f "$pidf" ] && is_running "$(cat "$pidf")"; then
    echo "$name: running (pid $(cat "$pidf"))"
  else
    echo "$name: not running and no exit code recorded (killed?)"
  fi
  echo "--- last 5 lines of $log"
  tail -n 5 "$log"
  [ -f "$exitf" ] && exit "$(cat "$exitf")"
  ;;
wait)
  [ -f "$log" ] || { echo "longjob: no job named '$name'" >&2; exit 1; }
  timeout=0
  if [ "${3:-}" = "--timeout" ]; then timeout=${4:-0}; fi
  waited=0
  while [ ! -f "$exitf" ]; do
    if [ -f "$pidf" ] && ! is_running "$(cat "$pidf")"; then
      sleep 1  # give the exit-code write a moment
      [ -f "$exitf" ] && break
      echo "$name: process gone without an exit code (killed?)"
      tail -n 20 "$log"; exit 1
    fi
    if [ "$timeout" -gt 0 ] && [ "$waited" -ge "$timeout" ]; then
      echo "$name: still running after ${timeout}s (pid $(cat "$pidf" 2>/dev/null))"
      echo "--- last 5 lines of $log"; tail -n 5 "$log"; exit 124
    fi
    sleep 2; waited=$((waited+2))
  done
  code=$(cat "$exitf")
  start=$(cat "$startf" 2>/dev/null || echo 0)
  dur=$(( $(date +%s) - start ))
  echo "$name: exit code $code, ran $((dur/60))m$((dur%60))s"
  echo "--- last 20 lines of $log"
  tail -n 20 "$log"
  exit "$code"
  ;;
tail)
  [ -f "$log" ] || { echo "longjob: no job named '$name'" >&2; exit 1; }
  tail -n "${3:-20}" "$log"
  ;;
*) usage;;
esac
