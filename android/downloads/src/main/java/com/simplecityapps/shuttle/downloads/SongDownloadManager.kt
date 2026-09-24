package com.simplecityapps.shuttle.downloads

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.DownloadService
import com.simplecityapps.shuttle.downloads.service.SongDownloadService
import com.simplecityapps.shuttle.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import timber.log.Timber

/** Starts and removes song downloads. Their state is read through [SongDownloadRepository]. */
interface SongDownloadManager {
    /** Downloads [song] from [uri], keyed by the song's path. */
    fun download(
        song: Song,
        uri: Uri
    )

    /** Downloads the song at [path] from [uri], e.g. to retry a failed download with a fallback URL. */
    fun download(
        path: String,
        mimeType: String,
        uri: Uri
    )

    fun remove(song: Song)

    fun removeAll()

    fun setRequirements(wifiOnly: Boolean)
}

/**
 * Sends each command to [SongDownloadService] through Media3's static `DownloadService.sendXxx`
 * helpers, which apply it to the `DownloadManager` inside the service.
 *
 * Every command uses `foreground = true`, which goes through `startForegroundService`: plain
 * `startService` throws `ForegroundServiceStartNotAllowedException` from any background context on
 * Android 12+. `startForegroundService` isn't immune either: Android 12+ rejects it from most
 * background contexts too, synchronously at the call site, before the service's own guard in
 * [SongDownloadService.onStartCommand] can run (a Wi-Fi-only change applied while the app is in the
 * background hits this). [send] swallows that one exception: the `DownloadManager` persists its
 * downloads, so they resume the next time the service can legally start.
 */
@UnstableApi
class DefaultSongDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context
) : SongDownloadManager {
    override fun download(
        song: Song,
        uri: Uri
    ) = download(song.path, song.mimeType, uri)

    override fun download(
        path: String,
        mimeType: String,
        uri: Uri
    ) = send("download") {
        DownloadService.sendAddDownload(context, SongDownloadService::class.java, downloadRequest(path, mimeType, uri), true)
    }

    override fun remove(song: Song) = send("remove") {
        DownloadService.sendRemoveDownload(context, SongDownloadService::class.java, song.path, true)
    }

    override fun removeAll() = send("removeAll") {
        DownloadService.sendRemoveAllDownloads(context, SongDownloadService::class.java, true)
    }

    override fun setRequirements(wifiOnly: Boolean) = send("setRequirements") {
        DownloadService.sendSetRequirements(context, SongDownloadService::class.java, downloadRequirements(wifiOnly), true)
    }

    private inline fun send(
        operation: String,
        block: () -> Unit
    ) {
        try {
            block()
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                Timber.w(e, "Couldn't start the download service for $operation from the background; it applies on the next start")
            } else {
                throw e
            }
        }
    }
}
