# Device checks

Checks that need a real device, real audio, or a real Cast/Jellyfin setup, and that the emulator
checks (`support/scripts/checks/run-all.sh`) can't cover. Work through them on a debug or internal
build, tick them off, and file anything wrong with `/note`. Remove ticked items once a release ships.

## Playback refactor (#250), S0–S7 and the callbacks → flows work

### Position and restore
- [x] Pause mid-track, force-stop the app, reopen it. It resumes at the paused position. — automated: `emu-verify.sh --check restore-position`
- [ ] Let a track finish into the next one, force-stop, reopen. It resumes on the new track near 0:00.
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
- [ ] The notification can be dismissed while paused, with no crash.
- [x] Resume playback from a headset/Bluetooth media button after the service has stopped. — automated: `emu-verify.sh --check media-buttons`
- [ ] Resume playback from the notification after the service has stopped.
- [x] Unplug headphones while playing. Playback pauses. — automated: `emu-verify.sh --check becoming-noisy`
- [ ] Disconnect Bluetooth while playing. Playback pauses.

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
- [ ] A FLAC by content:// URI, and a real messaging-app attachment (a FileProvider URI with a read grant), each play in S2.
- [ ] After opening a file, force-stop and reopen S2. The queue restores without errors, and the opened file is skipped with a message if it can no longer be read.

### M3U playlist sync (#168), needs a real SAF folder
- [x] With a Shuttle/Taglib folder containing an .m3u (including a line S2 can't resolve), remove a song from that playlist in-app. The .m3u loses only that song's entry; the unresolved line is still there. — automated: `emu-verify.sh --check m3u-sync` (removal driven via debug broadcast, not the UI gesture — see #383)

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
- [ ] With Settings, "Media session artwork" turned off, the lock screen shows no artwork.
- [ ] Bluetooth headset: play/pause, next and previous (single, double and triple press) act on S2.
- [ ] Widget: play/pause, next and previous work with the app open, and with the app force-stopped (the service starts in the foreground without a crash).
- [ ] Resumption after reboot: after a reboot, the system's media resumption controls (quick settings) show the last song; play resumes the saved queue at that song (RS-44). The same with a headset's play button.
- [ ] Assistant: "play <artist> on S2" and "play <song> on S2" play the right songs, with S2 closed and open.
- [ ] Cold start from the widget, API 31+: play something, then force-stop S2 (Settings, Apps, S2, Force stop) so the app is dead. Press the widget's play button. A notification appears (a "Loading" one first on a large library, then the playing song's), the saved queue plays, and nothing crashes, even if the queue takes several seconds to restore (RS-48).
- [ ] Cold start from a headset button, API 31+: the same, with the app force-stopped, pressing a Bluetooth or wired headset's play button. A notification appears, the saved queue plays, and nothing crashes; a double press still skips (RS-48).
- [ ] A third-party controller app (e.g. "Media Controller Test" or any media remote app) connected to S2: it can play, pause and skip, but clearing, adding to or reordering the queue from it does nothing, and it can't browse S2's library (RS-47).
- [ ] An app on the old session library (the same controller test app in its MediaControllerCompat mode) plays a song by its media id and plays a search, and an empty search plays every song (RS-46; Robolectric can't route a platform MediaController to the session).
- [ ] A 10,000-song queue: the notification and lock screen stay responsive when the queue changes (docs/architecture/media3-playback-design.md, "10k queue spike").

## Behaviour spec, device-only rules

The rules in `docs/testing/playback-behaviour-spec.md` the JVM can't run.

- [ ] RS-17: with the EQ on (any non-flat preset) and ReplayGain on, a 24-bit FLAC plays to its end cleanly, with no noise or skip. (The 24-bit PCM path is covered by `AudioOutputSpecTest`; this row is for native FLAC decoding.)
- [ ] RS-18: with a system EQ app (e.g. Wavelet) attached, change a setting that rebuilds the player (turn USB DAC direct output on and off) and skip. The EQ app keeps applying.
- [ ] RS-19: play a song from the "S2 Transcode Test" album on Jellyfin and then Emby with transcoding forced (a low streaming bitrate). Each plays, seeks, and advances to the next song.
- [ ] RS-20: with USB DAC direct output on, seek to the last second of a track and change the output format (turn the setting off and on) just as it ends. The next track plays from its start, with no skip past it.
- [ ] RS-21: while paused on Cast, seek from another sender (e.g. the Google Home app). S2's seekbar moves to the new position.
- [ ] RS-21: on Cast, press Previous before the receiver has reported a position (straight after a skip). The song restarts rather than jumping two songs back.
- [ ] RS-21: with a system EQ app attached, switch to Cast. The EQ app detaches; switch back and it reattaches.
