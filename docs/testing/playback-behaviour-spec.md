# Playback behaviour spec

What playback must do, as seen by the user, one rule per behaviour. Each rule comes from the playback fixes in
`git log --grep='^fix(playback'` and names the commits it came from. The rules outlive the code that fixed them:
the Media3 refactor (#345, `docs/architecture/media3-playback-design.md`) must keep every one.

Each JVM rule has one test named with its RS id, in `android/playback/src/test/java/com/simplecityapps/playback/spec/`:
`PlaybackSpecTest` for queue and transport rules, `AudioOutputSpecTest` for the audio that comes out. The tests run
the real `PlaybackManager`, `QueueManager` and `ExoPlayerPlayback` on a real ExoPlayer (fake clock, production
renderers, audio sink and EQ/ReplayGain processors, WAV files from the test resources). They call only
`PlaybackOperations` and `QueueOperations` and observe their flows, the PCM written to the AudioTrack, and audio
focus. A rule the JVM can't run is **device-only** and points at its check in `docs/testing/device-checks.md`.

Run them with `./support/scripts/unit-test playback --tests '*.spec.*'`.

## Queue and transport

**RS-01: auto-advance.** Given a queue of two songs playing, when the first plays to its end, then the queue moves to
the second, a track end is reported for each, and the published progress from then on is the second song's
(duration and position), never the first's. (ea250913) — JVM.

**RS-02: skip re-anchors at once.** Given a song loaded at 1:30, when the user skips to the next song, then the
position anchor (what the media session, notification and seekbar extrapolate from) reads 0 on the new song
straight away, before the new song has loaded. (70581fbe) — JVM.

**RS-03: seek while paused.** Given a paused song, when the user seeks, then the published progress and the anchor
move to the new position while still paused, and pressing play resumes from there. (77ac6765) — JVM.

**RS-04: pause releases audio focus.** Given a song playing with audio focus, when the user pauses, then focus is
abandoned so other apps can play. (ea250913) — JVM.

**RS-05: removing the last song stops cleanly.** Given one song playing, when the user removes it from the queue,
then the queue is empty, playback stops (paused) and audio focus is abandoned. (d77f516d) — JVM.

**RS-06: adding with shuffle on keeps the chosen order.** Given shuffle on, when the user adds several songs to the
queue, then they join the end of both the shuffled and the unshuffled queue in the order they were chosen.
(25f4113b) — JVM.

**RS-07: restoring a shuffle order that holds a song twice.** Given a saved queue whose shuffled order holds the same
song twice, when it is restored, then the shuffled order is exactly as saved, each entry is its own queue item, the
current item is the saved position, and the unshuffled order is kept. (fca45f5b) — JVM.

**RS-08: a tag edit doesn't disturb playback.** Given a queued song loaded at 1:12, when its tags are edited, then the
queue shows the new tags on the same queue item, and playback neither reloads nor moves. (ba044736) — JVM.

**RS-09: an opened file that can't be read is reported.** Given a queued song whose file can no longer be read (an
opened file whose URI grant lapsed), when it is played, then a playback failure is reported for that song (the app
says so) and playback stops. (33aaad91) — JVM. The restore half is device-only: *Open audio files from other apps*.

**RS-10: load failures skip or stop cleanly.** Given a song that can't be loaded (a remote server that can't be
reached), when it is played, then the next song that can load plays instead; when no song in the queue can load,
the load reports failure and playback stops, paused, on the last song tried. (e48e3be2) — JVM.

**RS-11: resuming near a song's end starts it again.** Given a saved position within the song's last moments, when
playback resumes, then the song plays from the start; a saved position mid-song resumes from that position.
(70038678) — JVM.

**RS-12: repeat one holds from the first play.** Given repeat one set before anything has played (the player not yet
created), when the song ends, then it plays again rather than advancing. (9b1c45bd) — JVM.

**RS-13: speed and volume survive a new player.** Given a playback speed set before anything has loaded, then that
speed is reported (and shown) straight away. (e11a74e2) — JVM. The volume half (a rebuilt player starts at full
volume, not stuck ducked) is device-only: *Cast* — duck, switch to Cast and back.

**RS-14: every queue change is published.** Given a queue, when a song is moved, removed, added or played next, then
each change publishes a new queue state, so the queue screen, notification and session all update. (fdec2b2c) — JVM.

