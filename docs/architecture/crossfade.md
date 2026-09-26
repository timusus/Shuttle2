# Crossfade (design B, #97): proof of concept

Status: proof of concept in `:android:playback`, off by default (`crossfade_duration_ms` = 0, no UI).

## Design

One playback ExoPlayer and one AudioTrack; the MediaSession sees the same items and queue as before.

- **Clip.** `CrossfadeClippingMediaSourceFactory` wraps each queue entry's source in a `ClippingMediaSource`
  ending at `duration - F`. Songs shorter than `2F` aren't clipped.
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
- **Pipeline quirk.** The pipeline queues end of stream on every pass until the processor ends. The mixer acts
  on the first one only, and emits the playout from `getOutput()`/`isEnded()` so it's never lost behind pending
  output.

## The five risks

1. **Tail pre-decode faster than real time: proved on the JVM, open on device.** Measured in `CrossfadeTest`
   (Robolectric, 44.1 kHz 16-bit WAV, 2.5 s tails): 8–13x on the first decode (cold JIT and helper setup) and
   90–300x after that. Not measured with the FLAC/Opus extension decoders or MediaCodec on a device.
2. **Sample-accuracy of the end clip: disproved, then handled.** `ClippingSampleStream` keeps the access
   unit that crosses the clip point, so a WAV stream overshoots by up to 100 ms (145 530 frames against a
   143 325 clip). The mixer counts each stream's frames from its flush offset, drops output past the clip
   frame, and plays the tail from that frame. The tail's start is its seek position floored to a frame (a
   seek lands on the frame at or before it). The same-album test proves the joins sample-exact for WAV.
   Compressed formats that drop decode-only samples at buffer granularity may not start the tail on that
   frame. Open until tested with MP3/FLAC.
3. **Sample rate or channel count mismatch: skipped.** The tail plays out unfaded (a `Join`) instead of
   mixing. Resampling, or mixing through a common output format, is a follow-up.
4. **Shown duration and re-clipping: open.** The player's timeline shows the clipped duration (`duration - F`),
   so anything reading the window duration (the session's seek bar) is F short. Changing F applies only to
   items prepared after the change. Existing items need `replaceMediaItems` (or a playlist rebuild) to
   re-clip, which isn't done.
5. **Unseekable streams: open.** `TailDecoder` gives up on a non-seekable window, but the item is still clipped,
   so its last F seconds are lost. The same happens whenever a tail isn't decoded before the sink reaches the
   clip (a decode error, a very slow decode). The fix is to clip only once a tail is ready, or to clip only
   seekable local and remote files.

## Tests (`CrossfadeTest`, F = 2 s)

- A fades into B: the length is `lenA + lenB - F`; both tones sound across the overlap; A falls and B rises
  to -3 dB at halfway; there's no silence and no step at either end; B's tail fades out at the end of the queue.
- A skip during the overlap: the next song plays whole, with nothing of the tail mixed in. The harness's sink
  writes ahead to the end of the queue, so this proves the flush drops the tail, not the timing of a cut
  mid-fade.
- F = 0 leaves the output identical to back-to-back playback, and `GaplessJoinTest` still passes.
- Songs of one album join sample for sample, with their clipped tails played out unfaded.

The test queues the songs, waits for the first two tails, then plays. The decoder works in wall time while
the fake clock runs the whole queue through the sink at once.

## What remains

- A settings UI for F.
- Resampling (risk 3).
- The stream fallback: don't clip until a tail is ready (risk 5).
- The displayed duration: report the unclipped duration to the session and UI (risk 4).
- Re-clipping when F changes (risk 4).
- Float output (the mixer and capture handle 16/24-bit PCM only).
- Device verification: decode speed with the extension decoders, audible joins, Cast, and Android Auto.
- Crossfade into a song that starts with silence (no trim).
