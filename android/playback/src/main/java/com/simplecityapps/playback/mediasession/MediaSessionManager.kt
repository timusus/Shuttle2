package com.simplecityapps.playback.mediasession

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.LruCache
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
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.PlaybackNotificationManager
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.PlaybackWatcher
import com.simplecityapps.playback.PlaybackWatcherCallback
import com.simplecityapps.playback.R
import com.simplecityapps.playback.androidauto.MediaIdHelper
import com.simplecityapps.playback.getArtworkCacheKey
import com.simplecityapps.playback.queue.QueueChangeCallback
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueWatcher
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.pendingintent.PendingIntentCompat
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.query.SongQuery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

class MediaSessionManager
@Inject
constructor(
    private val context: Context,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val playbackManager: PlaybackManager,
    private val queueManager: QueueManager,
    private val mediaIdHelper: MediaIdHelper,
    private val artistRepository: AlbumArtistRepository,
    private val albumRepository: AlbumRepository,
    private val songRepository: SongRepository,
    private val genreRepository: GenreRepository,
    private val artworkImageLoader: ArtworkImageLoader,
    private val artworkCache: LruCache<String, Bitmap?>,
    private val preferenceManager: GeneralPreferenceManager,
    playbackWatcher: PlaybackWatcher,
    queueWatcher: QueueWatcher,
) : PlaybackWatcherCallback,
    QueueChangeCallback {
    private val placeholder: Bitmap? by lazy {
        PlaybackNotificationManager.drawableToBitmap(ResourcesCompat.getDrawable(context.resources, R.drawable.ic_music_note_black_24dp, context.theme)!!)
    }

    val mediaSession: MediaSessionCompat = MediaSessionCompat(context, "ShuttleMediaSession")

    private var activeQueueItemId = -1L

    private var playbackStateBuilder = PlaybackStateCompat.Builder()

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
                extras: Bundle?,
            ) {
                Timber.v("onPlayFromMediaId()")
                playFromMediaId(playWhenReady = true, mediaId = mediaId, extras = extras)
            }

            override fun onPrepareFromMediaId(
                mediaId: String?,
                extras: Bundle?,
            ) {
                Timber.v("onPrepareFromMediaId()")
                playFromMediaId(playWhenReady = false, mediaId = mediaId, extras = extras)
            }

            private fun playFromMediaId(
                playWhenReady: Boolean,
                mediaId: String?,
                extras: Bundle?,
            ) {
                Timber.v("playFromMediaId()")
                appCoroutineScope.launch {
                    mediaId?.let {
                        mediaIdHelper.getPlayQueue(mediaId)?.let { playQueue ->
                            if (queueManager.setQueue(songs = playQueue.songs, position = playQueue.position)) {
                                playbackManager.load { result ->
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
            }

            override fun onPlayFromSearch(
                query: String?,
                extras: Bundle?,
            ) {
                Timber.v("onPlayFromSearch()")
                playFromSearch(playWhenReady = true, query = query, extras = extras)
            }

            override fun onPrepareFromSearch(
                query: String?,
                extras: Bundle?,
            ) {
                Timber.v("onPrepareFromSearch()")
                playFromSearch(playWhenReady = false, query = query, extras = extras)
            }

            override fun onCustomAction(
                action: String?,
                extras: Bundle?,
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
                extras: Bundle?,
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
                            if (queueManager.setQueue(songs)) {
                                playbackManager.load { result ->
                                    result.onSuccess {
                                        if (playWhenReady) {
                                            playbackManager.play()
                                        }
                                    }
                                    result.onFailure { error -> Timber.e(error, "Failed to load songs") }
                                }
                            }
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

        playbackWatcher.addCallback(this)
        queueWatcher.addCallback(this)

        playbackStateBuilder.setActions(
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

        updateShuffleAction()

//        playbackStateBuilder.setState(
//            getPlaybackState(),
//            playbackManager.getProgress()?.toLong() ?: PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
//            playbackManager.getPlaybackSpeed()
//        )

        mediaSession.setPlaybackState(playbackStateBuilder.build())
    }

    private fun updateShuffleAction() {
        playbackStateBuilder = playbackStateBuilder.copyWithoutCustomActions()
        when (queueManager.getShuffleMode()) {
            QueueManager.ShuffleMode.Off -> {
                playbackStateBuilder.addCustomAction(
                    PlaybackStateCompat.CustomAction.Builder(ACTION_SHUFFLE, context.getString(com.simplecityapps.core.R.string.shuffle_on), R.drawable.ic_shuffle_off_black_24dp).build()
                )
            }

            QueueManager.ShuffleMode.On -> {
                playbackStateBuilder.addCustomAction(
                    PlaybackStateCompat.CustomAction.Builder(ACTION_SHUFFLE, context.getString(com.simplecityapps.core.R.string.shuffle_off), R.drawable.ic_shuffle_black_24dp).build()
                )
            }
        }
    }

    private fun getPlaybackState() = when (playbackManager.playbackState()) {
        is PlaybackState.Loading -> PlaybackStateCompat.STATE_BUFFERING
        is PlaybackState.Playing -> PlaybackStateCompat.STATE_PLAYING
        else -> PlaybackStateCompat.STATE_PAUSED
    }

    private fun updatePlaybackState() {
        Timber.v("updatePlaybackState()")
        val playbackState = playbackStateBuilder.build()
        activeQueueItemId = playbackState.activeQueueItemId
        mediaSession.setPlaybackState(playbackState)
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
                    .subList(((queueManager.getCurrentPosition() ?: 0) - 5).coerceAtLeast(0), queueManager.getSize() - 1)
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
            val activeQueueItemId = currentItem.toQueueItem().queueId
            if (activeQueueItemId != this.activeQueueItemId) {
                updateQueue()
                playbackStateBuilder.setActiveQueueItemId(activeQueueItemId)
                playbackStateBuilder.setState(getPlaybackState(), 0L, playbackManager.getPlaybackSpeed())
                updatePlaybackState()
                updateMetadata()
            }
        }
    }

    // PlaybackWatcherCallback Implementation

    override fun onPlaybackStateChanged(playbackState: PlaybackState) {
        mediaSession.isActive = playbackState == PlaybackState.Loading || playbackState == PlaybackState.Playing
        playbackStateBuilder.setState(getPlaybackState(), playbackManager.getProgress()?.toLong() ?: PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, playbackManager.getPlaybackSpeed())
        updatePlaybackState()
    }

    override fun onProgressChanged(
        position: Int,
        duration: Int,
        fromUser: Boolean,
    ) {
        if (fromUser) {
            playbackStateBuilder.setState(getPlaybackState(), position.toLong(), playbackManager.getPlaybackSpeed())
            updatePlaybackState()
        }
    }

    override fun onTrackEnded(song: Song) {
        super.onTrackEnded(song)

        playbackStateBuilder.setState(getPlaybackState(), playbackManager.getProgress()?.toLong() ?: PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, playbackManager.getPlaybackSpeed())
        updatePlaybackState()
    }

    // QueueChangeCallback Implementation

    override fun onQueueRestored() {
        updateQueue()
        updateCurrentQueueItem()
    }

    override fun onQueueChanged(reason: QueueChangeCallback.QueueChangeReason) {
        updateQueue()
    }

    override fun onQueuePositionChanged(
        oldPosition: Int?,
        newPosition: Int?,
    ) {
        updateCurrentQueueItem()
    }

    override fun onShuffleChanged(shuffleMode: QueueManager.ShuffleMode) {
        mediaSession.setShuffleMode(shuffleMode.toShuffleMode())
        updateShuffleAction()
        updatePlaybackState()
    }

    override fun onRepeatChanged(repeatMode: QueueManager.RepeatMode) {
        mediaSession.setRepeatMode(repeatMode.toRepeatMode())
    }

    fun PlaybackStateCompat.Builder.copyWithoutCustomActions(): PlaybackStateCompat.Builder {
        val thing = this.build()
        return PlaybackStateCompat.Builder()
            .setState(thing.state, thing.position, thing.playbackSpeed)
            .setActions(thing.actions)
            .setActiveQueueItemId(thing.activeQueueItemId)
    }

    companion object {
        const val ACTION_SHUFFLE = "com.simplecityapps.shuttle.shuffle"
    }
}