**RS-22: only a song that plays out counts as played through.** Given the last song in the queue playing, when the
user removes it from the queue, then no track end is reported for it or for the song the queue falls back to (no
play count, no saved position at its end); a song that plays to its end is reported (RS-01). (#345) — JVM.

**RS-23: any song that fails to load is skipped.** Given a queue where a song's file can't be read (missing,
unsupported, a 404), when it is played, or reached by playing on, then a failure is reported for it and the next song
that can load plays; up to 15 songs in a row are tried before playback stops, paused, on the 15th. (#345) — JVM.

**RS-24: a saved position resumes only its own song.** Given a saved position for the current song and nothing
loaded (after a failure, or a restore), when another song is made current (from the queue screen or a media
controller) and played, then it plays from its start, not from the other song's position. (#345) — JVM.

**RS-25: a tag edit keeps the shuffled order.** Given shuffle on, when a tag edit changes queued songs (their tags,
or the file a song plays from), then each song keeps its place in the shuffled and unshuffled queue. (#345) — JVM.

**RS-26: setting the same queue again refreshes its songs.** Given a queue loaded, when the same songs are set as the
queue again with changed data (a library refresh), then the queue shows the new data, and the current song keeps
playing from where it was, without reloading. (#345) — JVM.

**RS-27: a saved shuffle order survives songs leaving the library.** Given a saved shuffled queue holding a song the
queue no longer has, when it's restored with shuffle on, then the other songs keep their saved order and the song
that was current is current again. (#345) — JVM.

**RS-28: removing many songs is one change per run.** Given a long queue playing, when the queue is cleared (the
current song stays) or a selection is removed, then each run of adjacent songs leaves in one playlist change, not
one per song, so the time taken doesn't grow with the square of the queue's length. (#345) — JVM.

**RS-29: playback and queue calls work from any thread.** Given a song playing, when a caller off the main thread
reads the progress or duration, pauses, or removes a queue item, then the read returns the last published state and
each change takes effect on the main thread, without an error. (#345) — JVM.

**RS-30: removing the current song moves on to the next.** Given a queue with a song after the current one, when
the current song is removed, then the next song becomes current: it plays if the removed song was playing, and waits
paused at its start if it was paused. (#345) — JVM.

**RS-31: clearing a paused queue empties it.** Given a queue loaded and paused, when the queue is cleared, then it's
empty and playback stays paused. (Cleared while playing, the current song stays: RS-28.) (#345) — JVM.

**RS-32: play next with shuffle on.** Given shuffle on, when songs are played next, then they come straight after
the current song in both the shuffled and the unshuffled queue, in the order chosen, and the current song plays on.
(#345) — JVM.

**RS-33: previous goes back early, else restarts.** Given a song playing, when previous is pressed within its first
2 seconds, then the song before it becomes current; after 2 seconds, the song restarts instead. (#345) — JVM.

## Audio output

**RS-15: ReplayGain from the first sample.** Given ReplayGain on and a song with a track gain, when it starts, then
the gain applies from its first sample, not after the first buffers. (b3490b18) — JVM.

**RS-16: ReplayGain per song across a gapless transition.** Given two songs with different gains playing gaplessly,
then each plays at its own gain, switching exactly at the song boundary, with no gap. (cb76a79f) — JVM.

**RS-17: 24-bit audio plays cleanly.** Given a 24-bit song with EQ and ReplayGain on, then it plays to its end without
noise or failure. (4ad26a26) — device-only: *Audio*, 24-bit FLAC with EQ and ReplayGain on. Robolectric's
AudioTrack has no minimum buffer size for 24-bit PCM, so the player fails before any audio is written.

**RS-18: a boosted EQ doesn't clip.** Given an EQ preset that boosts every band by 12 dB, then the output keeps
headroom instead of clipping. (f8e529c2) — JVM. The audio session half (a system EQ app stays attached when the
player is rebuilt) is device-only: *Behaviour spec, device-only rules*.

**RS-19: transcoded streams play.** Given a Jellyfin or Emby song the server transcodes, when it is played, then it
plays, seeks and advances like any other song. (79a0a26f) — device-only: *Behaviour spec, device-only rules*. The
transcode is HLS with AAC, which needs the server and a device decoder.

**RS-20: USB DAC output follows the stream.** Given USB DAC direct output on, when the stream's format changes (a new
track, or a setting changed while paused), then a new AudioTrack opens at the new format without skipping, including
at a track's last millisecond. (9e2c3b84, c8f4df6b, 6b80d191) — device-only: *USB DAC direct output* and *Behaviour
spec, device-only rules*.

## Cast

**RS-21: Cast keeps the position, state and effects in step.** Given playback on Cast, then the position only
re-anchors on a real jump (a seek, from this or another sender), the playback state is published when switching
between local and Cast, a superseded switch never overrides a newer one, the audio effect session is closed while
casting, and Previous with no reported position restarts the song. (b1a2a27d, 9dfbf8ac, 22a03157, 42cd7f03,
a3dd7b59, e48e3be2) — device-only: *Cast* and *Behaviour spec, device-only rules*.

## Commits with no rule

Mechanism only, with no behaviour of their own to hold (the design doc's section 4 list, plus thread-safety and
lifecycle plumbing): 9105184a, 53a2b6ab, 0176feba, 240058fb, da5cdebd, e35ed66e, b5b8735e, 88a9e303, 9927ba90,
2dbb8904, c53af4ea, bdf11635, 687a6240, f7643603, a9be35e1.

Test or compile fixes only: 01707e88, 08b4c21e, c2e2ef28.
