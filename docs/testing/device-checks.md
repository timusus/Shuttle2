# Device checks

Checks that need a real device, real audio, or a real Cast/Jellyfin setup, and that the emulator
checks (`support/scripts/checks/run-all.sh`) can't cover. Work through them on a debug or internal
build, tick them off, and file anything wrong with `/note`. Remove ticked items once a release ships.

## Playback refactor (#250), S0–S7 and the callbacks → flows work

### Position and restore
- [x] Pause mid-track, force-stop the app, reopen it. It resumes at the paused position. — automated: `emu-verify.sh --check restore-position`
- [x] Let a track finish into the next one, force-stop, reopen. It resumes on the new track near 0:00. — automated: `emu-verify.sh --check restore-track-finish`
- [x] Skip and pause right away, before the new track is audible, then force-stop and reopen. It resumes on the new track at 0:00. — automated: `emu-verify.sh --check restore-skip-pause`
- [x] Podcasts and audiobooks resume about 5 s before where they stopped. — automated: `emu-verify.sh --check spoken-word-rewind`

### Sleep timer
- [x] A sleep timer without "play to end" pauses when it runs out. — automated: `emu-verify.sh --check sleep-timer-expiry`
- [x] A sleep timer with "play to end" lets the current track finish, then pauses. — automated: `emu-verify.sh --check sleep-timer-expiry`

### Repeat, shuffle and speed
- [x] Repeat one, repeat all and off each behave correctly at the end of a track and the end of the queue. — automated: `emu-verify.sh --check repeat-modes`
- [x] Turn shuffle on mid-queue. The next track comes from the shuffled order. — automated: `emu-verify.sh --check queue-shuffle`
- [x] Set playback speed ≠ 1.0×, then change repeat mode. The speed doesn't reset. — automated: `emu-verify.sh --check speed-survives-repeat`

