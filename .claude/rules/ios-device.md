---
paths:
  - "ios/scripts/install-device.sh"
  - "ios/scripts/run-sim-server.sh"
  - "ios/scripts/maestro-sim.sh"
  - "ios/maestro/**"
---

# iOS: running on a device, the simulator POC and Maestro

## Device

```bash
ios/scripts/install-device.sh [DEVICE_ID] [TEAM_ID]   # default team 9HYNX943MQ (Podcasts')
```

Builds the device (`iosArm64`) `Shared.framework`, then builds, installs and launches `S2.app` (`xcrun devicectl list
devices` for IDs; defaults to the owner's iPhone). `CONFIGURATION=Release` and `LAUNCH=0` are env overrides. The team is
only ever passed on the `xcodebuild` command line: project.yml's `DEVELOPMENT_TEAM: "$(APP_TEAM)"` stays empty so
simulator builds need no team; a GUI Xcode device build needs a team picked by hand in Signing & Capabilities.

The device needs its developer profile trusted once (Settings > General > VPN & Device Management) and, on iOS 16+,
Developer Mode on. Automatic signing (`-allowProvisioningUpdates`) needs an Xcode account in the team. The build log's
"Apple Development: <name> (<id>)" is the certificate's id, not the team; check the team with
`security find-certificate -c "Apple Development" -p | openssl x509 -noout -subject` (OU field; this Mac's is
9HYNX943MQ). Switching teams changes the signing identity and iOS refuses to upgrade across teams
(`MismatchedApplicationIdentifierEntitlement`): delete the app from the device first.

## Simulator POC

The app signs in only through its own screen: Library's empty state (or the Settings gear) > Sources > Connect a Server >
Jellyfin, Emby or Plex (`ServerSignInView`). The address needs its scheme; plain `http://` LAN servers work (ATS allows
them). The session is saved in the Keychain.

```bash
# Build, install and launch on the simulator. BUILD=0 skips the build; RESET=1 wipes the simulator keychain first;
# S2_SIMULATOR_UDID picks the simulator (default this session's leased device)
ios/scripts/run-sim-server.sh
S2_SIMULATOR_UDID=<simulator> ios/scripts/maestro-sim.sh   # poc-play.yaml, or name another flow
```

The end-to-end check signs in, then Library > Songs, taps the first song, and the mini player shows it playing
(screenshots under the output dir's `<timestamp>/`).

`maestro-sim.sh` reads `~/.config/s2-test/jellyfin.env` (`URL=`, `API_KEY=`) and passes both with `-e`; never printed or
committed. A fresh install opens the first-run setup: `onboarding.yaml` walks it, and every other flow that clears state
taps `onboarding.skip` first. The env file has no password, so `sign-in-jellyfin.yaml` types the address and username
(`shuttle-test`), taps Sign In with Quick Connect and approves its own code with the API key
(`approve-quick-connect.js`, `POST /QuickConnect/Authorize`). Emby has no Quick Connect, so no flow signs in to it.

Flows find views by `accessibilityIdentifier` (`onboarding.*`, `serverTypePicker.*`, `serverSignIn.*`, `songRow.title`,
`miniPlayer.title`, `miniPlayer.playPause`); an id on a container overrides its children's, so give each control its own.
