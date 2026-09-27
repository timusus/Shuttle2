package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.shuttle.model.Song
import kotlinx.coroutines.flow.Flow

/**
 * Keeps a remote song on the device for offline playback (S4 in docs/architecture/ios-port/phase-4-platform-seams.md):
 * Android's `ServerSongDownloader` asks the song's provider for a download URL and queues it with the downloads
 * module. iOS has no downloads yet; `PlatformFeatures.offlineDownloads` hides the actions there.
 */
interface SongDownloader {
    /** Queues [song]'s download; false if its provider couldn't give a download URL (e.g. the server's auth failed). */
    suspend fun download(song: Song): Boolean

    fun remove(song: Song)

    /** The paths on the device or on their way there: queued, downloading, completed or stopped, but not failed. */
    fun observeHeldPaths(): Flow<Set<String>>
}
