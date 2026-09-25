---
name: debug-receivers
description: Drive the S2 debug build's playback and queue over ADB broadcasts — play the whole library, play/pause, skip, seek, remove a queue item, toggle shuffle/repeat, dump playback state as JSON, reimport the library, download or remove a song offline and dump the downloads. Use when checking playback on an emulator or device without tapping through the UI, or when you need the exact `am broadcast` syntax.
---

# Debug Broadcast Receivers

Debug-only receivers in `android/app/src/debug/` (never in release). All are exported but guarded
by `android.permission.DUMP`, which `adb shell` holds and third-party apps don't.

**Use the wrapper**, `support/scripts/s2-debug.sh <ACTION> [am broadcast extras]`. It sends the
broadcast, waits for the receiver's reply on logcat tag `S2Debug`, prints it, and exits non-zero on
an error or when no reply arrives within `S2_DEBUG_TIMEOUT` seconds (10). It honours
`ANDROID_SERIAL` / `ANDROID_ADB_SERVER_PORT`, so on a WSL lane `eval "$(support/scripts/remote-emu.sh env)"`
first. Broadcasts carry `FLAG_INCLUDE_STOPPED_PACKAGES`, so they also reach (and start) the app
after `am force-stop`. If the WSL lane's tunnel drops mid-run ("device offline"/"device not
found"), the wrapper reconnects it once via `remote-emu.sh reconnect` and retries once before
failing.

| Action | Extras | Does |
|---|---|---|
| `PLAY_ALL` | `[--ei index N] [--es album NAME]` | Queue every library song (`SongRepository`, `SongQuery.All()`), or only album NAME's, and play from index N, via the `PlaySongs` use case the song list uses |
| `PLAY` / `PAUSE` | | `PlaybackManager.play()` / `pause()` |
| `NEXT` / `PREV` | | `skipToNext()` / `skipToPrev()` (`PREV` restarts the track past 2 s, as the UI does) |
| `SEEK` | `--el ms 20000` | `seekTo(ms)` |
| `REMOVE_QUEUE_ITEM` | `--ei position N` | `PlaybackManager.removeQueueItem`, the queue screen's "Remove from Queue" path. N indexes the queue in its displayed (shuffle-aware) order |
| `REMOVE_PLAYLIST_SONG` | `--es playlist NAME --es song TITLE` | `PlaylistRepository.removeFromPlaylist`, the playlist detail screen's per-row "Remove" path (`PlaylistDetailPresenter.remove`) |
| `REORDER_QUEUE` | `--ei from N --ei to N` | `QueueManager.move(from, to)`, the queue screen's drag-to-reorder path. Both indices are in the displayed (shuffle-aware) order |
| `SHUFFLE` | `[--ez enabled true\|false]` | Toggle, or set, the shuffle mode |
| `REPEAT` | `[--es mode off\|all\|one]` | Toggle (Off → All → One), or set, the repeat mode |
| `SPEED` | `--ef multiplier 1.5` | `PlaybackManager.setPlaybackSpeed(multiplier)` |
| `SLEEP_TIMER` | `--el seconds 3 [--ez play_to_end true\|false]` | `SleepTimer.startTimer`, the same timer the Sleep Timer dialog starts |
| `DUMP_STATE` | | Print the state as one JSON line (below) |
| `DOWNLOAD_SONG` | `[--el song_id N]` | `DebugDownloadReceiver`: download a song for offline use (default: the first remote song) from its current stream URI, keyed by `song.path` |
| `REMOVE_DOWNLOAD` | `[--el song_id N]` | Remove that song's download |
| `DOWNLOAD_WIFI_ONLY` | `--ez enabled true\|false` | Set the Wi-Fi-only download preference (default true); pushed to the DownloadManager's requirements |
| `DUMP_DOWNLOADS` | | One JSON line: `wifiOnly`, `cacheFiles`/`cacheBytes` under `filesDir/downloads`, and `downloads` (`path`, `state`, `progress`, `bytesDownloaded`, `contentLength`) |
| `IMPORT` | | Wrapper-only alias for `DebugMediaImportReceiver`: reimport the library from MediaStore. Fire-and-forget; give it a few seconds |

Replies: `<ACTION> ok[: detail]` (e.g. `PLAY_ALL ok: 5 songs from index 0`,
`REMOVE_QUEUE_ITEM ok: <removed title>`) or `<ACTION> error: <reason>`. An action not in the
manifest's intent filter is never delivered, so the wrapper times out on typos.

`DUMP_STATE` fields: `state` (`PlaybackManager.playbackState()`), `reportedState` (the
`playbackStateFlow` value), `positionMs` (`getProgress()`), `progressMs` (`progressFlow`),
`durationMs`, `savedPositionMs` (the persisted resume position), `queuePosition`, `queueSize`,
`title` (current song), `inLibrary` (false for a file opened from another app that isn't in the library,
playing as a transient song), `queueTitles` (every song name in the queue's displayed, shuffle-aware
order -- `queueTitles[queuePosition + 1]` is the item that will auto-advance to next), `shuffle`,
`repeat`, `speed` (`getPlaybackSpeed()`), `pendingLoad` (a track load in flight; read reflectively
from `PlaybackManager`'s private `LoadCoordinator`, `null` if that field moves).

```json
{"state":"Playing","reportedState":"Playing","positionMs":3225,"progressMs":3153,"durationMs":60029,"savedPositionMs":3050,"queuePosition":0,"queueSize":5,"title":"Playback One","queueTitles":["Playback One","Playback Two","Playback Three","Playback Four","Playback Five"],"shuffle":"Off","repeat":"Off","speed":1.0,"pendingLoad":false}
```

## Typical check

```bash
support/scripts/remote-emu.sh start && eval "$(support/scripts/remote-emu.sh env)"
support/scripts/remote-emu.sh install
support/scripts/seed-test-media.sh playback --skip-onboarding   # 5 x 60 s tracks, library imported
support/scripts/s2-debug.sh PLAY_ALL
support/scripts/s2-debug.sh REMOVE_QUEUE_ITEM --ei position 0   # remove the playing item
support/scripts/s2-debug.sh DUMP_STATE                          # expect Playing, title "Playback Two"
support/scripts/remote-emu.sh stop
```

Parse the JSON with `python3 -c 'import json,sys; ...'` or `jq`. Ready-made checks built on this
live in `support/maestro/` (see its README).

## Raw broadcast

```bash
adb shell am broadcast -f 32 -p com.simplecityapps.shuttle.dev \
  -a com.simplecityapps.shuttle.debug.SEEK --el ms 20000
adb logcat -d -s S2Debug:I | tail -1
```

## Adding an action

Add a branch to `DebugPlaybackReceiver.handle()` (return an optional detail string, throw with the
reason on failure), add the `<action>` to the receiver's intent filter in
`android/app/src/debug/AndroidManifest.xml`, and add a row above. Keep release code untouched: reach
state through injected classes, not new public hooks.

**Never `pm clear` a device you don't own**; on a WSL lane, `remote-emu.sh reset` is the way to
wipe the debug app.
