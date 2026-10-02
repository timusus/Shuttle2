# S2Playback

S2's iOS player engine, phase 6 of the iOS port (#588, epic #581; design:
[`docs/architecture/ios-port/phase-6-playback.md`](../../docs/architecture/ios-port/phase-6-playback.md)).
It is a Swift package for iOS 17. It also has a macOS 14 platform, so `swift test` runs on the Mac
without a simulator.

It holds three things:

- the byte sources and FFmpeg pull decoder copied from Shuttle Podcasts;
- the package's link to a dynamic, LGPL-only FFmpeg build (`ios/scripts/build-ffmpeg.sh`);
- `MusicPlaybackController`, the gapless two-track AVAudioEngine player that the Kotlin
  `EnginePlayerController` drives.

## Architecture

```
PlaybackTrack(uid, gainDb, source) ──► TrackPCMSource ──► PCMProcessor ──► AVAudioPlayerNode ──► mainMixer ──► output
                                        (FFmpegTrackSource:    (ReplayGain → EQ    (one node, one stream,
                                         FileByteReader or      preamp → biquads    fixed stereo float32 at
                                         HTTPRangeByteSource    → lookahead         outputSampleRate)
                                         → FFmpegStreamDecoder  limiter)
                                         → swresample)
```

**Swift knows only the current and the next track.** Kotlin owns the queue, shuffle, repeat and
ReplayGain resolution. It calls `load(current:next:startMs:playWhenReady:)` and `setNext(_:)`, plus
the play, pause, seek, stop, speed, volume and EQ calls.

**Gapless.** Each source converts its track to the one output format: swresample in the C decoder,
via `stream_decoder_set_output`. When the current source ends, the same buffer continues with the
next track's first frames. The player node never sees a boundary, only one stream, so the join is
sample-exact whatever the two tracks' rates are.

**Timeline.** Scheduled frames are numbered from the last load or seek. A segment list maps those
stream indices to a track and a media frame. The node's `playerTime` maps to the stream index
through anchors, which move only if the node starved. Position, the transition callback and the
end of the queue all come from that mapping.

**Failures.** A track that can't be opened or read is reported through `onFailed` and counts as
ended. A current track that fails carries on into the next, if one is set. A next that fails to open
is never transitioned into: the current track ends, and Kotlin, told of the failure, decides what
follows. A next set while the queue's end is scheduled but not yet heard (including straight after a
load whose current track failed) carries on from the last frame. After the end of a track that
failed, `setNext` starts the new next at once; after one that played out, Kotlin loads what follows.

