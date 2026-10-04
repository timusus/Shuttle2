# Offline downloads

Songs from Jellyfin, Emby and Plex can be downloaded to play without a connection. Android has had this
for a while; iOS gets it before 1.0 (#759). This note is about how the work is split between shared code
and each platform, and why iOS keeps its record of downloads differently from Android.

## What's shared

| Piece | Where | What it does |
|---|---|---|
| `StreamUrlProvider.downloadSource(song)` | `:android:mediaprovider:core` commonMain, overridden by each server's `*StreamUrlProvider` | Where a song's file comes from, and the MIME type of what's there. Jellyfin and Emby give the original file. Plex gives the part file when the player can decode it, otherwise an uncapped progressive transcode. Android's `*MediaInfoProvider.downloadInfo` wraps it. |
| `SongDownloader` | `:android:presentation` commonMain | The port the shared actions talk to: `download(song)`, `remove(song)`, `observeHeldPaths()`. |
| `DownloadSongs`, `MediaActionHandler` | `:android:presentation` commonMain | The Download and Remove Download actions. Download is gated by `TryDownloadFromServer` (Pro or the trial, otherwise the paywall). They report `DownloadQueued`, `DownloadFailed` and `DownloadRemoved`. |
| `AvailableMediaActions`, `downloadActions(songs, heldPaths)` | `:android:presentation` commonMain | Which of the two actions some songs offer: Download while any remote song isn't held, Remove Download while any is. Hidden unless `PlatformFeatures.offlineDownloads`. |

Downloads are keyed by `Song.path` (`jellyfin://item/...`) on both platforms, so they survive a re-import
that renumbers song ids.

## Android

`ServerSongDownloader` binds `SongDownloader` to Media3's `DownloadManager`. Media3's download index and
its cache are the record: `SongDownloadRepository` maps the index into `SongDownload` states (Queued,
Downloading, Completed, Failed, Stopped, Removing), and playback reads a completed download straight from
the cache. `DownloadFallbackObserver` retries a 401 or 403 once with the stream URL. The Wi-Fi-only
setting and storage requirements are `DownloadRequirementsManager`'s.

## iOS

`OfflineDownloads` (`:shared` commonMain, `shared/.../downloads/`) binds `SongDownloader` on iOS. It asks the
song's `StreamUrlProvider` for the `DownloadSource`, hands the fetch to a `DownloadTransport`, and holds each
song's state (`Downloading` with progress, `Completed`, `Failed`) in a `StateFlow`. A failed download isn't
held, so it offers Download again. `summary(songs)` gives a detail screen its indicator (Downloading,
Downloaded) and which actions to offer, by the shared `downloadActions` rule.

The transport is `UrlSessionDownloads` (iosMain), over a background `URLSession`
(`<bundle id>.downloads`). iOS runs those downloads out of process, so they carry on while the app is
suspended or killed. Each task's `taskDescription` carries the MIME type and the song's path. When a task
finishes, its file is moved into `Application Support/Downloads/` (excluded from iCloud backup) as
`<base64url(Song.path)>.<ext>` (`DownloadFileNames`). The extension comes from the MIME type, falling back to
the server's suggested name, so the engine's format probe has something to lean on. A non-2xx response or
a failed move reports the download failed.

**The files are the record.** At launch `restore()` lists the directory: every file there is a completed
download, and its name gives back the path. The session's still-running tasks are found the same way
(`getTasksWithCompletionHandler`) and reported as running. The brief asked for the state "in the shared
DB"; it isn't, deliberately:

- `MediaDatabase` is shared with Android. A downloads table there is a schema bump and a migration on
  both platforms for something only iOS would write to, while Android's record stays Media3's index.
- A table can say a file is there when iOS has removed it, or miss one a background session finished
  while the app wasn't running. The directory listing and the session can't disagree with themselves.
  This is the same choice as Android, where Media3's own index is the record, not a Room table.

**Playback.** `SongStreamResolver` asks `OfflineDownloads.fileUrl(path)` for a server song. A completed
download plays from its `file://` URL, from the start, offline. It still asks the Pro gate
(`serverStreamAccess`) first, as Android's `ServerStreamPolicy` is asked before a download plays.

**Screens.** Album and playlist detail screens offer Download and Remove Download in their toolbar menu
and in each track's context menu (`DownloadMenuItems`), and the hero shows Downloading or Downloaded
(`DownloadStatusLabel`). A failed download raises an alert. Queued and removed downloads show no notice:
the hero says what happened.

**Tests.** `OfflineDownloadsTest`, `DownloadFileNamesTest` and `SongStreamResolverTest` (commonTest, run on
the JVM with `:shared:testAndroidHostTest`). An isolated test graph (`IosStorage(isolatedName)`) gets a plain
session and its own directory under tmp, so tests never touch the app's downloads.

## Not done yet

Each is its own issue (label `design`):

- Retrying a 401 or 403 with the stream URL, as Android's `DownloadFallbackObserver` does.
- `application(_:handleEventsForBackgroundURLSession:completionHandler:)`, so iOS can relaunch the app in
  the background to finish a download. Without it the files still arrive, and are picked up at the next
  launch.
- Download on Wi-Fi only (Android's setting), and a cellular rule.
- A Downloaded filter in the Library, storage management, download quality.
- A badge on each downloaded song's row in the lists.
- iOS plurals (`.stringsdict`) for the download messages.
