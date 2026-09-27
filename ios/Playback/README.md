# S2Playback

S2's iOS player engine, phase 6 of the iOS port (#588, epic #581; design:
[`docs/architecture/ios-port/phase-6-playback.md`](../../docs/architecture/ios-port/phase-6-playback.md)).
It is a Swift package for iOS 17. It also has a macOS 14 platform, so `swift test` runs on the Mac
without a simulator.

It holds three things:

- the byte sources and FFmpeg pull decoder copied from Shuttle Podcasts;
- a static, LGPL-only FFmpeg build;
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

**Scheduling.** The controller keeps `scheduleAheadSeconds` (default 1 s) of processed audio queued
ahead of the playhead. It refills on buffer completion and on a 100 ms ticker, which also emits
`onPosition`. An EQ change is heard at most that far ahead; the byte source's own read-ahead is what
rides out the network.

**DSP.** `PCMProcessor` applies, in order: the track's ReplayGain, the EQ preamp, one biquad per band
per channel, then a stereo-linked lookahead limiter (ceiling −0.1 dBFS, attack 5 ms, release 100 ms).

- **Coefficients.** They come from Kotlin, 5 per band (b0, b1, b2, a1, a2), computed for
  `outputSampleRate`. Swift never designs a filter.
- **Limiter delay.** The limiter's lookahead delay is compensated: the first `latency` outputs are
  dropped, and the tail is flushed at the queue's end. Output frame n is therefore input frame n, and
  below the ceiling the chain is bit-exact.
- **State.** Filter and limiter state carries across track boundaries. It resets on a load or seek.

**Time-pitch.** `AVAudioUnitTimePitch` is only in the graph when the speed is not 1×. Even when
bypassed, it pulls a whole render block (4096 frames) ahead of the player node and holds it. The
node's clock then runs a block ahead of what is heard, and a seek plays out the stale block first.
Both are measured, and the seek test catches them.

**Threading.** Public methods return at once. All source I/O and node operations run on one serial
engine queue. A seek or stop first interrupts a read stalled on the network. Callbacks arrive on
`callbackQueue` (main by default).

## Provenance: copied, adapted, new

Copied from Shuttle Podcasts at `podcasts@9ee6e0954`. Each file's first line names its upstream
path. The only edits are these renames: the log subsystem becomes `com.simplecityapps.shuttle2`,
`CStreamDecode` becomes `CS2StreamDecode`, the `SpineNative` import is dropped, and the test imports
change. They are kept diffable so a later shared AudioCore can take them back.

- **Copied**:
  - `Decode/`: `StreamByteReader`, `FileByteReader`, `ReadAheadTunables`, `StreamingPCMReader`,
    `StartupTiming`.
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
  - `Biquad` adds `coefficients` and `adoptState`; the high-pass factory was dropped.
  - `LookaheadLimiter` is now stereo-linked and frame-interleaved, and its window includes the
    emitted frame.
- **New**:
  - `Engine/TrackPCMSource.swift`: the protocol and `FFmpegTrackSource`, with frame-exact seek.
  - `Engine/MusicPlaybackController.swift`.
  - `DSP/PCMProcessor.swift`.
  - `scripts/build-ffmpeg.sh`, widened from the Podcasts script.
  - The music tests and the FLAC and Opus fixtures.
- **Left behind**:
  - SilenceGate, VoiceEnhance, SkipCueMixer, the compressor, K-weighting and LUFS meter;
  - the Podcasts `AVAudioEnginePlaybackController`;
  - the Spine/ad-skip code;
  - `PlaybackControlling` and the enhancement settings.

## FFmpeg build

```sh
ios/Playback/scripts/build-ffmpeg.sh                 # clones n7.1 into $TMPDIR/s2-ffmpeg-ios/ffmpeg-src
FFMPEG_SRC=/path/to/ffmpeg ios/Playback/scripts/build-ffmpeg.sh
```

The script writes `ios/Playback/Frameworks/FFmpeg.xcframework`, which is gitignored. It has one
merged `libffmpeg.a` per slice (iOS arm64, iOS Simulator arm64, and macOS arm64 for `swift test`)
and a `CFFmpeg` module map. `COPYING.LGPLv2.1` and `VERSION.txt` (the tag and the flags) sit inside
it.

`Package.swift` includes the binary target and the C decoder only when the xcframework exists.
Without it, the package still builds, and the FFmpeg-backed tests skip.

