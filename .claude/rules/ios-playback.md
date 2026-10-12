---
paths:
  - "ios/S2/Platform/**"
  - "ios/Playback/**"
  - "ios/S2Tests/**/*Audio*"
  - "ios/S2Tests/**/*NowPlaying*"
  - "ios/S2Tests/**/*Playback*"
  - "shared/src/iosMain/**"
---

# iOS player, Now Playing and the system surfaces (#1020)

The app plays through AudioPlaybackKit's `Player`, which owns decode, gapless joins and the audio session: interruptions,
the route-change pause (headphones out) and media-services resets are its job, tested upstream. `NowPlayingController`
takes its system objects behind protocols (`NowPlayingInfoCenter`, `RemoteCommandCenter`) and is tested with fakes.
Background audio is `UIBackgroundModes: [audio]` in project.yml's `info:`.

- `KitAudioPlayer` is the Kotlin `IosAudioPlayer` over the `Player`: Kotlin keeps the policy (queue, skipping failed
  items, restart-vs-previous) and hands the whole order over; each item resolves its stream through
  `playerController.itemResolver` when the Player decodes it. **Threading:** Kotlin calls it on main; the Player's
  events reach the Kotlin listener on the main actor. The equalizer is a no-op until it joins the Player's processors.
- `NowPlayingController`: metadata on `setItem`, elapsed/rate through `updatePlayback`, which only writes on a
  state/speed change, a >1 s jump or every 10 s (safe per tick). Artwork via the `loadArtwork` closure, dropped if the
  item changed. `SkipMode.interval` swaps next/previous for skip ±N s.
- `PlaybackSystemCoordinator` wires Now Playing, the remote commands, the widgets' snapshot and `PlayIntent` to the
  Kotlin `IosPlayerController` (`AppGraph.shared.playerController`): remote commands call `PlaybackOperations`; Now
  Playing follows the controller's flows, republished from all of their current values whenever any changes (never
  per-flow copies: observer tasks resume in no particular order, #691). A stalled stream sets the rate to 0.
  `AppGraph.initialize()` (from `S2App.init`) builds `KitAudioPlayer`, the graph and the coordinator once.
- The Player stays `.ready` after a skip or seek until its transport answers, so the restart can read as a stall for a
  moment; a paused load reports ready before anything is decoded.
- Logging (#897): everything under subsystem `com.simplecityapps.shuttle2`, the Kotlin `Logger` included (`OsLogLogger`
  in :shared, tag as category, through Swift's `KotlinLogSink`: the format string must be a Swift literal or `log show`
  renders `<compose failure>`, #899). Playback's categories persist at info (project.yml `OSLogPreferences`), so
  `sudo log collect --device` after a stall has them: `playback` (Kotlin `IosPlayerController` and Swift `PlayIntent`:
  every command with its source, state changes, transitions, failures) and `session` (remote skips and seeks). Stream
  downloads log under AudioPlaybackKit's own subsystem, `com.simplecityapps.AudioPlaybackKit` category `download`,
  persisted the same way. Song ids, never titles; nothing per buffer.
