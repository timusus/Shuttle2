# Scrobbling

Status: slices 1 and 2 built 2026-09-27 ([#503](https://github.com/timusus/Shuttle2/issues/503)). The design is the two spike comments on #503; the owner's
decisions of 2026-09-27 there are binding: the Last.fm shared secret ships in the APK through
`BuildConfig`, Last.fm sign-in is browser approval (auth URL and a callback deep link, never
`auth.getMobileSession`), "Scrobble server streams too" defaults to off, and scrobbling is free for
everyone.

## Module

`:android:scrobbling` is a data-layer module (`buildSrc/.../ModuleLayers.kt`). It depends on
`:android:domain` and never on `:android:playback` or `:android:app`: it sees playback only through
the domain types the initializer hands it. It's an Android library because later slices add the
Room queue, the flush worker and the service clients; the planner in it is pure Kotlin.

## `ScrobblePlanner`

`ScrobblePlanner` turns playback events into decisions and holds the one play of the current item.
It has no Android, no I/O and no coroutines: the clocks come in as functions, so every rule is a
plain JVM test (`ScrobblePlannerTest`).

**Inputs**, which the initializer (slice 4) feeds from the domain flows, in order, on one thread:

| Call | Fed from |
|---|---|
| `onCurrentItemChanged(QueueItem?)` | `QueueOperations.queueStateFlow.currentItem` |
| `onStateChanged(PlaybackState)` | `PlaybackOperations.playbackStateFlow` |
| `onProgress(positionMs)` | `PlaybackOperations.progressFlow` |
| `onPlaybackSpeedChanged(speed)` | `PlaybackOperations.playbackSpeedFlow` |
| `onTrackEnded(Song)` | `PlaybackOperations.trackEndedFlow` |
| `onScrobbleServerStreamsChanged(enabled)` | the "Scrobble server streams too" setting |

Constructor: `isServerSong(Song)` says a song's server already hears of the play (slice 4 passes
`AggregatePlaybackReporter.handles(song) && reportPlaybackToServer`); `elapsedRealtimeMs()` times
listening (`SystemClock.elapsedRealtime`); `currentTimeMs()` stamps a play's start (wall clock).

**Outputs**: each call returns at most one `Decision`.

- `NowPlaying(song)`: a play started. Fire and forget; never queued.
- `Scrobble(song, startedAtEpochSec)`: the play counts. `startedAtEpochSec` is the wall-clock
  start of the play, which is what `track.scrobble` and ListenBrainz's `listened_at` take.

**Rules**

- A play starts when the current item is playing, never for one that is only loaded (a restored
  queue). Pausing and resuming is the same play.
- Listened time is the sum of the position's forward moves, each counted only if it matches the
  time played since the last event, scaled by the playback speed, to within 2 s. Anything else is a
  seek or a late tick and doesn't count. Track time is what counts, so a 2x listen covers twice the
  song, and coarse ticks (Cast) count as long as they match the time played.
- A song longer than 30 s is scrobbled once listened time reaches half its duration or 4 minutes,
  whichever is less; once per play.
- A new current item (a new queue item uid) is a new play. The same item after it played through
  (repeat one, or played again after the queue ended) is a new play once its position has gone
  back and then moved on: the progress republished on an automatic transition can arrive before
  the new current item, and must not replay the item that ended. A seek back mid-song is the same
  play, so it can't scrobble twice.
- Thresholds use `Song.duration`, not the clipped item: a crossfade ends the item
  `crossfadeMs` early, and half the song is still reachable. A late tick from the outgoing item
  after the item changes is a seek and doesn't count towards the next.
- A song `isServerSong` marks is skipped while server streams are off. Turning them off drops such
  a play; turning them on mid-song starts a new play there.
- An item whose song data is edited in place (same uid) keeps its play, and the scrobble carries
  the edited song.

**Tolerances.** A seek of under 2 s forward counts as listened; the last tick interval before a
track ends (at most 100 ms, `ProgressTicker.INTERVAL_MS`) isn't counted, since `trackEndedFlow`
carries no position. Neither can move a play across a threshold that matters.

## Last.fm credentials

`android/app/build.gradle.kts` reads `LASTFM_API_KEY` and `LASTFM_SHARED_SECRET` into `BuildConfig`, each from
`local.properties`, else a Gradle property of the same name, else the environment variable of the same name:

```
# local.properties
LASTFM_API_KEY=...
LASTFM_SHARED_SECRET=...
```

Register an API account at <https://www.last.fm/api/account/create> to get both. A build without them has empty
values: `LastFmAccountState.Unavailable`, and Settings hides the Scrobbling row.

## Slices

Each lands with JVM tests, verified by `unit-test --changed` and `assembleDebug`.

1. **Module and `ScrobblePlanner`** (this document). Done.
2. **Queue and flush worker.** Done. A Room table in the module's own `scrobbles.db` (version 1,
   `QueuedScrobbleEntity`; one row per service, unique on service, startedAt, track), capped at
   `ScrobbleQueue.MAX_QUEUE_SIZE` (5,000 rows, oldest dropped on enqueue via
   `ScrobbleDao.trimToNewest`). `ScrobbleFlushWorker` (a unique WorkManager job,
   `NetworkType.CONNECTED`, exponential backoff) drains it oldest first, 50 per `track.scrobble`
   call for Last.fm. It deletes accepted rows and rows Last.fm permanently rejects
   (`ignoredMessage.code != "0"`), leaves rows alone and asks WorkManager to retry on a transient
   error code or HTTP failure, drops rows on an unrecoverable error code, and drops rows older than
   14 days before sending anything. An invalid session (error code 9) signs the user out via
   `LastFmSessionStore` rather than retrying forever. Building the worker pulled the Last.fm signer
   and API client (`LastFmSigner`, `LastFmApi`, `LastFmScrobbleResponse`) forward from slice 5,
   since the worker needs something concrete to call; slice 5's remaining scope is the browser
   sign-in flow and "powered by AudioScrobbler" attribution UI. ListenBrainz's up-to-1000 batch size
   and the up-to-1000/other-service branching in the worker are deferred to slice 3, since there is
   no ListenBrainz client yet to exercise them. There was no direct-send path to remove: slice 1's
   `ScrobblePlanner` has no production caller yet (that wiring is slice 4), so nothing short-circuits
   the queue.
3. **ListenBrainz client and settings screen.** Token entry validated with `/1/validate-token`,
   `submit-listens` payloads, 401 signs out, 429 honours `X-RateLimit-Reset-In`.
4. **`ScrobblingInitializer`** in `:android:app/appinitializers`, beside
   `PlaybackReportingInitializer`. Done: feeds the flows above to the planner, sends now-playing and queues
   scrobbles through `LastFmScrobbler`, supplies `isServerSong`, and schedules a flush at start-up.
5. **Last.fm sign-in.** Done: `LastFmAuthenticator` fetches a token (`auth.getToken`), the user approves it in
   the browser, and `auth.getSession` trades it for a session kept in `LastFmSessionStore` (encrypted prefs, with
   the pending token so the process can die meanwhile). Settings > Playback & sound > Scrobbling opens last.fm and
   finishes the sign-in when the user returns to the screen (or taps "I've approved it"); there is no callback
   deep link. Signing out also drops the queued scrobbles. The "scrobble server streams too" switch is on the
   same screen. Last.fm error codes are read from 4xx bodies as well as 200s (`LastFmClient`).
