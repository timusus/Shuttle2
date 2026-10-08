# Deploying Shuttle Music for iOS

Local TestFlight pipeline, run from this Mac via the `deploy-ios` skill. App Store Connect app id
6818057709, bundle id `com.simplecityapps.shuttle`, team `9HYNX943MQ`, SKU `shuttle-music-ios`.

There is deliberately no CI lane: the repo is public, and a self-hosted runner on a public repo lets a
forked PR run code on the Mac that holds the signing and SSH keys. `ios/archive-and-upload.sh` runs
locally instead, and the `ios/vYYMMDDNN` tag is pushed only afterwards, as the release record.

## How a release flows

The skill runs `ios/archive-and-upload.sh` with a build number `YYMMDDNN` (CFBundleVersion; marketing
version `20YY.MM.DD` is derived from it):

1. `ios/scripts/build-framework.sh --device --release` links the Release iosArm64 `Shared.framework`
   (FFmpeg is a static library inside the shuttle-playback package, resolved with the others);
2. `xcodegen generate`, then the script archives scheme `S2` (Release, bundle id
   `com.simplecityapps.shuttle`), checks the archive carries the build number and exports, which
   uploads to App Store Connect. Without API-key flags the export signs automatically with Xcode's
   signed-in account (`-allowProvisioningUpdates`; the script drops `ExportOptions.plist`'s manual
   signing entries from a copy in `ios/build`), so the profiles always match the app's entitlements.
   With the key flags it signs manually with the profiles `ios/scripts/ensure-store-profiles.sh`
   creates; that script only recreates a profile that is invalid, missing or bound to another
   certificate, so a profile that predates an entitlement change can still fail the export.

After a successful upload the skill pushes the `ios/vYYMMDDNN` tag on the deployed commit. Nothing
triggers on it.

Debug builds keep `com.simplecityapps.shuttle.dev`. `project.yml` takes the versions from the
`S2_MARKETING_VERSION` / `S2_BUILD_NUMBER` build settings; the script overrides them on the
xcodebuild command line.

## One-time owner setup

1. App Store Connect > Users and Access > Integrations > App Store Connect API > Team Keys: generate a
   key with role App Manager (or reuse the Podcasts one) and keep the `.p8` and its Key ID and Issuer ID.
   Put them in `~/.secrets/asc.env` (mode 600; `ASC_KEY_ID`, `ASC_ISSUER_ID`, `ASC_KEY_PATH`); the
   `support/scripts/asc` reads it (key default `/Users/tim/.secrets/AuthKey_98Q5SW65X5.p8`); the
   deploy itself does not need it.
2. Xcode signed in (Settings > Accounts) with developer@simplecityapps.com, team 9HYNX943MQ: the
   deploy signs with it. The team's API keys cannot use a cloud-managed distribution certificate,
   which is why the key flags (optional) switch export to manual signing and need an **Apple
   Distribution: Simplecity Apps Pty Ltd** certificate in the login keychain.

## App Store Connect via API

`support/scripts/asc` (python3 stdlib + openssl, credentials from `~/.secrets/asc.env`) replaces Chrome for
routine App Store Connect work. `--json` gives raw output; writes print their change and need `--yes`
(`--dry-run` shows the request). Default app is Shuttle Music; `--app <bundle id>` picks another.

```bash
support/scripts/asc apps
support/scripts/asc builds [--limit N]                     # version, build, processing + review state
support/scripts/asc testflight groups
support/scripts/asc testflight testers <group>
support/scripts/asc testflight add-tester <group> <email> [first] [last]
support/scripts/asc testflight add-build <group> <build-number>
support/scripts/asc testflight submit-review <build-number>   # beta app review
support/scripts/asc review-info                            # contact, demo account, notes
support/scripts/asc review-info set --notes "..." | --contact-email E | --demo-user U --demo-password-env VAR
support/scripts/asc whats-new <build-number> "text"        # What to Test
```

Chrome is only for what the API can't do: agreements, tax and banking, the App Privacy questionnaire.

## Local deploy

```bash
# Dry run: archive + export an IPA to ios/build/export, no upload
ios/archive-and-upload.sh --no-upload --build-number 26100301

# Real upload: same without --no-upload
```

| Flag | Meaning |
|---|---|
| `--build-number N` | CFBundleVersion, eight digits `YYMMDDNN`. |
| `--marketing-version V` | CFBundleShortVersionString; derived from the build number (`20YY.MM.DD`) when omitted. |
| `--no-upload` | Export the IPA to `ios/build/export` instead of uploading. |
| `--skip-shared-framework` | Skip `Shared.framework` (built separately first). |
| `--api-key-path P --api-key-id K --api-issuer-id I` | Optional, all three or none: headless upload with manual signing (see above). Without them Xcode's signed-in account signs automatically. |

Logs: `ios/build/archive.log`, `ios/build/export.log`.

## After the upload

The build appears in App Store Connect > Shuttle Music > TestFlight after processing (usually 5-30
minutes; an email follows). Add it to a tester group or the internal testers; the first build also needs
the export-compliance answer (the Info.plist already sets `ITSAppUsesNonExemptEncryption` to false).

## Known caveats

- FFmpeg is linked statically into S2 (shuttle-playback's `FFmpeg.xcframework`, #957); nothing of it is
  embedded in `S2.app/Frameworks`.
- The export warns that Sentry's dSYM is missing. Expected, not a build-setting gap: S2 itself already
  gets a dSYM (Release default, holding Shared.framework's and FFmpeg's code), but Sentry's SPM binary
  framework ships none. Only S2's dSYM is uploaded to Sentry by `scripts/upload-dsyms.sh`.
- Build numbers must increase per upload; never reuse a tag.
