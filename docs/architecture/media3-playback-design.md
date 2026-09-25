# Media3 playback design (#345)

Goal: the same features with Media3 as the single source of truth for queue, position, state, session and Cast. Media3 is on 1.11.1 (gradle/libs.versions.toml:50). Only media3-exoplayer, -hls and -database are in the catalog (:157-159), so media3-session and media3-cast have to be added. Cast framework is 22.3.1 (:64).

## 1. Today: why it breaks

- Position has three owners: the Playback implementation, LoadCoordinator's pending load and PlaybackManager's anchor (PlaybackManager.kt:399, :562-575).
- ExoPlayer is fed one next item at a time (ExoPlayerPlayback.kt:159-207) by LoadCoordinator.requestNext (LoadCoordinator.kt:198).
- QueueManager keeps its own base list and shuffle list (QueueManager.kt:484-593) and its own repeat mode (:292-313). It runs under a mutex on IO, while the rest runs on main.
- CastPlayback re-implements load, play and position (chromecast/CastPlayback.kt:49-201). PlaybackSwitcher moves playback between engines (PlaybackSwitcher.kt:64).
- The session is compat: PlaybackService extends MediaBrowserServiceCompat (PlaybackService.kt:35) and MediaSessionManager wraps MediaSessionCompat (MediaSessionManager.kt:77).
- 44 of 73 `fix(` commits are `fix(playback)` (`git log --grep='^fix(playback'`).

## 2. Feature inventory → Media3 owner

Paths are relative to `android/playback/src/main/java/com/simplecityapps/playback` unless they start with `app/` or `downloads/`.

