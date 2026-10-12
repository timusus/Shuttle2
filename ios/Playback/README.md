# S2Playback

S2's playback glue over [AudioPlaybackKit](https://github.com/timusus/AudioPlaybackKit) (pinned `exact:` in
`Package.swift`, epic #581). The app plays through AudioPlaybackKit's `Player` (#1020): `KitAudioPlayer`
(`ios/S2/Platform/Audio`) is the Kotlin `IosAudioPlayer` over it, and the Player owns decode, gapless joins, the
audio session (interruptions, route-change pauses, media-services resets) and its own position and state. Decode is
its `PlaybackDecode`, with its one dynamic, LGPL-only FFmpeg.framework (#957). It is a Swift package for iOS 17. It
also has a macOS 14 platform, so `swift test` runs on the Mac without a simulator. This package holds:

- the streaming glue over AudioPlaybackKit's `PlaybackStreaming` (#958);
- the EQ's DSP, not yet wired into the Player;
- `S2Tags` (product `S2Tags`): the local-file tag reader (`AudioFileTags`, `CS2Tags/tag_read.c`) on the
  package's `FFmpeg` product, so the app links exactly one FFmpeg;
- `S2PlaybackTestSupport`: the fixtures, a loopback HTTP origin and AudioPlaybackKit's `PlayerTestRig` for S2Tests.

**Streaming (#958).** `StreamStore.reader` hands the Player a file, a kept download, or a new
`GrowingFileByteSource` downloading into `StreamStore`, S2's `GrowingFileStore` under Caches (512 MiB, swept of
partials at launch). A track downloaded whole is kept, keyed on `StreamCacheKey` (the URL less its per-play session
id and token), and plays from the file next time. The server's custom headers and trusted certificate come from
`ServerConnections.policy` as the source's connection policy. `StreamWarmUp` keeps a connection open to the last
stream's server (#822). `StreamReadAhead` (a minute of audio at the library's bitrate on an expensive path) is not passed to the
source yet.

**DSP.** `PCMProcessor` applies, in order: the track's ReplayGain, the EQ preamp, one biquad per band per channel,
then a stereo-linked lookahead limiter (ceiling −0.1 dBFS, attack 5 ms, release 100 ms). Coefficients come from
Kotlin, 5 per band (b0, b1, b2, a1, a2), computed for a fixed 48 kHz that Kotlin reads through
`IosAudioPlayer.engineSampleRate()`; Swift never designs a filter. The limiter's lookahead delay is compensated, so
output frame n is input frame n, and below the ceiling the chain is bit-exact.

## Provenance: copied, adapted, new

Copied from Shuttle Podcasts at `podcasts@9ee6e0954`. Each file's first line names its upstream
path. The only edits are these renames: the log subsystem becomes `com.simplecityapps.shuttle2`,
the `SpineNative` import is dropped, and the test imports change. They are kept diffable so a later
shared package can take them back.

- **Copied**: test support `LoopbackMediaServer`, `PlaybackTestMedia`, `tone.mp3`, `tone_moov_last.m4a`. Streaming
  tests serve through AudioPlaybackKit's own `LoopbackMediaServer` (`PlaybackStreamingTestSupport`) instead.
- **Adapted.** S2 additions are marked `S2:`.
  - `Biquad` adds `adoptState`; its factories were dropped, as the shared Kotlin EQ designs the bands.
  - `LookaheadLimiter` is now stereo-linked and frame-interleaved, and its window includes the
    emitted frame.
