package com.simplecityapps.playback.mediasession

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.util.LruCache
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat
import androidx.media.session.MediaButtonReceiver
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.simplecityapps.mediaprovider.repository.albums.AlbumQuery
import com.simplecityapps.mediaprovider.repository.albums.AlbumRepository
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistQuery
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistRepository
import com.simplecityapps.mediaprovider.repository.genres.GenreQuery
import com.simplecityapps.mediaprovider.repository.genres.GenreRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.PlaybackNotificationManager
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.R
import com.simplecityapps.playback.androidauto.MediaIdHelper
import com.simplecityapps.playback.getArtworkCacheKey
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.playback.queue.QueueState
import com.simplecityapps.shuttle.coroutines.launchCollectingChanges
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.pendingintent.PendingIntentCompat
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.query.SongQuery
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

class MediaSessionManager
@Inject
constructor(
    private val context: Context,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val playbackManager: PlaybackOperations,
    private val queueManager: QueueOperations,
    private val mediaIdHelper: MediaIdHelper,
    private val uriSongResolver: UriSongResolver,
    private val artistRepository: AlbumArtistRepository,
    private val albumRepository: AlbumRepository,
    private val songRepository: SongRepository,
    private val genreRepository: GenreRepository,
    private val artworkImageLoader: ArtworkImageLoader,
    private val artworkCache: LruCache<String, Bitmap?>,
    private val preferenceManager: GeneralPreferenceManager
) {
    private val placeholder: Bitmap? by lazy {
        PlaybackNotificationManager.drawableToBitmap(ResourcesCompat.getDrawable(context.resources, R.drawable.ic_music_note_black_24dp, context.theme)!!)
    }

    val mediaSession: MediaSessionCompat = MediaSessionCompat(context, "ShuttleMediaSession")

    /** The current item's queue id, as last published; only changes to another item, never back to none. */
    private val activeQueueItemId = MutableStateFlow(MediaSessionCompat.QueueItem.UNKNOWN_ID.toLong())

    private val mediaSessionCallback =
        object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                Timber.v("onPlay()")
                playbackManager.play()
            }

            override fun onPause() {
                Timber.v("onPause()")
                playbackManager.pause()
            }

            override fun onSkipToPrevious() {
                playbackManager.skipToPrev()
            }

            override fun onSkipToNext() {
                playbackManager.skipToNext(ignoreRepeat = true)
            }

            override fun onSkipToQueueItem(id: Long) {
                val index = queueManager.getQueue().indexOfFirst { queueItem -> queueItem.toQueueItem().queueId == id }
                if (index != -1) {
                    playbackManager.skipTo(index)
                }
            }

            override fun onSeekTo(pos: Long) {
                playbackManager.seekTo(pos.toInt())
            }

            override fun onSetRepeatMode(repeatMode: Int) {
                queueManager.setRepeatMode(repeatMode.toRepeatMode())
            }

            override fun onSetShuffleMode(shuffleMode: Int) {
                appCoroutineScope.launch {
                    queueManager.setShuffleMode(shuffleMode.toShuffleMode(), reshuffle = true)
                }
            }

            override fun onPlayFromMediaId(
                mediaId: String?,
                extras: Bundle?
            ) {
                Timber.v("onPlayFromMediaId()")
                playFromMediaId(playWhenReady = true, mediaId = mediaId, extras = extras)
            }

            override fun onPrepareFromMediaId(
                mediaId: String?,
                extras: Bundle?
            ) {
                Timber.v("onPrepareFromMediaId()")
                playFromMediaId(playWhenReady = false, mediaId = mediaId, extras = extras)
            }

            private fun playFromMediaId(
                playWhenReady: Boolean,
                mediaId: String?,
                extras: Bundle?
            ) {
                Timber.v("playFromMediaId()")
                appCoroutineScope.launch {
                    mediaId?.let {
                        mediaIdHelper.getPlayQueue(mediaId)?.let { playQueue ->
                            playQueue(songs = playQueue.songs, position = playQueue.position, playWhenReady = playWhenReady, source = "onPlayFromMediaId")
                        }
                    }
                }
            }

            override fun onPlayFromUri(
                uri: Uri?,
                extras: Bundle?
            ) {
                Timber.v("onPlayFromUri()")
                playFromUri(playWhenReady = true, uri = uri, extras = extras)
            }

            override fun onPrepareFromUri(
                uri: Uri?,
                extras: Bundle?
            ) {
                Timber.v("onPrepareFromUri()")
                playFromUri(playWhenReady = false, uri = uri, extras = extras)
            }

            /** Plays the file at [uri] (e.g. one opened from a file manager) on its own, replacing the queue. */
            private fun playFromUri(
                playWhenReady: Boolean,
                uri: Uri?,
                extras: Bundle?
            ) {
                uri ?: return
                appCoroutineScope.launch {
                    val song = uriSongResolver.resolve(uri, extras?.getString(EXTRA_MIME_TYPE))
                    if (song == null) {
                        Timber.w("Can't play $uri: it can't be read")
                        Toast.makeText(context, com.simplecityapps.core.R.string.open_file_failed, Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    playQueue(songs = listOf(song), position = 0, playWhenReady = playWhenReady, source = "onPlayFromUri")
                }
            }

            /**
             * Replaces the queue with [songs] and loads it. Waits for the saved queue to be restored first, so a
             * request arriving as the app starts isn't overwritten by the restore.
             */
            private suspend fun playQueue(
                songs: List<Song>,
                position: Int,
                playWhenReady: Boolean,
                source: String
            ) {
                queueManager.queueStateFlow.awaitRestored()
                if (queueManager.setQueue(songs = songs, position = position)) {
                    playbackManager.load { result ->
                        result.onSuccess {
                            if (playWhenReady) {
                                playbackManager.play()
                            }
                        }
                        result.onFailure { error -> Timber.e(error, "Failed to load playback after $source") }
                    }
                }
            }

            override fun onPlayFromSearch(
                query: String?,
                extras: Bundle?
            ) {
                Timber.v("onPlayFromSearch()")
                playFromSearch(playWhenReady = true, query = query, extras = extras)
            }

            override fun onPrepareFromSearch(
                query: String?,
                extras: Bundle?
            ) {
                Timber.v("onPrepareFromSearch()")
                playFromSearch(playWhenReady = false, query = query, extras = extras)
            }

            override fun onCustomAction(
                action: String?,
                extras: Bundle?
            ) {
                if (action == ACTION_SHUFFLE) {
                    appCoroutineScope.launch {
                        queueManager.toggleShuffleMode()
                    }
                }
            }

            private fun playFromSearch(
                playWhenReady: Boolean,
                query: String?,
                extras: Bundle?
            ) {
                Timber.v("performSearch($query)")

                val mediaFocus = extras?.get(MediaStore.EXTRA_MEDIA_FOCUS)
                val artist = extras?.getString(MediaStore.EXTRA_MEDIA_ARTIST)
                val album = extras?.getString(MediaStore.EXTRA_MEDIA_ALBUM)
                val genre = extras?.getString(MediaStore.EXTRA_MEDIA_GENRE)

                val flow =
                    when (mediaFocus) {
                        MediaStore.Audio.Artists.CONTENT_TYPE -> {
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

                        else -> {
                            songRepository.getSongs(query?.let { SongQuery.Search(query = query) } ?: SongQuery.All())
                        }
                    }.flowOn(Dispatchers.IO)

                appCoroutineScope.launch {
                    flow.firstOrNull()?.let { songs ->
                        if (songs.isNotEmpty()) {
                            playQueue(songs = songs, position = 0, playWhenReady = playWhenReady, source = "onPlayFromSearch")
                        } else {
                            Timber.v("Search query $query with focus $mediaFocus yielded no results")
                        }
                    } ?: Timber.v("Search query $query with focus $mediaFocus yielded no results")
                }
            }

            override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
                Timber.i("onMediaButtonEvent: ${mediaButtonEvent?.action}")

                return super.onMediaButtonEvent(mediaButtonEvent)
            }
        }

    init {
        mediaSession.setCallback(mediaSessionCallback)
        val mediaButtonReceiverIntent =
            PendingIntent.getBroadcast(
                context,
                0,
                Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                    setClass(context, MediaButtonReceiver::class.java)
                },
                PendingIntentCompat.FLAG_MUTABLE
            )
        mediaSession.setMediaButtonReceiver(mediaButtonReceiverIntent)

        val shuffleMode = queueManager.getShuffleMode()
        val repeatMode = queueManager.getRepeatMode()
        val queueState = queueManager.queueStateFlow.value
        val playbackState = MediaSessionPlaybackState(playbackManager.positionAnchorFlow.value, shuffleMode, activeQueueItemId.value)

        publishPlaybackState(playbackState)

        // Changes from the state read above, so a change made in between isn't missed.
        appCoroutineScope.launchCollectingChanges(queueManager.shuffleModeFlow, shuffleMode, Dispatchers.Main.immediate) { _, current ->
            mediaSession.setShuffleMode(current.toShuffleMode())
        }
        appCoroutineScope.launchCollectingChanges(queueManager.repeatModeFlow, repeatMode, Dispatchers.Main.immediate) { _, current ->
            mediaSession.setRepeatMode(current.toRepeatMode())
        }
        appCoroutineScope.launchSessionQueueUpdates(
            queueStateFlow = queueManager.queueStateFlow,
            baseline = queueState,
            context = Dispatchers.Main.immediate,
            onQueueChanged = ::updateQueue,
            onCurrentItemChanged = ::updateCurrentQueueItem,
            onCurrentSongChanged = ::updateMetadata
        )

        // The playback state is published from here alone, on Main.immediate so a change made on the main thread is applied
        // straight away.
        appCoroutineScope.launch(Dispatchers.Main.immediate) {
            combine(playbackManager.positionAnchorFlow, queueManager.shuffleModeFlow, activeQueueItemId, ::MediaSessionPlaybackState)
                .dropWhile { state -> state == playbackState }
                .distinctUntilChanged()
                .collect(::publishPlaybackState)
        }
    }

    private fun publishPlaybackState(state: MediaSessionPlaybackState) {
        Timber.v("publishPlaybackState()")
        val publishedState = state.toPublishedPlaybackState()
        mediaSession.isActive = publishedState.playback.isActive
        mediaSession.setPlaybackState(publishedState.toPlaybackStateCompat(context::getString))
    }

    private fun updateMetadata() {
        Timber.v("updateMetadata()")
        queueManager.getCurrentItem()?.let { currentItem ->
            val mediaMetadataCompat =
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, currentItem.song.id.toString())
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, currentItem.song.albumArtist)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentItem.song.friendlyArtistName ?: currentItem.song.albumArtist ?: context.getString(com.simplecityapps.core.R.string.unknown))
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, currentItem.song.album ?: context.getString(com.simplecityapps.core.R.string.unknown))
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentItem.song.name ?: context.getString(com.simplecityapps.core.R.string.unknown))
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, currentItem.song.duration.toLong())
                    .putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, currentItem.song.track?.toLong() ?: 1)
                    .putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, queueManager.getSize().toLong())

            if (preferenceManager.mediaSessionArtwork) {
                mediaMetadataCompat.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, placeholder)

                val artworkSize = 512
                synchronized(artworkCache) {
                    artworkCache[currentItem.song.getArtworkCacheKey(artworkSize, artworkSize)]?.let { image ->
                        mediaMetadataCompat.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, image)
                    }
                } ?: run {
                    artworkImageLoader.loadBitmap(
                        data = currentItem.song,
                        width = artworkSize,
                        height = artworkSize
                    ) { image ->
                        mediaMetadataCompat.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, image)
                        mediaSession.setMetadata(mediaMetadataCompat.build())
                        if (image != null) {
                            synchronized(artworkCache) {
                                artworkCache.put(currentItem.song.getArtworkCacheKey(artworkSize, artworkSize), image)
                            }
                        }
                    }
                }
            }

            mediaSession.setMetadata(mediaMetadataCompat.build())
        } ?: Timber.e("Metadata update failed.. current item null")
    }

    private fun updateQueue() {
        Timber.v("updateQueue()")
        val queue = queueManager.getQueue()
        if (queue.isNotEmpty()) {
            mediaSession.setQueue(
                queueManager.getQueue()
                    .subList(((queueManager.getCurrentPosition() ?: 0) - 5).coerceAtLeast(0), queueManager.getSize())
                    .take(30)
                    .map { queueItem -> queueItem.toQueueItem() }
            )
        } else {
            mediaSession.setQueue(emptyList())
        }
    }

    private fun updateCurrentQueueItem() {
        Timber.v("updateCurrentQueueItem()")
        queueManager.getCurrentItem()?.let { currentItem ->
            if (currentItem.uid != activeQueueItemId.value) {
                updateQueue()
                // PlaybackManager re-anchors at the new track's start position as it starts loading it.
                activeQueueItemId.value = currentItem.uid
                updateMetadata()
            }
        }
    }

    companion object {
        const val ACTION_SHUFFLE = "com.simplecityapps.shuttle.shuffle"

        /** Optional [Bundle] extra for playFromUri: the MIME type the caller gave for the URI, used when its provider reports none. */
        const val EXTRA_MIME_TYPE = "com.simplecityapps.shuttle.mime_type"
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

/**
 * Collects [queueStateFlow], comparing each state with the last one handled, starting from [baseline].
 *
 * [onQueueChanged] runs when the queue's contents change (including a song's data being replaced in
 * place), [onCurrentItemChanged] when the current item or position does, and [onCurrentSongChanged] when
 * the current item's song data changes without the item itself changing; the first two run, in that
 * order, when the queue is restored.
 */
internal fun CoroutineScope.launchSessionQueueUpdates(
    queueStateFlow: StateFlow<QueueState>,
    baseline: QueueState,
    context: CoroutineContext,
    onQueueChanged: () -> Unit,
    onCurrentItemChanged: () -> Unit,
    onCurrentSongChanged: () -> Unit
): Job = launchCollectingChanges(queueStateFlow, baseline, context) { previous, current ->
    val restored = current.isRestored && !previous.isRestored
    if (restored || current.contentVersion != previous.contentVersion || current.songDataVersion != previous.songDataVersion) {
        onQueueChanged()
    }
    if (restored || current.currentItem != previous.currentItem || current.currentPosition != previous.currentPosition) {
        onCurrentItemChanged()
    } else if (current.currentItem?.song != previous.currentItem?.song) {
        onCurrentSongChanged()
    }
}
