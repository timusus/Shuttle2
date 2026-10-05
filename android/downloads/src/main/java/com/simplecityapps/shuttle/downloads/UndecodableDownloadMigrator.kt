package com.simplecityapps.shuttle.downloads

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import com.simplecityapps.mediaprovider.AggregateMediaInfoProvider
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.di.IoDispatcher
import com.simplecityapps.shuttle.model.MediaProviderType
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Downloads again, through the transcode the provider now gives, each completed download of a song the player can't
 * decode (ALAC, #156): they were saved as the original and play silent. Run once at start-up, it is idempotent: a
 * download made from the transcode is recorded as the transcode's MIME type, not the song's own, so it isn't picked up
 * a second time.
 */
@SingleIn(AppScope::class)
@UnstableApi
class UndecodableDownloadMigrator
@Inject
constructor(
    private val downloadManager: DownloadManager,
    private val songDownloadManager: SongDownloadManager,
    private val mediaInfoProvider: AggregateMediaInfoProvider,
    private val songRepository: SongRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    fun migrate(scope: CoroutineScope) {
        scope.launch { redownloadUndecodable() }
    }

    internal suspend fun redownloadUndecodable() {
        withContext(ioDispatcher) {
            val completed = downloadManager.downloadIndex.getDownloads(Download.STATE_COMPLETED).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(cursor.download.request)
                }
            }
            if (completed.isEmpty()) return@withContext

            val songs = MediaProviderType.entries
                .filter { it.remote }
                .flatMap { songRepository.loadProviderSongs(it) }
                .associateBy { it.path }
            for (request in completed) {
                val song = songs[request.id] ?: continue
                if (request.mimeType != song.mimeType || !mediaInfoProvider.downloadsAsTranscode(song)) continue
                val info = mediaInfoProvider.downloadInfo(song) ?: continue
                Timber.i("Downloading ${song.path} again through the transcode: the player can't decode its original")
                songDownloadManager.restart(song.path, info.mimeType, info.uri)
            }
        }
    }
}
