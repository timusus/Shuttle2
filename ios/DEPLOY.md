# Deploying Shuttle Music for iOS

Local TestFlight pipeline, run from this Mac via the `deploy-ios` skill. App Store Connect app id
6818057709, bundle id `com.simplecityapps.shuttle`, team `9HYNX943MQ`, SKU `shuttle-music-ios`.

There is deliberately no CI lane: the repo is public, and a self-hosted runner on a public repo lets a
forked PR run code on the Mac that holds the signing and SSH keys. `ios/archive-and-upload.sh` runs
locally instead, and the `ios/vYYMMDDNN` tag is pushed only afterwards, as the release record.

## How a release flows

The skill runs `ios/archive-and-upload.sh` with a build number `YYMMDDNN` (CFBundleVersion; marketing
version `20YY.MM.DD` is derived from it):

1. `ios/scripts/build-framework.sh --device --release` installs the FFmpeg frameworks
   (`ios/Playback/Frameworks`) and links the Release iosArm64 `Shared.framework`;
2. `xcodegen generate`, then the script archives scheme `S2` (Release, bundle id
   `com.simplecityapps.shuttle`), checks the archive carries the build number, makes sure the App
   Store profile exists (`ios/scripts/ensure-store-profiles.sh`) and exports with `ExportOptions.plist`,
   which uploads to App Store Connect.

After a successful upload the skill pushes the `ios/vYYMMDDNN` tag on the deployed commit. Nothing
triggers on it.

Debug builds keep `com.simplecityapps.shuttle.dev`. `project.yml` takes the versions from the
`S2_MARKETING_VERSION` / `S2_BUILD_NUMBER` build settings; the script overrides them on the
xcodebuild command line.

## One-time owner setup

1. App Store Connect > Users and Access > Integrations > App Store Connect API > Team Keys: generate a
   key with role App Manager (or reuse the Podcasts one) and keep the `.p8` and its Key ID and Issuer ID.
   The skill reads the key from `ASC_API_KEY_PATH` (default: the Podcasts key
   `/Users/tim/projects/simplecity-apps/podcasts/AuthKey_98Q5SW65X5.p8`).
2. An **Apple Distribution: Simplecity Apps Pty Ltd (9HYNX943MQ)** certificate must be in the login
   keychain of this Mac. Export signs manually with the profile "Shuttle Music App Store" (the team's
   API keys cannot use a cloud-managed distribution certificate); `ensure-store-profiles.sh` creates and
   installs it when the key flags are given, so no manual profile step is needed.

## Local deploy

```bash
# Dry run: archive + export an IPA to ios/build/export, no upload
ios/archive-and-upload.sh --no-upload --build-number 26100301 \
  --api-key-path ~/keys/AuthKey_XXXXXXXXXX.p8 --api-key-id XXXXXXXXXX --api-issuer-id <issuer-uuid>

# Real upload: same without --no-upload
```

| Flag | Meaning |
|---|---|
| `--build-number N` | CFBundleVersion, eight digits `YYMMDDNN`. |
| `--marketing-version V` | CFBundleShortVersionString; derived from the build number (`20YY.MM.DD`) when omitted. |
| `--no-upload` | Export the IPA to `ios/build/export` instead of uploading. |
| `--skip-shared-framework` | Skip FFmpeg and `Shared.framework` (built separately first). |
| `--api-key-path P --api-key-id K --api-issuer-id I` | Headless signing and upload; all three or none. Without them Xcode's signed-in Apple-ID session is used, but export still needs the Store profile, which only the key flags can create. |

Logs: `ios/build/archive.log`, `ios/build/export.log`.

## After the upload

The build appears in App Store Connect > Shuttle Music > TestFlight after processing (usually 5-30
minutes; an email follows). Add it to a tester group or the internal testers; the first build also needs
the export-compliance answer (the Info.plist already sets `ITSAppUsesNonExemptEncryption` to false).

## Known caveats

- FFmpeg's four frameworks are dynamic and re-signed by Xcode when embedding; App Store processing
  accepting them is still to be confirmed with the first upload (`docs/architecture/ios-port/phase-6-playback.md`).
- Build numbers must increase per upload; never reuse a tag.
