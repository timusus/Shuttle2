package com.simplecityapps.shuttle.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.simplecityapps.mediaprovider.AggregateMediaInfoProvider
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.downloads.DownloadPreferences
import com.simplecityapps.shuttle.downloads.SongDownloadManager
import com.simplecityapps.shuttle.downloads.SongDownloadRepository
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Debug-build-only: downloads and removes songs over `adb shell am broadcast`, until the UI for it
 * lands. Replies on logcat tag `S2Debug` like [DebugPlaybackReceiver], so `support/scripts/s2-debug.sh`
 * drives it too; the debug-receivers skill documents the actions.
 *
 * A download uses the song's current stream URI, which is what playback would fetch.
 */
@AndroidEntryPoint
class DebugDownloadReceiver : BroadcastReceiver() {
    @Inject
    lateinit var songRepository: SongRepository

    @Inject
    lateinit var mediaInfoProvider: AggregateMediaInfoProvider

    @Inject
    lateinit var songDownloadManager: SongDownloadManager

    @Inject
    lateinit var songDownloadRepository: SongDownloadRepository

    @Inject
    lateinit var downloadPreferences: DownloadPreferences

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        val action = intent.action?.removePrefix(DebugPlaybackReceiver.ACTION_PREFIX) ?: return
        val pendingResult = goAsync()
        scope.launch {
            try {
                val detail = handle(context, action, intent)
                Log.i(TAG, "$action ok${detail?.let { ": $it" }.orEmpty()}")
            } catch (e: Exception) {
                Log.e(TAG, "$action error: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handle(
        context: Context,
        action: String,
        intent: Intent
    ): String? = when (action) {
        "DOWNLOAD_SONG" -> {
            val song = song(intent)
            val uri = mediaInfoProvider.getMediaInfo(song).path
            songDownloadManager.download(song, uri)
            "${song.id} ${song.name} (${song.path})"
        }

        "REMOVE_DOWNLOAD" -> {
            val song = song(intent)
            songDownloadManager.remove(song)
            "${song.id} ${song.name}"
        }

        "DOWNLOAD_WIFI_ONLY" -> {
            check(intent.hasExtra("enabled")) { "--ez enabled true|false is required" }
            downloadPreferences.wifiOnly = intent.getBooleanExtra("enabled", true)
            "wifiOnly=${downloadPreferences.wifiOnly}"
        }

        "DUMP_DOWNLOADS" -> {
            val downloads = songDownloadRepository.observeDownloads().first()
            val files = File(context.filesDir, "downloads").walk().filter { it.isFile }.toList()
            JSONObject()
                .put("wifiOnly", downloadPreferences.wifiOnly)
                .put("cacheFiles", files.size)
                .put("cacheBytes", files.sumOf { it.length() })
                .put(
                    "downloads",
                    JSONArray(
                        downloads.map { download ->
                            JSONObject()
                                .put("path", download.path)
                                .put("state", download.state.name)
                                .put("progress", download.progress.toDouble())
                                .put("bytesDownloaded", download.bytesDownloaded)
                                .put("contentLength", download.contentLength)
                        }
                    )
                ).toString()
        }

        else -> error("unknown action")
    }

    /** The song with `--el song_id N`, or without it the first song from a remote provider. */
    private suspend fun song(intent: Intent): Song {
        val songs = songRepository.getSongs(SongQuery.All()).firstOrNull().orEmpty()
        return if (intent.hasExtra("song_id")) {
            val id = intent.getLongExtra("song_id", -1)
            checkNotNull(songs.firstOrNull { it.id == id }) { "no song with id $id" }
        } else {
            checkNotNull(songs.firstOrNull { it.mediaProvider.remote }) { "the library has no remote songs; seed a remote provider first" }
        }
    }

    companion object {
        private const val TAG = "S2Debug"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }
}
