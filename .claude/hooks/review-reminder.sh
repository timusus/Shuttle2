#!/usr/bin/env bash
# Nudges the owner to run /reply-reviews when Play reviews haven't been checked for a week.
# The Play API only returns reviews from the last 7 days, so a longer gap means replying in Play Console.
stamp="$HOME/.local/state/s2-play-reviews/last-run"
if [ -f "$stamp" ]; then
  age_days=$(( ($(date +%s) - $(stat -f %m "$stamp")) / 86400 ))
  [ "$age_days" -lt 7 ] && exit 0
  msg="Play reviews last checked ${age_days} days ago. Remind the owner to run /reply-reviews."
else
  msg="Play reviews have never been checked from this machine. Remind the owner to run /reply-reviews."
fi
jq -cn --arg m "$msg" '{hookSpecificOutput:{hookEventName:"SessionStart",additionalContext:$m}}'
