package com.simplecityapps.shuttle.downloads

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.DownloadService
import com.simplecityapps.shuttle.di.ApplicationContext
import com.simplecityapps.shuttle.downloads.service.SongDownloadService
import com.simplecityapps.shuttle.model.Song
import dev.zacsweers.metro.Inject
import timber.log.Timber

/** Starts and removes song downloads. Their state is read through [SongDownloadRepository]. */
interface SongDownloadManager {
    /**
     * Downloads [song] from [uri], keyed by the song's path, recorded as [mimeType] — the MIME type of what's
     * actually at [uri], which for a transcoded download isn't [Song.mimeType].
     */
    fun download(
        song: Song,
        uri: Uri,
        mimeType: String
    )

    /**
     * Downloads the song at [path] again from [uri], discarding whatever an earlier attempt cached first: the new
     * URL may serve a different stream (the static stream rather than the original, or a transcode), and bytes
     * from the two stitched together are corrupt.
     */
    fun restart(
        path: String,
        mimeType: String,
        uri: Uri
    )

    fun remove(song: Song)

    /** Removes the download at [path], the key [download] recorded it under. */
    fun remove(path: String)

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
        uri: Uri,
        mimeType: String
    ) = send("download") {
        DownloadService.sendAddDownload(context, SongDownloadService::class.java, downloadRequest(song.path, mimeType, uri), true)
    }

    override fun restart(
        path: String,
        mimeType: String,
        uri: Uri
    ) = send("restart") {
        // Media3 restarts a download re-added while it is being removed, once its cached spans are gone.
        DownloadService.sendRemoveDownload(context, SongDownloadService::class.java, path, true)
        DownloadService.sendAddDownload(context, SongDownloadService::class.java, downloadRequest(path, mimeType, uri), true)
    }

    override fun remove(song: Song) = remove(song.path)

    override fun remove(path: String) = send("remove") {
        DownloadService.sendRemoveDownload(context, SongDownloadService::class.java, path, true)
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
