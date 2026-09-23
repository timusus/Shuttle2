---
name: generate-changelog
description: Generate a user-facing changelog entry for a new Android release from the unreleased changelog fragment, audited against git commits since the last shipped tag. Updates changelog.json and resets the fragment.
user_invocable: true
---

# Generate Changelog

Generate a changelog entry for a new release from `android/changelog-unreleased.json`, the
fragment maintained at commit time (rules in `.claude/rules/changelog.md`), audited against git
commits since the last shipped tag.

**This skill is invoked automatically by `/deploy-android`.** It can also be run standalone.

## Inputs

You need two values — either passed as arguments or calculated the same way `/deploy-android` does:
- **VERSION_NAME**: date-based version, `YYYY.MM.DD` (e.g., `2026.09.23`)
- **TAG**: `vYYMMDDNN` (e.g., `v26092301`)

## Steps

### 1. Collect the unreleased changes

```bash
git fetch origin main --quiet
```

Read `android/changelog-unreleased.json`. This is the **primary source**: whoever made each
change recorded it there when committing it.

- **`since`** — the last tag that actually shipped to users. This is the release baseline, **not**
  `git describe`: a tag can exist on a build that was withdrawn before users saw it, and the
  fragment's `since` is the only record of what really shipped.
- **`features` / `improvements` / `fixes`** — the draft entry lines, each with the commits that
  contributed.

The git log since `since` is an **audit** for anything missed, not the source. Run it with full
commit messages and file stats so you can understand the actual changes rather than guessing from
subject lines:

```bash
git log <SINCE>..origin/main --no-merges --stat -- "android/"
```

If the audit turns up a user-facing change that is missing from the fragment, add it to the entry,
and tell the user the fragment discipline slipped so it can be corrected going forward.

**Anti-pattern — do NOT skim with subject lines only.** `--oneline` strips the body, which is where
user-facing impact actually lives. A `perf(playback)` commit might be a fail-fast change, not a
speed-up; a `fix(app)` commit might be internal hardening for a bug no user ever saw. Read the body
of every commit you're considering including or dismissing.

If the fragment has no `since` (bootstrap before the first tagged release under this scheme), fall
back to recent commits:
```bash
git log origin/main --no-merges --stat -20 -- "android/"
```

If the fragment and the audit are both empty, tell the user and stop.

#### Understanding ambiguous commits

When a commit subject is unclear about the user-facing impact, read the actual diff:

```bash
git show <commit-hash> -- "android/"
```

Only do this for commits where the subject + body + file stats aren't enough to confidently
describe the user impact.

#### The before-state hard rule

Any line implying a before-state ("no longer", "now", "instead of", "faster", "fixed") must be
checked against the **source code at the last shipped tag**, never against commit messages or the
previous changelog entry:

```bash
git show <SINCE>:<path>
git diff <SINCE>..HEAD -- <path>
```

If the before-state never shipped — the feature was itself added since `since` and then polished —
the line is not a fix: fold it into the feature's own description, or drop it.

#### When in doubt, ask

The user reviews each release. If you're unsure whether to include a particular item, ask before
generating the JSON. A brief question is much cheaper than a wrong release note.

#### A short changelog is better than a wrong one

If after filtering you only have one user-facing change, write a one-bullet changelog. Don't pad
with marginal items under "improvements". Single-fix releases are normal — say so plainly.

### 2. Generate the changelog entry

Read the commits and write plain, friendly lines a user would understand — not developer jargon.
Look at existing entries in `app/src/main/assets/changelog.json` for tone.

**Style:**
- Plain language, no em dashes, no filler editorialising ("for a cleaner look", "for a better
  experience") — state the change.
- No audience labels ("Alpha testers:").
- One line per change, most exciting/impactful first within each category.
- Omit empty categories from the entry (don't write `"features": []` if there are none).

### 3. Output format

Match the existing schema in `android/app/src/main/assets/changelog.json`:

```json
{
  "versionName": "2026.09.23",
  "releaseDate": "23/09/2026",
  "features": ["feature 1", "feature 2"],
  "fixes": ["fix 1"],
  "improvements": ["improvement 1"],
  "notes": []
}
```

- `versionName` is the date-based `YYYY.MM.DD` form (matches `TAG` without the `v` and with dots).
- `releaseDate` is `DD/MM/YYYY`, matching the format of every existing entry.
- `notes` is an optional freeform blurb array (existing entries use it for a short aside from the
  developer) — leave it as `[]` unless the user supplies one; don't invent a "voice" for them.

### 4. Update changelog.json and reset the fragment

Read `android/app/src/main/assets/changelog.json` (a JSON array of release entries, newest first).

- If an entry with the same `versionName` already exists, **replace** it.
- Otherwise, **prepend** the new entry to the array.

Write the updated file. Ensure valid JSON with consistent formatting (2-space indent).

Then reset `android/changelog-unreleased.json` to the new baseline with empty arrays:

```json
{
  "since": "<TAG>",
  "features": [],
  "improvements": [],
  "fixes": []
}
```

**Commit both files together** with the release: a changelog entry committed without its fragment
reset makes the next release describe a delta against a fragment that still claims the old
baseline.

```bash
git add android/app/src/main/assets/changelog.json android/changelog-unreleased.json
git commit -m "docs(app): update changelog for $VERSION_NAME"
```

This repo has no Play Store listing or release-notes automation (no fastlane) — the in-app
changelog is the only user-facing release note. Nothing further to write.
