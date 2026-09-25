package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.AggregateMediaInfoProvider
import com.simplecityapps.shuttle.downloads.SongDownloadManager
import com.simplecityapps.shuttle.model.Song
import javax.inject.Inject

/**
 * Downloads a selection's remote songs for offline playback ([download] true), or removes their downloads ([download]
 * false). Local songs are skipped: they're already on the device.
 */
class DownloadSongs @Inject constructor(
    private val songDownloadManager: SongDownloadManager,
    private val mediaInfoProvider: AggregateMediaInfoProvider,
    private val resolveSongs: ResolveSongs,
) {
    /** [changed] were queued for download or removal; [failed] had no download URL (e.g. the server's auth failed). */
    data class Result(val changed: List<Song>, val failed: List<Song>)

    suspend operator fun invoke(selection: MediaSelection, download: Boolean = true): Result {
        val songs = resolveSongs(selection).filter { it.mediaProvider.remote }
        if (!download) {
            songs.forEach { songDownloadManager.remove(it) }
            return Result(songs, emptyList())
        }
        val changed = mutableListOf<Song>()
        val failed = mutableListOf<Song>()
        songs.forEach { song ->
            val uri = mediaInfoProvider.downloadUri(song)
            if (uri == null) {
                failed += song
            } else {
                songDownloadManager.download(song, uri)
                changed += song
            }
        }
        return Result(changed, failed)
    }
}
