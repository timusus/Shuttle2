# Phase 6: iOS playback (#588)

Design for playback on iOS that shares as much as possible with Android. Parent plan:
[`../ios-port.md`](../ios-port.md). Depends on phase 2 (prefs behind shared interfaces), phase 3
(`MediaInfoProvider`/stream URLs in commonMain on Ktor) and phase 4 (shared Player/Queue ViewModels).

## Summary

- **Engine**: `AVAudioEngine` + one `AVAudioPlayerNode`, fed PCM we decode ourselves with FFmpeg
  (the model Shuttle Podcasts already ships), extended to schedule the next track's buffers
  back to back for sample-exact gapless. Not `AVQueuePlayer`.
- **Podcasts' `Playback` package**: copy its lower layers (byte source, FFmpeg pull decoder, stall
  detection, loopback test server, `Biquad`/limiter) into an S2 package; write a new multi-item
  controller. Do not depend on the package: it is single-item and coupled to podcast concerns.
- **Kotlin owns policy**: the queue model, shuffle order, repeat, skip-failed, persistence, sleep
  timer, ReplayGain dB and EQ coefficients are Kotlin (commonMain, JVM-testable). Swift implements a
  thin `IosAudioPlayer` of primitives and never sees the queue beyond "current" and "next".

## 1. What Android does outside Media3, and where each goes on iOS

Contracts already in `android/domain/src/commonMain/kotlin/com/simplecityapps/playback/`:
`PlaybackOperations`, `QueueOperations`, `PlaybackState`, `PlaybackProgress`, `SongPosition`,
`queue/{QueueItem,QueueState,ShuffleMode,RepeatMode}`, `equalizer/EqualizerFrequencyResponse`. The iOS
controller implements the two interfaces unchanged, so every shared ViewModel works as-is.

Android's queue *is* the Media3 playlist (`queue/Playlist.kt`, `PlaylistEditor`, `QueueStatePublisher`
derive everything from `Player` events), and per `media3-playback-design.md` that stays. iOS has no
playlist-owning player, so it needs a pure-Kotlin queue model. Paths below are under
`android/playback/src/main/java/com/simplecityapps/playback/`.

