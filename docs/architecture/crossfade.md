# Crossfade (design B, #97): proof of concept

Status: proof of concept in `:android:playback`, off by default (`crossfade_duration_ms` = 0, no UI).

## Design

One playback ExoPlayer and one AudioTrack; the MediaSession sees the same items and queue as before.

- **Clip, once the tail is ready.** `CrossfadeClippingMediaSourceFactory` wraps each queue entry's source in a
  `ClippingMediaSource` that clips in the media period and follows the item's `clippingConfiguration`. Items are
  queued unclipped. When an entry's tail is decoded, `Crossfade` replaces its item with a copy clipped to end at
  `duration - F` (`replaceMediaItem`). Media3 updates the source in place, with no re-prepare. When the tail is
  dropped (the entry leaves the current and next, or F changes), `Crossfade` unclips the item the same way. Songs
  shorter than `2F` are never clipped. See "Why clip late" below.
- **Pre-decode the tail.** `TailDecoder` plays the entry from `duration - F - 500 ms` to its end on a helper
  ExoPlayer, built from the factory's renderers (so the FLAC/Opus extensions decode) and media source factory
  (so remote songs resolve). Its sink runs the entry's ReplayGain and then a capture processor, and outputs to
  `CapturingAudioOutput`, which takes every write whole and reports it as played. The decode runs as fast as the
  decoder can go. `Crossfade` (a `Player.Listener`) decodes the current and next entries' tails, one at a time,
  and hands `CrossfadeMixer` a plan for each entry, keyed by entry uid.
- **Mix.** `CrossfadeMixer` is an `AudioProcessor` between ReplayGain and the EQ (the EQ moved to last, so its
  headroom attenuation covers the mixed signal). At the end of an entry's stream, its plan says what to do:
  - `MixInto(next)`: hold the tail, and if the sink's next flush is that entry's gapless start, mix it into
    the head with equal-power curves (`head·sin θ + tail·cos θ`);
  - `FadeOut` (end of queue): play the tail out, fading;
  - `Join`: play the tail out unfaded. This covers the same album (possibly gapless), repeat-one, a next song
    too short to clip, and a sample rate or channel mismatch.

  Any other flush (a seek or skip) drops the tail, so those cut hard. Plans are keyed by entry because the sink
  runs ahead of the player's position and can reach the next entry's end before the player moves on.
- **Pass-through.** A stream with no plan and nothing held, fading or playing out (every stream while
  crossfade is off) isn't copied. The mixer hands the input buffer on as its output, unconsumed, and the pipeline
  consumes it when the next stage reads it.
