# Playback behaviour spec

What playback must do, as seen by the user, one rule per behaviour. Each rule comes from the playback fixes in
`git log --grep='^fix(playback'` and names the commits it came from. The rules outlive the code that fixed them:
the Media3 refactor (#345, `docs/architecture/media3-playback-design.md`) must keep every one.

Each JVM rule has one test named with its RS id, in `android/playback/src/test/java/com/simplecityapps/playback/spec/`:
`PlaybackSpecTest` for queue and transport rules, `AudioOutputSpecTest` for the audio that comes out,
`AudioFocusSpecTest` for other apps taking audio focus and headphones being unplugged; a Cast rule
names its test, in `chromecast/` or `spec/CastSpecTest`; a media session rule is in `spec/MediaSessionSpecTest`, driven
through a Media3 `MediaBrowser` connected to the session. The tests run
the real `PlaybackManager` and `QueueManager` over a real ExoPlayer whose playlist is the queue, built by the
production `ExoPlayerFactory` (fake clock, lazy preparation as in production, production renderers, audio sink and
EQ/ReplayGain processors, WAV files from the test resources). They call only
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

**RS-04: pausing keeps audio focus, and another app can still take it.** Given a song playing with audio focus, when
the user pauses, then S2 keeps focus, as Android asks of media apps. When another app asks for focus while S2 is paused,
it gets it: S2 is told it lost focus and gives it up, and doesn't resume on its own afterwards, nor when a short
interruption that began while paused ends. Playing out the queue pauses at its end and keeps focus the same way; a
failure that stops playback, or emptying the queue (RS-05), stops the player, which gives focus up. (ea250913; changed
in #345 step 3, which follows Media3: S2 used to give focus up on every pause, a refactor-era fix rather than a user
report) — JVM (`PlaybackSpecTest`, `AudioFocusSpecTest`).

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
speed is reported (and shown) straight away. (e11a74e2) — JVM. Given a speed set, when the app starts again, then
the new player plays at that speed (#408) — JVM. The volume half (a rebuilt player starts at full
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

**RS-34: a song reached by playing on is playing, not loading.** Given a song that playback moved on to by playing
out the one before, when the user seeks in it (or it rebuffers), then it shows as playing, not loading; and when it
fails once playing (its file deleted or its stream dropped), then playback stops on it, paused, rather than skipping
ahead as if it had failed to load (RS-23). (#345) — JVM.

**RS-35: a restored song shows its real length.** Given a song whose tags give the wrong length, when it's loaded
paused (a restore), then the published progress carries its real length as soon as it's ready, not only after the
next seek or play. (#345) — JVM.

**RS-36: queue changes apply in the order they're made.** Given a new queue still being set (a long album or
playlist played), when songs are added to the queue or played next before it's ready, then they join the new queue,
after its current song or at its end, rather than the queue it replaces. (#345) — JVM.

## Audio output

**RS-15: ReplayGain from the first sample.** Given ReplayGain on and a song with a track gain, when it starts, then
the gain applies from its first sample, not after the first buffers. (b3490b18) — JVM.

**RS-16: ReplayGain per song across a gapless transition.** Given two songs with different gains playing gaplessly,
then each plays at its own gain, switching exactly at the song boundary, with no gap. (cb76a79f) — JVM.

**RS-17: 24-bit audio plays cleanly.** Given a 24-bit song with EQ and ReplayGain on, then it plays to its end without
noise or failure. (4ad26a26) — JVM, with a 24-bit WAV: every frame comes out, with no wrapped samples. Decoding
24-bit FLAC (a native decoder) stays device-only: *Audio*, 24-bit FLAC with EQ and ReplayGain on.

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

## Audio focus and headphones

ExoPlayer handles audio focus (`setAudioAttributes(music, handleAudioFocus = true)`) and unplugged headphones
(`setHandleAudioBecomingNoisy(true)`) since #345 step 3; S2's own focus helper and noisy receiver are gone. The
harness plays the platform's part: `changeAudioFocus` hands the player the focus change another app causes, and
`unplugHeadphones` sends the becoming-noisy broadcast.

**RS-50: a short interruption pauses, and playback resumes when it ends.** Given a song playing, when another app
takes focus for a moment (a phone call, a voice assistant), then playback shows paused; when the app gives focus
back, it plays on from where it was. If the user pauses during the interruption, it stays paused after it ends.
(#345) — JVM. The same as before
step 3, now Media3's behaviour.

**RS-51: a navigation prompt ducks playback, without pausing it.** Given a song playing, when another app takes focus
and allows ducking (a navigation prompt, a notification sound), then playback carries on playing, at a lower volume
until focus returns. (#345) — JVM for playing on; the volume (Media3 ducks to 20%, as S2 did) is device-only:
*Audio focus (#345 step 3)*.

**RS-52: another music app taking over pauses playback for good.** Given a song playing, when another app takes focus
permanently (another music app starts), then playback pauses, gives focus up and doesn't resume by itself. (#345) —
JVM. New in step 3: S2 now gives focus up at this point, where it used to hold on to it while paused.

**RS-53: unplugging headphones pauses playback.** Given a song playing, when headphones are unplugged (or a Bluetooth
headset disconnects), then playback pauses, keeping audio focus as any pause does (RS-04). (#345) — JVM for the broadcast; a real unplug and
a Bluetooth disconnect are device-only: *Service and notification*.

**RS-54: pressing play during a phone call waits for it to end.** Given a call ringing or in progress (a phone call,
a VoIP call, or one being screened or redirected: any audio mode but normal), when the user presses play, from the app,
the media session, a widget or a headset button, then playback doesn't start over the call: it shows paused and takes
no audio focus. On API 31+ it starts when the call ends (the audio mode returns to normal), unless the user pauses, a
new queue is loaded or the queue changes first, which drops it. Below API 31, which can't say when a call ends, the
play is dropped, and the user presses play again after the call. A play on a Cast receiver goes ahead. (#345) — JVM
(`AudioFocusSpecTest`, the audio mode set through Robolectric's `AudioManager`); how it sounds on a real call is
device-only: *Audio focus (#345 step 3)*. The same as before step 3 in effect: S2 used to wait for the delayed focus
grant a call gives; Media3 takes that grant as focus, so `PlaybackManager` checks the audio mode itself.

**RS-55: pressing play while an interruption holds playback off.** Given playback held paused by a short interruption
(RS-50), when the user presses play, then during a call nothing plays and it still shows paused, until the call ends
and gives focus back (RS-54 holds the play); any other interruption (a voice assistant, another app's short sound) loses
focus to S2, which asks for it again and plays at once. Both unchanged from before step 3, where a call's focus request
was delayed and any other was granted, and documented rather than changed. (#345) — JVM; the notification and lock
screen meanwhile are device-only: *Audio focus (#345 step 3)*.

## Cast

**RS-21: Cast keeps the position, state and effects in step.** Given playback on Cast, then the position only
re-anchors on a real jump (a seek, from this or another sender), the playback state is published when switching
between local and Cast, a superseded switch never overrides a newer one, the audio effect session is closed while
casting, and Previous with no reported position restarts the song. (b1a2a27d, 9dfbf8ac, 22a03157, 42cd7f03,
a3dd7b59, e48e3be2) — device-only: *Cast* and *Behaviour spec, device-only rules*.

**RS-37: only the Cast session can read what the phone serves it.** Given a Cast session, when anything asks the
phone's stream server for a song's audio or artwork without that session's key, whether a local file or a
remote-provider song whose stream URL holds the provider's credential, then it's refused (403) and nothing is
redirected; a new session gets a new key. (#345) — JVM (`chromecast/HttpServerTest`).

**RS-38: a remote song is cast as the stream its server sends.** Given a Jellyfin or Emby song the receiver can't play
as it is, when it's cast, then the receiver is told the type the server transcodes it to (HLS) rather than the file's
own, the stream being resolved before the song is sent; a window of songs goes out as far as their streams are
resolved, starting from the current song at its position, and the rest follows in order. (#345) — JVM
(`chromecast/CastQueueTest`); the real transcode is device-only: *Cast*.

**RS-39: casting to the end of the queue ends it as playing locally does.** Given the receiver playing the last song
with repeat off, when it plays that song to its end (a Cast receiver goes idle rather than reporting an end), then the
song is reported as ended and playback pauses there, as it does locally; a receiver stopped from elsewhere, idle on
any song but the last, or idle while a new queue is on its way, ends nothing. (#345) — JVM (`spec/CastSpecTest`);
the receiver's idle reason is device-only: *Cast*.

**RS-40: casting under repeat-all plays on from the last song to the first.** Given repeat-all while casting, when the
receiver nears the end of the queue, then the songs from the start of the queue (in play order) are sent after the
last, so it plays on round without a gap and S2's current song follows it; a receiver holding the whole queue repeats
it by itself, and turning repeat off sends the window again, ending at the queue's last song. (#345) — JVM
(`chromecast/CastWindowTest`, `spec/CastSpecTest`).

## Media session

What another app (Android Auto, a Bluetooth headset, the notification and lock screen, Assistant) sees and can do
through the media session. Each test connects a `MediaBrowser` to the session over the real playback stack.

**RS-41: a controller's play, pause, seek and skip act as the app's own buttons do.** Given a queue, when a controller
plays, pauses, seeks, skips next or previous, or picks an item from the queue it sees, then playback does the same as
the app's own controls: playing takes audio focus, and next skips even with repeat-one on. (#345) — JVM
(`spec/MediaSessionSpecTest`); headset buttons, the notification and the lock screen are device-only: *Media session
through Media3*.

**RS-42: the browse tree lists the library.** Given a library, when a controller browses the session, then the root
lists Artists, Albums, Playlists and Shuffle All, an album lists its songs as playable, search finds songs by name, and
an id that names nothing has no children. (#345) — JVM (`spec/MediaSessionSpecTest`); Android Auto is device-only:
*Media session through Media3*.

**RS-43: playing a browsed song plays its album from that song.** Given a song browsed within an album, when a
controller plays it, then the album is queued and plays from that song, and the controller sees the queue as it is.
(#345) — JVM (`spec/MediaSessionSpecTest`).

**RS-44: play with nothing loaded resumes the saved queue.** Given the app starting (a headset's play button, the
system's resumption controls) with the saved queue still being restored, when a controller plays, then once the queue
is restored it plays from the saved song. (#345) — JVM (`spec/MediaSessionSpecTest`); resumption after a reboot is
device-only: *Media session through Media3*.

**RS-45: the shuffle and repeat buttons change the modes and show the ones they're in.** Given the session's shuffle
and repeat buttons, when one is pressed, then shuffle toggles and repeat goes off, all, one, and each button's icon
follows the mode, whichever way it changed. (#345) — JVM (`spec/MediaSessionSpecTest`).

**RS-46: a voice search plays what it finds, and adding one adds the songs it names.** Given a library, when a
controller asks to play a search, then what it finds plays (RS-60; a search for nothing in particular is RS-61); and
adding a search or a file to the queue adds the songs it names. (#345, #424) — JVM (`spec/MediaSessionSpecTest`); an
app on the old session library (`MediaControllerCompat` play-from-id and play-from-search) is device-only, as
Robolectric's platform `MediaController` never reaches the session: *Media session through Media3*.

**RS-47: an app that isn't trusted can play but can't browse or change the queue.** Given a controller that isn't the
system, S2 or a caller the app knows (Android Auto), when it connects, then its browse root is empty and it can't add,
remove, move or clear the queue's items, but it can control playback and ask to play a media id, a file or a search;
a trusted controller can do all of it. (#345) — JVM (`spec/MediaSessionSpecTest`); a real third-party app is
device-only: *Media session through Media3*.

**RS-48: a cold start in the foreground stays there until its command has run.** Given S2 not running, when the
widget, a shortcut or a headset's play button starts the playback service in the foreground, then it's in the
foreground straight away (with Media3's notification, or a placeholder under the same id) and stays there while the
saved queue is restored, even while Media3 has no notification to show; once the queue plays, Media3's notification
takes the foreground over, and a command that doesn't play leaves the foreground once it has run. (#345) — JVM
(`spec/ForegroundStartSpecTest`, over a test service with PlaybackService's start handling, as PlaybackService itself
needs Hilt); API 31+ with the app dead is device-only: *Media session through Media3*.

**RS-49: play-pause while a song is loading follows where the load is headed.** Given a song still loading paused (the
saved queue's restore, as the widget's play-pause cold-starts the app), when play-pause is pressed, then it plays;
given a song loading to play (a skip), then it pauses. (#345) — JVM (`spec/PlaybackSpecTest`); the widget's cold start
is on the emulator (`checks/cold-start-widget.sh`).

**RS-56: a restored song that can't load stays where it was left.** Given a saved queue whose current song can't be
loaded when the app starts (a server out of reach, a file that can't be read), when the queue is restored, then that
song stays current, paused, and a failure is reported for it, rather than the queue moving on; when it's then
played, it's skipped for the next song that can load (RS-23). (#394) — JVM.

## Opening files from other apps

A file manager, a download or a messaging attachment can open an audio file with S2 (`ACTION_VIEW` of `audio/*`,
by a file:// or content:// URI). Decided in #425: S2 plays it straight away, on its own, as other players do; what
changes is only whether it's the library's song or a transient one. Neither case adds anything to the library or
asks to (a file in a folder the library reads is imported by the next scan anyway). RS-09 covers an opened file that
can no longer be read.

**RS-57: an opened file that's in the library plays as its library song, on its own.** Given a file the library holds
(matched by the URI itself, the file path behind a file:// or MediaStore URI, or a SAF document id), when another app
opens it, then the queue it replaces becomes that one library song, which plays, counts its plays and restores after
a restart like any library song. When several rows hold the file (one per local provider, #420), the choice doesn't
depend on the library's order: a row that isn't excluded, then S2's own scan over MediaStore's, then the lowest id.
(#425, #426) — JVM (`spec/MediaSessionSpecTest`, and `OpenedAudioTest` for each tie-break); on the emulator
(`checks/open-file-intent.sh`).

**RS-58: an opened file that isn't in the library plays on its own, outside the library.** Given a file the library
doesn't hold (a download, an attachment, or a content:// URI with no file path behind it), when another app opens it,
then the queue it replaces becomes that one transient song, named from its tags (or its file name), which plays under
the opening app's URI grant. It isn't added to the library and its plays aren't counted; once the app is killed the
grant has lapsed, so it isn't restored and the queue comes back empty. (#425) — JVM (`spec/MediaSessionSpecTest`);
the restore is on the emulator (`checks/open-file-intent.sh`).

**RS-59: leaving S2 after opening a file keeps it playing.** Given a file opened from another app, when the user
presses back, then S2 goes to the background and the file keeps playing, with its notification. Relaunching S2 from
recents doesn't open the file again. (#425) — device-only (`checks/open-file-intent.sh`).

## Voice search

**RS-60: a voice search plays the closest match for what it names.** Given a library, when a voice search
("play Radiohead on S2") focuses on an artist, album, song, genre or playlist, by the parts Assistant parses out or
by its words, then that plays: an artist's or a genre's songs, an album or a playlist in order, or a song followed by
the rest of its album. A search with no focus matches every kind and plays the best match, an artist over an album,
a playlist, a song and a genre where they match as well; a song can be named with its artist ("Creep by
Radiohead"). Case, accents, punctuation and a leading "the" don't count, and a name misheard, misspelt or with words
around it plays the closest match rather than nothing; a focus with nothing of its kind to play (no playlists, or
only empty ones) is searched with no focus. (#424) — JVM (`mediasession/VoiceSearchResolverTest` per focus, `spec/MediaSessionSpecTest` through
the session); on the emulator (`checks/voice-search.sh`); Assistant itself is device-only: *Media session through
Media3*.

**RS-61: a voice search for nothing in particular resumes the queue, or shuffles the library.** Given a queue, when
a voice search with no words and no parts arrives ("play music on S2", or a request that names nothing), then the
queue plays from where it was, unchanged; given no queue, every song plays, shuffled. (#424) — JVM
(`spec/MediaSessionSpecTest`); on the emulator (`checks/voice-search.sh`).

**RS-62: a voice search with the app not running plays once the saved queue is restored.** Given S2 not running,
when a voice search arrives (`MEDIA_PLAY_FROM_SEARCH`), then the playback service starts in the foreground and stays
there (RS-48), waits for the saved queue's restore, and plays the search's songs in its place; the search runs once,
and isn't run again when S2 is reopened from recents. (#424) — JVM (`spec/ForegroundStartSpecTest`); the cold start
on the emulator (`checks/voice-search.sh`).

## Commits with no rule

Mechanism only, with no behaviour of their own to hold (the design doc's section 4 list, plus thread-safety and
lifecycle plumbing): 9105184a, 53a2b6ab, 0176feba, 240058fb, da5cdebd, e35ed66e, b5b8735e, 88a9e303, 9927ba90,
2dbb8904, c53af4ea, bdf11635, 687a6240, f7643603, a9be35e1.

Test or compile fixes only: 01707e88, 08b4c21e, c2e2ef28.