- **New**: `Streaming/`, `DSP/PCMProcessor.swift`, `S2Tags` (#590) and the music fixtures.

## FFmpeg

FFmpeg comes from AudioPlaybackKit: its `FFmpeg` product, one dynamic `Frameworks/FFmpeg.xcframework`
(`FFmpeg.framework` per slice) committed in that repo (iOS arm64, iOS Simulator arm64 and macOS arm64 slices; the macOS one is for
`swift test`). Nothing is built here, and a new worktree needs no step: SwiftPM resolves the pinned tag.

- **Version.** FFmpeg **n7.1** plus one patch, `0001-mp3dec-keep-xing-frames-when-size-unknown.patch`.
  The xcframework's `VERSION.txt` records the tag, the configure flags and the patches; AudioPlaybackKit's
  `scripts/build-ffmpeg.sh` builds it, and its `scripts/ffmpeg-patches/` holds the patch. Its formats
  are a superset of the music formats S2 plays (it adds the `loas` demuxer).
- **Linking.** `S2Playback` links it through `PlaybackDecode`, and `CS2Tags` depends on the `FFmpeg`
  product directly, with the system libraries FFmpeg needs (`z`, `iconv`, CoreFoundation,
  CoreMedia, CoreVideo, VideoToolbox). The app links one FFmpeg, the dynamic `FFmpeg.framework`, and
  embeds it in `S2.app/Frameworks` so a user can relink against a modified build (LGPL-2.1 section 6).
- **Bumping it.** Rebuild and tag in AudioPlaybackKit, then change the `exact:` pin in `Package.swift`
  and the tag in `SettingsContent.ffmpegSourceURL` and the Acknowledgements pane together.
- **Byte input.** No network protocols are built. Bytes arrive through the AVIO callbacks.

## Tests

```sh
ios/scripts/test.sh --package    # swift test in ios/Playback, on the Mac: S2PlaybackTests and S2TagsTests
ios/scripts/test.sh              # the app's S2 scheme on an available iPhone simulator
```

Nothing skips: every test decodes through the real FFmpeg. Playback itself is tested in S2Tests
(`KitAudioPlayerTests` and the controller-level suites) over `PlayerTestRig`. Here: `PCMProcessorTests` pins the
DSP chain and the limiter's latency compensation; `ServerConnectionPolicyTests`, `StreamCacheKeyTests`,
`StreamInfoProbeTests`, `StreamReadAheadTests` and `StreamWarmUpTests` cover the streaming glue;
`AudioFileTagsTests` covers the tag reader. The music fixtures were made with:

```sh
S="sine=frequency=440:sample_rate=44100"; A="volume=4,pan=stereo|c0=c0|c1=c0"
ffmpeg -f lavfi -i "$S:duration=0.5"  -af "$A" -sample_fmt s16 -c:a flac tone-44k.flac          # 14 KB
ffmpeg -f lavfi -i "sine=frequency=440:sample_rate=48000:duration=1" -af "$A" -c:a libopus -b:a 48k tone-48k.opus  # 9 KB
ffmpeg -f lavfi -i "$S:duration=0.5"  -af "$A" -c:a vorbis -strict -2 -q:a 3 tone-44k.ogg         # 5 KB
ffmpeg -f lavfi -i "$S:duration=0.5"  -af "$A" -sample_fmt s16p -c:a alac tone-44k-alac.m4a      # 15 KB
ffmpeg -f lavfi -i "$S:duration=0.25" -af "$A" -c:a pcm_s16be tone-44k.aiff                      # 43 KB
ffmpeg -f lavfi -i "sine=frequency=440:sample_rate=48000:duration=0.25" -af "$A" -c:a pcm_s24le tone-48k-s24.wav  # 70 KB
# chirp: a linear sweep whose phase names every sample, as an aevalsrc at the rate given
ffmpeg -f lavfi -i "$(chirp 44100)" -c:a aac -b:a 128k chirp-44k-aac.m4a                      # 33 KB
ffmpeg -f lavfi -i "$(chirp 44100)" -c:a libmp3lame -b:a 128k chirp-44k.mp3                   # 32 KB
ffmpeg -f lavfi -i "$(chirp 48000)" -c:a libopus -b:a 64k chirp-48k.opus                      # 18 KB
```

## LGPL notes

AudioPlaybackKit's FFmpeg is plain **LGPL v2.1+**:

- no `--enable-gpl`, `--enable-version3` or `--enable-nonfree`;
- no external codec libraries beyond the system zlib. Opus and Vorbis use FFmpeg's native decoders,
  not libopus or libvorbis.

FFmpeg is a separate dynamic `FFmpeg.framework`, embedded in `S2.app/Frameworks` (AudioPlaybackKit
ADR-0001, #957). LGPL v2.1 §6 asks that a user can replace FFmpeg with a modified build, and a
dynamic framework in the bundle is that: the app never contains FFmpeg's code in its own binary. The
sources are public too: Shuttle2 (`timusus/Shuttle2`, this app with `ios/project.yml` and its build
scripts) and AudioPlaybackKit (`timusus/AudioPlaybackKit`, the decoder and `scripts/build-ffmpeg.sh`).
The app carries:

1. **The notice**, in the app's Settings pane (`ios/S2/Settings.bundle`: Settings › Shuttle Music ›
   Acknowledgements). It gives FFmpeg's name, version and licence, the source (the n7.1 tag, the patch
   and AudioPlaybackKit's build script) and how to relink. The pane links to the full LGPL v2.1 text,
   and also acknowledges AudioPlaybackKit, used under licence from its copyright holder. Settings ›
   About has an "FFmpeg source code" link to the build script at the pinned tag. Android's licences
   are generated from Gradle dependencies (AboutLibraries), which can't see FFmpeg, so the Settings
   bundle is the one place.
2. **How to relink.** Build a modified FFmpeg with AudioPlaybackKit's `scripts/build-ffmpeg.sh`
   (`FFMPEG_SRC` points it at a modified checkout; the flags are in the script and `VERSION.txt`),
   then either replace `FFmpeg.framework` in the app bundle's `Frameworks` folder with it and re-sign
   the app, or rebuild AudioPlaybackKit's `FFmpeg.xcframework` and build the app against that
   checkout (point `ios/Playback/Package.swift` at it; `ios/scripts/build-framework.sh` and Xcode).

Do not add a GPL-only component (for example `--enable-gpl` or libx264-style externals) or a
`nonfree` one.
