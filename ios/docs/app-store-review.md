# App Store review readiness: Shuttle Music for iOS

Review notes, launch checklist and open items for the first submission (ASC app 6818057709, issue #610).
The listing text, categories, age rating and App Privacy answers are in `ios/store/metadata/`
(`asc-answers.md` is the portal mirror); screenshots come from `ios/support/store-screenshots/`.

## Launch checklist (owner, in order)

1. Land the StoreKit/paywall work (#609): the review notes below describe a 14-day trial and a one-off
   Pro purchase, so the IAP product must exist and be attached to the version.
2. A reachable demo server: stand up a public HTTPS Jellyfin with a small set of freely licensed music
   (see Screenshots in `ios/support/store-screenshots/README.md` for why not the test server's library),
   and a dedicated reviewer user. Fill in the placeholders in the notes below. Keep it up until approval.
3. Host the FFmpeg n7.1.5 source tarball (e.g. a GitHub release asset) and link it from About (#610).
4. App Store Connect > App Information: paste `en-AU/*.txt`, set the privacy policy URL, category
   Music, content rights and age rating from `asc-answers.md`.
5. App Privacy: Data Not Collected, publish.
6. Version > Previews and Screenshots: upload `ios/store/screenshots/en-AU/{iphone-6.9,iphone-6.5,ipad-13}`
   once rendered from artwork we may show (#610 guideline 5.2 note).
7. Version > App Review Information: paste the notes below with the placeholders filled; sign-in required
   Yes is not needed (the demo credentials go in the notes); contact phone and email filled.
8. Confirm the Info.plist background mode is `audio` only and the FFmpeg frameworks are not renamed.
9. Push the archive tag, wait for TestFlight to process, install on a device, play from the demo server
   and run through the purchase with a sandbox account, then submit.

## Review notes (paste into "Notes" under App Review Information)

```
Shuttle Music is a music player for the user's own Jellyfin, Emby or Plex media server. It has no
catalogue of its own, so it needs a server to show anything. We have set up a demo Jellyfin server:

  Server address: <DEMO SERVER URL, e.g. https://demo.example.com>
  Username:       <DEMO USERNAME>
  Password:       <DEMO PASSWORD>

To test: open the app, tap Skip (or Add a Source), choose Add a Server > Jellyfin, enter the address,
username and password, and tap Sign In. The library imports, then Home, Library (Albums, Artists, Songs,
Genres, Playlists), Search, Now Playing, the queue and Settings > Equalizer all work against it.

In-app purchase: Shuttle Music is free to download. Streaming from a server is part of Shuttle Music
Pro: a 14-day free trial begins the first time a song from a server is played, after which Pro is a
one-off lifetime purchase (no subscription). A sandbox Apple Account is enough to test the purchase,
and Restore Purchases is on the paywall.

Background audio: UIBackgroundModes is limited to audio, used so music keeps playing with the screen
locked or the app in the background, with lock screen and Control Centre controls.

Local network: the app asks for local network access only so it can connect to media servers on the
user's Wi-Fi. Many Jellyfin and Emby servers are plain http on the LAN or use self-signed
certificates, which is why App Transport Security allows arbitrary loads; the app only contacts the
server the user entered.

Privacy: the app collects no data. There is no analytics or crash reporting, no account with us, and
server credentials stay in the device Keychain and go only to the user's server.

Open source: playback uses FFmpeg (LGPL-2.1+ build, dynamically linked, unmodified frameworks) to
decode formats such as FLAC. The FFmpeg licence notice is in Settings > About, and the matching
source is available at <FFMPEG SOURCE URL>.

Contact: <owner email / phone as in ASC>.
```

(About 2,500 characters; the limit is 4,000.)

## Checks and open items

| Item | State |
|---|---|
| Public demo server and reviewer credentials (#610) | open: owner to provide; never commit credentials |
| Local-file playback with no server (#590) | not built; the listing says so ("NEEDS YOUR OWN SERVER") |
| FFmpeg source tarball hosted (#610) | open |
| FFmpeg LGPL notice in About (#610) | open: verify Settings > About names FFmpeg and links the source |
| No reverse-engineering ban in the EULA (#610) | open: keep Apple's standard EULA |
| Never rename the FFmpeg frameworks (#610) | do not touch `ios/scripts/build-ffmpeg.sh` naming |
| Background modes limited to audio | `ios/project.yml` `UIBackgroundModes: [audio]` |
| Privacy manifest matches "Data Not Collected" | verified 2026-10-03, see `asc-answers.md` |
| Paywall, trial and Restore Purchases (#609) | other worker; re-check the notes above once it lands |
| Downloads on iOS | no download UI yet, so the listing does not mention downloads |
