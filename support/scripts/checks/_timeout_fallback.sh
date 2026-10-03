# Generic watchdog fallback for running a command under a timeout when neither GNU `timeout` nor
# macOS's `gtimeout` (coreutils) is on PATH (#416, #454). Defines one function and sets no shell
# options itself, so it's safe to source from callers under either `set -e` or plain `set -u`.
# Sourced by checks/_lib.sh (adb_retry's ${ADB_CALL_TIMEOUT}s deadline) and
# support/scripts/emu-verify.sh (--suite's per-flow timeout); not run directly.

_timeout_tree_pids() { # <pid>: prints the pid and all its descendants, children first
    local child
    for child in $(pgrep -P "$1" 2>/dev/null); do
        _timeout_tree_pids "$child"
    done
    echo "$1"
}

# run_with_timeout <seconds> <cmd...>: runs the command, killing it (TERM, then KILL 2s later if it
# ignores that) after <seconds>. Mirrors `timeout`'s exit-124-on-timeout convention so a caller
# written against `timeout`/`gtimeout` behaves the same either way.
run_with_timeout() {
    local secs="$1"; shift
    "$@" &
    local cmd_pid=$!
    # The watchdog sleep runs backgrounded with its pid tracked in watchdog_sleep_pid, and the TERM
    # trap kills it before exiting -- so when the caller kills this subshell after the command
    # finishes early (the common case), the sleep dies with it instead of running to completion as
    # an orphan (a plain foreground `sleep` in a killed subshell isn't signalled itself and keeps
    # running for up to <seconds>).
    ( local watchdog_sleep_pid tree_pids
      trap 'kill "$watchdog_sleep_pid" 2>/dev/null; exit 0' TERM
      sleep "$secs" & watchdog_sleep_pid=$!
      wait "$watchdog_sleep_pid" 2>/dev/null
      kill -0 "$cmd_pid" 2>/dev/null || exit 0
      # The whole tree, not just the subshell: a function or wrapper script as <cmd> would otherwise
      # die while the process it launched (e.g. Maestro's java) keeps running as an orphan.
      # List it once up front: after the TERM the children are reparented and pgrep can't find them.
      tree_pids="$(_timeout_tree_pids "$cmd_pid")"
      # shellcheck disable=SC2086
      kill -TERM $tree_pids 2>/dev/null
      sleep 2 & watchdog_sleep_pid=$!
      wait "$watchdog_sleep_pid" 2>/dev/null
      # shellcheck disable=SC2086
      kill -KILL $tree_pids 2>/dev/null ) &
    local watchdog_pid=$!
    local status=0
    wait "$cmd_pid" || status=$?
    kill "$watchdog_pid" 2>/dev/null || true
    wait "$watchdog_pid" 2>/dev/null || true
    [ "$status" -ge 128 ] && status=124
    return "$status"
}
