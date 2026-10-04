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
  jq -s --arg since "$since" '
    . as $all | {since: $since}
    + ([ "features", "improvements", "fixes" ][] as $t
       | {($t): [$all[] | select(.type == $t) | {text, commits}]}) ' "${files[@]}" \
  | jq -s 'add'
fi
