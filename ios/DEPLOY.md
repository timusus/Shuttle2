# Deploying Shuttle Music for iOS

Tag-driven TestFlight pipeline, ported from Shuttle Podcasts. App Store Connect app id 6818057709,
bundle id `com.simplecityapps.shuttle`, team `9HYNX943MQ`, SKU `shuttle-music-ios`.

## How a release flows

Push a tag `ios/vYYMMDDNN` (NN = that day's sequence, e.g. `ios/v26100301`) on a commit that is on
`main`. `.github/workflows/ios-deploy.yml` runs on the self-hosted `mac-builder` runner:

1. derives build number `YYMMDDNN` (CFBundleVersion) and marketing version `20YY.MM.DD`;
2. writes the App Store Connect API key to `$RUNNER_TEMP` (removed at the end);
3. `ios/scripts/build-framework.sh --device --release` installs the FFmpeg frameworks
   (`ios/Playback/Frameworks`) and links the Release iosArm64 `Shared.framework`;
4. `xcodegen generate`, then `ios/archive-and-upload.sh` archives scheme `S2` (Release, bundle id
   `com.simplecityapps.shuttle`), checks the archive carries the tag's build number, makes sure the App
   Store profile exists (`ios/scripts/ensure-store-profiles.sh`) and exports with `ExportOptions.plist`,
   which uploads to App Store Connect.

Debug builds keep `com.simplecityapps.shuttle.dev`. `project.yml` takes the versions from the
`S2_MARKETING_VERSION` / `S2_BUILD_NUMBER` build settings; the script overrides them on the
xcodebuild command line.

`workflow_dispatch` is a smoke run: build number = today + `99`, IPA exported to `ios/build/export`
and not uploaded.

## One-time owner setup

1. App Store Connect > Users and Access > Integrations > App Store Connect API > Team Keys: generate a
   key with role App Manager (or reuse the Podcasts one) and keep the `.p8` and its Key ID and Issuer ID.
2. Store the secrets: `ios/scripts/setup-asc-secrets.sh [--smoke] path/to/AuthKey_XXXXXXXXXX.p8`
   sets `ASC_KEY_ID`, `ASC_ISSUER_ID` (prompted) and `ASC_API_KEY_P8` (base64 of the file) on this repo;
   `--smoke` then fires the no-upload dispatch run.
3. The `mac-builder` runner must be registered on this repo and online, with Xcode, xcodegen, a JDK and
   the **Apple Distribution: Simplecity Apps Pty Ltd (9HYNX943MQ)** certificate in the login keychain.
4. Export signs manually with the profile "Shuttle Music App Store" (the team's API keys cannot use a
   cloud-managed distribution certificate). `ensure-store-profiles.sh` creates and installs it
   when the key flags are given, so no manual profile step is needed.

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
| `--skip-shared-framework` | Skip FFmpeg and `Shared.framework` (the workflow builds them in its own step). |
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