- **Fall back where the mixer can't run (#569).** `Crossfade` stands down, as though F were 0 (tails dropped,
  the decode cancelled, items unclipped), while any of these holds, and picks up again once none does:
  - **Casting.** `Crossfade.followCast` listens to the app player (`AppPlayer`); while it `isRemote`, the local
    ExoPlayer is idle and the receiver plays each song whole (the Cast converter ignores clipping).
  - **Audio offload allowed.** The player's `trackSelectionParameters` ask for offload
    (`audioOffloadMode != AUDIO_OFFLOAD_MODE_DISABLED`). Offloaded audio never runs through the app's processors.
  - **Float output.** The last AudioTrack the sink opened is `ENCODING_PCM_FLOAT` (or offloaded), from
    `onAudioTrackInitialized`. `DefaultAudioSink` runs the app's processors only on its integer PCM path, so the
    mixer (which takes 16/24-bit PCM) never sees float output.

  It checks at every update (item, timeline, repeat, shuffle and track selection parameter changes) and when a
  new AudioTrack or a Cast handover changes the answer, so a change mid-song unclips the playing item. Standing
  down lets that item's clip move even after the next item is preloaded (#562): a clip left on it would cut the
  song short, as nothing plays its tail. S2 enables neither float output nor offload today (the renderers
  factory's default, and `audioOffloadPreferences` left disabled), so those two are guards against a later change.
- **Pipeline quirk.** The pipeline queues end of stream on every pass until the processor ends. The mixer acts
  on the first one only, and emits the playout from `getOutput()`/`isEnded()` so it's never lost behind pending
  output.

## Telemetry: `crossfade_skipped`

Each song that should have crossfaded into the next (F on, the song long enough, the next plays after it on its
own, a `MixInto` plan) but played into it without is reported once, as an Analytics `crossfade_skipped` event
with a `reason` (`CrossfadeSkip`). `Crossfade` works it out as the player moves on to the next item on its own
(`MEDIA_ITEM_TRANSITION_REASON_AUTO`): first any reason the mixer recorded as the song's stream ended
(`CrossfadeMixer.takeSkip`), then the fallback in force, then why the song was never clipped.

| `reason` | When |
|---|---|
| `tail_late` | The tail wasn't ready before the playing item came within `CLIP_CHANGE_MARGIN_MS` of its end, or never landed |
| `unseekable` | `TailDecoder` couldn't seek in the stream |
| `decode_failed` | The tail decode failed or produced no audio |
| `format_mismatch` | The next song's sample rate or channel count differs: the tail played out unfaded (risk 3) |
| `short_song_loaded` | The tail landed after the player had loaded the item to its end, so clipping it would drop the next item's preload (#562) |
| `not_gapless` | The next song didn't follow gaplessly in the sink (a new AudioTrack), so the tail faded out |
| `cast` | The receiver moved on by itself while casting (reported from the app player's transition) |
| `float_output` / `offload` | The fallbacks above |

Seeks and skips aren't reported: they cut hard by design. `ExoPlayerFactory` takes the reporting callback;
`PlaybackEngineModule` passes `Analytics.crossfadeSkipped`.

## The five risks

1. **Tail pre-decode faster than real time: proved on the JVM, open on device.** Measured in `CrossfadeTest`
   (Robolectric, 44.1 kHz 16-bit WAV, 2.5 s tails): 8–13x on the first decode (cold JIT and helper setup) and
   90–300x after that. Not measured with the FLAC/Opus extension decoders or MediaCodec on a device.
2. **Sample-accuracy of the end clip: disproved, then handled.** The clip keeps the access
   unit that crosses the clip point, so a WAV stream overshoots by up to 100 ms (145 530 frames against a
   143 325 clip). The mixer counts each stream's frames from its flush offset, drops output past the clip
   frame, and plays the tail from that frame. The tail's start is its seek position floored to a frame (a
   seek lands on the frame at or before it). The same-album test proves the joins sample-exact for WAV.
   Compressed formats that drop decode-only samples at buffer granularity may not start the tail on that
   frame. Open until tested with MP3/FLAC.
3. **Sample rate or channel count mismatch: skipped.** The tail plays out unfaded (a `Join`) instead of
   mixing, reported as `format_mismatch`. Resampling, or mixing through a common output format, is a follow-up.
4. **Shown duration: handled (#561). Re-clipping: handled.** The player's window shows the song's whole
   duration, clipped or not, so the session, notification, Android Auto and the app's progress all show the real
   length, and it doesn't jump by F as the item is clipped. Only the period is clipped, so the item still ends,
   and hands over, at `duration - F`: the position runs to there and the next song starts, as in other players
   that crossfade. A seek past the clip end lands at the period's end and cuts to the next song. A change to F takes effect at the next timeline or item change. The current and next entries' tails
   are then dropped, their items unclipped, and new tails decoded and clipped. The playing item is the
   exception once it's near its end (see below).
5. **Unseekable streams and failed decodes: handled.** An item is clipped only once its tail is decoded. A
   stream `TailDecoder` can't seek in, a decode error, or a decode still running when the sink reaches the
   end all leave the item whole. It plays to its end with no crossfade, reported as `unseekable`,
   `decode_failed` or `tail_late`.

## Why clip late

Clipping every eligible item when its source is built loses the last F seconds of any song whose tail never
arrives, for example an unseekable or transcoded stream, or a decode error (#544). There were two ways out:

- **Let the mixer play out the unclipped end.** This can't work while the source clips: the mixer can't play
  audio the source never delivered. And if the source didn't clip, the mixer would drop the last F seconds
  itself. The sink would then count frames the listener never hears, so the player's position, the item's end,
  and the gapless handover to the next stream would all drift from what plays.
- **Clip only once the tail is ready: chosen.** The clip stays in the source, where the player's timeline, the
  end of the item and the gapless transition agree with the audio, but it becomes dynamic. A `ClippingMediaSource`
  with clipping in the media period takes a new clipping configuration in place. `ProgressiveMediaPeriod` moves
  its sample queues' read end, and `HlsMediaSource`, `ProgressiveMediaSource` and `StreamSniffingMediaSource`
  accept the updated item. A source that can't take the update is rebuilt by `replaceMediaItem`, which still
  plays correctly but restarts that item's loading.

Details that make it hold:

- **Near the end, the playing item's clip stays put.** The sink runs up to 750 ms ahead of the player's
  position, and the renderer reads ahead of that. So the playing item's clip only moves (set, changed or
  removed) while both the old and new ends are 3 s ahead of the position (`CLIP_CHANGE_MARGIN_MS`). A tail that
  lands later isn't used: the item plays whole. A clipped item keeps its clip and tail to its end, even if F
  changes or crossfade is turned off.
- **Once the next item is preloaded, the playing item's clip stays put too (#562).** A clip changes the item's
  period duration, and `MediaPeriodQueue.updateQueuedPeriods` answers a queued period whose duration changed by
  releasing every period after it (`removeAfter`). So moving the playing item's clip after the player has loaded
  to its end and created the next item's period throws that preload away, and the next item loads again from
  scratch. The playing item's clip therefore moves only while the player hasn't buffered to its end
  (`bufferedPosition` short of the clip end, or of the duration when it's unclipped). A tail that lands after
  that isn't used, as above. The cost is a short song (under about 50 s, the default buffer) whose tail
  arrives after it has fully loaded: it gets no crossfade out. The next item's clip still moves freely: its period is
  normally the last one queued, so there's nothing after it to drop (only when the next item is short enough
  to be fully loaded while the current one plays does its clip cost the preload of the one after).
- **`Crossfade` records the clips it applies.** A plan exists only for a clipped entry, and the plans are set
  before the clips change. `Crossfade` keeps its own record of each clip, with its tail and the entry it was
  applied to. An entry that `PlaylistEditor` replaces for a changed song comes back unclipped, so its record and
  tail are dropped.
- **The timeline shows the updated item.** A `ClippingMediaSource` refreshes its timeline from its child's, which
  keeps the item the child was prepared with. So an item updated in place (re-clipped, or renamed by a library
  update) would revert to the old one. The factory wraps the clipping source in `UpdatedItemMediaSource`, which
  puts the latest item back into the timeline. It also puts back the unclipped window duration, which a
  `DurationRecordingMediaSource` under the clipping source records from the delegate's timeline (risk 4).
- **The delegate never sees a clip.** The delegate source factory (and `TailDecoder`) get the item without its
  clipping. Otherwise `DefaultMediaSourceFactory` would add a second, fixed `ClippingMediaSource`.

## Tests (`CrossfadeTest`, F = 2 s)

- A fades into B: the length is `lenA + lenB - F`; both tones sound across the overlap; A falls and B rises
  to -3 dB at halfway; there's no silence and no step at either end; B's tail fades out at the end of the queue.
- A skip during the overlap: the next song plays whole, with nothing of the tail mixed in. The harness's sink
  writes ahead to the end of the queue, so this proves the flush drops the tail, not the timing of a cut
  mid-fade.
- F = 0 leaves the output identical to back-to-back playback, and `GaplessJoinTest` still passes.
- Songs of one album join sample for sample, with their clipped tails played out unfaded.
- A song whose tail can't be decoded (a Matroska file with no cues, so it can't seek) plays whole, sample for
  sample, then the next song plays with its own tail faded out at the end: the length is `lenA + lenB`. It's
  reported once, as `unseekable`; the crossfade that mixed reports nothing.

`CrossfadeClipTest` (a real player over fake sources and a fake tail source) covers the other reasons and the
fallbacks: `tail_late`, `short_song_loaded`, `decode_failed`, nothing reported between songs of one album; float
output unclipping the playing item and 16-bit output bringing the clip back; offload allowed mid-song and from the
start; casting standing down and a receiver's own transition reported as `cast`. `CrossfadeMixerTest` covers
`format_mismatch` and `not_gapless`.

The test queues the songs, waits for the first two tails, then plays. The decoder works in wall time while
the fake clock runs the whole queue through the sink at once.

## What remains

- A settings UI for F.
- Resampling (risk 3).
- Observing F: a change applies at the next timeline or item change, not straight away (risk 4).
- Crossfade with float output or offload: both fall back to none (the mixer and capture handle 16/24-bit PCM only,
  and offloaded audio skips the processors).
- Device verification: decode speed with the extension decoders, audible joins, Cast, and Android Auto, and the
  `crossfade_skipped` rates from the testing tracks.
- Crossfade into a song that starts with silence (no trim).
