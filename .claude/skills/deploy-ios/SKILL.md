---
name: deploy-ios
description: Deploy Shuttle Music for iOS to TestFlight via GitHub Actions. Runs pre-flight checks, creates an ios/vYYMMDDNN tag and pushes it; also covers the local archive-and-upload path.
user_invocable: true
---

# Deploy iOS to TestFlight

Pushing an `ios/vYYMMDDNN` tag triggers `.github/workflows/ios-deploy.yml` (self-hosted `mac-builder`:
archive, export, upload to TestFlight). Details and one-time setup: `ios/DEPLOY.md`.

## Steps

### 1. Pre-flight: Git status

Same as deploy-android: on `main`, clean tree, `HEAD == origin/main` after `git fetch origin main`.
**STOP** and tell the user what to fix if not.

### 2. Pre-flight: iOS builds

The Mac must be free; take the build through a longjob, never a long foreground call.

```bash
support/scripts/longjob.sh start ios-preflight -- sh -c 'ios/scripts/build-framework.sh --release --device -q && cd ios && xcodegen generate && xcodebuild build -project S2.xcodeproj -scheme S2 -destination "generic/platform=iOS Simulator" -derivedDataPath build/DerivedData -quiet'
support/scripts/longjob.sh wait ios-preflight
```

If iOS changed since the last full verify, also run `ios/scripts/test.sh`. **STOP** on failure.

### 3. Pre-flight: secrets and runner

```bash
gh secret list | grep -c 'ASC_KEY_ID\|ASC_ISSUER_ID\|ASC_API_KEY_P8'   # must be 3
gh api repos/{owner}/{repo}/actions/runners --jq '.runners[] | select(.labels[].name=="mac-builder") | .status'
```

Missing secrets or an offline runner: STOP and point the user at `ios/DEPLOY.md`.

### 4. Pick the tag

Format `ios/vYYMMDDNN`, NN the day's sequence starting at 01.

```bash
git fetch --tags
TODAY=$(date +%y%m%d)
LAST=$(git tag -l "ios/v${TODAY}*" | sort | tail -1)
# none -> ios/v${TODAY}01, else increment NN (zero-padded)
```

Confirm the tag with the user, then:

```bash
git tag ios/v26100301
git push origin ios/v26100301
```

(`git push` of a tag is its own bare command.) Marketing version becomes `2026.10.03`, build
number `26100301`.

### 5. Watch the run

```bash
RUN=$(gh run list --workflow ios-deploy.yml --limit 1 --json databaseId -q '.[0].databaseId')
support/scripts/longjob.sh start ios-deploy -- gh run watch "$RUN" --exit-status
support/scripts/longjob.sh wait ios-deploy
```

On failure: `gh run view "$RUN" --log-failed | tail -60`, report the cause. A re-run needs a new tag
(or `gh run rerun`) only if nothing was uploaded; a spent build number needs `NN+1`.

### 6. Find the build

App Store Connect > Shuttle Music (id 6818057709) > TestFlight. Processing takes 5-30 minutes.
Report the tag, build number and run URL.

## Local path (no CI)

```bash
# Dry run, IPA lands in ios/build/export
ios/archive-and-upload.sh --no-upload --build-number 26100301 \
  --api-key-path /path/AuthKey_KEYID.p8 --api-key-id KEYID --api-issuer-id ISSUER
# Real upload: drop --no-upload
```

Run it through `longjob.sh` (archive is slow). Never print or commit the `.p8`.
