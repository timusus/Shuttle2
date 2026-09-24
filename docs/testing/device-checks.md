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

### Widgets (after the WidgetManager migration)
- [ ] Each widget size shows the current song, artwork and play/pause state, and updates on skip, pause and resume.
- [ ] The widget controls (play/pause, next, previous) work while the app is in the background and after a force-stop.
- [ ] Shuffle/repeat changes show on widgets that display them.

### Audio (still outstanding from earlier stages)
- [ ] ReplayGain (track and album modes) sounds right.
- [ ] Gapless playback across an album with no gaps between tracks.
- [ ] EQ presets and custom bands apply, and survive a skip and a Cast round-trip.
- [ ] 24-bit FLAC plays cleanly.
- [ ] Jellyfin streaming: play, skip, seek and resume after a force-stop.