**Pre-opening (#605, #620).** The next track is opened, and its first chunk decoded, on a background
queue ahead of the current track's end, so a slow HTTP open or a transcode that takes seconds to
start is ready by the join. The end is the container's duration, or `PlaybackTrack.expectedDurationMs`
(the library's, from Kotlin) when the container has none, as a progressive transcode doesn't. If the
stream reaches the join first, it waits: the current track's last frames go out, the node runs dry,
and the next resumes the stream from its first frame once open.

- *How far ahead* is `PreopenLead`: `preopenSeconds` (default 10 s), or twice the slowest of the last
  five opens (to the first decoded chunk; the current track's own load counts) when that's longer,
  capped at 60 s. A fixed window left a gap whenever a server took longer than it to answer; the lead
  follows what opens have actually been taking, with room for one slower than any seen, and shrinks
  back as slow ones age out. The first open on a slow server can still be late (nothing measured
  yet), but the current track's own load is measured before its next is due, so that is rare.
- *With no end known* (neither a container duration nor an expected one), the next opens once
  `steadySeconds` (default 5 s) of the current track have been read since its load or last seek:
  not at once, where it would compete with the current track's own start and be wasted by a quick
  skip. A track of unknown length shorter than that joins with the open's wait.
- *Identity.* A pre-opened next that stops being next (a new next, a cleared one, a load of other
  streams) is cancelled. One not read yet survives a seek, and a load that hands its stream back,
  matched on `PlaybackTrack.streamIdentity` (a URL track's URL and headers), not on the hand-over
  `uid`, which Kotlin makes new for every hand-over: as the next again (Kotlin re-opening a transcode
  for a seek) or as the current track (a skip onto it, which then starts on the open and decoded
  first chunk, under the new uid and gain). A server URL carries its play session and transcode
  parameters, so the same song resolved again (another quality, a transcode from a position) is
  another stream and is opened afresh; Kotlin hands the pre-opened next's stream back for a skip
  instead of resolving the song again (`IosPlayerController.startLoad`).

**Scheduling.** The controller keeps `scheduleAheadSeconds` (default 1 s) of processed audio queued
ahead of the playhead. It refills on buffer completion and on a 100 ms ticker, which also emits
`onPosition`. An EQ change is heard at most that far ahead; the byte source's own read-ahead is what
rides out the network.

**DSP.** `PCMProcessor` applies, in order: the track's ReplayGain, the EQ preamp, one biquad per band
per channel, then a stereo-linked lookahead limiter (ceiling −0.1 dBFS, attack 5 ms, release 100 ms).

- **Coefficients.** They come from Kotlin, 5 per band (b0, b1, b2, a1, a2), computed for
  `outputSampleRate`, a fixed 48 kHz that Kotlin reads through `IosAudioPlayer.engineSampleRate()`.
  Swift never designs a filter.
- **Limiter delay.** The limiter's lookahead delay is compensated: the first `latency` outputs are
  dropped, and the tail is flushed at the queue's end. Output frame n is therefore input frame n, and
  below the ceiling the chain is bit-exact.
- **State.** Filter and limiter state carries across track boundaries. It resets on a load or seek.

**Time-pitch.** `AVAudioUnitTimePitch` is only in the graph when the speed is not 1×. Even when
bypassed, it pulls a whole render block (4096 frames) ahead of the player node and holds it. The
node's clock then runs a block ahead of what is heard, and a seek plays out the stale block first.
Both are measured, and the seek test catches them.

**Unseekable streams (#606).** A server's progressive transcode has no length and `Accept-Ranges:
none`, so no byte maps to a time and `FFmpegTrackSource.isSeekable` is false. The controller never
seeks or interrupts such a source. A seek leaves it playing where it was. A load at a position starts
it at 0. Both are reported through `onSeekUnsupported(uid, ms)`, and the owner (Kotlin's
`IosPlayerController`) re-opens the stream at the position with `StartTimeTicks`. A next track that
the skipped seek interrupted is sought back to where it was read to, so the join stays gapless.

**Threading.** Public methods return at once. Source seeks and reads and all node operations run on
one serial engine queue; a next track's open runs on a background queue, and nothing touches its
source until it's done. A seek or stop first interrupts a read stalled on the network (never an
open in flight). Callbacks arrive on
`callbackQueue` (main by default).

**Start timing.** Every start — a load that plays, or a play of a paused track — logs one
`engine: ttfa` line (category `MusicPlayback`) when the player node first renders: the total from
the call, and the stages in between (session, pre-open, first response, probe, seek, first buffer,
engine start, play, render), `-` for one that didn't happen (#687). A play within 0.5 s of a paused
load being ready is that load's start (`play-after-ready`). Over 3 s it is logged at error level.
On a device: Console.app, filter `engine: ttfa`, or `log stream --predicate 'eventMessage CONTAINS
"engine: ttfa"'` with the phone attached.

## Provenance: copied, adapted, new

Copied from Shuttle Podcasts at `podcasts@9ee6e0954`. Each file's first line names its upstream
path. The only edits are these renames: the log subsystem becomes `com.simplecityapps.shuttle2`,
`CStreamDecode` becomes `CS2StreamDecode`, the `SpineNative` import is dropped, and the test imports
change. They are kept diffable so a later shared AudioCore can take them back.

- **Copied**:
  - `Decode/`: `StreamByteReader`, `FileByteReader`, `ReadAheadTunables`, `StreamingPCMReader`.
  - `Streaming/`: `HTTPRangeByteSource`, `CachedRunStore`, `ResolvedURLCache`, `ReadAheadPolicy`,
    `ReadAheadControl`, `AudioByteTee`. The tee and appetite hooks are nil by default.
  - `Engine/`: `ClockStallDetector`, `PlayerStallRecovery`.
  - Test support: `LoopbackMediaServer`, `PlaybackTestMedia`, `tone.mp3`, `tone_moov_last.m4a`.
  - Their tests.
- **Adapted.** S2 additions are marked `S2:`.
  - `CS2StreamDecode/stream_decode.{c,h}` adds:
    - an output rate and channel count;
    - mono spread at full level;
    - a resampler rebuilt on a mid-stream format change;
    - frame timestamps after a byte-estimate seek for FLAC and PCM.
  - `FFmpegStreamDecoder` adds `setOutputFormat` and `read(into:maxFrames:)`.
  - `StartupTiming` keeps Podcasts' nested types and its `ttfa-net` line, but its record is the
    controller's start (#687): the Podcasts-only teardown, swap, tee and chain stages are gone, and
    it adds `open` (pre-opened or not), `play-after-ready` and `play`.
  - `Biquad` adds `adoptState`; its factories were dropped, as the shared Kotlin EQ designs the bands.
  - `LookaheadLimiter` is now stereo-linked and frame-interleaved, and its window includes the
    emitted frame.
- **New**:
  - `Engine/TrackPCMSource.swift`: the protocol and `FFmpegTrackSource`, with frame-exact seek.
  - `Engine/MusicPlaybackController.swift`.
  - `DSP/PCMProcessor.swift`.
  - `ios/scripts/build-ffmpeg.sh`, widened from the Podcasts script and built dynamic.
  - The music tests and the FLAC, Opus, Vorbis, ALAC, AIFF and 24-bit WAV fixtures.
- **Dropped**: Podcasts' `#if canImport(CStreamDecode)` fallback and `FFmpegStreamDecoder.isAvailable`.
  FFmpeg is required, so a package without it fails to resolve rather than building an engine that
  can't decode.
- **Left behind**:
  - SilenceGate, VoiceEnhance, SkipCueMixer, the compressor, K-weighting and LUFS meter;
  - the Podcasts `AVAudioEnginePlaybackController`;
  - the Spine/ad-skip code;
  - `PlaybackControlling` and the enhancement settings.

## FFmpeg build

```sh
ios/scripts/build-ffmpeg.sh            # build if the cache has no match, then install; 0.2 s once installed
ios/scripts/build-ffmpeg.sh --force    # rebuild the cache entry
FFMPEG_SRC=/path/to/ffmpeg ios/scripts/build-ffmpeg.sh   # an existing checkout of the pinned commit
```

`ios/scripts/build-framework.sh` and `ios/scripts/test.sh` run it first, so a new worktree needs no
separate step.

It builds FFmpeg **n7.1.5** (commit `3a0867c2bf`) as four **dynamic** frameworks, one xcframework per
library: `libavutil`, `libswresample`, `libavcodec`, `libavformat`. Each has iOS arm64, iOS Simulator
arm64 and macOS arm64 slices; the macOS one is for `swift test`. A framework is named after its
library, so `#include <libavcodec/avcodec.h>` resolves through the framework search path. The install
names are `@rpath/libavcodec.framework/libavcodec`, and each framework is stripped (`-x`) and ad-hoc
signed; Xcode re-signs it when it embeds it.

- **Cache.** The build goes to `${S2_FFMPEG_CACHE:-~/Library/Caches/s2-ffmpeg-ios}/n7.1.5-<key>/`,
  outside git. The key is the first 12 hex digits of the script's SHA-256, so any change to the pin,
  flags or packaging builds a new entry. The entry holds `VERSION.txt` (tag, commit, flags, library
  versions), `SHA256SUMS` and `COPYING.LGPLv2.1`. The FFmpeg clone is kept in `.../src/`. Old entries
  are never deleted by the script.
- **Install.** The entry is checked against `SHA256SUMS` and copied to `ios/Playback/Frameworks/`
  (gitignored). An install whose `VERSION.txt` matches and whose checksums pass is left alone.
- **Package.** `Package.swift` always declares the four binary targets, and `CS2StreamDecode` depends
  on them. No system library is linked by the package: the dylibs carry their own `libz` link.
- **App.** Xcode embeds and signs a package's dynamic binary targets in `S2.app/Frameworks` itself,
  so `project.yml` has no `embed:` entry for them.

A cold build takes 1.5 to 3 minutes, including the clone. Binary sizes (bytes):

| Library | iOS device | iOS Simulator |
|---|---|---|
| libavutil | 533,168 | 516,672 |
| libswresample | 103,072 | 86,656 |
| libavcodec | 622,608 | 606,144 |
| libavformat | 434,960 | 418,528 |
| total | 1,693,808 | 1,628,000 |

The library versions are avutil 59.39.100, swresample 5.3.100, avcodec 61.19.101 and avformat
61.7.103. Configure flags:

```
--disable-everything --disable-programs --disable-doc --disable-htmlpages --disable-manpages
--disable-podpages --disable-txtpages --disable-avdevice --disable-swscale --disable-postproc
--disable-avfilter --disable-network --disable-protocols --disable-devices --disable-filters
--disable-bsfs --disable-encoders --disable-muxers --disable-debug
--disable-audiotoolbox --disable-autodetect --enable-zlib
--enable-decoder=flac,alac,opus,vorbis,mp3,mp3float,aac,aac_latm,pcm_s16le,pcm_s24le,pcm_s32le,
                 pcm_f32le,pcm_f64le,pcm_u8,pcm_s16be,pcm_s24be,pcm_s32be,pcm_f32be,pcm_f64be
--enable-demuxer=ogg,matroska,wav,flac,mov,mp3,aac,aiff
--enable-parser=flac,opus,vorbis,mpegaudio,aac,aac_latm
--enable-swresample --enable-avformat --enable-avcodec --enable-avutil
--enable-shared --disable-static --enable-pic --enable-small
--enable-cross-compile --target-os=darwin --arch=arm64
```

The link adds `-Wl,-dead_strip_dylibs`.

- **Autodetect.** `--disable-autodetect` matters. Without it, configure finds VideoToolbox and, on
  the macOS slice, Homebrew's X11 and SDL2, and libavutil's hwcontext drags them into the link.
- **No iconv.** With autodetect off, configure doesn't link `-liconv`. Its only user is subtitle
  charset conversion, and no subtitle decoder is built.
- **Dead-stripped dylibs.** Without `-dead_strip_dylibs`, the dylibs load CoreFoundation, CoreVideo
  and CoreMedia for nothing. Their only dependencies are each other, `libz` and `libSystem`.
- **Byte input.** No network protocols are built. Bytes arrive through the AVIO callbacks.

## Tests

```sh
ios/scripts/test.sh --package    # swift test in ios/Playback, on the Mac: 134 XCTest + 10 swift-testing
ios/scripts/test.sh              # the app's S2 scheme on an available iPhone simulator
```

Nothing skips: every test decodes through the real FFmpeg.

The engine tests run the real `AVAudioEngine` in offline manual rendering. Nothing plays: the test
pulls the mixer's output in 512-frame slices and compares it, sample for sample, with the PCM the
sources handed over. `MusicPlaybackControllerTests` covers:

- **Two tracks back to back.** The render equals A followed by B exactly: no frame inserted or
  dropped at the start, the join or the end. The transition is reported within one slice after
  B's first frame.
- **44.1 kHz then 48 kHz, FFmpeg-decoded into a 48 kHz engine.** The render equals exactly what the
  two decoders produced, in order. A's 13,230 source frames become 14,400 ± 2.
- **Seek.** It resumes at exactly the requested frame, and the position is exact.
- **Next changes.** Seeking after the next track has started to be read, replacing an
  already-scheduled next, and setting a next after the queue's end was scheduled are all gapless.
- **DSP.** A flat EQ is bit-exact identity, and a peaking band changes the signal. ReplayGain
  −6 dB scales every sample by 0.501187. The limiter holds a +12 dB boost under the ceiling.

`PreopenTests` covers the next track's open (#605, #620): it starts `preopenSeconds` before the end,
by the container's duration or the expected one, or once `steadySeconds` are read with neither; a
0.3 s open widens a 0.1 s lead (only the lower bound is asserted, so a loaded machine can't fail it);
a replaced next, or a load of another stream, is cancelled; a seek, or a load handing the same stream
back as next or current (a skip onto it, even mid-open), keeps it, and the skip plays it sample-exact
at the new gain; another stream of the same song (a different identity, or none) is opened afresh;
the stream waits for an open still in flight, in silence, then plays the next from its first frame,
or ends if the next is cleared. And the gap itself: a real-time render into a next served by
`LoopbackMediaServer` 1.5 s late has no gap pre-opened, and about 0.9 s of silence opened at the
join. `PreopenLeadTests` pins the lead's rule.

`PrimingAndSeekTests` decodes 2 s chirps (AAC in MP4, MP3, Opus), whose phase names every sample, and
cross-correlates to find where the decoded audio came from: the priming is trimmed at the start and
after a seek back to 0:00, the end padding is trimmed (exactly 2 s of frames), and a seek lands on
the frame asked for (MP3 within one frame; see Known limits).

`FFmpegMusicDecodeTests` covers:

- FLAC decodes all 22,050 frames. Opus decodes about 1 s with pre-skip trimmed.
- Opus resamples to 44.1 kHz.
- A FLAC seek is frame-exact.
- Mono is spread to both sides at full level.

`MusicPlaybackFormatsTests` plays each fixture format end to end, as the app does:
`PlaybackTrack(uid:url:)` into the controller. The formats are MP3, AAC and ALAC in MP4, FLAC, Opus and
Vorbis in Ogg, 24-bit WAV, and AIFF. Each checks:

- the codec FFmpeg picks;
- that the render equals a separate FFmpeg decode of the same file, times the −6 dB ReplayGain, from
  the first frame (encoder delay trimmed) to the last;
- silence after, no failure, and the `ended` state.

`PCMProcessorTests` pins the limiter's latency compensation. The music fixtures were made with:

```sh
S="sine=frequency=440:sample_rate=44100"; A="volume=4,pan=stereo|c0=c0|c1=c0"
ffmpeg -f lavfi -i "$S:duration=0.5"  -af "$A" -sample_fmt s16 -c:a flac tone-44k.flac          # 14 KB
ffmpeg -f lavfi -i "sine=frequency=440:sample_rate=48000:duration=1" -af "$A" -c:a libopus -b:a 48k tone-48k.opus  # 9 KB
ffmpeg -f lavfi -i "$S:duration=0.5"  -af "$A" -c:a vorbis -strict -2 -q:a 3 tone-44k.ogg         # 5 KB
ffmpeg -f lavfi -i "$S:duration=0.5"  -af "$A" -sample_fmt s16p -c:a alac tone-44k-alac.m4a      # 15 KB
ffmpeg -f lavfi -i "$S:duration=0.25" -af "$A" -c:a pcm_s16be tone-44k.aiff                      # 43 KB
ffmpeg -f lavfi -i "sine=frequency=440:sample_rate=48000:duration=0.25" -af "$A" -c:a pcm_s24le tone-48k-s24.wav  # 70 KB
# chirp: the formula in PrimingAndSeekTests, as an aevalsrc at the rate given
ffmpeg -f lavfi -i "$(chirp 44100)" -c:a aac -b:a 128k chirp-44k-aac.m4a                      # 33 KB
ffmpeg -f lavfi -i "$(chirp 44100)" -c:a libmp3lame -b:a 128k chirp-44k.mp3                   # 32 KB
ffmpeg -f lavfi -i "$(chirp 48000)" -c:a libopus -b:a 64k chirp-48k.opus                      # 18 KB
```

## LGPL notes

The frameworks are plain **LGPL v2.1+**:

- no `--enable-gpl`, `--enable-version3` or `--enable-nonfree`;
- no external codec libraries. Opus and Vorbis use FFmpeg's native decoders, not libopus or
  libvorbis.

FFmpeg is linked **dynamically**: four frameworks in `S2.app/Frameworks`, not code in the app
binary. That meets LGPL v2.1 §6(b), a shared library mechanism a user can swap. It also avoids the
static build's obligation to offer the app's object files for relinking. The app carries:

1. **The notice**, in the app's Settings pane (`ios/S2/Settings.bundle`: Settings › S2 ›
   Acknowledgements). It gives FFmpeg's name, version and licence, the source (the n7.1.5 tag and
   commit, unmodified), and this build script. The pane links to the full LGPL v2.1 text. The iOS app
   has no in-app licences screen yet. Android's is generated from Gradle dependencies
   (AboutLibraries), which can't see FFmpeg, so the Settings bundle is the one place. An in-app
   acknowledgements screen should show the same text.
2. **How to relink.** Build the same major versions (libavutil 59, libswresample 5, libavcodec 61,
   libavformat 61) with the flags above. `build-ffmpeg.sh` builds any commit set as `FFMPEG_COMMIT`
   in it. Replace the four frameworks in `S2.app/Frameworks` with the result, and re-sign the app
   (`codesign --force --sign <identity>` on each framework, then on the app with its entitlements).
   The app looks the libraries up by install name (`@rpath/libavcodec.framework/libavcodec`) and
   checks only the compatibility version, so a same-major build loads.

Do not add a GPL-only component (for example `--enable-gpl` or libx264-style externals) or a
`nonfree` one.

## Known limits (spike)

- **Transition timing.** `onTransition` and `onPosition` fire from the 100 ms ticker. The audio
  join is exact; the callback can trail it by up to one tick.
- **Rebuild repeats.** Replacing a next that is already scheduled, or a route change, rebuilds the
  schedule from the node's last reported playhead. That can repeat up to one render cycle of audio;
  nothing is skipped.
- **Underruns.** After an underrun, the anchor that re-syncs position is taken when the next buffer
  is scheduled, so it is approximate by the scheduling latency.
- **Time-pitch position.** Off 1×, the time-pitch unit's own buffering makes position lead what is
  heard by up to one block.
- **MP3 seeks are within one frame.** FFmpeg's MP3 seek (the Xing TOC, or the bitrate for CBR) stamps
  the frame it syncs to with the time asked for, so a mid-file seek can land up to 1,152 frames
  (26 ms) off. A seek to 0:00 is exact. Exact seeking needs an index of frame offsets (#619).
- **Pre-open timing without a duration.** A next after a stream with neither a container duration
  nor an expected one is opened once the current track is steady, and its connection then idles
  until the join.
- **No audio session.** No `AVAudioSession` work, interruptions or route callbacks toward Kotlin;
  the app owns the session (phase 6 step 7).