| Behaviour | Android today | iOS v1 |
|---|---|---|
| Shuffle order that never reshuffles on edit | `engine/S2ShuffleOrder.kt` (pure IntArray logic behind Media3 `ShuffleOrder`) | **Share**: move the IntArray algorithm to commonMain `ShuffleOrderCore`; Android's `S2ShuffleOrder` wraps it |
| Queue model, `QueueState` version counters | Media3 timeline + `QueueStatePublisher` | **Share (new)**: commonMain `QueueModel` (entries with uid, order, current, repeat, next/prev, edits, version bumps); used by iOS |
| Queue/modes/position persistence + restore | `persistence/QueueStore.kt` (Media3 listener) over `PlaybackPreferenceManager` (SharedPreferences: `queueIds`, `shuffleQueueIds`, `queuePosition`, `playbackPosition`, modes) | **Share** the keys, the id-list format, restore-from-ids and `startOf(song)` (audiobook/podcast resume rewind) in commonMain; iOS saves from `QueueModel` changes. Listener stays Android |
| Skip failed items | `ItemLoader.kt` | **Re-implement** in the controller (on `onItemFailed`: emit `playbackFailureFlow`, advance; stop after a full lap of failures). Small shared policy function |
| Progress ticks | `ProgressTicker.kt` (100 ms) | **Re-implement**: coroutine on Main polls `currentPositionMs()` while playing |
| Sleep timer | `sleeptimer/SleepTimer.kt` (only `SystemClock` + `PlaybackOperations`) | **Share**: commonMain with `TimeSource.Monotonic` |
| Playback speed persistence | `PlaybackSpeedStore.kt` (Media3) | **Re-implement** (5 lines) over the shared prefs key |
| ReplayGain | `dsp/replaygain/ReplayGain.kt` (pure `replayGainDb`) + `ReplayGainAudioProcessor` | **Share** the dB rule and settings; Swift applies the gain (positive allowed, limiter after) |
| 10-band EQ | `dsp/equalizer/{Equalizer,EqualizerBand,BandProcessor,FrequencyResponse,DefaultEqualizerFrequencyResponse,Utils}.kt` (Orfanidis peaking biquads, headroom attenuation) + `exoplayer/EqualizerAudioProcessor.kt` | **Share** presets, band maths and coefficient computation in commonMain (drop `@StringRes`, Moshi, `java.io.Serializable`, Timber); Swift runs the cascade (vDSP biquad). Plotted curve == applied curve, unlike an `AVAudioUnitEQ` approximation |
| Audiobook seek buttons | `app/.../ui/shell/player/PlayerSeek.kt` constants, `QueueStore.startOf` | **Share** (constants move with the Now Playing VM in phase 4); remote commands switch on song type |
| Play count / resume position | `app/.../appinitializers/PlaybackInitializer.kt` (`saveSongPosition`, `recordPlayedThrough`) | **Share**: commonMain `PlaybackHistoryRecorder` collecting the domain flows |
| Queued song metadata refresh | `queue/QueueSongRefresher.kt` (pure) | **Share** as is |
| Server playback reporting | `app/.../playbackreporting/{PlaybackReportPlanner,PendingPlays,PlaybackReportSender}.kt` | **Share** after phase 3 (planner is pure; `PendingPlays` moves to shared prefs). Wire in step 9 |
| Scrobbling | `:android:scrobbling` (`ScrobblePlanner` pure) | **Out of scope v1**; it consumes only domain flows, so it works once the module is KMP |
| Stream URL resolution | `engine/SongUriResolver.kt` (lazy, on loader thread) + `exoplayer/MediaResolver.kt` → `MediaInfoProvider` | **Share** `MediaResolver`; iOS resolves in Kotlin before handing a URL to Swift, and resolves *next* ahead for gapless |
| Crossfade | `dsp/crossfade/*` | **Out of scope v1** (the engine choice keeps it possible) |
| Cast handover | `CastHandover.kt`, `chromecast/*` | **Out of scope** (phase 9). AirPlay comes free with the audio session |
| Call hold, wake mode, foreground starts, bit-perfect, audio effect session, Android Auto, voice search | various | **Android only**; iOS equivalents are audio-session interruptions, background mode, CarPlay (phase 9) |

New module: a KMP `:android:playback:core` (commonMain + commonTest) that `:android:playback` depends
on, holding the shared rows above. Android keeps Media3 as the source of truth; `QueueModel` is only
used by the iOS controller, but lives in commonMain so its tests run on the JVM.

## 2. Shuttle Podcasts' iOS playback

Paths under `~/projects/simplecity-apps/podcasts/main/mobile/`.

- `shared/src/iosMain/.../platform/IosAudioPlayer.kt`: nine non-suspending primitive calls
  (`load(url, episodeId, positionMs, playWhenReady)`, play/pause/seek, position/duration, speed,
  volume). One item at a time.
- `shared/src/iosMain/.../platform/IosPlayerController.kt`: implements Podcasts' `PlaybackController`/
  `PlayerQueueController` over an in-memory list and index; stages the current item into Swift.
  Pattern worth copying (staging, pending resume position, Swift-reported seeks via `onPlayerSeek`),
  interfaces are not (different domain).
- `ios/ShuttlePodcasts/Platform/Audio/AVAudioEnginePlaybackController+IosAudioPlayer.swift`: a separate
  bridge object; every call hops to the main actor (`DispatchQueue.main.sync` when off main) so
  Kotlin's ordering holds. Copy this pattern verbatim.
