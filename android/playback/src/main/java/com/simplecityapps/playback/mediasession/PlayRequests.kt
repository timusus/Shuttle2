package com.simplecityapps.playback.mediasession

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import com.simplecityapps.mediaprovider.repository.albums.AlbumQuery
import com.simplecityapps.mediaprovider.repository.albums.AlbumRepository
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistQuery
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistRepository
import com.simplecityapps.mediaprovider.repository.genres.GenreQuery
import com.simplecityapps.mediaprovider.repository.genres.GenreRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.androidauto.MediaIdHelper
import com.simplecityapps.playback.androidauto.PlayQueue
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.playback.queue.QueueState
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Requests to play something from outside the app's own screens: a media id browsed in Android Auto, a file opened
 * from another app, or a voice search. Each resolves to songs, which replace the queue through [QueueOperations].
 * The media session's callback and the app's own entry points (a search intent, a file opened with the app) share it.
 */
@Singleton
class PlayRequests
@Inject
constructor(
    @ApplicationContext private val context: Context,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val playbackOperations: PlaybackOperations,
    private val queueOperations: QueueOperations,
    private val mediaIdHelper: MediaIdHelper,
    private val uriSongResolver: UriSongResolver,
    private val artistRepository: AlbumArtistRepository,
    private val albumRepository: AlbumRepository,
    private val songRepository: SongRepository,
    private val genreRepository: GenreRepository
) {
    /** The songs for the playable item [mediaId], or null for an id that isn't one. */
    suspend fun songsForMediaId(mediaId: String): PlayQueue? = mediaIdHelper.getPlayQueue(mediaId)

    /** The file at [uri] (e.g. one opened from a file manager) as a song, or null if it can't be read. */
    suspend fun songForUri(uri: Uri, mimeType: String?): Song? = uriSongResolver.resolve(uri, mimeType)

    /**
     * The songs a voice search asks for. [extras] may focus it ([MediaStore.EXTRA_MEDIA_FOCUS]) on an artist, album
     * or genre; otherwise [query] is matched against songs, and no query at all means every song.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun songsForSearch(query: String?, extras: Bundle?): List<Song> {
        val artist = extras?.getString(MediaStore.EXTRA_MEDIA_ARTIST)
        val album = extras?.getString(MediaStore.EXTRA_MEDIA_ALBUM)
        val genre = extras?.getString(MediaStore.EXTRA_MEDIA_GENRE)

        val flow =
            when (extras?.getString(MediaStore.EXTRA_MEDIA_FOCUS)) {
                MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE, MediaStore.Audio.Artists.CONTENT_TYPE -> {
                    artist?.let {
                        artistRepository
                            .getAlbumArtists(AlbumArtistQuery.Search(query = artist))
                            .flatMapConcat { albumArtists ->
                                songRepository.getSongs(SongQuery.ArtistGroupKeys(albumArtists.map { albumArtist -> SongQuery.ArtistGroupKey(albumArtist.groupKey) }))
                            }
                    } ?: emptyFlow()
                }

                MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE -> {
                    album?.let {
                        albumRepository
                            .getAlbums(AlbumQuery.Search(query = album))
                            .flatMapConcat { albums ->
                                songRepository.getSongs(SongQuery.AlbumGroupKeys(albums.map { album -> SongQuery.AlbumGroupKey(album.groupKey) }))
                            }
                    } ?: emptyFlow()
                }

                MediaStore.Audio.Genres.ENTRY_CONTENT_TYPE -> {
                    genre?.let {
                        genreRepository
                            .getGenres(GenreQuery.Search(genre))
                            .flatMapConcat { genres ->
                                genres.firstOrNull()?.let { genre ->
                                    genreRepository.getSongsForGenre(genre.name, SongQuery.All())
                                } ?: emptyFlow()
                            }
                    } ?: emptyFlow()
                }

                else -> songRepository.getSongs(query?.takeIf { it.isNotBlank() }?.let { SongQuery.Search(query = query) } ?: SongQuery.All())
            }.flowOn(Dispatchers.IO)

        return flow.firstOrNull().orEmpty().also { songs ->
            if (songs.isEmpty()) Timber.v("Search query $query with extras $extras yielded no results")
        }
    }

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
                result.onFailure { error -> Timber.e(error, "Failed to load playback after $source") }
                continuation.resume(result.isSuccess)
            }
        }
    }

    /** Plays the songs a voice search asks for (see [songsForSearch]). */
    fun playFromSearch(query: String?, extras: Bundle?): Job = appCoroutineScope.launch {
        songsForSearch(query, extras).takeIf { it.isNotEmpty() }?.let { songs ->
            if (setQueue(songs, position = 0, source = "playFromSearch")) playbackOperations.play()
        }
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
