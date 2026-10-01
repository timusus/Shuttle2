#!/usr/bin/env bash
# SessionStart: one line saying how far origin/main is past the last full verify (the watermark).
# No fetch, silent on error.
root="$(git rev-parse --show-toplevel 2>/dev/null)" || exit 0
msg="$("$root/support/scripts/full-verify.sh" --status --short 2>/dev/null)" || exit 0
[ -n "$msg" ] || exit 0
jq -cn --arg m "$msg" '{hookSpecificOutput:{hookEventName:"SessionStart",additionalContext:$m}}'
