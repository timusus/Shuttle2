---
paths:
  - "android/**"
---

# Changelog upkeep

`android/changelog.d/` holds the draft changelog for the next release, one fragment file per
change. Each is committed alongside its code change, and `/generate-changelog` builds the release
entry from them at deploy time. One file per change means parallel branches never conflict on it.
Commit messages alone cannot say what users actually saw (features get fixed before they ever
ship, builds get withdrawn), so the record is kept at the moment the change is made.

Name it `<issue>-<short-slug>.json` (e.g. `825-fragments.json`; use the branch name if there is no
issue) and keep the slug specific so two branches never pick the same name:

```json
{ "type": "features", "text": "…", "commits": ["abc1234"] }
```

`type` is `features`, `improvements` or `fixes`. `android/changelog.d/SINCE` holds the last tag
that actually shipped to users; a tag can exist on a build that was withdrawn, so `git describe`
is not the authority. Only the release step edits it.

`support/scripts/changelog-gather.sh` prints all fragments as one `{since, features, improvements,
fixes}` document.

## Rules

- A commit that changes what a user sees or experiences adds one fragment file, in the
  user's language: plain English, no em dashes, no jargon, no "Alpha testers:" or any audience
  label.
- If the change iterates on an item already in a fragment (a fix or polish to something not yet
  shipped), edit that fragment's text and append the commit hash. Never add a separate "fix" line for
  a bug no user saw.
- If the change is internal (tests, refactors, tooling, CI, DI plumbing, lint config), add the
  trailer `Changelog: none` to the commit message instead.

The `.githooks/commit-msg` hook enforces this for `feat`/`fix`/`perf` commits: stage a fragment
file or carry the trailer (`SKIP_CHANGELOG_CHECK=1` bypasses). At release time
`/generate-changelog` folds the fragments into `android/app/src/main/assets/changelog.json`,
deletes them and moves `SINCE`; see `.claude/skills/generate-changelog/SKILL.md`.