| Feature | Today | Media3 owner | What stays custom |
|---|---|---|---|
| Queue | QueueManager base list | ExoPlayer playlist of MediaItems (full queue) | Song→MediaItem mapper. The mediaId is the song id. The tag carries QueueEntry(uid, song, replayGain). |
| Shuffle | QueueManager shuffle list; generateShuffleQueue (:506) keeps the current item first | `ExoPlayer.setShuffleOrder` with a custom S2ShuffleOrder. DefaultShuffleOrder(int[], seed) exists ([playlists doc](https://developer.android.com/media/media3/exoplayer/playlists)). | S2ShuffleOrder: cloneAndInsert appends (DefaultShuffleOrder inserts at random positions, [source](https://github.com/androidx/media/blob/1.11.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/source/ShuffleOrder.java)). cloneAndMove remaps indices, since the default is a no-op. cloneAndSet puts the start item first. A move in the shuffled view means building a new order and calling setShuffleOrder. |
| Shuffle with duplicate songs | inOrderOf occurrence matching (QueueManager.kt:217) | Shuffle order = permutation of playlist indices, so duplicates are distinct indices | Restore maps shuffle_queue_ids to indices occurrence by occurrence (the same algorithm) |
| Repeat | QueueManager.RepeatMode, getNext (:292) | Player.setRepeatMode OFF/ONE/ALL | none |
| Gapless | LoadCoordinator + loadNext, dropping played items (88a9e303) | Automatic between playlist items | none; LoadCoordinator is deleted |
| Resolving remote URIs | suspend MediaResolver (exoplayer/MediaResolver.kt), Jellyfin auth (JellyfinMediaInfoProvider.getMediaInfo) | Playlist items keep their jellyfin://, emby://, plex:// URIs. A ResolvingDataSource.Factory resolves them when opened (on the loader thread; runBlocking there is acceptable). | The resolver itself. StreamSniffingMediaSourceFactory is kept. |
| Downloaded or fallback URIs | AggregateMediaInfoProvider (mediaprovider/core MediaInfoProvider.kt:48-66). DownloadFallbackObserver belongs to downloads, not playback. | Same resolver inside the ResolvingDataSource | unchanged |
| ReplayGain per track | ReplayGainAudioProcessor reads the MediaItem tag in onFlush(StreamMetadata) (dsp/replaygain/ReplayGainAudioProcessor.kt:91-96) | Already Media3 AudioProcessor + StreamMetadata; works as is on a full playlist | Keep it. The tag must survive, so add items through the in-process facade and not through MediaController. |
| EQ | EqualizerAudioProcessor in DefaultAudioSink (exoplayer/ExoAudioPlayer.kt:35-45) | AudioProcessor, unchanged | Keep ExoPlayerFactory's sink setup |
| System audio effects session | AudioEffectSessionManager | Player.Listener.onAudioSessionIdChanged + ExoPlayer.setAudioSessionId | Done (step 3): AudioEffectSessionManager stays as the thin listener. It follows the local ExoPlayer's onAudioSessionIdChanged and the active player (no session while casting) and broadcasts the open/close intents system EQ apps need, which Media3 doesn't send. |
| USB DAC bit-perfect | BitPerfectOutput + AudioTrackMonitor; reopenAudioTrack uses a ±1ms seek trick (ExoAudioPlayer.kt:90) | ExoPlayer.setPreferredAudioDevice + AnalyticsListener.onAudioTrackInitialized. Media3 has no AudioMixerAttributes API (androidx/media #415, status unverified). | BitPerfectOutput stays, as a Player/AnalyticsListener with no queue knowledge |
| Audio focus + ducking | AudioFocusHelper* (audiofocus/AudioFocusHelperBase.kt:36-90) | ExoPlayer.setAudioAttributes(attrs, handleAudioFocus = true) | Done (step 3). Media3 keeps focus through a pause, as Android asks, and gives it up on a permanent loss or when the player stops; S2 follows it (RS-04) and stops the player when the queue empties (RS-05). A user play during a phone call waits for the call to end (RS-54). |
| Becoming noisy | NoiseManager | ExoPlayer.setHandleAudioBecomingNoisy(true) | Done (step 3) |
| Sleep timer | SleepTimer on trackEndedFlow | Timed mode: pause after a delay. Play-to-end mode: ExoPlayer.setPauseAtEndOfMediaItems(true) once the deadline passes. | Small class |
| Playback speed (trial slows it) | TrialInitializer.kt:42 → setPlaybackSpeed | Player.setPlaybackParameters; RemoteCastPlayer caps speed at 2.0 | none |
| Position, progress and anchor | progressFlow, positionAnchorFlow, ProgressTicker | Player.currentPosition + isPlaying + playbackParameters; controllers get position from the session | Facade ticker that derives progressFlow and positionAnchorFlow from the Player |
| trackEnded / pausePosition / failure events | PlaybackManager SharedFlows | onMediaItemTransition(AUTO or REPEAT), onIsPlayingChanged, onPlayerError | Facade maps them to the same flows |
| Queue persistence and restore | PlaybackInitializer + prefs queue_ids, shuffle_queue_ids, queue_position, playback_position, shuffle and repeat (persistence/PlaybackPreferenceManager.kt:25-90) | Player.Listener onTimelineChanged / onMediaItemTransition / onShuffle / onRepeat triggers saves. Restore via setMediaItems + setShuffleOrder. MediaSession.Callback.onPlaybackResumption for system resume ([background playback doc](https://developer.android.com/media/media3/session/background-playback)). | QueueStore: same keys and format. Keep the content-version race guard (PlaybackInitializer.kt:85-178). |
| Tag-edit refresh | updateQueueSongs (TagEditorPresenter.kt:290) | Player.replaceMediaItem, which does not interrupt when only metadata changes | none |
| Media session, Auto, Assistant | MediaSessionManager (play from id, uri or search, shuffle custom action), PlaybackService.onGetRoot/onLoadChildren (:292-307), MediaIdHelper, PackageValidator | MediaLibraryService + MediaLibrarySession.Callback (onGetLibraryRoot, onGetChildren, onSearch, onAddMediaItems, onSetMediaItems). The manifest keeps the android.media.browse.MediaBrowserService action for legacy clients. | Port MediaIdHelper's browse tree. PackageValidator is replaced by ControllerInfo.isTrusted + an allow-list. |
| Notification | PlaybackNotificationManager (312 lines) | MediaSessionService's own notification; buttons via setMediaButtonPreferences | Delete except the shuffle/repeat CommandButtons |
| Widget | Glance NowPlayingWidget → actionStartService with custom actions (app/.../widgets/NowPlayingWidget.kt:692, PlaybackService.kt:211-216). State from WidgetManager flows. | 1.10 adds widget PendingIntent builders ([release notes](https://raw.githubusercontent.com/androidx/media/1.11.0/RELEASENOTES.md)) | Keep the custom actions via onStartCommand in the new service (call super). WidgetManager keeps reading the facade flows. |
| Files opened by intent | MainActivity.kt:143-145 → playFromUri; OpenedAudio, UriSongResolver | Facade.playUri → resolve with UriSongResolver → setMediaItems | UriSongResolver stays |
| Cast | CastSessionManager, CastPlayback, CastPositionTracker, HttpServer on NanoHTTPD port 5000 (chromecast/HttpServer.kt:11) | media3-cast CastPlayer.Builder().setLocalPlayer(exo) switches between local and remote with a TransferCallback (1.9+, [CastPlayer.java](https://github.com/androidx/media/blob/1.11.0/libraries/cast/src/main/java/androidx/media3/cast/CastPlayer.java), [guide](https://developer.android.com/media/media3/cast/create-castplayer)) | HttpServer stays, and refuses any URL without the session's random key. Custom MediaItemConverter maps every item to http://ip:5000/{key}/songs/{id}/audio: a local file is served, a remote one redirects to its castCompatibilityMode URI, whose content type (HLS when the server transcodes) CastStreams resolves before the item is sent. Custom TransferCallback handles shuffle order and the window (section 3). |
| Playback reporting to servers | PlaybackReporter (ded7f569), only on branch worktree-pr-integ | Consumes facade flows | Unchanged if the facade keeps its flows |
| Debug receivers | app/src/debug/.../DebugPlaybackReceiver.kt (PLAY_ALL, PLAY, PAUSE, NEXT, PREV, SEEK, REMOVE_QUEUE_ITEM, SHUFFLE, REPEAT, DUMP_STATE) | Facade | DUMP_STATE adds Player state |
| Crossfade, scrobbling | Not present | n/a | n/a |

Consumers: 46 non-test files inject PlaybackOperations or QueueOperations. The ones that consume flows: PlaybackPresenter, MiniPlayerPresenter, QueueBinder/QueuePresenter, PlaybackInitializer, SleepTimer, MediaSessionManager, WidgetManager, NowPlayingWidgetState, Shuffle/RepeatButton, ShortcutManager, MainPresenter, and the AlbumDetail/AlbumArtistDetail ViewModels.

## 3. Risks (with evidence)

1. **Cast.** RemoteCastPlayer.setShuffleModeEnabled/getShuffleModeEnabled are TODO no-ops (RemoteCastPlayer.java:837-845 @1.11.0). PlayerTransferState carries only the shuffle flag and base-order items, not the order (PlayerTransferState.java:175-182, setToPlayer :235). The queue goes out in one LOAD with no chunking (RemoteCastPlayer.java:1572-1605; the 1.9 release note says QUEUE_LOAD became LOAD). Whether a large queue hits a Cast message size limit is unverified. DefaultCastPlayerTransferCallback drops items without a URI (isPlayable). Mitigation: a custom TransferCallback sends items in play order, capped to a window, with converted URIs. Going back to local rebuilds from the facade's saved queue rather than from the remote timeline. Cast has no gapless and no local EQ or ReplayGain, which is the same as today.
2. **Large queues.** The session sends the timeline to every controller. androidx/media #57 measured a 10k-item playlist at 1-3 s of main thread per controller, and #94 hit TransactionTooLargeException. Today's legacy session publishes only a 30-item window (MediaSessionManager.kt:413-425). Whether Media3 windows the legacy queue is unverified. A spike with 10k songs measuring the notification, Auto and Bluetooth is needed before step 3 ships. Building 10k MediaItems happens off the main thread; setMediaItems is called on main.
3. **Service lifecycle and threading.** The Player's application looper must be main, and every facade call hops to Main (QueueManager's mutex and IO go away). 1.11 enforces stricter MediaSession threading. The facade drives the Player directly; the service must be bound or started so MediaSessionService foregrounds on play (unverified for a bound-only service; check in a spike). The widget starts a foreground service with custom actions, so the new service must call startForeground or let Media3 do it within the deadline. Auto: legacy MediaBrowser clients work through the manifest action; test on DHU.
4. **Shuffle semantics.** S2 shows and moves items in shuffled order (QueueManager.kt:564 move by mode), and playNext inserts at a chosen shuffle index (:527). Only setShuffleOrder can express these, and it is ExoPlayer-only, not reachable from MediaController. That is the main reason the UI stays on an in-process facade.
5. **Tags lost across the controller boundary.** localConfiguration and tag don't reach controllers (1.8 release note). onAddMediaItems/onSetMediaItems must rebuild items from mediaId for Auto and external requests.
6. **API stability.** The facade keeps PlaybackOperations/QueueOperations. getPlayback()/switchToPlayback() (PlaybackOperations.kt) go away; only CastSessionManager uses them.

### 10k queue spike (player only)

`android/playback/src/test/.../spec/LargeQueueSpikeTest.kt` (`@Ignore`d, run manually) puts a real ExoPlayer
(`TestExoPlayerBuilder`, `FakeClock`) under Robolectric with 10,000 `MediaItem`s (mediaId = song id, a fake
per-item URI, tag = a `Song`-sized object) and times the operations a full-queue playlist needs, median of 5
runs, plus a single 50,000-item run of build + `setMediaItems`/`prepare`:

| Operation | 10k median | 50k median |
|---|---|---|
| Build the `MediaItem`s | 3.0 ms | 5.0 ms |
| `setMediaItems` + `prepare` | 8.1 ms | 39.6 ms |
| `addMediaItem` at the end | 1.8 ms | — |
| `addMediaItem` at index 5,000 | 1.4 ms | — |
| `removeMediaItem` in the middle | 1.0 ms | — |
| `moveMediaItem` (first to last) | 1.0 ms | — |
| `replaceMediaItem` in the middle | 6.0 ms | — |
| `setShuffleOrder`, build + apply | 5.4 ms | — |
| `seekTo(index 9,999)` | 0.3 ms | — |

Heap delta per op stayed at 1-12 MB (dominated by the `MediaItem`/`Song`/URI allocations, not player-internal
structures); the 50k build + `setMediaItems` pair moved ~116 MB. Nothing threw. Nothing showed O(n²) growth:
`setMediaItems`+`prepare` scaled from 8.1 ms at 10k to 39.6 ms at 50k, under 5x for 5x the items. Every
individual op stayed under 16 ms except the 50k `setMediaItems`+`prepare` pair, and only main-thread call
latency was measured — actual media loading happens later on ExoPlayer's own playback thread, off the
Robolectric main looper. One trap surfaced while writing the test: draining the Robolectric main looper
(`shadowOf(Looper.getMainLooper()).idle()`) between iterations, combined with the auto-advancing `FakeClock`
and the fake per-item URIs never resolving, spun forever — an artifact of the test harness, not evidence
about the real player, and the fix (drop the `idle()` calls; playlist state reads back synchronously without
draining the looper) is already in the committed test.

**Conclusion.** Robolectric numbers are indicative of relative cost and scaling, not device-accurate
wall-clock time, but nothing here rules out the whole queue living in ExoPlayer's playlist: building and
editing a 10k-item playlist is single-digit milliseconds per op, well under a frame, and the growth to 50k
items is sub-linear-to-linear rather than quadratic. `setMediaItems` is the one op worth being deliberate
about — build the `MediaItem` list off the main thread (as the risk section above already calls for) and
call `setMediaItems` once with the finished list; there is no sign that chunking it is necessary at these
sizes, but it would be the first mitigation to reach for if a real device run (this spike is JVM/Robolectric
only) shows it over 16 ms.

**From setting the queue to playing.** `.../spec/LargeQueueStartTimingTest.kt` (`@Ignore`d, run manually) times the
whole path, `QueueManager` and `PlaybackManager` over the real player with WAV files, from the queue change until
the state is Playing, one run each after a warm-up. Lazy preparation (production's `ExoPlayer.Builder` default, and
the harness's since #345 step 1a) against every item prepared as it joins the playlist; S2's media sources report a
single window, which lazy preparation needs (`LazyPreparationTest`). Robolectric numbers, ms:

| Scenario | Preparation | 300 | 1k | 2k | 5k | 10k |
|---|---|---|---|---|---|---|
| Set the queue, play | lazy | 20 | 28 | 48 | 97 | 188 |
| | eager | 231 | 3,321 | 14,086 | — | — |
| Restore (middle item, 1 s in), play | lazy | 13 | 15 | 43 | 81 | 158 |
| | eager | 216 | 3,289 | 14,079 | — | — |
| Add all to a playing queue | lazy | 1 | 3 | 5 | 12 | 27 |
| | eager | 0 | 2 | 4 | — | — |
| Shuffle all, play | lazy | 11 | 14 | 32 | 95 | 174 |
| | eager | 209 | 3,288 | 14,025 | — | — |

Lazy grows linearly, about 19 µs an item at 10k. Eager grows with the square of the queue (2x the items, over 4x
the time) and at 5k items runs the test JVM's 512 MB heap out of memory, so it stops at 2k. Adding to a playing
queue is fast either way because the song already playing keeps playing; eager preparation of the added items
happens afterwards, on the playback thread.

**Through the media session (#345 step 2).** `.../spec/LargeQueueSessionSpikeTest.kt` (`@Ignore`d, run manually)
puts the `MediaLibrarySession` the playback service builds over the same stack, with a 10k queue, and measures the
main-thread cost of the session and what a remote controller is sent (androidx/media #57, #94). Robolectric, median
of 5:

| Measure | 10k |
|---|---|
| Set the queue: no session / session / session + a connected controller | 41 / 66 / 49 ms |
| Add one song: no session / session / session + a connected controller | 13 / 36 / 29 ms |
| A controller connecting (in-process) | 1.0 ms |
| Bundling the timeline's windows and periods for a remote controller | 9.0 ms |
| Window bundles, total (one at most 272 B) / period bundles, total | 2,649 KB / 429 KB |
| The timeline bundle itself (the windows go by a binder of their own) | 39 KB |

The session adds about 15 to 25 ms to each change of a 10k queue (runs vary, and a connected controller adds nothing
measurable), whatever the change. That's the platform session's copy of the queue (`MediaSessionLegacyStub`
converts every item to a legacy queue item on each timeline change, for Android Auto's queue view and other legacy
controllers), which Media3 trims to what fits in one binder transaction, so the queue can't overflow one (#94). A
Media3 controller in another process gets the timeline in chunks (`BundleListRetriever`), 3 MB of bundles at 10k
that would never fit one 1 MB transaction, costing the session about 9 ms of bundling per change for each such
controller. Connecting is cheap: the connection carries the timeline by binder, not inline.

**Conclusion.** No mitigation: the cost is per queue change, not per song played (a track change isn't a timeline
change), and a 10k queue changes when the user changes it. The smallest fix, if a device shows a queue edit costing
frames, is to publish no legacy queue (take `COMMAND_GET_TIMELINE` from the platform session's controller), at the
price of Android Auto's queue view; a windowed timeline would break the one-queue model this design rests on.

## 4. Routes compared

**A: refactor in place.** Replace LoadCoordinator with a full playlist inside ExoPlayerPlayback, then fold QueueManager into it, then the session, then Cast. Each step ships, but every intermediate state keeps a Playback interface shaped around one next item and two queue owners. That is exactly the seam where the 44 fixes landed.

**B: rebuild beside.** (Considered, not taken; see the decision below.) New `:android:playback3` with its own MediaLibraryService, CastPlayer and playlist. The app picks the module behind a Hilt-bound flag, and the old module is deleted once the new one reaches parity.

- Must stay compatible: the prefs keys and formats (queue_ids, shuffle_queue_ids, queue_position, playback_position, shuffle/repeat ordinals, EQ, ReplayGain, bit-perfect; PlaybackPreferenceManager.kt), and the PlaybackOperations/QueueOperations surface (as a facade). Service intent actions and names (widget, notification delete intent), the Auto root and media-id scheme (MediaIdHelper), the debug receiver actions, and the manifest service class name. If the class name changes, Auto and widgets pinned to the old component break (unverified), so keep the name or add an alias.
- Keeping the lessons: turn the fix history into a behaviour spec. For each of the 44 commits, write one line of the form "Given / when / then" and tag it RS-nn. Drop the ones that are pure mechanism (LoadCoordinator tokens, loadNext generation, anchoring mid-load: 9105184a, 53a2b6ab, 0176feba, 240058fb, da5cdebd, e35ed66e, b5b8735e, 88a9e303, 9927ba90, 2dbb8904, c53af4ea). The rest are user-visible: duplicate-song shuffle restore (fca45f5b), reload near the end (70038678, 6b80d191), USB mixer on reopen and while paused (9e2c3b84, c8f4df6b), ReplayGain before the first sample and per item (b3490b18, cb76a79f), focus abandoned on pause and on the last item removed (ea250913, d77f516d), add-to-queue order with shuffle on (25f4113b), tag-edit refresh (ba044736), opened-file failure message (33aaad91), HLS transcodes (79a0a26f), 24-bit PCM (4ad26a26), EQ headroom (f8e529c2), Cast discontinuity (b1a2a27d), stop cleanly on failure (e48e3be2). Each surviving RS becomes a media3-test-utils test (#346: TestExoPlayerBuilder, FakeClock, TestPlayerRunHelper) against the facade, run on both modules while the flag exists.

**Decision (owner, 2026-09-24): replace in place, no flag, no second engine.** Take B's target (Media3 owns queue, state, session and Cast) but reach it by refactoring the existing module: each step swaps a responsibility over to Media3 and deletes the code it replaces in the same change. There is never a second engine or a feature flag to maintain. The app-facing PlaybackOperations/QueueOperations surface and the prefs format stay, so the 46 consumers don't change. Main keeps building and playing after every step; the rollout is one release once every step has landed and the device checks pass.

## 5. Migration steps (each deletes what it replaces)

0. **Spec, harness, spike.** #346: real ExoPlayer under test through media3-test-utils. RS list in docs/testing/playback-behaviour-spec.md, each rule a test written against PlaybackOperations/QueueOperations so it survives the swap. Spike: 10k songs as an ExoPlayer playlist (build cost, setMediaItems on main, memory). Verify: `./support/scripts/unit-test playback`.
1. **The Media3 player owns queue, state and Cast.** ExoPlayer holds the full queue (S2ShuffleOrder, ResolvingDataSource resolver, existing EQ/ReplayGain processors). CastPlayer(local = exo) with the HttpServer MediaItemConverter and a TransferCallback that sends play order, capped to a window. PlaybackOperations/QueueOperations become a thin layer over the Player, with flows derived from Player events. QueueStore saves and restores on Player events in the existing format. The existing MediaSessionManager and notification read the new layer's flows until step 2. Deletes: LoadCoordinator, the Playback interface, ExoPlayerPlayback, AudioPlayer/ExoAudioPlayer (sink setup moves to the player factory), PlaybackSwitcher, CastPlayback, CastPositionTracker, ProgressTicker and the anchor plumbing, QueueManager's lists and its mutex. Lands as several commits; goes to main only when local playback and Cast both work. Verify: RS suite, Maestro playback flows, the Cast checklist.
2. **Session.** MediaLibraryService (same class name) replaces MediaBrowserServiceCompat; a library callback ports MediaIdHelper; media button preferences for shuffle and repeat; onStartCommand keeps the widget actions; onPlaybackResumption. Session queue measured with the 10k queue. Deletes: MediaSessionManager, MediaSessionPlaybackState, PlaybackNotificationManager, PackageValidator, the old service body. Verify: DHU Auto browse and play, widget, lock screen, Bluetooth headset (device-checks.md), Maestro.
3. **Platform concerns.** ExoPlayer handles audio focus and becoming noisy. SleepTimer, BitPerfectOutput, trial speed and PlaybackReporter listen to the Player. Deletes: AudioFocusHelper*, NoiseManager, AudioEffectSessionManager, and whatever is left of PlaybackManager and QueueManager. Supersedes #250 and #327. Verify: RS focus and USB tests, USB DAC device checks.
   *Status:* focus, becoming noisy and the effects session landed. AudioFocusHelper* and NoiseManager are deleted; S2's focus behaviour is Media3's (RS-50 to RS-53), with one change: pausing keeps focus, as Android asks, where S2 used to give it up (RS-04). Media3 takes the delayed focus a phone call gives as focus, so PlaybackManager holds a play during a call itself, through CallMonitor, until the call ends (RS-54). SleepTimer, trial speed and PlaybackReporter already listen to PlaybackOperations' flows, which derive from the Player; BitPerfectOutput listens to the AudioTrack through AudioTrackMonitor. PlaybackManager and QueueManager stay for now: what's left in them isn't forwarding. PlaybackManager keeps load completion, failure skipping, the saved resume position, the near-end restart on previous, the progress ticker and the Cast switch; QueueManager keeps S2ShuffleOrder, building entries off the main thread, the queue-restore content-version guard and the app-wide shuffle and repeat enums. Collapsing them further means moving that logic, not deleting it.
4. **Rollout.** Full unit suite, smoke group, Maestro, the device-checks batch, then one release.

## 6. End state

- Queue, current item, shuffle order, repeat, play/pause, position, speed, errors: the Player (CastPlayer wrapping ExoPlayer) is the only source.
- Session, notification, Auto, external controllers: the MediaLibrarySession on that Player.
- Persisted queue: QueueStore mirrors Player events into the existing prefs.
- Left custom: the in-process facade (flows plus the Ops API), S2ShuffleOrder, the URI resolver, the ReplayGain and EQ processors, BitPerfectOutput, HttpServer with the Cast converter and transfer callback, SleepTimer, and the library browse tree.
- LoadCoordinator is deleted. PlaybackManager and QueueManager remain as the facade (see step 3's status).

## Open questions and unverified points

- Everything marked (unverified) above. In particular: Cast message limits with large queues; whether Media3 windows the legacy session queue; foregrounding when the Player is driven directly; whether focus is abandoned on pause; and Auto and widget behaviour if the service class name changes.
- Owner decisions (2026-09-24): the UI keeps an in-process layer over the Player, not MediaController; casting with shuffle on sends the queue flattened into play order; the Cast queue is capped to a window if the spike shows a limit; no flag and no parallel engine; playback reporting (ded7f569, branch worktree-pr-integ) lands before step 1.
- Owner decision (2026-09-25): keep Media3's default foreground timeout for a paused service (10 minutes). The notification stays pinned that long, and the service stops when the app is swiped from Recents, replacing the old 15 s self-stop.
