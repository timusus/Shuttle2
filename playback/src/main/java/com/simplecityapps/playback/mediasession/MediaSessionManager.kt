package com.simplecityapps.playback.mediasession

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.LruCache
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.simplecityapps.mediaprovider.repository.*
import com.simplecityapps.playback.*
import com.simplecityapps.playback.androidauto.MediaIdHelper
import com.simplecityapps.playback.queue.QueueChangeCallback
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueWatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named

class MediaSessionManager @Inject constructor(
    private val context: Context,
    @Named("AppCoroutineScope") private val appCoroutineScope: CoroutineScope,
    private val playbackManager: PlaybackManager,
    private val queueManager: QueueManager,
    private val mediaIdHelper: MediaIdHelper,
    private val artistRepository: AlbumArtistRepository,
    private val albumRepository: AlbumRepository,
    private val songRepository: SongRepository,
    private val genreRepository: GenreRepository,
    private val artworkImageLoader: ArtworkImageLoader,
    private val artworkCache: LruCache<String, Bitmap?>,
    playbackWatcher: PlaybackWatcher,
    queueWatcher: QueueWatcher
) : PlaybackWatcherCallback,
    QueueChangeCallback {

    private val placeholder: Bitmap? by lazy {
        PlaybackNotificationManager.drawableToBitmap(context.resources.getDrawable(R.drawable.ic_music_note_black_24dp, context.theme))
    }

    val mediaSession: MediaSessionCompat by lazy {
        val mediaSession = MediaSessionCompat(context, "ShuttleMediaSession")
        mediaSession.setCallback(mediaSessionCallback)
        mediaSession
    }

    private var playbackStateBuilder = PlaybackStateCompat.Builder()
        .setActions(
            PlaybackStateCompat.ACTION_PLAY
                    or PlaybackStateCompat.ACTION_PAUSE
                    or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                    or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                    or PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM
                    or PlaybackStateCompat.ACTION_SEEK_TO
                    or PlaybackStateCompat.ACTION_SET_REPEAT_MODE
                    or PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE
                    or PlaybackStateCompat.ACTION_PREPARE_FROM_SEARCH
                    or PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH
                    or PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID
                    or PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
        )

    init {
        playbackWatcher.addCallback(this)
        queueWatcher.addCallback(this)
    }

    private fun getPlaybackState() = if (playbackManager.isPlaying()) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED


    // PlaybackWatcherCallback Implementation

    override fun onPlaystateChanged(isPlaying: Boolean) {
        mediaSession.isActive = isPlaying
        playbackStateBuilder.setState(getPlaybackState(), playbackManager.getProgress()?.toLong() ?: 0, 1.0f)
        mediaSession.setPlaybackState(playbackStateBuilder.build())
    }

    override fun onProgressChanged(position: Int, duration: Int) {
        playbackStateBuilder.setState(getPlaybackState(), position.toLong(), 1.0f)
        mediaSession.setPlaybackState(playbackStateBuilder.build())
    }


    // QueueChangeCallback Implementation

    override fun onQueueChanged() {
        val queue = queueManager.getQueue()
        if (queue.isNotEmpty()) {
            mediaSession.setQueue(queueManager.getQueue()
                .subList(((queueManager.getCurrentPosition() ?: 0) - 10).coerceAtLeast(0), queueManager.getSize() - 1)
                .take(50)
                .map { queueItem -> queueItem.toQueueItem() })
        } else {
            mediaSession.setQueue(emptyList())
        }
    }

    override fun onQueuePositionChanged(oldPosition: Int?, newPosition: Int?) {
        queueManager.getCurrentItem()?.let { currentItem ->
            playbackStateBuilder.setActiveQueueItemId(currentItem.toQueueItem().queueId)

            playbackStateBuilder.setState(getPlaybackState(), playbackManager.getProgress()?.toLong() ?: 0, 1.0f)

            val artworkSize = 512

            mediaSession.setPlaybackState(playbackStateBuilder.build())
            val mediaMetadataCompat = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, currentItem.song.id.toString())
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentItem.song.albumArtist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, currentItem.song.album)
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentItem.song.name)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, currentItem.song.duration.toLong())
                .putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, currentItem.song.track.toLong())
                .putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, queueManager.getSize().toLong())
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, placeholder)

            synchronized(artworkCache) {
                artworkCache[currentItem.song.getArtworkCacheKey(artworkSize, artworkSize)]?.let { image ->
                    mediaMetadataCompat.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, image)
                }
            } ?: run {
                artworkImageLoader.loadBitmap(
                    data = currentItem.song,
                    width = artworkSize,
                    height = artworkSize,
                ) { image ->
                    mediaMetadataCompat.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, image)
                    mediaSession.setMetadata(
                        mediaMetadataCompat.build()
                    )
                    if (image != null) {
                        synchronized(artworkCache) {
                            artworkCache.put(currentItem.song.getArtworkCacheKey(artworkSize, artworkSize), image)
                        }
                    }
                }
            }

            mediaSession.setMetadata(
                mediaMetadataCompat.build()
            )
        }
    }

    override fun onShuffleChanged(shuffleMode: QueueManager.ShuffleMode) {
        mediaSession.setShuffleMode(shuffleMode.toShuffleMode())
    }

    override fun onRepeatChanged(repeatMode: QueueManager.RepeatMode) {
        mediaSession.setRepeatMode(repeatMode.toRepeatMode())
    }


    // MediaSessionCompat.Callback Implementation

    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {

        override fun onPlay() {
            playbackManager.play()
        }

        override fun onPause() {
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

        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            playFromMediaId(playWhenReady = true, mediaId = mediaId, extras = extras)
        }

        override fun onPrepareFromMediaId(mediaId: String?, extras: Bundle?) {
            playFromMediaId(playWhenReady = false, mediaId = mediaId, extras = extras)
        }

        private fun playFromMediaId(playWhenReady: Boolean, mediaId: String?, extras: Bundle?) {
            appCoroutineScope.launch {
                mediaId?.let {
                    mediaIdHelper.getPlayQueue(mediaId)?.let { playQueue ->
                        playbackManager.load(playQueue.songs, playQueue.playbackPosition) { result ->
                            result.onSuccess {
                                if (playWhenReady) {
                                    playbackManager.play()
                                }
                            }
                            result.onFailure { error -> Timber.e(error, "Failed to load playback after onPlayFromMediaId") }
                        }
                    }
                }
            }
        }

        override fun onPlayFromSearch(query: String?, extras: Bundle?) {
            playFromSearch(playWhenReady = true, query = query, extras = extras)
        }

        override fun onPrepareFromSearch(query: String?, extras: Bundle?) {
            playFromSearch(playWhenReady = false, query = query, extras = extras)
        }

        private fun playFromSearch(playWhenReady: Boolean, query: String?, extras: Bundle?) {
            Timber.i("performSearch($query)")

            val mediaFocus = extras?.get(MediaStore.EXTRA_MEDIA_FOCUS)
            val artist = extras?.getString(MediaStore.EXTRA_MEDIA_ARTIST)
            val album = extras?.getString(MediaStore.EXTRA_MEDIA_ALBUM)
            val genre = extras?.getString(MediaStore.EXTRA_MEDIA_GENRE)

            val flow = when (mediaFocus) {
                MediaStore.Audio.Artists.CONTENT_TYPE -> {
                    artist?.let {
                        artistRepository.getAlbumArtists(AlbumArtistQuery.Search(artist)).flatMapConcat { albumArtists ->
                            songRepository.getSongs(SongQuery.AlbumArtists(albumArtists.map { SongQuery.AlbumArtist(it.name) }))
                        }
                    } ?: emptyFlow()
                }
                MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE -> {
                    album?.let {
                        albumRepository.getAlbums(AlbumQuery.Search(album)).flatMapConcat { albums ->
                            songRepository.getSongs(SongQuery.Albums(albums.map { album -> SongQuery.Album(album.name, album.albumArtist) }))
                        }
                    } ?: emptyFlow()
                }
                MediaStore.Audio.Genres.ENTRY_CONTENT_TYPE -> {
                    genre?.let {
                        genreRepository.getGenres(GenreQuery.Search(genre)).flatMapConcat { genres ->
                            genres.firstOrNull()?.let { genre ->
                                genreRepository.getSongsForGenre(genre.name, SongQuery.All())
                            } ?: emptyFlow()
                        }
                    } ?: emptyFlow()
                }
                else -> {
                    songRepository.getSongs(query?.let { SongQuery.Search(query) } ?: SongQuery.All())
                }
            }.flowOn(Dispatchers.IO)

            appCoroutineScope.launch {
                flow.firstOrNull()?.let { songs ->
                    if (songs.isNotEmpty()) {
                        playbackManager.load(songs, 0, 0) { result ->
                            result.onSuccess {
                                if (playWhenReady) {
                                    playbackManager.play()
                                }
                            }
                            result.onFailure { error -> Timber.e("Failed to load songs") }
                        }
                    } else {
                        Timber.i("Search query $query with focus $mediaFocus yielded no results")
                    }
                } ?: Timber.i("Search query $query with focus $mediaFocus yielded no results")
            }
        }
    }
}