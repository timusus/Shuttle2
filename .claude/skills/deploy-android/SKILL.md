---
name: deploy-android
description: Deploy the Android app to the Play Store via GitHub Actions. Runs pre-flight checks, creates a version tag, and pushes to trigger the deploy pipeline.
user_invocable: true
---

# Deploy Android to Play Store

Orchestrate a release of the Android app. This creates a `v*` tag that triggers the GitHub Actions `continuous_deployment.yml` pipeline (build → upload to Play Store Internal track).

**No direct builds or uploads.** The tag push triggers GitHub Actions which handles everything.

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

### 2. Pre-flight: Unit tests

```bash
./support/scripts/unit-test
```

**STOP if tests fail.** Investigate and report failures — do not skip.

### 3. Pre-flight: Instrumented tests (skip by default)

Skip instrumented tests unless the user explicitly asks to run them.

If yes:
```bash
./gradlew :android:app:smokeGroupDebugAndroidTest
```

If no device is available or user skips, note it and continue.

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

### 5. Create and push tag

Confirm with the user before pushing: "Ready to push tag `$TAG` to trigger deployment?"

```bash
git tag "$TAG"
git push origin main && git push origin "$TAG"
```

### 6. Monitor deployment

```bash
gh run watch
```

Or show the user how to monitor:
```
gh run list --workflow=continuous_deployment.yml
gh run view --web
```

The pipeline will:
1. Build release bundle
2. Upload to Play Store Internal track

## Error Recovery

- **Test failures:** Investigate and report — never skip tests
- **Tag already exists:** Increment the revision number (NN) and retry
- **Push rejected:** Check if main is behind remote (`git pull origin main`)
