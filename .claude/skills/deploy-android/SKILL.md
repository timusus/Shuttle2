---
name: deploy-android
description: Deploy the Android app to the Play Store from this Mac. Runs pre-flight checks, creates a version tag, then builds and uploads the bundle with support/scripts/release-android.
user_invocable: true
---

# Deploy Android to Play Store

Orchestrate a release of the Android app. This creates a `v*` tag marking the release, then
`support/scripts/release-android` builds the signed bundle and uploads it to the Play Store Internal
track. Secrets come from the Keychain (`secret get shuttle2 <NAME>`); nothing on GitHub reacts to the tag.

## Steps

### 1. Pre-flight: Git status

Must be on `main`, clean, and in sync with remote.

```bash
# Must be on main
git branch --show-current

# No uncommitted changes
git diff --quiet && git diff --cached --quiet

# No untracked files (excluding known untracked dirs)
git ls-files --others --exclude-standard

# Fetch and verify local matches remote
git fetch origin main
LOCAL=$(git rev-parse HEAD)
REMOTE=$(git rev-parse origin/main)
# LOCAL must equal REMOTE — no unpushed or missing commits
```

**STOP if any check fails.** Tell the user what needs fixing:
- Wrong branch → `git checkout main`
- Uncommitted changes → commit or stash
- Local behind remote → `git pull origin main`
- Local ahead of remote → `git push origin main`

### 2. Pre-flight: Full verify (watermark)

Landings only run a light verify; the full one is recorded as a watermark. Before tagging, the
watermark must cover the commit being released:

```bash
support/scripts/full-verify.sh --covers "$(git rev-parse HEAD)"   # exit 0 = covered
```

It passes when the watermark is `HEAD`, or an ancestor of it with only the changelog files from
step 5 changed since. If it exits non-zero, run the full verify (one wait; takes a while):

```bash
detach start full-verify -- support/scripts/full-verify.sh "$(git rev-parse HEAD)"
detach wait full-verify
```

**STOP if it fails** — it files (or comments on) a `bug` issue naming the step; do not tag. Exit 3 means an
infrastructure problem (lock, worktree, `local.properties`), not a test failure: fix it and rerun.

### 3. Pre-flight: Device checks (skip by default)

There are no instrumented tests; on-device coverage is the Maestro flows. Skip them unless the user
explicitly asks, then run the device smoke set with `support/scripts/emu-verify.sh --suite` (see
the `emulator-check` skill). If no emulator lane is free or the user skips, note it and continue.

### 4. Calculate version code

The version scheme is `vYYMMDDNN` where NN is the daily sequence number.

```bash
# Fetch tags
git fetch --tags

# Calculate
YY=$(date +%y)
MM=$(date +%m)
DD=$(date +%d)
BASE_CODE=$((YY * 1000000 + 10#$MM * 10000 + 10#$DD * 100))

# Count existing tags for today to determine revision
TODAY_PATTERN="v${YY}${MM}${DD}"
EXISTING=$(git tag -l "${TODAY_PATTERN}*" 2>/dev/null | wc -l | tr -d ' ')
REV=$((EXISTING + 1))
VERSION_CODE=$((BASE_CODE + REV))
VERSION_NAME="20${YY}.${MM}.${DD}"
TAG="v${VERSION_CODE}"
```

Tell the user: "Version: **$VERSION_NAME** (code: `$VERSION_CODE`, tag: `$TAG`)"

### 5. Generate changelog

Invoke `/generate-changelog` with VERSION_NAME and TAG from step 4.

This builds the entry from the fragments in `android/changelog.d/` (audited against commits since
`SINCE`), updates `android/app/src/main/assets/changelog.json` (in-app changelog), deletes the
consumed fragments and sets `SINCE` to `$TAG`.

Before generating, check `android/changelog.d/SINCE` names the tag this release **replaces** (the last
tag that actually shipped to users). If it disagrees with `git describe`, `SINCE` wins — a tag
can point at a build that was withdrawn.

**The changelog and fragment cleanup must be committed together before creating the tag**, so the
tagged commit includes them:
```bash
git add -A android/app/src/main/assets/changelog.json android/changelog.d
git commit -m "docs(app): update changelog for $VERSION_NAME"
```

### 6. Tag, push and release

Confirm with the user before uploading: "Ready to tag `$TAG` and upload to Play internal?"

```bash
git tag "$TAG"
git push origin main
git push origin "$TAG"
```

Run each push as its own bare command. Then build and upload, detached, with one wait:

```bash
detach start release-android -- support/scripts/release-android
detach wait release-android
detach tail release-android 40
```

`release-android --dry-run` builds the bundle without uploading. A "missing secret" failure names the
`secret set shuttle2 <NAME>` command to run.

## Error Recovery

- **Test failures:** Investigate and report — never skip tests
- **Tag already exists:** Increment the revision number (NN) and retry
- **Push rejected:** Check if main is behind remote (`git pull origin main`)
- **Changelog JSON invalid:** Show the error, help fix the JSON syntax
