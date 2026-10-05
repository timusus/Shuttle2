---
name: deploy-ios
description: Deploy Shuttle Music for iOS to TestFlight from this Mac. Runs pre-flight checks, archives and uploads via ios/archive-and-upload.sh, then records the release with an ios/vYYMMDDNN tag.
user_invocable: true
---

# Deploy iOS to TestFlight (local)

The deploy runs entirely on this Mac via `ios/archive-and-upload.sh` — no CI. The repo is public, so a
self-hosted runner is not acceptable (a forked PR could run code on the signing Mac); the
`ios/vYYMMDDNN` tag is only the release record, pushed **after** a successful upload. Details and
one-time setup: `ios/DEPLOY.md`.

## Steps

### 1. Pre-flight: Git status

Same as deploy-android: on `main`, clean tree, `HEAD == origin/main` after `git fetch origin main`.
**STOP** and tell the user what to fix if not.

### 2. Pre-flight: iOS builds

The Mac must be free; take the build through a longjob, never a long foreground call.

```bash
support/scripts/longjob.sh start ios-preflight -- sh -c 'ios/scripts/build-framework.sh -q && cd ios && xcodegen generate && cd .. && ios/scripts/build-app.sh --force'
support/scripts/longjob.sh wait ios-preflight
```

`--force` makes this a real compile and relink at the release commit, never a skipped build. It also builds the test target, so a test-target compile error blocks the preflight; that is intended.

If iOS changed since the last full verify, also run `ios/scripts/test.sh`. **STOP** on failure.

### 3. Pre-flight: Xcode account

The deploy signs with Xcode's signed-in account (developer@simplecityapps.com, team 9HYNX943MQ), not
an API key: no key flags, so the export signs automatically and always has current profiles. If the
export fails on signing, ask the user to check Xcode > Settings > Accounts (`ios/DEPLOY.md`).

### 4. Pick the tag

Format `ios/vYYMMDDNN`: NN is the highest existing `ios/v${TODAY}NN` — local tags and `origin`'s
(`git ls-remote`) — plus one, else 01. Build number is the tag's eight digits; the script derives the
marketing version (`20YY.MM.DD`) from it. Compute `TAG` and `BUILD_NUMBER` **here, once**, and reuse
both in steps 5 and 6 — never recompute `TODAY` later (a deploy that crosses midnight must not mint
a different tag than the build it uploaded).

```bash
git fetch --tags origin
TODAY=$(date +%y%m%d)
LAST_NN=$( { git tag -l "ios/v${TODAY}??"; git ls-remote --tags origin "refs/tags/ios/v${TODAY}??"; } \
  | sed -E "s/^.*ios\/v${TODAY}//; s/\^\{\}$//" | sort -n | tail -1)
NN=$(printf '%02d' $((10#${LAST_NN:-0} + 1)))
TAG="ios/v${TODAY}${NN}"
BUILD_NUMBER="${TODAY}${NN}"
```

### 5. Confirm with the user, then archive and upload

**Ask before every upload** — it is outward-facing. State the tag, build number and marketing version,
and offer a dry run (`--no-upload`, IPA lands in `ios/build/export`) before the real thing.

```bash
support/scripts/longjob.sh start ios-deploy -- ios/archive-and-upload.sh \
  --build-number "$BUILD_NUMBER"
# dry run: add --no-upload
support/scripts/longjob.sh wait ios-deploy
```

The script builds FFmpeg and the Release iosArm64 `Shared.framework` itself (add
`--skip-shared-framework` only if they were just built). On failure: `ios/build/archive.log` /
`ios/build/export.log`; a spent build number needs `NN+1`, so move to a fresh tag.

### 6. Record the release

Two conditions, both required: `longjob.sh wait ios-deploy` exited 0 **and** the run was a real
upload — not a `--no-upload` dry run. The tag marks an uploaded build, so a failed attempt or dry
run records nothing and the next attempt picks `NN+1`:

```bash
git tag "$TAG"
git push origin "$TAG"
```

(`git push` of a tag is its own bare command.) The tag triggers nothing — no workflow watches it.

### 7. Find the build

App Store Connect > Shuttle Music (id 6818057709) > TestFlight. Processing takes 5-30 minutes.
Report the tag, build number and IPA path.

### App Store Connect via API

Use `support/scripts/asc`, not Chrome (credentials from `~/.secrets/asc.env`): `apps`, `builds [--limit N]`,
`testflight groups|testers <group>|add-tester <group> <email> [first] [last]|add-build <group> <build>|submit-review <build>`,
`review-info [set --notes ...|--contact-email ...|--demo-user ... --demo-password-env VAR]`,
`whats-new <build> "<text>"`. Writes need `--yes` (`--dry-run` shows the request); ask the owner first, they are
outward-facing. Chrome is only for agreements, tax and banking, and the App Privacy questionnaire. Details: `ios/DEPLOY.md`.
