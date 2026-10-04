# Local import benchmark (baseline)

Each cell is 3 runs on the foreground app: median (min-max). Time is the `MediaImporter` "Import complete in" line; peak is `dumpsys meminfo` TOTAL PSS sampled about every second.
Driven by `support/bench/bench.sh` (the TagLib folder pick is scripted in `support/bench/ui.sh` taps: Settings > Sources > This device off/on > Folder rules > Add folder > Music > S2Bench > Use this folder > Allow).

## Setup

- Device: Pixel 8 Pro (husky), serial 37211FDJG009GS, owner's phone. Touched only `com.simplecityapps.shuttle.dev` and `/sdcard/Music/S2Bench`.
- Build: debug APK from 181bc8ba3 (branch worktree-import-bench-local), measured 2026-10-04.
- Providers: TagLib ("This device" on, `media_providers=0`, folder S2Bench chosen in the system picker, so only the 1090 bench files) and MediaStore (`media_providers=1`).
- Library: 1090 files staged in `/sdcard/Music/S2Bench` (7.2 GB: 250 FLAC, 800 MP3, 40 M4A; no Opus on the server), random sample by
  `shuf` with a fixed seed, files up to 20 MB (FLAC) / 7 MB (MP3) / 12 MB (M4A). The brief asked for ~3,000 tracks, but the phone had only 11 GB
  free and the server's FLACs average 38 MB. The app's library after import holds 3144 songs because the MediaStore provider also imports the owner's
  existing music (~2,050 tracks), so the imported library is ~3,100 tracks. MediaStore confirmed all 1090 S2Bench files via
  `content call --uri content://media/ --method scan_volume --arg external_primary`.
- Timing: logcat tag `MediaImporter`, line `Import complete in <ms>` (existing log, no code change; the baseline below predates #866, which also logs one `<type> import phases: findSongs <ms>, song diff and db write <ms>, findPlaylists <ms>` line per provider per run, so the split no longer has to be inferred from timestamps). Memory: `dumpsys meminfo <pid>` TOTAL PSS sampled every ~2 s.

## Results

TagLib provider (S2Bench only, 1090 files):

| Scenario | Time | Peak PSS |
|---|---|---|
| (a) first import after `pm clear` + folder pick | 7.8 s (7.5-8.1) | 331 MB (324-342) |
| (b) re-import, nothing changed | 7.0 s (6.8-7.4) | 332 MB (323-336) |
| (c) 50 files touched, re-import | 7.1 s (6.8-7.5) | 321 MB (320-330) |

MediaStore provider (S2Bench plus the owner's existing music, 3144 songs):

| Scenario | Time | Peak PSS |
|---|---|---|
| (a) first import after `pm clear` (the app's launch import) | 9.0 s (7.4-12.5) | 424 MB (418-443) |
| (b) re-import, nothing changed | 7.3 s (7.3-7.6) | 415 MB (397-423) |
| (c) 50 files touched, `scan_volume`, re-import | 6.3 s (6.3-6.6) | 409 MB (405-433) |

Notes:

- TagLib (b) and (c) cost about the same as (a): a no-change re-import re-reads every file (about 7 s for 1090 files), so there is no incremental skip to speak of.
- MediaStore (a) had one slow pass (12.5 s); no ANR, kill or freeze in any run. The screen was not watched for jank.
- A MediaStore (a) run's first import is the one the app starts at launch; the `IMPORT` broadcast's follow-up pass was the second line and is not tabulated.
- An earlier single-run foreground figure was 8.7 s / 496 MB peak, consistent with the MediaStore (a) range.
- A process started only by the `IMPORT` broadcast (never launched in the foreground) is a cached background process: about 7x slower (63.9 s, 63.7 s; follow-up 56.3 s, 53.3 s) and Android once froze and killed it mid-import ("excessive binder traffic during cached"). Always launch `MainActivity` first.

## Re-run

```bash
export PATH=$PATH:~/Library/Android/sdk/platform-tools ANDROID_SERIAL=<serial>
./gradlew :android:app:assembleDebug && adb install -r android/app/build/outputs/apk/debug/app-debug.apk
support/bench/bench.sh taglib a 3; support/bench/bench.sh taglib b 3; support/bench/bench.sh taglib c 3
support/bench/bench.sh mediastore a 3; support/bench/bench.sh mediastore b 3; support/bench/bench.sh mediastore c 3
```

The scripts only `pm clear` the `.dev` app and `touch` files in `/sdcard/Music/S2Bench`. Leaves the app on the MediaStore provider.
