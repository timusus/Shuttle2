package com.simplecityapps.shuttle.ui.screens.playback

import android.content.Context
import com.simplecityapps.mediaprovider.repository.*
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.PlaybackWatcher
import com.simplecityapps.playback.PlaybackWatcherCallback
import com.simplecityapps.playback.queue.QueueChangeCallback
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueWatcher
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import com.simplecityapps.shuttle.ui.lyrics.QuickLyricManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.abs

class PlaybackPresenter @Inject constructor(
    private val playbackManager: PlaybackManager,
    private val playbackWatcher: PlaybackWatcher,
    private val queueManager: QueueManager,
    private val queueWatcher: QueueWatcher,
    private val playlistRepository: PlaylistRepository,
    private val albumRepository: AlbumRepository,
    private val albumArtistRepository: AlbumArtistRepository,
    private val context: Context
) : BasePresenter<PlaybackContract.View>(),
    PlaybackContract.Presenter,
    QueueChangeCallback,
    PlaybackWatcherCallback {

    private var favoriteUpdater: Job? = null

    override fun bindView(view: PlaybackContract.View) {
        super.bindView(view)

        playbackWatcher.addCallback(this)
        queueWatcher.addCallback(this)

        // One time update of all UI components
        updateProgress()
        onQueueChanged()
        onQueuePositionChanged(null, queueManager.getCurrentPosition())
        onPlaybackStateChanged(playbackManager.playbackState())
        onShuffleChanged(queueManager.getShuffleMode())
        onRepeatChanged(queueManager.getRepeatMode())
    }

    override fun unbindView() {
        playbackWatcher.removeCallback(this)
        queueWatcher.removeCallback(this)

        updateFavorite()

        super.unbindView()
    }


    // Private

    private fun updateProgress() {
        queueManager.getCurrentItem()?.song?.let { currentSong ->
            onProgressChanged(
                position = playbackManager.getProgress() ?: 0,
                duration = playbackManager.getDuration() ?: currentSong.duration,
                fromUser = false
            )
        }
    }

    private fun updateFavorite() {
        favoriteUpdater?.cancel()
        val job = launch {
            val isFavorite = playlistRepository
                .getSongsForPlaylist(playlistRepository.getFavoritesPlaylist().id)
                .firstOrNull()
                .orEmpty()
                .contains(queueManager.getCurrentItem()?.song)
            this@PlaybackPresenter.view?.setIsFavorite(isFavorite)
        }

        favoriteUpdater = job
    }


    // PlaybackContract.Presenter Implementation

    override fun togglePlayback() {
        playbackManager.togglePlayback()
    }

    override fun toggleShuffle() {
        launch {
            queueManager.toggleShuffleMode()
        }
    }

    override fun toggleRepeat() {
        queueManager.toggleRepeatMode()
    }

    override fun skipNext() {
        playbackManager.skipToNext(ignoreRepeat = true)
    }

    override fun skipPrev() {
        playbackManager.skipToPrev()
    }

    override fun skipTo(position: Int) {
        playbackManager.skipTo(position)
    }

    override fun seekForward(seconds: Int) {
        Timber.v("seekForward() seconds: $seconds")
        playbackManager.getProgress()?.let { position ->
            playbackManager.seekTo(position + seconds * 1000)
        }
    }

    override fun seekBackward(seconds: Int) {
        Timber.v("seekBackward() seconds: $seconds")
        playbackManager.getProgress()?.let { position ->
            playbackManager.seekTo(position - seconds * 1000)
        }
    }

    override fun seek(fraction: Float) {
        queueManager.getCurrentItem()?.song?.let { currentSong ->
            playbackManager.seekTo(((playbackManager.getDuration() ?: currentSong.duration) * fraction).toInt())
        } ?: Timber.v("seek() failed, current song null")
    }

    override fun updateProgress(fraction: Float) {
        queueManager.getCurrentItem()?.song?.let { currentSong ->
            view?.setProgress(((playbackManager.getDuration() ?: currentSong.duration) * fraction).toInt(), (playbackManager.getDuration() ?: currentSong.duration).toInt())
        } ?: Timber.v("seek() failed, current song null")
    }

    override fun sleepTimerClicked() {
        view?.presentSleepTimer()
    }

    override fun setFavorite(isFavorite: Boolean) {
        launch {
            queueManager.getCurrentItem()?.song?.let { song ->
                val favoritesPlaylist = playlistRepository.getFavoritesPlaylist()
                if (isFavorite) {
                    playlistRepository.addToPlaylist(favoritesPlaylist, listOf(song))
                } else {
                    playlistRepository.removeFromPlaylist(favoritesPlaylist, listOf(song))
                }
            }
        }
    }

    override fun goToAlbum() {
        launch {
            queueManager.getCurrentItem()?.song?.let { song ->
                val albums = albumRepository.getAlbums(AlbumQuery.AlbumGroupKey(song.albumGroupKey)).firstOrNull().orEmpty()
                albums.firstOrNull()?.let { album ->
                    view?.goToAlbum(album)
                } ?: Timber.e("Failed to retrieve album for song: ${song.name}")
            }
        }
    }

    override fun goToArtist() {
        launch {
            queueManager.getCurrentItem()?.song?.let { song ->
                val artists = albumArtistRepository.getAlbumArtists(AlbumArtistQuery.ArtistGroupKey(key = song.artistGroupKey)).firstOrNull().orEmpty()
                artists.firstOrNull()?.let { artist ->
                    view?.goToArtist(artist)
                } ?: Timber.e("Failed to retrieve album artist for song: ${song.name}")
            }
        }
    }

    override fun showSongInfo() {
        queueManager.getCurrentItem()?.let { queueItem ->
            view?.showSongInfoDialog(queueItem.song)
        }
    }

    override fun showOrLaunchLyrics() {
        queueManager.getCurrentItem()?.let { queueItem ->
            queueItem.song.lyrics?.let { lyrics ->
                view?.displayLyrics(lyrics)
            } ?: launchQuickLyric()
        }
    }

    override fun launchQuickLyric() {
        queueManager.getCurrentItem()?.let { queueItem ->
            if (QuickLyricManager.isQuickLyricInstalled(context)) {
                view?.launchQuickLyric(queueItem.song.albumArtist ?: context.getString(R.string.unknown), queueItem.song.name ?: context.getString(R.string.unknown))
            } else {
                if (QuickLyricManager.canDownloadQuickLyric(context)) {
                    view?.getQuickLyric()
                } else {
                    view?.showQuickLyricUnavailable()
                }
            }
        }
    }


    // PlaybackWatcherCallback Implementation

    override fun onPlaybackStateChanged(playbackState: PlaybackState) {
        view?.setPlaybackState(playbackState)
    }


    // PlaybackManager.ProgressCallback

    override fun onProgressChanged(position: Int, duration: Int, fromUser: Boolean) {
        view?.setProgress(position, duration)
    }


    // QueueChangeCallback Implementation

    override fun onQueueChanged() {
        view?.setQueue(queueManager.getQueue(), queueManager.getCurrentPosition())
    }

    override fun onQueuePositionChanged(oldPosition: Int?, newPosition: Int?) {
        view?.setCurrentSong(queueManager.getCurrentItem()?.song)
        view?.setQueuePosition(queueManager.getCurrentPosition(), queueManager.getSize(), abs((newPosition ?: Int.MAX_VALUE) - (oldPosition ?: 0)) <= 1)

        updateFavorite()
    }

    override fun onShuffleChanged(shuffleMode: QueueManager.ShuffleMode) {
        view?.setShuffleMode(shuffleMode)
    }

    override fun onRepeatChanged(repeatMode: QueueManager.RepeatMode) {
        view?.setRepeatMode(repeatMode)
    }
}