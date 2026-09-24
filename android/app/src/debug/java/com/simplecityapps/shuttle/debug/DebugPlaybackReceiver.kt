package com.simplecityapps.shuttle.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.ui.common.playback.PlaySongs
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Debug-build-only: drives playback and the queue over `adb shell am broadcast`, so emulator checks
 * don't have to tap through the UI. Every action replies with one line on logcat tag [TAG]:
 * `<ACTION> ok[: detail]` or `<ACTION> error: <reason>`; `DUMP_STATE` replies with the playback and
 * queue state as a single JSON line. `support/scripts/s2-debug.sh` wraps the
 * broadcast syntax; the project's debug-receivers skill documents the actions.
 *
 * Actions run on the main thread, like the UI calls they stand in for.
 */
@AndroidEntryPoint
class DebugPlaybackReceiver : BroadcastReceiver() {
    @Inject
    lateinit var playbackManager: PlaybackManager

    @Inject
    lateinit var queueManager: QueueManager

    @Inject
    lateinit var songRepository: SongRepository

    @Inject
    lateinit var playSongs: PlaySongs

    @Inject
    lateinit var playbackPreferenceManager: PlaybackPreferenceManager

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        val action = intent.action?.removePrefix(ACTION_PREFIX) ?: return
        val pendingResult = goAsync()
        scope.launch {
            try {
                val detail = handle(action, intent)
                Log.i(TAG, if (action == "DUMP_STATE") detail!! else "$action ok${detail?.let { ": $it" }.orEmpty()}")
            } catch (e: Exception) {
                Log.e(TAG, "$action error: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** Runs [action]; returns an optional detail for the reply line, or throws with the reason it failed. */
    private suspend fun handle(
        action: String,
        intent: Intent
    ): String? = when (action) {
        "PLAY_ALL" -> {
            val album = intent.getStringExtra("album")
            val songs = songRepository.getSongs(SongQuery.All()).firstOrNull().orEmpty()
                .filter { song -> album == null || song.album == album }
            check(songs.isNotEmpty()) { if (album == null) "the library has no songs; seed and import first" else "no songs on album '$album'" }
            val index = intent.getIntExtra("index", 0).coerceIn(0, songs.size - 1)
            val result = playSongs(songs, index)
            check(result is PlaySongs.Result.Success) { "playback failed: $result" }
            "${songs.size} songs from index $index"
        }

        "PLAY" -> null.also { playbackManager.play() }

        "PAUSE" -> null.also { playbackManager.pause() }

        "NEXT" -> null.also { playbackManager.skipToNext() }

        "PREV" -> null.also { playbackManager.skipToPrev() }

        "SEEK" -> {
            val ms = intent.getLongExtra("ms", -1L)
            require(ms >= 0) { "missing --el ms <position>" }
            playbackManager.seekTo(ms.toInt())
            "${ms}ms"
        }

        "REMOVE_QUEUE_ITEM" -> {
            val position = intent.getIntExtra("position", -1)
            val queueItem = requireNotNull(queueManager.getQueue().getOrNull(position)) {
                "no item at --ei position $position (queue size ${queueManager.getSize()})"
            }
            // The queue screen's "Remove from Queue" path (QueuePresenter.removeFromQueue).
            playbackManager.removeQueueItem(queueItem)
            queueItem.song.name
        }

        "SHUFFLE" -> {
            if (intent.hasExtra("enabled")) {
                val mode = if (intent.getBooleanExtra("enabled", false)) QueueManager.ShuffleMode.On else QueueManager.ShuffleMode.Off
                queueManager.setShuffleMode(mode, reshuffle = true)
            } else {
                queueManager.toggleShuffleMode()
            }
            queueManager.getShuffleMode().name
        }

        "REPEAT" -> {
            val mode = intent.getStringExtra("mode")
            if (mode != null) {
                queueManager.setRepeatMode(
                    requireNotNull(QueueManager.RepeatMode.entries.firstOrNull { it.name.equals(mode, ignoreCase = true) }) {
                        "--es mode must be off, all or one"
                    }
                )
            } else {
                queueManager.toggleRepeatMode()
            }
            queueManager.getRepeatMode().name
        }

        "DUMP_STATE" -> dumpState().toString()

        else -> throw IllegalArgumentException("unknown action")
    }

    private fun dumpState(): JSONObject {
        val currentSong = queueManager.getCurrentItem()?.song
        return JSONObject().apply {
            put("state", playbackManager.playbackState().toString())
            put("reportedState", playbackManager.playbackStateFlow.value.toString())
            put("positionMs", playbackManager.getProgress() ?: JSONObject.NULL)
            put("progressMs", playbackManager.progressFlow.value?.position ?: JSONObject.NULL)
            put("durationMs", playbackManager.getDuration() ?: JSONObject.NULL)
            put("savedPositionMs", playbackPreferenceManager.playbackPosition ?: JSONObject.NULL)
            put("queuePosition", queueManager.getCurrentPosition() ?: JSONObject.NULL)
            put("queueSize", queueManager.getSize())
            put("title", currentSong?.name ?: JSONObject.NULL)
            put("shuffle", queueManager.getShuffleMode().name)
            put("repeat", queueManager.getRepeatMode().name)
            put("pendingLoad", pendingLoad())
        }
    }

    /** Whether a track load is in flight: the player hasn't made the current item ready yet. */
    private fun pendingLoad(): Boolean = playbackManager.playbackState() is PlaybackState.Loading

    companion object {
        private const val TAG = "S2Debug"
        const val ACTION_PREFIX = "com.simplecityapps.shuttle.debug."

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }
}
