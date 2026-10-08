---
paths:
  - "ios/S2/Platform/**"
  - "ios/Playback/**"
  - "ios/S2Tests/**/*Audio*"
  - "ios/S2Tests/**/*NowPlaying*"
  - "ios/S2Tests/**/*Playback*"
  - "shared/src/iosMain/**"
---

# iOS audio session, Now Playing and the engine (phase 6, #588)

The app owns the session; `S2Playback` never touches `AVAudioSession`. Both controllers are `@MainActor`, take their
system object behind a protocol (`AudioSession`, `NowPlayingInfoCenter`, `RemoteCommandCenter`) and are tested with fakes
and notifications posted on a private centre. Background audio is `UIBackgroundModes: [audio]` in project.yml's `info:`.

- `AudioSessionController`: `.playback` / `.default` / `.longFormAudio`. Interruption: pause; resume on `.shouldResume`
  only if we were playing and nothing paused or played meanwhile (Android's transient focus loss); no `.shouldResume`
  stays paused (permanent loss). `.oldDeviceUnavailable`: pause, never auto-resume (Android's becoming-noisy), but only
  when a personal output (headphones, Bluetooth, USB, line out, car, AirPlay) left and the new route has none of its kind
  (`pausesOnRouteChange`, #715): a Bluetooth profile or codec switch plays on. Asks for an engine rebuild on a
  media-services reset. It ignores the route's sample rate: the engine renders at a fixed 48 kHz, the rate the shared EQ
  is designed at. Route-change notifications arrive off the main thread: read each into
  a Sendable event before hopping to main. `EngineAudioPlayer`'s `onWillPlay`/`onPaused` hooks call `playRequested()`
  before any play and `playbackPaused()` on every pause; the engine's `activateOutput` calls `activate()` off the main
  thread as a play is made, so the session activates while the track opens (#687), and a failed activation refuses the
  play (the engine stays paused and reports it).
- `NowPlayingController`: metadata on `setItem`, elapsed/rate through `updatePlayback`, which only writes on a
  state/speed change, a >1 s jump or every 10 s (safe per tick). Artwork via the `loadArtwork` closure, dropped if the
  item changed. `SkipMode.interval` swaps next/previous for skip ±N s.
- `PlaybackSystemCoordinator` owns both and wires them to the Kotlin `IosPlayerController`
  (`AppGraph.shared.playerController`): session events and remote commands call `PlaybackOperations`; Now Playing follows
  the controller's flows, republished from all four flows' current values whenever any changes (never per-flow copies:
  observer tasks resume in no particular order, #691). `AppGraph.initialize()` (from `S2App.init`) builds the engine,
  `EngineAudioPlayer`, `IosAppGraphKt.createIosAppGraph(audioPlayer:)` and the coordinator once.
- `EngineAudioPlayer` is the Kotlin `IosAudioPlayer`: one engine call per method and no policy (queue, next track and
  failure handling are Kotlin's). **Threading:** Kotlin calls it on main; the engine reports on the main queue and each
  report reaches the Kotlin listener synchronously there; a failure found in the adapter (an unparseable URL) is posted
  to main, never raised inside the Kotlin call. The engine sits behind the `AudioEngine` protocol so S2Tests use
  `FakeAudioEngine`.
- The engine pauses its `AVAudioEngine` output on every paused, idle or ended state: iOS reads a running output (even
  rendering silence) as playing, and the lock screen would show a pause button (#691). A play that can't start
  (`engine.start()` throws) stays paused and reports paused; the node never plays on a stopped engine. After a route
  change (`AVAudioEngineConfigurationChange`) the engine has already stopped, so the rebuild starts where the last tick
  heard it (`Timeline.heard`, #714); a start that fails then reports loading and is retried a few times before staying
  paused (#715).
- Logging (#897): everything under subsystem `com.simplecityapps.shuttle2`, the Kotlin `Logger` included (`OsLogLogger`
  in :shared, tag as category, through Swift's `KotlinLogSink`: the format string must be a Swift literal or `log show`
  renders `<compose failure>`, #899). Playback's four categories persist at info (project.yml `OSLogPreferences`), so
  `sudo log collect --device` after a stall has them: `playback` (Kotlin `IosPlayerController` and Swift `PlayIntent`:
  every command with its source, state changes, transitions, failures), `audio-engine` (state, gapless or not
  transitions, underruns, buffer health, restarts), `network` (byte source opens, closes with bytes/duration/throughput,
  retries, reopens, failures: host, and path hashed, never a query) and `session` (interruptions, route changes with
  outputs, where each pause came from). Song ids, never titles; nothing per buffer.
