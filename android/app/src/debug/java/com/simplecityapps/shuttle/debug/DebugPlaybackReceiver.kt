package com.simplecityapps.shuttle.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistQuery
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.playback.queue.RepeatMode
import com.simplecityapps.playback.queue.ShuffleMode
import com.simplecityapps.playback.sleeptimer.SleepTimer
import com.simplecityapps.shuttle.debug.crossfadetap.WavTapAudioProcessor
import com.simplecityapps.shuttle.di.appGraph
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.ui.actions.PlaySongs
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.json.JSONArray
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
class DebugPlaybackReceiver : BroadcastReceiver() {
    @ContributesTo(AppScope::class)
    interface Injector {
        fun inject(receiver: DebugPlaybackReceiver)
    }

    @Inject
    lateinit var playbackOperations: PlaybackOperations

    @Inject
    lateinit var queueOperations: QueueOperations

    @Inject
    lateinit var songRepository: SongRepository

    @Inject
    lateinit var mediaImporter: MediaImporter

    @Inject
    lateinit var playlistRepository: PlaylistRepository

    @Inject
    lateinit var playSongs: PlaySongs

    @Inject
    lateinit var playbackPreferenceManager: PlaybackPreferenceManager

    @Inject
    lateinit var sleepTimer: SleepTimer

