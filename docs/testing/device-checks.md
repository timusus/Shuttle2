# Device checks

Checks that need a real device, real audio, or a real Cast/Jellyfin setup, and that the emulator
checks (`support/scripts/checks/run-all.sh`) can't cover. Work through them on a debug or internal
build, tick them off, and file anything wrong with `/note`. Remove ticked items once a release ships.

## Playback refactor (#250), S0–S7 and the callbacks → flows work

### Cast
- [ ] While playing, switch local → Cast. Playback continues on the Cast device from the same position.
- [ ] While playing on Cast, switch back to local. Playback continues locally from the same position.
- [ ] Toggle local → Cast → local quickly. It ends up on local and playing, from the right position, with no double audio.
- [ ] Skip to the next track and switch to Cast at once. The new track starts at 0:00 on Cast, not at the old track's position.
- [ ] While paused, switch local → Cast. It stays paused on Cast at the same position.
- [ ] Duck (e.g. a navigation prompt) while local, then switch to Cast and back. Volume is normal and nothing is stuck ducked.

### Position and restore
- [ ] Pause mid-track, force-stop the app, reopen it. It resumes at the paused position.
- [ ] Let a track finish into the next one, force-stop, reopen. It resumes on the new track near 0:00.
- [ ] Skip and pause right away, before the new track is audible, then force-stop and reopen. It resumes on the new track at 0:00.
- [ ] Podcasts and audiobooks resume about 5 s before where they stopped.

### Sleep timer
- [ ] A sleep timer without "play to end" pauses when it runs out.
- [ ] A sleep timer with "play to end" lets the current track finish, then pauses.

### Repeat, shuffle and speed
- [ ] Repeat one, repeat all and off each behave correctly at the end of a track and the end of the queue.
- [ ] Turn shuffle on mid-queue. The next track comes from the shuffled order.
- [ ] Set playback speed ≠ 1.0×, then change repeat mode. The speed doesn't reset.

### Service and notification
- [ ] Pause, leave the app in the background for several minutes. The notification can be dismissed and the service stops, with no crash.
- [ ] Resume playback from the notification and from a Bluetooth/headset button after the service has stopped.
- [ ] Unplug headphones or disconnect Bluetooth while playing. Playback pauses.

### Next-track preparation and slow loads (#300)
- [ ] Gapless auto-advance still works after reordering the queue, turning shuffle on, and removing the next track.
- [ ] A slow Jellyfin transcode or Cast load (weak Wi-Fi, large FLAC) that takes more than 30 s still plays that track instead of skipping it.

### Next-track ownership (#315)
- [ ] Jellyfin: let an album play through; every transition is gapless, and ReplayGain is right on each track.
- [ ] On a slow server, skip and then immediately reorder the queue: the track that plays next is the new next song, not the old one.

### Widgets (after the WidgetManager migration)
- [ ] Each widget size shows the current song, artwork and play/pause state, and updates on skip, pause and resume.
- [ ] The widget controls (play/pause, next, previous) work while the app is in the background and after a force-stop.
- [ ] Shuffle/repeat changes show on widgets that display them.

### Tag edits reaching a queued song (#270)
- [ ] With the Shuttle (TagLib) local provider set up (needs a real SAF folder pick, which
      `seed-test-media.sh` deliberately skips by using the MediaStore provider), queue several
      songs and play one. Edit the playing song's title via the batch tag editor; the queue, now
      playing, mini player and notification all update without playback interrupting.
- [ ] Edit a queued-but-not-playing song's title the same way; the queue screen shows the new
      title, playback isn't affected.

### Audio (still outstanding from earlier stages)
- [ ] ReplayGain (track and album modes) sounds right.
- [ ] Gapless playback across an album with no gaps between tracks.
- [ ] EQ presets and custom bands apply, and survive a skip and a Cast round-trip.
- [ ] 24-bit FLAC plays cleanly.
- [ ] Jellyfin streaming: play, skip, seek and resume after a force-stop.

## Ported PRs (Sep 2026)

### USB DAC direct output (#198), Android 14+ with a USB DAC
- [ ] Turn on Settings → Playback → USB DAC direct output, plug in the DAC, play a 44.1 kHz and then a 48 kHz file. Both play at the right pitch and speed, with no glitch at the track change.
- [ ] With it on, the equaliser and ReplayGain have no audible effect; turn it off and they apply again.
- [ ] Toggle it while paused, then resume. Output switches without a restart of the track.
- [ ] Unplug the DAC mid-track. Playback continues (or pauses) on the phone speaker with no crash; replug and it goes direct again.

### Open audio files from other apps (#186)
- [ ] Open an MP3 and a FLAC from a file manager and from a messaging app attachment. Each plays in S2.
- [ ] After opening a file, force-stop and reopen S2. The queue restores without errors, and the opened file is skipped with a message if it can no longer be read.

### M3U playlist sync (#168), needs a real SAF folder
- [ ] With a Shuttle/Taglib folder containing an .m3u (including a line S2 can't resolve), remove a song from that playlist in-app. The .m3u loses only that song's entry; the unresolved line is still there.

### Playback reporting (#191)
- [ ] Play a Jellyfin song, then an Emby song. Each server's dashboard shows it as now playing with a moving position, and clears it on pause/stop.
- [ ] Play a Plex song to the end. It shows in Plex's now playing and is marked played afterwards.

## Behaviour spec, device-only rules

The rules in `docs/testing/playback-behaviour-spec.md` the JVM can't run.

- [ ] RS-17: with the EQ on (any non-flat preset) and ReplayGain on, a 24-bit FLAC plays to its end cleanly, with no noise or skip.
- [ ] RS-18: with a system EQ app (e.g. Wavelet) attached, change a setting that rebuilds the player (turn USB DAC direct output on and off) and skip. The EQ app keeps applying.
- [ ] RS-19: play a song from the "S2 Transcode Test" album on Jellyfin and then Emby with transcoding forced (a low streaming bitrate). Each plays, seeks, and advances to the next song.
- [ ] RS-20: with USB DAC direct output on, seek to the last second of a track and change the output format (turn the setting off and on) just as it ends. The next track plays from its start, with no skip past it.
- [ ] RS-21: while paused on Cast, seek from another sender (e.g. the Google Home app). S2's seekbar moves to the new position.
- [ ] RS-21: on Cast, press Previous before the receiver has reported a position (straight after a skip). The song restarts rather than jumping two songs back.
- [ ] RS-21: with a system EQ app attached, switch to Cast. The EQ app detaches; switch back and it reattaches.
