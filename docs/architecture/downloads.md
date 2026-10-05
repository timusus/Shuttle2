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
the server's suggested name, so the engine's format probe has something to lean on. The file is moved in
under a staging name first, then replaces any earlier one (`replaceItemAt`), so a failed move never loses
a good download. A non-2xx or empty response, or a failed move, reports the download failed.

The transport keeps each path's wanted task. Removing a download cancels that task there and then, and
then cancels whatever else the session still runs for the path (a task from an earlier launch not found
yet), except a download started for it since. A superseded task's progress and outcome are ignored, so a
quick remove and re-download can't cancel or fail the new one.

**Relaunches.** When the session's downloads finish while the app isn't running, iOS relaunches it in the
background and calls the app delegate's `application(_:handleEventsForBackgroundURLSession:completionHandler:)`
(`AppDelegate` in `S2App.swift`). `AppGraph.initialize()` builds `OfflineDownloads` at every launch, so
the session is reattached before its events arrive; the delegate hands the completion handler to
`UrlSessionDownloads.handleBackgroundEvents`, which calls it on the main queue from
`urlSessionDidFinishEvents(forBackgroundURLSession:)`. A finished download reported this way arrives as a
completion `OfflineDownloads` hasn't heard of (no `onRunning` first), and is kept. Only a song the user
removed since its last download has a late file deleted again.

**The files are the record.** At launch `restore()` lists the directory: every file there is a completed
download, and its name gives back the path. The session's still-running tasks are found the same way
(`getTasksWithCompletionHandler`) and reported as running. The brief asked for the state "in the shared
DB"; it isn't, deliberately:

- `MediaDatabase` is shared with Android. A downloads table there is a schema bump and a migration on
  both platforms for something only iOS would write to, while Android's record stays Media3's index.
- A table can say a file is there when iOS has removed it, or miss one a background session finished
  while the app wasn't running. The directory listing and the session can't disagree with themselves.
  This is the same choice as Android, where Media3's own index is the record, not a Room table.

The file names carry no server id: `Song.path` is `jellyfin://item/<id>`, the same shape for every
Jellyfin server. Switching to another server of the same kind keeps the old server's files under the new
library's paths, and a song whose item id matches would play the old server's file. Jellyfin's ids are
GUIDs, so that's unlikely there; Emby's and Plex's are small integers, so it's likely after a switch.
Android's Media3 index has the same key. Removing a server should remove its downloads (see Not done
yet).

**Playback.** `SongStreamResolver` asks `OfflineDownloads.fileUrl(path)` for a server song. A completed
download plays from its `file://` URL, from the start, offline, without asking the Pro gate
(`serverStreamAccess`): songs already downloaded never disappear when the trial lapses, as Android's
`EntitledServerStreamPolicy` allows a completed download before it asks the gate. Streaming still asks. A
completed download whose file has gone or is empty is forgotten, and the song streams instead.

**Pro.** Starting a download is gated the same way on both platforms: `DownloadSongs` asks
`TryDownloadFromServer`, which iOS binds to the StoreKit-fed `ServerAccessGate`
(`IosEntitlementModule`), so without Pro or the trial Download opens the paywall and nothing is fetched.
Removing a download never asks.

**Screens.** Album and playlist detail screens offer Download and Remove Download in their toolbar menu
and in each track's context menu (`DownloadMenuItems`), and the hero shows Downloading or Downloaded
(`DownloadStatusLabel`). A failed download raises an alert. Queued and removed downloads show no notice:
the hero says what happened.

**Where downloads show.** A small badge (`DownloadBadgeView`) sits on every `SongRow`, from one observation of
`OfflineDownloads.downloads` at the root (`observingDownloadBadges`, an environment value, redrawn only when a
download starts, finishes, fails or goes, not on progress). Song menus in Library > Songs, Search, genres and smart
playlists offer Download and Remove Download too (`SongRowMenu`). Library > Songs has a Downloaded toggle in its
toolbar, shown once a song is downloaded (Android's Library has no such filter yet); with it on, the list, Play and
Shuffle cover only the downloaded songs. Settings > Downloads (`DownloadsView`) shows the count and size on disk
(the completed files' own sizes), Remove All (`OfflineDownloads.removeEverything`, behind a confirmation), the
running downloads and the failed ones, which can be retried (through the gated Download action) or dismissed.
`DownloadRequests` keeps each requested song's id and name by path across launches, so a download that fails while
the app isn't running is listed with its name and its song is loaded from the library again to retry it
(`OfflineDownloads.loadRequestedSong`). A record goes when its download completes, is removed or is dismissed.

**401/403.** `UrlSessionDownloads` reports the HTTP status of a refused download, and `OfflineDownloads` retries it
once from `StreamUrlProvider.downloadFallback` (the stream URL; Jellyfin and Emby also remember a 403 as revoked
download permission), as Android's `DownloadFallbackObserver` does. A second refusal fails the download.

**Wi-Fi only.** `DownloadSettings.WifiOnly` (`:android:core`, shared with Android, default on) is read when a
download starts and set on its request as `allowsCellularAccess` and `allowsExpensiveNetworkAccess`, so the background
session holds it until Wi-Fi is back. A download already started keeps the rule it began with. Settings > Sources has
the switch.

**Tests.** `OfflineDownloadsTest`, `DownloadFileNamesTest` and `SongStreamResolverTest` (commonTest, run on
the JVM with `:shared:testAndroidHostTest`). An isolated test graph (`IosStorage(isolatedName)`) gets a plain
session and its own directory under tmp, so tests never touch the app's downloads.

## Not done yet

Each is its own issue (label `design`):

- Removing a server's downloads with the server, and a server id in the file names (see above).
- Download quality.
- Plurals (`.stringsdict`) for the other count strings; only the download-failed alert has one.