    @Inject
    lateinit var wavTap: WavTapAudioProcessor

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        context.appGraph<Injector>().inject(this)
        val action = intent.action?.removePrefix(ACTION_PREFIX) ?: return
        countPlayingEntries()
        val pendingResult = goAsync()
        scope.launch {
            try {
                val detail = handle(context, action, intent)
                Log.i(TAG, if (action == "DUMP_STATE") detail!! else "$action ok${detail?.let { ": $it" }.orEmpty()}")
            } catch (e: CancellationException) {
                Log.i(TAG, "$action dropped: replaced by a later request")
            } catch (e: Exception) {
                Log.e(TAG, "$action error: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** Runs [action]; returns an optional detail for the reply line, or throws with the reason it failed. */
    private suspend fun handle(
        context: Context,
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

        "PLAY" -> null.also { playbackOperations.play() }

        "PAUSE" -> null.also { playbackOperations.pause() }

        "NEXT" -> null.also { playbackOperations.skipToNext() }

        "PREV" -> null.also { playbackOperations.skipToPrev() }

        "SEEK" -> {
            val ms = intent.getLongExtra("ms", -1L)
            require(ms >= 0) { "missing --el ms <position>" }
            playbackOperations.seekTo(ms.toInt())
            "${ms}ms"
        }

        "REMOVE_QUEUE_ITEM" -> {
            val position = intent.getIntExtra("position", -1)
            val queueItem = requireNotNull(queueOperations.getQueue().getOrNull(position)) {
                "no item at --ei position $position (queue size ${queueOperations.getSize()})"
            }
            // The queue screen's "Remove from Queue" path (PlayerViewModel.removeQueueItem).
            playbackOperations.removeQueueItem(queueItem)
            queueItem.song.name
        }

        "REMOVE_PLAYLIST_SONG" -> {
            val playlistName = intent.getStringExtra("playlist")
            val songTitle = intent.getStringExtra("song")
            requireNotNull(playlistName) { "missing --es playlist <name>" }
            requireNotNull(songTitle) { "missing --es song <title>" }
            val playlist = playlistRepository.getPlaylists(PlaylistQuery.All(mediaProviderType = null)).firstOrNull()
                ?.firstOrNull { it.name == playlistName }
                ?: throw IllegalArgumentException("no playlist named '$playlistName'")
            val playlistSong = playlistRepository.getSongsForPlaylist(playlist).firstOrNull()
                ?.firstOrNull { it.song.name == songTitle }
                ?: throw IllegalArgumentException("no song titled '$songTitle' in playlist '$playlistName'")
            // The playlist screen's per-row "Remove" path (the RemoveFromPlaylist use case).
            playlistRepository.removeFromPlaylist(playlist, listOf(playlistSong))
            "removed '$songTitle' from '$playlistName'"
        }

        "REORDER_QUEUE" -> {
            val from = intent.getIntExtra("from", -1)
            val to = intent.getIntExtra("to", -1)
            val size = queueOperations.getSize()
            require(from in 0 until size && to in 0 until size) { "--ei from and --ei to must be within the queue (size $size)" }
            // The queue screen's drag-to-reorder path (QueueOperations.move), in the order getQueue()/DUMP_STATE's
            // queueTitles present -- shuffle-aware, same as the "Up Next" list.
            queueOperations.move(from, to)
            "moved $from -> $to"
        }

        "SHUFFLE" -> {
            if (intent.hasExtra("enabled")) {
                val mode = if (intent.getBooleanExtra("enabled", false)) ShuffleMode.On else ShuffleMode.Off
                queueOperations.setShuffleMode(mode, reshuffle = true)
            } else {
                queueOperations.toggleShuffleMode()
            }
            queueOperations.getShuffleMode().name
        }

        "REPEAT" -> {
            val mode = intent.getStringExtra("mode")
            if (mode != null) {
                queueOperations.setRepeatMode(
                    requireNotNull(RepeatMode.entries.firstOrNull { it.name.equals(mode, ignoreCase = true) }) {
                        "--es mode must be off, all or one"
                    }
                )
            } else {
                queueOperations.toggleRepeatMode()
            }
            queueOperations.getRepeatMode().name
        }

        "SPEED" -> {
            val multiplier = intent.getFloatExtra("multiplier", -1f)
            require(multiplier > 0f) { "missing --ef multiplier <speed>" }
            playbackOperations.setPlaybackSpeed(multiplier)
            "${multiplier}x"
        }

        "SLEEP_TIMER" -> {
            val seconds = intent.getLongExtra("seconds", -1L)
            require(seconds >= 0) { "missing --el seconds <delay>" }
            val playToEnd = intent.getBooleanExtra("play_to_end", false)
            sleepTimer.startTimer(seconds * 1000L, playToEnd)
            "${seconds}s, playToEnd=$playToEnd"
        }

        "TAP_START" -> {
            val name = intent.getStringExtra("name") ?: "crossfade-tap"
            require(name.matches(Regex("[A-Za-z0-9._-]+"))) { "--es name must be letters, digits, '.', '_' or '-'" }
            val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, TAP_DIR)
            val file = File(dir, "$name.wav")
            wavTap.start(file)
            file.absolutePath
        }

        "TAP_STOP" -> {
            val result = checkNotNull(wavTap.stop()) { "nothing recorded: not started, or no audio reached the mixer" }
            val seconds = result.dataBytes / (result.sampleRate.toDouble() * result.channelCount * result.bitsPerSample / 8)
            "${result.file.absolutePath} ${result.sampleRate}Hz ${result.channelCount}ch ${result.bitsPerSample}bit %.2fs".format(seconds) +
                (if (result.skippedBytes > 0) ", ${result.skippedBytes} bytes skipped (format change)" else "")
        }

        "DUMP_STATE" -> dumpState().toString()

        else -> throw IllegalArgumentException("unknown action")
    }

    private suspend fun dumpState(): JSONObject {
        val currentSong = queueOperations.getCurrentItem()?.song
        return JSONObject().apply {
            put("state", playbackOperations.playbackState().toString())
            put("reportedState", playbackOperations.playbackStateFlow.value.toString())
            put("positionMs", playbackOperations.getProgress() ?: JSONObject.NULL)
            put("progressMs", playbackOperations.progressFlow.value?.position ?: JSONObject.NULL)
            put("durationMs", playbackOperations.getDuration() ?: JSONObject.NULL)
            put("savedPositionMs", playbackPreferenceManager.playbackPosition ?: JSONObject.NULL)
            put("queuePosition", queueOperations.getCurrentPosition() ?: JSONObject.NULL)
            put("queueSize", queueOperations.getSize())
            put("title", currentSong?.name ?: JSONObject.NULL)
            put("inLibrary", currentSong?.isInLibrary ?: JSONObject.NULL)
            put("queueTitles", JSONArray(queueOperations.getQueue().map { it.song.name }))
            put("queueSongIds", JSONArray(queueOperations.getQueue().map { it.song.id }))
            put("shuffle", queueOperations.getShuffleMode().name)
            put("repeat", queueOperations.getRepeatMode().name)
            put("speed", playbackOperations.getPlaybackSpeed())
            put("pendingLoad", pendingLoad())
            put("playingEntries", playingEntries.get())
            put("libraryImporting", mediaImporter.isImporting)
            put("librarySongCount", songRepository.countSongs().first())
            put("libraryPlaylistCount", playlistRepository.getPlaylists(PlaylistQuery.All(mediaProviderType = null)).firstOrNull()?.size ?: JSONObject.NULL)
        }
    }

    /**
     * Counts transitions into Playing from the first broadcast on, so a check can assert playback never
     * resumed between two polls of DUMP_STATE. Immediate dispatch keeps a resume-then-pause on the main
     * thread from being conflated away.
     */
    private fun countPlayingEntries() {
        if (!collecting.compareAndSet(false, true)) return
        scope.launch(Dispatchers.Main.immediate) {
            var previous: PlaybackState? = null
            playbackOperations.playbackStateFlow.collect { state ->
                if (state is PlaybackState.Playing && previous !is PlaybackState.Playing) playingEntries.incrementAndGet()
                previous = state
            }
        }
    }

    /** Whether a track load is in flight: the player hasn't made the current item ready yet. */
    private fun pendingLoad(): Boolean = playbackOperations.playbackState() is PlaybackState.Loading

    companion object {
        private const val TAG = "S2Debug"

        /** Under the app's external files dir, so `adb pull` reaches it. */
        const val TAP_DIR = "crossfade-tap"
        const val ACTION_PREFIX = "com.simplecityapps.shuttle.debug."

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        private val collecting = AtomicBoolean(false)
        private val playingEntries = AtomicInteger(0)
    }
}
