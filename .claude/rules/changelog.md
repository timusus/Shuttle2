---
paths:
  - "android/**"
---

# Changelog upkeep

`android/changelog-unreleased.json` is the draft changelog for the next release. It is committed
alongside code changes, and `/generate-changelog` builds the release entry from it at deploy time.
Commit messages alone cannot say what users actually saw (features get fixed before they ever
ship, builds get withdrawn), so the record is kept at the moment the change is made.

```json
{
  "since": "v26092301",
  "features": [ { "text": "…", "commits": ["abc1234"] } ],
  "improvements": [],
  "fixes": []
}
```

`since` is the last tag that actually shipped to users; a tag can exist on a build that was
withdrawn, so `git describe` is not the authority.

## Rules

- A commit that changes what a user sees or experiences adds one line to the fragment, in the
  user's language: plain English, no em dashes, no jargon, no "Alpha testers:" or any audience
  label.
- If the change iterates on an item already in the fragment (a fix or polish to something not yet
  shipped), edit that item's text and append the commit hash. Never add a separate "fix" line for
  a bug no user saw.
- If the change is internal (tests, refactors, tooling, CI, DI plumbing, lint config), add the
  trailer `Changelog: none` to the commit message instead.

The `.githooks/commit-msg` hook enforces this for `feat`/`fix`/`perf` commits: stage a fragment
change or carry the trailer (`SKIP_CHANGELOG_CHECK=1` bypasses). At release time
`/generate-changelog` folds the fragment into `android/app/src/main/assets/changelog.json` and
resets it; see `.claude/skills/generate-changelog/SKILL.md`.
