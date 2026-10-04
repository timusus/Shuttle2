#!/bin/bash
# Print the unreleased changelog as one JSON document ({since, features, improvements, fixes}),
# gathered from the fragments in android/changelog.d/ (rules in .claude/rules/changelog.md).
# Read-only; the release step (/generate-changelog) deletes the fragments it consumed.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)/android/changelog.d"
shopt -s nullglob
files=(*.json)
since=$(tr -d '[:space:]' < SINCE)
if [ ${#files[@]} -eq 0 ]; then
  jq -n --arg since "$since" '{since: $since, features: [], improvements: [], fixes: []}'
else
  # One jq pass, buffered, so a malformed fragment or unknown type prints nothing and fails.
  out=$(jq -s --arg since "$since" '
    (map(select(.type | IN("features", "improvements", "fixes") | not)) | length) as $bad
    | if $bad > 0 then error("\($bad) fragment(s) with a type other than features/improvements/fixes")
      else . as $all | reduce ("features", "improvements", "fixes") as $t ({since: $since};
        .[$t] = [$all[] | select(.type == $t) | {text, commits: (.commits // [])}])
      end' "${files[@]}")
  printf '%s\n' "$out"
fi
