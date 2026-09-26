# Scrobbling

Status: slice 1 built 2026-09-27 ([#503](https://github.com/timusus/Shuttle2/issues/503)). The design is the two spike comments on #503; the owner's
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

## Slices

Each lands with JVM tests, verified by `unit-test --changed` and `assembleDebug`.

1. **Module and `ScrobblePlanner`** (this document). Done.
2. **Queue and flush worker.** A Room table in the module's own `scrobbles.db` (one row per
   service; unique on service, startedAt, track; capped, oldest dropped), flushed by a unique
   WorkManager job on `NetworkType.CONNECTED`, oldest first, 50 per call for Last.fm and up to 1000
   for ListenBrainz, retrying or dropping per error code.
3. **ListenBrainz client and settings screen.** Token entry validated with `/1/validate-token`,
   `submit-listens` payloads, 401 signs out, 429 honours `X-RateLimit-Reset-In`.
4. **`ScrobblingInitializer`** in `:android:app/appinitializers`, beside
   `PlaybackReportingInitializer`: feeds the flows above to the planner and the decisions to the
   clients and queue, and supplies `isServerSong`.
5. **Last.fm**: the key and secret from `BuildConfig` (hidden without them), browser sign-in, signed
   calls, "powered by AudioScrobbler" attribution.