### Service and notification
- [x] Pause, then swipe S2 away in Recents. The service stops and the notification goes, with no crash. — automated: `emu-verify.sh --check service-stop`
- [x] The notification can be dismissed while paused, with no crash. — automated: `emu-verify.sh --check dismiss-paused-notification`
- [x] Resume playback from a headset/Bluetooth media button after the service has stopped. — automated: `emu-verify.sh --check media-buttons`
- [ ] Resume playback from the notification after the service has stopped.
- [ ] RS-53: unplug wired headphones while playing. Playback pauses. (The emulator check went with S2's own noisy receiver in #345 step 3; ExoPlayer handles it now.)
- [ ] RS-53: disconnect Bluetooth while playing. Playback pauses.

### Next-track preparation and slow loads (#300)
- [x] Gapless auto-advance still works after reordering the queue, turning shuffle on, and removing the next track. — automated: `emu-verify.sh --check gapless-after-edits`
- [ ] A slow Jellyfin transcode or Cast load (weak Wi-Fi, large FLAC) that takes more than 30 s still plays that track instead of skipping it.

### Next-track ownership (#315)
- [ ] Jellyfin: let an album play through; every transition is gapless, and ReplayGain is right on each track.
- [ ] On a slow server, skip and then immediately reorder the queue: the track that plays next is the new next song, not the old one.

### Widgets (after the WidgetManager migration)
- [x] Each widget layout shows the current song and its play/pause state, and the empty state renders. — automated: `NowPlayingWidgetRenderTest`
- [x] The play/pause, next, previous, shuffle and repeat buttons send the right playback action. — automated: `NowPlayingWidgetRenderTest`
- [x] Shuffle/repeat state shows on the layouts that display it. — automated: `NowPlayingWidgetRenderTest`
- [ ] On the launcher, the widget updates on skip, pause and resume, and shows the artwork.
- [ ] The widget controls work while the app is in the background and after a force-stop.

### Tag edits reaching a queued song (#270)
- [x] With the Shuttle (TagLib) local provider set up (needs a real SAF folder pick, which
      `seed-test-media.sh` deliberately skips by using the MediaStore provider), queue several
      songs and play one. Edit the playing song's title via the batch tag editor; now playing
      and the notification update without playback interrupting. — automated: `emu-verify.sh --check tag-edit-queued`
- [ ] After that edit, the queue sheet and mini player show the new title too.
- [x] Edit a queued-but-not-playing song's title the same way; the queue screen shows the new
      title, playback isn't affected. — automated: `emu-verify.sh --check tag-edit-queued`

### Audio (still outstanding from earlier stages)
- [x] ReplayGain track and album modes change the level by the tagged gain, within ±0.5 dB. — automated: `ReplayGainLevelTest`
- [ ] ReplayGain sounds right by ear (final listening pass).
- [x] Gapless joins with the EQ off: no inserted silence or discontinuity. — automated: `GaplessJoinTest`
- [ ] Gapless joins with the EQ on have no click (#365; its test is ignored until fixed), and a real FLAC album plays gapless by ear.
- [x] EQ bands boost/cut by the set amount (±1 dB) and still apply after a skip. — automated: `EqualizerResponseTest`
- [ ] The EQ survives a Cast round-trip.
- [x] 24-bit PCM goes through EQ and ReplayGain to the end cleanly. — automated: `AudioOutputSpecTest` (RS-17, 24-bit WAV)
- [ ] A real 24-bit FLAC plays cleanly (FLAC decoding is native, device-only).
- [x] Jellyfin streaming: play, skip, seek and resume after a force-stop. — automated: `emu-verify.sh --remote jellyfin --check remote-playback`

## Ported PRs (Sep 2026)

### USB DAC direct output (#198), Android 14+ with a USB DAC
- [ ] Turn on Settings → Playback → USB DAC direct output, plug in the DAC, play a 44.1 kHz and then a 48 kHz file. Both play at the right pitch and speed, with no glitch at the track change.
- [ ] With it on, the equaliser and ReplayGain have no audible effect; turn it off and they apply again.
- [ ] Toggle it while paused, then resume. Output switches without a restart of the track.
- [ ] Unplug the DAC mid-track. Playback continues (or pauses) on the phone speaker with no crash; replug and it goes direct again.

### Open audio files from other apps (#186)
- [x] An MP3 opened by a MediaStore content:// URI, and an MP3 and a FLAC opened by file path, each play in S2. — automated: `emu-verify.sh --check open-file-intent`
- [x] A library song opened by content:// URI and by file path plays as the library song alone, keeps playing after back, and restores after a force-stop; a file outside the library comes back as an empty queue (#425). — automated: `emu-verify.sh --check open-file-intent`
- [ ] A FLAC by content:// URI, and a real messaging-app attachment (a FileProvider URI with a read grant), each play in S2.
- [ ] After opening a file, force-stop and reopen S2. The queue restores without errors, and the opened file is skipped with a message if it can no longer be read.

### M3U playlist sync (#168), needs a real SAF folder
- [x] With a Shuttle/Taglib folder containing an .m3u (including a line S2 can't resolve), remove a song from that playlist in-app. The .m3u loses only that song's entry; the unresolved line is still there. — automated: `emu-verify.sh --check m3u-sync` (removal driven via debug broadcast, not the UI gesture — see #383)
- [ ] Add a folder holding an .m3u as an S2 Media Provider on a fresh install (#371, #127). The playlist appears after that first scan, with no rescan. Then edit the .m3u on disk (swap a song) and rescan (#116): the playlist holds exactly the file's songs in its order, and the file itself is unchanged by the rescan.

### Playback reporting (#191)
- [x] Jellyfin and Emby each show a song S2 plays as now playing with a moving position, then paused on pause. — automated: `emu-verify.sh --remote jellyfin --check remote-reporting` (and `--remote emby`)
- [ ] Play a Jellyfin song, then an Emby song, in one session. The Jellyfin dashboard clears it when S2 moves to Emby, and each dashboard clears it on stop.
- [x] Play a Plex song to the end. It shows in Plex's now playing and is marked played afterwards. — automated: `emu-verify.sh --remote plex --check remote-reporting`

## Cast through Media3's Cast player (#345)

The Cast player now plays the queue on the receiver itself: a window of up to 100 items around the current one, in
play order. Coming back to the phone always lands paused, at the receiver's position.

- [ ] Cast a local file while playing. It carries on on the receiver from the same position, with title, artist, album and artwork showing.
- [ ] While paused, switch to Cast. It stays paused on the receiver at the same position.
- [ ] Cast a Jellyfin song (then an Emby and a Plex one). It plays on the receiver, seeks, and moves on to the next song.
- [ ] Cast a Jellyfin song the receiver can't play as it is (an ALAC or high-bitrate FLAC file), then the same with transcoding forced (a low streaming bitrate), then an Emby one. Each plays, from the phone's position, seeks, and moves on to the next song (RS-38). With a long Jellyfin queue, casting starts within a few seconds and the next songs keep arriving.
- [ ] Turn shuffle on while casting, then skip a few times. The receiver plays the songs S2's queue shows as next, in that order; turning shuffle off goes back to queue order.
- [ ] Cast a queue of 1,000+ songs from the middle. It starts playing within a few seconds; skip forward past 90 songs and back; the receiver keeps up, and skipping back 10 still works.
- [ ] Add, remove and move songs in the queue while casting. The receiver's next songs follow; the song playing carries on (a brief restart of it is known).
- [ ] Mid-track, switch local → Cast, then Cast → local. Each lands on the same song within a second or two of the position; on the phone it's paused.
- [ ] Skip to the next track and switch to Cast at once. The new track starts at 0:00 on the receiver.
- [ ] Toggle local → Cast → local quickly. It ends up paused on the phone at the right position, with no double audio.
- [ ] Let a song end on the receiver. The next one plays, and S2's now playing, notification and lock screen follow it.
- [ ] While casting, the notification and lock screen controls (play, pause, skip, seek) control the receiver, and there's only one S2 media notification.
- [ ] Disconnect by turning the receiver off or leaving Wi-Fi mid-track. The phone comes back paused at about the last position, and force-stopping and reopening S2 resumes there.
- [ ] With a system EQ app attached, switch to Cast. The EQ app detaches; switch back and it reattaches (RS-21 below).
- [ ] Duck (e.g. a navigation prompt) while local, then switch to Cast and back. Volume is normal and nothing is stuck ducked.
- [ ] Force-stop S2 while casting, reopen it, and reconnect. Playback on the receiver picks up S2's queue (it reloads the current song).
- [ ] While casting a Jellyfin song, from a laptop on the same Wi-Fi, `curl -i http://<phone-ip>:5000/songs/<id>/audio` and the same with a made-up first path segment. Both answer 403 with no `Location` header (RS-37). The receiver's own URL (from `adb logcat` or the receiver's debug console) carries a 32-character key and plays; after disconnecting and casting again, the old URL answers 403.
- [ ] Cast the last two songs of a queue with repeat off and let them play out. The receiver stops after the last, S2 shows it paused on that song, and the play count of both goes up (RS-39). Stop the receiver from the Google Home app mid-song instead: S2 doesn't count it as played.
- [ ] With repeat-all on, cast a queue of 150+ songs from about 5 from the end and let it play past the last song (or skip there). The receiver goes on to the first song of the queue without stopping, and S2 shows it; do the same with a 3-song queue (RS-40).

## Media session through Media3 (#345)

The media session, notification and Android Auto browsing now go through Media3's session library. The
notification is Media3's own, with shuffle and repeat as its extra buttons.

- [ ] Android Auto (DHU): browse Artists, Albums, Playlists and a song list; play a song. Its album plays from that song, and the queue view lists the queue and skips to a chosen item (RS-42, RS-43).
- [ ] Android Auto (DHU): Shuffle All plays, and search ("play <song>") finds and plays the song.
- [ ] Lock screen: title, artist and artwork show; play, pause, skip and seek work; the shuffle and repeat buttons change the modes and their icons follow (RS-45).
- [ ] Notification: the same controls work, the artwork shows, and there's only one S2 media notification. Paused, it can be swiped away.
- [x] With Settings, "Media session artwork" turned off, the lock screen shows no artwork. — automated: `emu-verify.sh --check notification-art` (asserts the media notification has no large icon; the lock screen reads the same session metadata, so eyeball it once on a device)
- [ ] Bluetooth headset: play/pause, next and previous (single, double and triple press) act on S2.
- [x] Widget: play/pause, next and previous work with the app open, and with the app force-stopped (the service starts in the foreground without a crash). — automated: `emu-verify.sh --check widget-controls`
- [ ] Resumption after reboot: after a reboot, the system's media resumption controls (quick settings) show the last song; play resumes the saved queue at that song (RS-44). The same with a headset's play button.
- [ ] Assistant, spoken (#424): "play <artist> on S2", "play the album <album> on S2", "play <song> by <artist> on S2", "play the playlist <playlist> on S2", "play some <genre> on S2" and "play music on S2" each play the right thing (RS-60, RS-61), with S2 force-stopped (RS-62) and with it open. Try the name the launcher shows too ("on Shuttle"), and note which names Assistant picks S2 for; the intent itself is automated by `checks/voice-search.sh`.
- [x] Cold start from the widget, API 31+: play something, then force-stop S2 (Settings, Apps, S2, Force stop) so the app is dead. Press the widget's play button. A notification appears (a "Loading" one first on a large library, then the playing song's), the saved queue plays, and nothing crashes, even if the queue takes several seconds to restore (RS-48). — automated: `emu-verify.sh --check cold-start-widget`
- [x] Cold start from a headset button, API 31+: the same, with the app force-stopped, pressing a Bluetooth or wired headset's play button. A notification appears, the saved queue plays, and nothing crashes; a double press still skips (RS-48). — automated: `emu-verify.sh --check cold-start-media-button`
- [ ] A third-party controller app (e.g. "Media Controller Test" or any media remote app) connected to S2: it can play, pause and skip, but clearing, adding to or reordering the queue from it does nothing, and it can't browse S2's library (RS-47).
- [ ] An app on the old session library (the same controller test app in its MediaControllerCompat mode) plays a song by its media id and plays a search, and an empty search resumes the queue (RS-46, RS-61; Robolectric can't route a platform MediaController to the session).
- [ ] A 10,000-song queue: the notification and lock screen stay responsive when the queue changes (docs/architecture/media3-playback-design.md, "10k queue spike").

## Audio focus (#345 step 3)

ExoPlayer handles audio focus now, in place of S2's own helper, and keeps focus while paused; `CallHold` holds
a play during a call. Rules in `docs/testing/playback-behaviour-spec.md`.

- [x] RS-50: while playing, take a phone call. Playback pauses; end the call and it resumes by itself. — automated: `emu-verify.sh --check phone-call`
- [ ] RS-50: while playing, take a phone call and pause S2 from its notification during the call. End the call: S2 stays paused.
- [x] RS-55: while S2 is held paused by a call, check the notification and lock screen, and press play during the call. Nothing plays over the call; S2 resumes when the call ends. — automated: `emu-verify.sh --check phone-call`
- [ ] RS-55: while playing, hold the home button for the voice assistant, and press play in S2's notification while it listens. S2 takes focus back and plays at once.
- [ ] RS-51: with navigation (Google Maps) giving spoken directions, S2's volume drops during each prompt and comes back after it, without pausing.
- [ ] RS-52: while playing, start another music app (e.g. YouTube Music). S2 pauses and stays paused after the other app stops.
- [ ] RS-04: pause S2, then start another music app. It plays normally; stop it, and S2 stays paused.
- [ ] RS-04: pause S2, then let a navigation prompt or a notification sound play. S2 stays paused afterwards.
- [ ] RS-54 (Android 12+): with S2 paused, take a phone call and press play in S2 (app, notification, and a headset button). Nothing plays over the call, and S2 shows paused; end the call and S2 starts playing.
- [x] RS-54 (Android 12+): as above, but pause S2 again before ending the call. S2 stays paused after the call. — automated: `emu-verify.sh --check phone-call`
- [ ] RS-54 (Android 11 or lower): with S2 paused, take a phone call and press play in S2. Nothing plays over the call, and S2 stays paused after it; press play again to start.
- [ ] RS-54: with S2 paused, start a WhatsApp or Meet call and press play in S2. Same as a phone call.
- [ ] Cast: duck while casting (a navigation prompt), then switch back to the phone. Local playback is at full volume.

## Behaviour spec, device-only rules

The rules in `docs/testing/playback-behaviour-spec.md` the JVM can't run.

- [ ] RS-17: with the EQ on (any non-flat preset) and ReplayGain on, a 24-bit FLAC plays to its end cleanly, with no noise or skip. (The 24-bit PCM path is covered by `AudioOutputSpecTest`; this row is for native FLAC decoding.)
- [ ] RS-18: with a system EQ app (e.g. Wavelet) attached, change a setting that rebuilds the player (turn USB DAC direct output on and off) and skip. The EQ app keeps applying.
- [ ] RS-19: play a song from the "S2 Transcode Test" album on Jellyfin and then Emby with transcoding forced (a low streaming bitrate). Each plays, seeks, and advances to the next song.
- [ ] RS-20: with USB DAC direct output on, seek to the last second of a track and change the output format (turn the setting off and on) just as it ends. The next track plays from its start, with no skip past it.
- [ ] RS-21: while paused on Cast, seek from another sender (e.g. the Google Home app). S2's seekbar moves to the new position.
- [ ] RS-21: on Cast, press Previous before the receiver has reported a position (straight after a skip). The song restarts rather than jumping two songs back.
- [ ] RS-21: with a system EQ app attached, switch to Cast. The EQ app detaches; switch back and it reattaches.

## Local scanner through MediaStore (#370)

The S2 (TagLib) provider now finds files with a MediaStore query and reads them through content URIs. The emulators cover only API 36 and 37.

- [ ] With an SD card holding music, select only the S2 provider and import. Songs from the card show up with full tags and artwork, and they play.
- [ ] On an API 29 device, import with only the S2 provider. Every song imports with the same tags as the MediaStore provider shows, and they play.
- [ ] On an API 23–28 device, the same check as on API 29.
- [ ] On a real 10k-track library, time a full import with the S2 provider (`Import complete in` in logcat) and compare it with the last Play build, which walks SAF folders.
- [x] Put music in a folder containing `.nomedia` and grant that folder under Media > Directories. Those songs don't import (expected until the optional SAF "Add folder" lands); nothing else breaks. — automated: `emu-verify.sh --check nomedia-import`

## Devices without Google Play (#167)

- [ ] On a device or profile without Google Play services and the Play Store (GrapheneOS without sandboxed Play, or an AOSP emulator image), install S2 and play a local song. It launches without crashing, Now Playing shows no Cast button, and the S2 Pro screen says prices are unavailable.

## Default music app (#106)

- [ ] S2 is offered wherever the device picks a music app (the OEM default music app setting, e.g. OnePlus; a Bluetooth autoplay app such as Bluetooth Autoplay Music), and "Hey Google, play <artist> on S2" plays that artist.

## Tags read from the file by the MediaStore provider (#367, #150)

- [ ] Select only the Android Media Store provider, `seed-test-media.sh gapless` and import. The two `.mka` songs show album "Gapless Album", artist "Gapless Artist" and their track numbers, not the folder name; their titles stay the file names until KTagLib returns the Matroska segment title.
- [ ] With the Media Store provider, import the #150 repro mp3 (`repro_song.zip` on the issue). Its title reads "Chanson d’un jour d’hiver", not "Chanson dâ€™un jour dâ€™hiver".

## Analytics default-on and the one-time notice (#421, #481)

- [ ] Fresh install: Analytics and Crash reporting in Settings > Privacy are already on, and Home never shows the analytics notice.
- [ ] Simulate an upgrade from a user who never chose (clear the app's data, downgrade `previousVersionCode`, or use an old build's data if you have one) with a library loaded: Analytics and Crash reporting turn on, and Home shows the one-time "S2 now shares anonymous usage data..." message once, with a Settings action that opens Settings > Privacy. Reopening Home doesn't show it again.
- [ ] Simulate the same upgrade for a user who had answered or dismissed the old consent card (no longer present) without an explicit choice: Analytics stays off, and Home never shows the notice.

## Sources parity (#474, #479)

- [ ] Force a scan to fail (turn off Wi-Fi mid-scan, or point a server sign-in at an unreachable host) and confirm Sources shows "Scan failed" with the error, and tapping it retries (#474).
- [ ] Revoke a folder's access in Settings > Apps > S2 > Permissions > Files and media (or via `adb shell content revoke_persistable_uri_permission`), confirm Sources flags it with "Access removed", and that re-picking the same folder or removing it clears the flag (#479).

## Song info sheet on phones (#463)

`ModalBottomSheet` is a separate window, so the JVM tests prove the scene and the pop, not the window's insets or gestures.

- [ ] On a phone in portrait with gesture navigation, open a song's menu → Song Info. It opens as a sheet over the screen, above the mini player and nav bar; the drag handle clears the status bar when the sheet is dragged to full height, and the last row scrolls clear of the gesture bar. Repeat with three-button navigation.
- [ ] With the sheet open, start a predictive back swipe: the sheet shrinks and follows the gesture, and cancelling it restores the sheet. Completing it dismisses only the sheet: the screen underneath and the mini player stay as they were. Swiping the sheet down and tapping the scrim dismiss it the same way.
- [ ] Open Song Info from Now Playing's More options. The player settles to the mini player first, then the sheet opens; back closes the sheet and leaves the player at Mini.
- [ ] Rotate to landscape with the sheet open (width 600 dp and up): song info shows as a screen rather than a sheet, and rotating back shows the sheet again.

## Redesign parity, device-only (#377, #382)

The JVM-proven parts of these items are mapped in `docs/architecture/parity-audit.md`; these are what's left for a device.

- [ ] Turn Settings → Appearance → Show Home on launch off, swipe S2 away and reopen it. It opens on Library, and back from Home returns to Library; with the setting on it opens on Home.
- [ ] Now Playing's Cast button finds a Chromecast on the network and connects; playback moves to the receiver.
- [ ] Turn on Settings → Playback → Keep shuffle mode, turn shuffle on, then play a different album. The new queue starts shuffled; with the setting off it starts in order.
- [ ] With Artwork → Wi-Fi only on and Wi-Fi off, a song without cached artwork shows its placeholder; on Wi-Fi it loads. With Local only on, remote artwork never loads.
- [ ] With Media session artwork on, the notification, lock screen and a Bluetooth head unit show the cover; with it off they don't.
- [ ] Add both widget sizes to the home screen, then play, pause and skip. Both update their title, artwork and play state.
- [ ] Crash reporting: with it on, force a crash on a debug build, reopen the app, and the report reaches Sentry; with it off, nothing does. With usage analytics on, events reach PostHog; with it off, none do.
- [ ] Remote Config: change a flag in the console, reopen the app (decision 6: fetch on launch, `MainActivity`), and the new value applies.
- [ ] Set S2 as the default music app, then open an audio file from Files and from a messaging app. S2 plays it.
- [ ] Long-press the launcher icon and tap the Toggle playback shortcut. Playback starts, and a second tap pauses.
- [ ] On an Android Auto head unit (or the DHU), browse the library and play a song; the transport controls work.
- [ ] Grant music access through the in-context prompt on API 32 (`READ_EXTERNAL_STORAGE` dialog) and on API 33+ (`READ_MEDIA_AUDIO`); deny it twice and the Library offers to open the app settings instead.
- [ ] Edit a Jellyfin, Emby and Plex server's address and user from Sources. The change saves and the library reimports (View-based dialogs until #434).
- [ ] Now Playing → More options → Save Queue to Playlist → New Playlist, name it and tap Create (#472). The new playlist holds the queue's songs in queue order; picking an existing playlist instead appends them.
- [ ] `support/scripts/emu-verify.sh --suite` passes with every flow green in `build/maestro/results.md`.

## Streaming quality (#504)

Set Settings → Sources → Streaming quality → On mobile data to 128 kbps and leave On Wi-Fi at Original. The S2 Transcode Test album on each server has a FLAC to try.

- [ ] Jellyfin: on Wi-Fi a FLAC plays as the original (the server's dashboard shows Direct Play). On mobile data the next song transcodes (the dashboard shows Transcode at about 128 kbps) and sounds right; seeking forward and back while it transcodes lands at the right position and keeps playing. A song already under 128 kbps direct-plays.
- [ ] Emby: the same as Jellyfin. On mobile data the dashboard shows the stream transcoding at about 128 kbps (the parameter is untested against Emby), and seeking while it transcodes works.
- [ ] Plex: on Wi-Fi a FLAC plays as the original file. On mobile data the next song plays through Plex's transcoder (Plex Web → Dashboard shows a transcode at about 128 kbps); seeking while it transcodes lands at the right position, and a song whose bitrate is already under the cap plays the original.
