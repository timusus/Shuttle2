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
the route-change pause (headphones out) and media-services resets are its job, tested upstream. So is Now Playing: the
package's `NowPlayingSession`, tested here on its `FakeNowPlayingInfoCenter`/`FakeRemoteCommandCenter`
(`makeTestCoordinator`, `TestGraph.swift`). Background audio is `UIBackgroundModes: [audio]` in project.yml's `info:`.

- `KitAudioPlayer` is the Kotlin `IosAudioPlayer` over the `Player`: Kotlin keeps the policy (queue, skipping failed
  items, restart-vs-previous) and hands the whole order over; each item resolves its stream through
  `playerController.itemResolver` when the Player decodes it. **Threading:** Kotlin calls it on main; the Player's
  events reach the Kotlin listener on the main actor. It sets the Player's processors once, `ReplayGainProcessor`,
  `EqualizerProcessor` then `LimiterProcessor` (S2Playback `DSP/`), all always active. Each item's ReplayGain is the
  `IosStream.gainDb` it resolved with (mode and preamp applied in Kotlin), taken at the item's flush; `IosEqualizer`
  hands the EQ a design that each item's rate is filtered at.
- `PlaybackSystemCoordinator` runs a `NowPlayingSession` over `NowPlayingPlayer` (a `ForwardingNowPlayingControllable`
  over the `Player`) and keeps the widgets' snapshot. The session reads the Player (elapsed, rate, seekability) and
  `NowPlayingPlayer`'s overrides: play/pause shown is `PlayIntent.isPlayIntended`, duration is the song's, next stays
  enabled (Kotlin wraps the queue), and audiobook/podcast songs (`Song.type != .audio`) skip 30 s / back 10 s. Every
  remote command goes through `IosPlayerController`/`PlayIntent`, never the Player, so Kotlin's policy decides
  (restart-vs-previous). Metadata is read once per item; the clock shows rate 0 until audio flows; scrub and skip are
  enabled once the item is seekable. `AppGraph.initialize()` (from `S2App.init`) builds `KitAudioPlayer`, the graph and
  the coordinator once.
- The Player stays `.ready` after a skip or seek until its transport answers, so the restart can read as a stall for a
  moment. A paused load or seek is `.buffering` until its first audio; `KitAudioPlayer` reports it as paused.
- Each item plays through S2Playback's `TimeOffsetItemSource`. A stream Kotlin resolved as a transcode
  (`IosStream.transcoded`) declares the song's duration and is seekable. A seek re-resolves it at the position
  (`IosItemResolver.resolveAt`, under the entry's play, so Plex replaces its transcode) at whole seconds, and drops
  frames up to the target. A failure or an end more than 5 s short re-opens at the position the same way; three
  re-opens that play under a second end the item. Files and raw streams seek through the decoder as before.
- Logging (#897): everything under subsystem `com.simplecityapps.shuttle2`, the Kotlin `Logger` included (`OsLogLogger`
  in :shared, tag as category, through Swift's `KotlinLogSink`: the format string must be a Swift literal or `log show`
  renders `<compose failure>`, #899). Playback's categories persist at info (project.yml `OSLogPreferences`), so
  `sudo log collect --device` after a stall has them: `playback` (Kotlin `IosPlayerController` and Swift `PlayIntent`:
  every command with its source, state changes, transitions, failures) and `session` (remote skips and seeks). Stream
  downloads log under AudioPlaybackKit's own subsystem, `com.simplecityapps.AudioPlaybackKit` category `download`,
  persisted the same way. Song ids, never titles; nothing per buffer.