- `ios/Playback/` (iOS 17, depends on `ios/Spine` for `SpineNative`): `AVAudioEnginePlaybackController`
  (2.4k lines, single episode; chain `reader -> SilenceGate -> VoiceEnhance -> SkipCueMixer ->
  playerNode -> mixer -> timePitch -> fadeMixer -> main`), `StreamingPCMReader` (FFmpeg pull decode,
  `file://` or HTTP, auth headers), `Streaming/HTTPRangeByteSource` (range reads, read-ahead),
  `CachedRunStore`, `ResolvedURLCache`, `ClockStallDetector`, `PlayerStallRecovery`, `DSP/{Biquad,
  LookaheadLimiter,LufsMeter}`, `PlaybackTestSupport/LoopbackMediaServer`; interruption and route
  change handling live in the controller.
- FFmpeg (Podcasts' `ios/scripts/build-ffmpeg.sh`, static LGPL xcframework; S2's is dynamic) is built with only
  `mp3,aac` decoders and `mp3,aac,mov` demuxers, no network protocols; HLS is a load error.
- `RemoteCommandHandler.swift` / `NowPlayingInfoManager.swift`: play/pause/toggle, skip ±interval,
  change position, change rate; rate/elapsed written on state change, not per tick.

**Reusable directly (copy into S2 `ios/Playback`)**: `HTTPRangeByteSource`, `StreamingPCMReader` and
`CStreamDecode`, `ResolvedURLCache`, `ClockStallDetector`, `PlayerStallRecovery`, `Biquad`,
`LookaheadLimiter`, `LoopbackMediaServer` + fixtures, the build-ffmpeg script, the bridge's main-hop
helper, the session/interruption/route handling, and `RemoteCommandHandler`/`NowPlayingInfoManager`
as starting points.

**Not reusable**: the controller itself (single item, prepare/play per URL, podcast stages),
`SilenceGate`, `VoiceEnhanceProcessor`, `SkipCueMixer`, the `AudioByteTee`/Spine skip layer.

**What music needs that Podcasts lacks**: next-item preload and gapless scheduling; encoder
delay/padding trimming (FFmpeg `skip_samples`/`AV_PKT_DATA_SKIP_SAMPLES`, iTunSMPB, LAME header);
FLAC/ALAC/Opus/Vorbis/WAV/AIFF decoders and `flac,ogg,matroska,wav,aiff` demuxers in the FFmpeg
build; per-track ReplayGain with limiter; 10-band EQ; large queues (Kotlin side only; Swift sees two
items); transcode fallback without HLS (below); security-scoped local files (phase 8: resolve the
bookmark and `startAccessingSecurityScopedResource` in the Swift resolver; the reader already
takes `file://`). Auth headers are already supported, and S2's providers put tokens in the query
(`ApiKey=` Jellyfin/Emby, `X-Plex-Token=` Plex), so headers are optional.

**Why not depend on the package**: cross-repo path dependency on a package whose public API is
shaped for podcasts, iOS 17 pin, and Spine coupling. Follow-up (file on start): extract the byte
source + decoder into a shared `AudioCore` SPM repo both apps consume, once S2's copy has settled.

## 3. Engine choice

| | AVQueuePlayer | AVAudioEngine + own decode (recommended) | SFBAudioEngine |
|---|---|---|---|
| Gapless | Close for AAC/ALAC with iTunSMPB; ms gaps reported for MP3 [1][2] | Sample-exact: consecutive buffers on one node, trimming under our control | Yes (`SFBAudioPlayerNode`) [4] |
| 10-band EQ | Only via `MTAudioProcessingTap`; works on files and progressive HTTP, **not HLS** [3] | PCM path (shared coefficients) | Via graph customisation [4] |
| ReplayGain > 0 dB | `audioMix` volume caps at 1.0; needs the tap | Yes, plus limiter | Yes |
| Streaming | Native HTTP + HLS | Own HTTP range source (Podcasts); no HLS | **File URLs only** [4] |
| Formats | No Vorbis/Ogg, Opus only in CAF/MP4 | Whatever FFmpeg is built with | Broad |
| Crossfade later | Hard | Natural | Possible |