The build is FFmpeg **n7.1**. The first run took about 2 min 20 s, including the clone. Sizes:

| Slice | `libffmpeg.a` |
|---|---|
| iOS device (arm64) | 2,636,920 B |
| iOS Simulator (arm64) | 2,635,888 B |
| macOS (arm64) | 2,636,200 B |
| whole xcframework on disk | 12 MB |

Configure flags:

```
--disable-everything --disable-programs --disable-doc --disable-htmlpages --disable-manpages
--disable-podpages --disable-txtpages --disable-avdevice --disable-swscale --disable-postproc
--disable-avfilter --disable-network --disable-protocols --disable-devices --disable-filters
--disable-bsfs --disable-encoders --disable-muxers --disable-debug --disable-symver
--disable-audiotoolbox --disable-autodetect --enable-zlib --enable-iconv
--enable-decoder=flac,alac,opus,vorbis,mp3,mp3float,aac,aac_latm,pcm_s16le,pcm_s24le,pcm_s32le,
                 pcm_f32le,pcm_f64le,pcm_u8,pcm_s16be,pcm_s24be,pcm_s32be,pcm_f32be,pcm_f64be
--enable-demuxer=ogg,matroska,wav,flac,mov,mp3,aac,aiff
--enable-parser=flac,opus,vorbis,mpegaudio,aac,aac_latm
--enable-swresample --enable-avformat --enable-avcodec --enable-avutil
--enable-static --disable-shared --enable-pic --enable-small
--enable-cross-compile --target-os=darwin --arch=arm64
```

- **Autodetect.** `--disable-autodetect` matters. Without it, configure finds VideoToolbox and, on
  the macOS slice, Homebrew's X11 and SDL2, and libavutil's hwcontext drags them into the link.
- **System libraries.** zlib and iconv are the two kept; the package links both (`-lz -liconv`).
- **Byte input.** No network protocols are built. Bytes arrive through the AVIO callbacks.

## Tests

```sh
cd ios/Playback && swift test                       # macOS, ~20 s, 95 tests
# optional, on a simulator (the xcframework has no x86_64 slice):
xcodebuild -scheme S2Playback-Package -destination 'platform=iOS Simulator,name=<iPhone>' \
  ARCHS=arm64 ONLY_ACTIVE_ARCH=YES test
```

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

`FFmpegMusicDecodeTests` covers:

- FLAC decodes all 22,050 frames. Opus decodes about 1 s with pre-skip trimmed.
- Opus resamples to 44.1 kHz.
- A FLAC seek is frame-exact.
- Mono is spread to both sides at full level.

`PCMProcessorTests` pins the limiter's latency compensation. The fixtures `tone-44k.flac` (14 KB)
and `tone-48k.opus` (9 KB) were made with:

```sh
ffmpeg -f lavfi -i "sine=frequency=440:sample_rate=44100:duration=0.5" -af "volume=4,pan=stereo|c0=c0|c1=c0" -sample_fmt s16 -c:a flac tone-44k.flac
ffmpeg -f lavfi -i "sine=frequency=440:sample_rate=48000:duration=1"   -af "volume=4,pan=stereo|c0=c0|c1=c0" -c:a libopus -b:a 48k tone-48k.opus
```

## LGPL notes

The xcframework is plain **LGPL v2.1+**:

- no `--enable-gpl`, `--enable-version3` or `--enable-nonfree`;
- no external codec libraries. Opus and Vorbis use FFmpeg's native decoders, not libopus or
  libvorbis.

Linking it statically into an App Store binary carries the LGPL's relinking obligation. The app
must:

1. Ship the licence text (`COPYING.LGPLv2.1`, copied into the xcframework) and an attribution in
   its acknowledgements screen.
2. Offer the FFmpeg source it used: the tag and flags in `VERSION.txt`, plus this script.
3. Offer the object files, or equivalent, that let a user relink the app against a modified FFmpeg.
   The usual form is a written offer to provide the app's object files on request.

Do not add a GPL-only component (for example `--enable-gpl` or libx264-style externals) or a
`nonfree` one. If a relink offer is unacceptable, the alternative is to ship FFmpeg as a dynamic
framework.

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
- **No audio session.** No `AVAudioSession` work, interruptions or route callbacks toward Kotlin;
  the app owns the session (phase 6 step 7).
