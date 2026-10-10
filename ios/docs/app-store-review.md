# App Store review readiness: Shuttle Music for iOS

Review notes, launch checklist and open items for the first submission (ASC app 6818057709, issue #610).
The listing text, categories, age rating and App Privacy answers are in `ios/store/metadata/`
(`asc-answers.md` is the portal mirror); screenshots come from `ios/support/store-screenshots/`.

## Launch checklist (owner, in order)

1. Land the StoreKit/paywall work (#609): the review notes below describe a 14-day trial and a one-off
   Pro purchase, so the IAP product must exist and be attached to the version.
2. The demo server is Emby at https://emby.mediaserver.timmalseed.dev, user `appreview`. The owner enters the
   password in the demo account fields in App Store Connect (it is never written in the repo). Keep the server up
   until approval.
3. Check https://github.com/timusus/AudioPlaybackKit/tree/0.7.1/scripts is still up (the About link points at it, the
   FFmpeg build script and patch, #610, #957).
4. App Store Connect > App Information: paste `en-AU/*.txt`, set the privacy policy URL, category
   Music, content rights and age rating from `asc-answers.md`.
5. App Privacy: the seven data types in `asc-answers.md` (#776; not "Data Not Collected"), publish.
6. Version > Previews and Screenshots: upload `ios/store/screenshots/en-AU/{iphone-6.9,iphone-6.5,ipad-13}`
   once rendered from artwork we may show (#610 guideline 5.2 note).
7. Version > App Review Information: paste the notes below with the placeholders filled;
   leave the demo-account toggle off (the credentials go in the notes); contact phone and email filled.
8. Confirm the Info.plist background modes are `audio` and `fetch` only.
9. Push the archive tag, wait for TestFlight to process, install on a device, play from the demo server
   and run through the purchase with a sandbox account, then submit.

## Review notes (paste into "Notes" under App Review Information)

```
Shuttle Music is a music player for the user's own Jellyfin, Emby or Plex media server. It has no
catalogue of its own: it plays audio files on the device, or from a media server. We have set up a
demo Emby server:

  Server address: https://emby.mediaserver.timmalseed.dev
  Username:       appreview
  Password:       (entered in the demo account fields in App Store Connect)

To test: open the app, tap Skip (or Add a Source), choose Add a Server > Emby, enter the address,
username and password, and tap Sign In. The library imports, then Home, Library (Albums, Artists, Songs,
Genres, Playlists), Search, Now Playing, the queue and Settings > Equalizer all work against it.
Plex signs in with a plex.tv account (email or username, password, optional two-factor code), not a
server address.

Local playback needs no server and is free: add audio files (FLAC, MP3, M4A, AAC, Ogg, Opus, WAV, AIFF)
to Files > On My iPhone > Shuttle Music, or from a Mac through Finder, or pick a folder. The songs appear
in the Library and play without Pro or a sign-in.

In-app purchase: Shuttle Music is free to download. Streaming from Jellyfin, Emby or Plex is part of Shuttle Music
Pro: the first time a server song is played the paywall opens, and tapping Start 14-day free trial begins a
free App Store trial (nothing is charged), after which Pro is a
one-off lifetime purchase (no subscription). A sandbox Apple Account is enough to test the purchase,
and Restore Purchases is on the paywall and in Settings.

Background modes: audio, so music keeps playing with the screen locked or the app in the background,
with lock screen and Control Centre controls; and background fetch, used only for system-scheduled
BGAppRefreshTask runs that sync the user's media server library and send queued Last.fm scrobbles.

Local network: the app asks for local network access only so it can connect to media servers on the
user's Wi-Fi. Many Jellyfin, Emby and Plex servers are plain http on the LAN or use self-signed
certificates, which is why App Transport Security allows arbitrary loads; the app only contacts the
server the user entered.

Privacy: the app sends crash reports (Sentry) and anonymous usage analytics (PostHog), not linked to
the user's identity and not used for tracking; both can be turned off in Settings > Privacy. There is no
account with us, and server credentials stay in the device Keychain and go only to the user's server.

Open source: playback uses FFmpeg (LGPL-2.1+ build, statically linked) to decode formats such as
FLAC. The FFmpeg licence notice is in Settings > About. The source is FFmpeg's n7.1 tag plus one
patch, built by the script at https://github.com/timusus/AudioPlaybackKit/tree/0.7.1/scripts; the
app's own source is public too, so it can be relinked against a modified FFmpeg.

Contact: <owner email / phone as in ASC>.
```

(About 1,985 characters; the limit is 4,000.)

## Checks and open items

| Item | State |
|---|---|
| Public demo server and reviewer credentials (#610) | open: owner to provide; never commit credentials |
| Local-file playback with no server (#590) | built; the listing and review notes describe it |
| FFmpeg source available (#610) | done: n7.1 plus the patch and build script in AudioPlaybackKit (#957), pinned by tag |
| FFmpeg LGPL notice in About (#610) | done: Acknowledgements names FFmpeg and LGPL-2.1+; About has an FFmpeg source code link |
| No reverse-engineering ban in the EULA (#610) | open: keep Apple's standard EULA |
| Background modes: audio, plus fetch for library refresh and scrobbles (#771) | `ios/project.yml` `UIBackgroundModes: [audio, fetch]` |
| App Privacy answers match Sentry/PostHog (#776) | see `asc-answers.md` |
| Built with release (GM) Xcode, not beta | App Review rejects beta-Xcode builds |
| Paywall, trial and Restore Purchases (#609) | other worker; re-check the notes above once it lands |
| Downloads on iOS | no download UI yet, so the listing does not mention downloads |