AVQueuePlayer's one real advantage is HLS, which S2 only uses for transcodes. The engine path gives
parity with Android's EQ, ReplayGain and gapless, and Podcasts has already paid for the hard
streaming/decoding parts. SFBAudioEngine cannot stream.

**Transcoding without HLS**: the provider URL builders (`JellyfinAuthenticationManager.buildJellyfinPath`
uses `TranscodingProtocol=hls`, `TranscodingContainer=ts`; Emby the same; Plex
`transcode/universal/start.m3u8`) take a per-platform `StreamProfile` in commonMain: containers the
platform decodes (iOS: the FFmpeg build's list, replacing `DirectPlayFormats.UNIVERSAL_CONTAINERS`)
and the transcode protocol (`http` progressive, container `mp3` or `aac`/`adts`). Seeking a
progressive transcode is not range-seekable: restart the request with `StartTimeTicks` (Jellyfin/Emby)
or `offset` (Plex) and tell the reader its base position.

**Graph**: `playerNode -> timePitch -> mainMixer -> output`, one fixed format (float32 stereo at the
session's output rate). FFmpeg `swresample` converts every track into it, so tracks of different
rates never reconnect the node and gapless is just "keep scheduling". In the PCM path, per buffer:
ReplayGain gain -> EQ cascade (with preamp/headroom from Kotlin) -> `LookaheadLimiter` -> volume. The
render thread never calls Kotlin.

## 4. The Kotlin/Swift contract

```kotlin
// commonMain (plain Kotlin, so the controller is JVM-testable); Swift implements it.
class AudioItem(val uid: Long, val url: String, val headers: Map<String, String>,
                val gainDb: Float, val startOffsetMs: Long, val isTranscode: Boolean)
interface IosAudioPlayer {
    fun setListener(listener: IosAudioPlayerListener)
    fun load(item: AudioItem, positionMs: Long, playWhenReady: Boolean)   // replaces current, drops next
    fun setNext(item: AudioItem?)                                          // gapless preload; null clears
    fun play(); fun pause(); fun seek(positionMs: Long)
    fun currentPositionMs(): Long; fun currentDurationMs(): Long
    fun setSpeed(speed: Float); fun setVolume(volume: Float)
    fun setEqualizer(enabled: Boolean, coefficients: DoubleArray, preampGain: Float) // 5 per band
    fun outputSampleRate(): Int
}
interface IosAudioPlayerListener {                        // Swift calls these on the main thread
    fun onStateChanged(state: Int)                        // loading / playing / paused / ended
    fun onTransition(fromUid: Long, toUid: Long)          // gapless handover at the sample boundary
    fun onFailed(uid: Long, message: String)
    fun onUserSeek(positionMs: Long)                      // lock screen / CarPlay seeks
    fun onOutputRouteChanged(sampleRate: Int)             // recompute EQ coefficients
}
```

- **`EnginePlayerController`** (commonMain; the iOS graph binds it) implements `PlaybackOperations`
  and `QueueOperations` over `QueueModel` + `IosAudioPlayer`. After every queue/mode change it
  recomputes `next = queue.getNext()` and calls `setNext` if it changed. On `onTransition` it moves
  the current index without reloading, emits `trackEndedFlow` for the old song, persists, and sends
  the new next. Repeat One sets next to the same song with a fresh generation.
- **Threading**: the controller is main-thread confined (the interfaces' documented rule; on iOS
  `Dispatchers.Main` is the main queue). Suspend functions build/resolve on `Dispatchers.Default`,
  apply on Main. The Swift bridge hops to the main actor as Podcasts' does; never `main.sync` from a
  Kotlin call already on main.
- **Swift observing Kotlin**: SKIE flows (`queueStateFlow`, `playbackStateFlow`, `progressFlow`)
  drive SwiftUI and `NowPlayingInfoManager`.
- **Persistence**: shared queue store writes through the phase 2 prefs interface on each change,
  on pause, every ~10 s while playing, and when Swift reports `scenePhase == .background`.
  Restore at graph creation, staged paused (Podcasts' "don't invent a position" rule).
- **Session (Swift)**: `UIBackgroundModes: audio`; `.playback`, mode `.default`, route sharing policy
  `.longFormAudio` (AirPlay 2). Interruption began: pause; ended with `.shouldResume`: resume.
  Route change `oldDeviceUnavailable`: pause. `mediaServicesWereReset` and
  `AVAudioEngineConfigurationChange`: rebuild the graph, reload current at its position.
- **Now Playing / remote commands (Swift)**: title/artist/album/artwork/duration from the current
  `QueueItem`; elapsed + rate only on state change and seek. Commands: play, pause, toggle,
  nextTrack, previousTrack, changePlaybackPosition, changeShuffleMode, changeRepeatMode, and
  skipForward/Backward instead of next/previous when the song is an audiobook or podcast.
- **CarPlay (phase 9)**: `CPNowPlayingTemplate` rides the same command centre and info center; the
  browse tree comes from shared repositories. Nothing in this design blocks it.

## 5. Implementation steps (one worker each; land and review between)

1. **`:android:playback:core` KMP module**: move `S2ShuffleOrder` core, ReplayGain rule, EQ presets and
   maths, `SleepTimer`, `QueueSongRefresher`, `QueueStore.startOf`/id-list codec, playback history
   recorder; Android wraps them. Verify: `unit-test --changed`, `assembleDebug`,
   `compileKotlinIosSimulatorArm64`. Tier standard.
2. **`StreamProfile` in the providers** (needs phase 3): per-platform containers and progressive
   transcode; `StartTimeTicks`/`offset` seek URLs. Verify: MockEngine URL tests, Android unchanged.
3. **`QueueModel` in commonMain** with a contract test suite (`QueueOperationsContract` in a test
   fixtures source set) covering set/add/playNext/move/remove, shuffle-without-reshuffle, repeat,
   version counters, `isRestored`. Run the same suite against Android's `QueueFacade` in its
   Robolectric tests to pin parity. Tier hard.
4. **`EnginePlayerController`** over a `FakeIosAudioPlayer`: gapless next bookkeeping, skip-failed,
   progress ticks, persistence/restore, speed, sleep timer. commonTest only. Tier hard.
5. **S2 `ios/Playback` package, decode layer**: copy the Podcasts files listed in section 2, FFmpeg
   rebuilt with music codecs/demuxers. Verify: `xcodebuild test` on the package, one fixture per
   format, range seek, encoder-delay trim (exact sample counts). Tier standard.
6. **Gapless engine**: `MusicEngine` (current + next readers, one node, gain/EQ/limiter, timePitch).
   Tests run with `enableManualRenderingMode(.offline)`, which renders without an output device or
   a running clock (Podcasts' tests hit both limits): assert no gap and no overlap at the boundary,
   transition reported at the right frame, EQ coefficients applied. Tier hard.
7. **App wiring**: bridge object, `IosAppGraph` binding, audio session, interruptions/routes,
   Now Playing, remote commands. Verify: simulator build, ViewInspector where UI exists.
8. **UI**: mini player (`safeAreaInset`), Now Playing, queue (reorder/remove) on the shared VMs.
9. **Server playback reporting + history** wired to the shared planner.
10. **Checkpoint**: one simulator run with a Maestro iOS flow against a test Jellyfin (play album,
    next, shuffle, seek, remove, relaunch restores queue and position); audible gapless, lock screen,
    interruption and AirPlay go to `docs/testing/device-checks.md`.

### Status of step 7 (app wiring)

Done, in `ios/S2/Platform/Audio/` and `ios/S2/KMP/AppGraph.swift`:

- `EngineAudioPlayer` is the Swift `IosAudioPlayer`: one engine call per method, each engine report
  forwarded to the Kotlin listener synchronously on the main queue. The engine is behind an
  `AudioEngine` protocol (`MusicPlaybackController+AudioEngine.swift`), so the adapter's tests run on
  a fake. Kotlin (`IosPlayerController`) keeps all policy: it feeds the next track after each
  transition and skips a track that fails to load.
- `PlaybackSystemCoordinator` owns the wiring. `AudioSessionController` interruptions and routes
  pause or resume the controller, a media-services reset rebuilds the engine and reloads, and the
  session is activated before any play. `NowPlayingController` follows the queue, state, progress and
  speed flows, and its remote commands call `PlaybackOperations` (next ignores repeat, previous
  unforced, ±30/10 s skips for non-music songs, as on Android).
- `AppGraph.initialize()` (from `S2App.init`) builds the engine, the adapter, `IosAppGraph(audioPlayer:)`
  and the coordinator once.

FFmpeg ships (#588). `ios/scripts/build-ffmpeg.sh` builds n7.1.5 as four dynamic LGPL frameworks,
cached in `~/Library/Caches/s2-ffmpeg-ios` and installed into `ios/Playback/Frameworks`. Xcode
embeds them in `S2.app/Frameworks`, and the app's Settings bundle carries the LGPL notice and relink
note (`ios/Playback/README.md`, "FFmpeg build" and "LGPL notes"). The package requires FFmpeg; there
is no longer a build without it. Its tests run with nothing skipped, and `MusicPlaybackFormatsTests`
plays each format through the controller: MP3, AAC and ALAC in MP4, FLAC, Opus, Vorbis, WAV and AIFF.
`ios/scripts/test.sh` runs the S2 scheme on an available simulator; `--package` runs `swift test`.

Still open (#588): stream
resolution is a placeholder (a song's path as its URL, so the demo library's `demo://` songs fail and
are skipped); artwork waits for a shared image loader; the output sample rate has no consumer until
the EQ is shared; queue persistence is not wired.

## Risks

1. **Engine effort and device-only truth**: the multi-item controller is new code in the part
   Podcasts found hardest (seek races, stalls, format changes, interruption recovery). The simulator
   clock does not advance, so manual-rendering tests carry the load and audible checks wait for a
   device.
2. **Transcode fallback without HLS**: progressive transcodes differ per server (Plex especially), are
   not range-seekable, and report no duration up front; seek restarts must reconcile positions.
3. **Queue parity drift**: iOS `QueueModel` vs Android's Media3 playlist semantics; the shared
   contract suite (step 3) is the guard.
4. **FFmpeg LGPL in an App Store build and binary size.** Resolved by linking dynamically: the four
   frameworks are 1.7 MB on device and can be swapped, which meets the LGPL's relink right without
   Podcasts' static-build object-file offer. Still to confirm at submission:
   - whether FFmpeg needs a privacy manifest entry (it isn't on Apple's list of commonly used SDKs);
   - that App Store processing accepts the four frameworks' ad-hoc-then-re-signed bundles.
5. **Kotlin/Native threading**: main-actor hops can deadlock if misused; Kotlin must never run on the
   render thread (GC pauses).

## Sources

1. Apple Developer Forums, "AVQueuePlayer Gapless Playback": https://developer.apple.com/forums/thread/111413
2. StreamingKit wiki, gapless playback and MP3 encoder padding: https://github.com/tumtumtum/StreamingKit/wiki/Gapless-playback
3. Apple Developer Forums, MTAudioProcessingTap with HLS (DTS: not available): https://developer.apple.com/forums/thread/45966 ; EQ for HLS with AVPlayer: https://developer.apple.com/forums/thread/758341
4. SFBAudioEngine README (gapless `SFBAudioPlayerNode`, "Only file URLs are supported"): https://github.com/sbooth/SFBAudioEngine/blob/main/README.md
5. JellyAmp (AVQueuePlayer Jellyfin client) and Finamp iOS gapless issue: https://github.com/satsdisco/JellyAmp , https://github.com/jmshrv/finamp/issues/998
