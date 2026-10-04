# Local import benchmark (baseline, PARTIAL)

Status: incomplete. The scripted 3x runs of the foreground first-import, no-change and 50-touched scenarios were blocked by the
auto-mode classifier before they ran (it flagged the batch with no explanation; the loop includes `pm clear` of the `.dev` app
and `touch` of files in S2Bench). Only the numbers below were collected. Re-run the missing rows with the steps at the end.

## Setup

- Device: Pixel 8 Pro (husky), serial 37211FDJG009GS, owner's phone. Touched only `com.simplecityapps.shuttle.dev` and `/sdcard/Music/S2Bench`.
- Build: debug APK from 542133370 (origin/main at the time), versionName 2026.09.23.
- Provider: MediaStore (`media_providers=1`). The S2 scanner (TagLib/Shuttle) provider needs a SAF folder pick (UI taps), so it was not measured.
- Library: 1090 files staged in `/sdcard/Music/S2Bench` (7.2 GB: 250 FLAC, 800 MP3, 40 M4A; no Opus on the server), random sample by
  `shuf` with a fixed seed, files up to 20 MB (FLAC) / 7 MB (MP3) / 12 MB (M4A). The brief asked for ~3,000 tracks, but the phone had only 11 GB
  free and the server's FLACs average 38 MB. The app's library after import holds 3144 songs because the MediaStore provider also imports the owner's
  existing music (~2,050 tracks), so the imported library is ~3,100 tracks. MediaStore confirmed all 1090 S2Bench files via
  `content call --uri content://media/ --method scan_volume --arg external_primary`.
- Timing: logcat tag `MediaImporter`, line `Import complete in <ms>` (existing log, no code change). Memory: `dumpsys meminfo <pid>` TOTAL PSS sampled every ~2 s.

## Findings so far

| Scenario | Result |
|---|---|
| First import, app launched in foreground (1 run) | 8.7 s; peak PSS 496 MB |
| Follow-up pass straight after it (nothing changed, same run) | 5.0 s |
| First import, app process started only by the debug broadcast (background, 2 runs) | 63.9 s, 63.7 s; peak PSS 230-233 MB |
| Follow-up pass after those (nothing changed) | 56.3 s, 53.3 s |

Caveats:

- A process started only by the `IMPORT` broadcast is a cached background process: ~7x slower, and in one run Android froze and killed it
  mid-import ("excessive binder traffic during cached"; no import completed, twice). Always launch `MainActivity` first, as the script below does.
- Launching the app triggers its own import; the `IMPORT` broadcast then requests a follow-up pass, so each "first import" run yields two timings
  (full, then no-change).
- The 8.7 s foreground figure is one run, not a median; do not quote min/max yet.
- No ANR or jank was observed, but the screen was not watched.

## Re-run

```bash
export PATH=$PATH:~/Library/Android/sdk/platform-tools ANDROID_SERIAL=<serial>
./gradlew :android:app:assembleDebug && adb install -r android/app/build/outputs/apk/debug/app-debug.apk
# (a) per run: pm clear, grant READ_MEDIA_AUDIO, write prefs (media_providers=1, changelog_show_on_launch=false) via run-as,
#     am start MainActivity, support/scripts/s2-debug.sh IMPORT, then wait for two "Import complete in" lines in `adb logcat -d | grep MediaImporter`
# (b) no clear: s2-debug.sh IMPORT with the app in the foreground
# (c) touch 50 files in /sdcard/Music/S2Bench, scan_volume (command above), wait ~20 s, IMPORT
```
