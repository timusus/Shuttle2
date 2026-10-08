---
paths:
  - "android/changelog.d/**"
  - ".githooks/commit-msg"
---

# Changelog fragments

`android/changelog.d/` is the draft changelog for the next release, one fragment file per change, committed with its code
change; `/generate-changelog` builds the release entry from them. One file per change means parallel branches never
conflict. Commit messages can't say what users saw (features get fixed before they ship, builds get withdrawn), so the
record is kept when the change is made.

Name it `<issue>-<short-slug>.json` (e.g. `825-fragments.json`; the branch name if no issue), specific enough that two
branches never collide:

```json
{ "type": "features", "text": "…", "commits": ["abc1234"] }
```

`type` is `features`, `improvements` or `fixes`. `android/changelog.d/SINCE` holds the last tag that actually shipped (a
tag can sit on a withdrawn build, so `git describe` is not the authority); only the release step edits it.
`support/scripts/changelog-gather.sh` prints all fragments as one `{since, features, improvements, fixes}` document.

- A commit that changes what a user sees adds one fragment, in the user's language: plain English, no em dashes, no
  jargon, no "Alpha testers:" or other audience label.
- A change that iterates on an item already in a fragment (a fix to something not yet shipped) edits that fragment's text
  and appends the commit hash; never add a "fix" line for a bug no user saw.
- Internal changes (tests, refactors, tooling, DI plumbing, lint config) carry the trailer `Changelog: none` instead.

`.githooks/commit-msg` enforces this for `feat`/`fix`/`perf` commits: stage a fragment or carry the trailer
(`SKIP_CHANGELOG_CHECK=1` bypasses). Release time: see `.claude/skills/generate-changelog/SKILL.md`.
