package com.simplecityapps.playback.mediasession

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.androidauto.MediaIdHelper
import com.simplecityapps.playback.androidauto.PlayQueue
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.playback.queue.QueueState
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.di.ApplicationContext
import com.simplecityapps.shuttle.model.PlayContext
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Requests to play something from outside the app's own screens: a media id browsed in Android Auto, a file opened
 * from another app, or a voice search. Each resolves to songs, which replace the queue through [QueueOperations].
 * The media session's callback and the app's own entry points (a search intent, a file opened with the app) share it.
 */
@SingleIn(AppScope::class)
class PlayRequests
@Inject
constructor(
    @ApplicationContext private val context: Context,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val playbackOperations: PlaybackOperations,
    private val queueOperations: QueueOperations,
    private val mediaIdHelper: MediaIdHelper,
    private val uriSongResolver: UriSongResolver,
    private val voiceSearchResolver: VoiceSearchResolver,
    private val songRepository: SongRepository,
    /** How long [shuffleAll] waits for the first song to load; tests that run the player's clock far ahead raise it. */
    private val shuffleAllLoadWaitMs: Long = SHUFFLE_ALL_LOAD_WAIT_MS
) {

    /** The songs for the playable item [mediaId], or null for an id that isn't one. */
    suspend fun songsForMediaId(mediaId: String): PlayQueue? = mediaIdHelper.getPlayQueue(mediaId)

    /** The file at [uri] (e.g. one opened from a file manager) as a song, or null if it can't be read. */
    suspend fun songForUri(uri: Uri, mimeType: String?): Song? = uriSongResolver.resolve(uri, mimeType)

    /**
     * The queue a voice search asks for (see [VoiceSearchResolver]), or null to play the queue as it is: a search for
     * nothing in particular resumes the queue, or shuffles the whole library when there's no queue to resume. A search
     * that finds nothing (an empty library) is an empty queue.
     */
    suspend fun queueForSearch(query: String?, extras: Bundle?): PlayQueue? = when (val result = voiceSearchResolver.resolve(VoiceSearch.from(query, extras))) {
        is VoiceSearchResult.Songs -> if (result.shuffled) PlayQueue(result.songs.shuffled(), 0) else PlayQueue(result.songs, result.position)

        VoiceSearchResult.Empty -> PlayQueue(emptyList(), 0)

        VoiceSearchResult.Anything -> {
            queueOperations.queueStateFlow.awaitRestored()
            if (queueOperations.getQueue().isNotEmpty()) null else PlayQueue(librarySongs().shuffled(), 0)
        }
    }.also { playQueue ->
        if (playQueue?.songs?.isEmpty() == true) Timber.v("Search query $query with extras $extras yielded no results")
    }

    /**
     * The songs a voice search names, to add to the queue: those it would play, from the one it would start at, and
     * every song for a search for nothing in particular.
     */
    suspend fun songsForSearch(query: String?, extras: Bundle?): List<Song> = when (val result = voiceSearchResolver.resolve(VoiceSearch.from(query, extras))) {
        is VoiceSearchResult.Songs -> if (result.shuffled) result.songs.shuffled() else result.songs.drop(result.position)
        VoiceSearchResult.Empty -> emptyList()
        VoiceSearchResult.Anything -> librarySongs()
    }

    private suspend fun librarySongs(): List<Song> = withContext(Dispatchers.IO) { songRepository.getSongs(SongQuery.All()).firstOrNull().orEmpty() }

    /**
     * Replaces the queue with [songs], starting at [position], and loads it, returning once it has loaded (or failed
     * to). Waits for the saved queue to be restored first, so a request arriving as the app starts isn't overwritten
     * by the restore.
     *
     * @return false if the queue was left alone.
     */
    suspend fun setQueue(songs: List<Song>, position: Int, source: String): Boolean {
        queueOperations.queueStateFlow.awaitRestored()
        if (!queueOperations.setQueue(songs = songs, position = position)) return false
        return suspendCancellableCoroutine { continuation ->
            playbackOperations.load { result ->
                result.onFailure { error -> logLoadFailure(error, "playback after $source") }
                continuation.resume(result.isSuccess)
            }
        }
    }

    /**
     * Plays what a voice search asks for (see [queueForSearch]), returning once it's playing, or once it's clear
     * nothing will: as the app starts, that waits for the saved queue to be restored.
     */
    suspend fun playSearch(query: String?, extras: Bundle?) {
        val playQueue = queueForSearch(query, extras)
        when {
            playQueue == null -> playbackOperations.play()
            playQueue.songs.isEmpty() -> Unit
            setQueue(playQueue.songs, playQueue.position, source = "playFromSearch") -> playbackOperations.play()
        }
    }

    /** A load replaced by a later one was dropped, not failed: that's no error. */
    private fun logLoadFailure(error: Throwable, what: String) {
        if (error is CancellationException) Timber.d("Load of $what was replaced by a later one") else Timber.e(error, "Failed to load $what")
    }

    /** Shuffles the whole library and plays it, once the saved queue is restored. */
    suspend fun shuffleAll() {
        queueOperations.queueStateFlow.awaitRestored()
        val songs = librarySongs()
        if (songs.isEmpty()) {
            Timber.w("shuffleAll: the library is empty")
            return
        }
        val loaded = CompletableDeferred<Result<Any?>>()
        playbackOperations.shuffle(songs, PlayContext.None) { loaded.complete(it) }
        // A replaced load answers with a CancellationException; still, don't wait forever on one that never answers.
        val result = withTimeoutOrNull(shuffleAllLoadWaitMs) { loaded.await() }
        if (result == null) {
            Timber.w("shuffleAll: timed out waiting for playback to load")
            return
        }
        result
            .onSuccess { playbackOperations.play() }
            .onFailure { error -> logLoadFailure(error, "playback after shuffleAll") }
    }

    /** Plays the file at [uri] on its own, replacing the queue, or says it can't be opened. */
    fun playFromUri(uri: Uri, mimeType: String?): Job = appCoroutineScope.launch {
        val song = songForUri(uri, mimeType)
        if (song == null) {
            Timber.w("Can't play $uri: it can't be read")
            Toast.makeText(context, com.simplecityapps.core.R.string.open_file_failed, Toast.LENGTH_LONG).show()
            return@launch
        }
        if (setQueue(listOf(song), position = 0, source = "playFromUri")) playbackOperations.play()
    }

    /**
     * Says a file can't be opened whenever one that isn't in the library fails to play: a file opened from another
     * app plays under the caller's URI grant, which lapses once the task that got it is gone, so a later reload can
     * fail where the first load didn't.
     */
    fun launchPlaybackFailureMessages(): Job = appCoroutineScope.launch(Dispatchers.Main.immediate) {
        playbackOperations.playbackFailureFlow.collect { song ->
            if (!song.isInLibrary) {
                Toast.makeText(context, com.simplecityapps.core.R.string.open_file_failed, Toast.LENGTH_LONG).show()
            }
        }
    }
}

/** How long a request to play something waits for the saved queue to be restored before going ahead anyway. */
internal const val RESTORE_WAIT_MS = 10_000L

/** How long shuffling the library waits for the first song to load before giving up. */
internal const val SHUFFLE_ALL_LOAD_WAIT_MS = 15_000L

/**
 * Waits for the saved queue to be restored, so a request to play something made as the app starts isn't
 * overwritten by the restore. The restore marks itself done however it ends, but the wait is bounded too, so a
 * restore that never finishes can't hold every request (including Android Auto's) up for good.
 */
internal suspend fun StateFlow<QueueState>.awaitRestored(timeoutMs: Long = RESTORE_WAIT_MS) {
    if (withTimeoutOrNull(timeoutMs) { first { queueState -> queueState.isRestored } } == null) {
        Timber.w("The queue wasn't restored within ${timeoutMs}ms; going ahead without it")
    }
}
