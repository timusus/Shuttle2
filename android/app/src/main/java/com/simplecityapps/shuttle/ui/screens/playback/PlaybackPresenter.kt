package com.simplecityapps.shuttle.ui.screens.playback

import android.content.Context
import com.simplecityapps.mediaprovider.repository.albums.AlbumQuery
import com.simplecityapps.mediaprovider.repository.albums.AlbumRepository
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistQuery
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistRepository
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.playback.queue.QueueState
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import timber.log.Timber

class PlaybackPresenter
@Inject
constructor(
    private val playbackManager: PlaybackOperations,
    private val queueManager: QueueOperations,
    private val playlistRepository: PlaylistRepository,
    private val albumRepository: AlbumRepository,
    private val albumArtistRepository: AlbumArtistRepository,
    @ApplicationContext private val context: Context
) : BasePresenter<PlaybackContract.View>(),
    PlaybackContract.Presenter {
    private var favoriteUpdater: Job? = null

    override fun bindView(view: PlaybackContract.View) {
        super.bindView(view)

        // Progress and playback state are drawn from live reads, which are at least as new as the flows'
        // values, so the flows are snapshotted before those reads and a change after it is still delivered.
        val queueState = queueManager.queueStateFlow.value
        val initialShuffleMode = queueManager.shuffleModeFlow.value
        val initialRepeatMode = queueManager.repeatModeFlow.value
        val initialPlaybackState = playbackManager.playbackStateFlow.value
        val initialProgress = playbackManager.progressFlow.value

        // One time update of all UI components
        updateProgress(queueState)
        updateShuffleMode(initialShuffleMode)
        updateRepeatMode(initialRepeatMode)
        updateQueue(queueState.items)
        updateQueuePosition(queueState.currentPosition, queueState.items.size)
        updateCurrentSong(queueState.currentItem?.song)
        updatePlaybackState(playbackManager.playbackState())
        updateFavorite()

        collectChanges(playbackManager.playbackStateFlow, initialPlaybackState) { _, playbackState -> updatePlaybackState(playbackState) }
        collectChanges(playbackManager.progressFlow, initialProgress) { _, progress ->
            progress?.let { view?.setProgress(progress.position, progress.duration) }
        }
        collectChanges(queueManager.queueStateFlow, queueState, ::onQueueStateChanged)
        collectChanges(queueManager.shuffleModeFlow, initialShuffleMode) { _, shuffleMode -> updateShuffleMode(shuffleMode) }
        collectChanges(queueManager.repeatModeFlow, initialRepeatMode) { _, repeatMode -> updateRepeatMode(repeatMode) }
    }

    override fun unbindView() {
        updateFavorite()

        super.unbindView()
    }

    // Private

    private fun updateProgress(queueState: QueueState) {
        queueState.currentItem?.song?.let { currentSong ->
            view?.setProgress(
                playbackManager.getProgress() ?: 0,
                playbackManager.getDuration() ?: currentSong.duration
            )
        }
    }

    private fun onQueueStateChanged(
        previous: QueueState,
        current: QueueState
    ) {
        val restored = current.isRestored && !previous.isRestored
        if (restored || current.contentVersion != previous.contentVersion || current.songDataVersion != previous.songDataVersion) {
            updateQueue(current.items)
        }
        val positionChanged = current.currentItem != previous.currentItem || current.currentPosition != previous.currentPosition
        val currentSongChanged = positionChanged || current.currentItem?.song != previous.currentItem?.song
        if (restored || currentSongChanged) {
            updateCurrentSong(current.currentItem?.song)
            updateQueuePosition(current.currentPosition, current.items.size)
        }
        if (positionChanged) {
            updateFavorite()
        }
    }

    private fun updateFavorite() {
        favoriteUpdater?.cancel()
        val job =
            launch {
                val isFavorite =
                    playlistRepository
                        .getSongsForPlaylist(playlistRepository.getFavoritesPlaylist())
                        .firstOrNull()
                        .orEmpty()
                        .map { it.song }
                        .contains(queueManager.getCurrentItem()?.song)
                this@PlaybackPresenter.view?.setIsFavorite(isFavorite)
            }

        favoriteUpdater = job
    }

    private fun updateQueue(queue: List<QueueItem>) {
        view?.clearQueue()
        view?.setQueue(queue)
    }

    private fun updateQueuePosition(
        newPosition: Int?,
        size: Int
    ) {
        view?.setQueuePosition(newPosition, size)
    }

    private fun updateCurrentSong(song: com.simplecityapps.shuttle.model.Song?) {
        view?.setCurrentSong(song)
    }

    private fun updateShuffleMode(shuffleMode: QueueManager.ShuffleMode) {
        view?.setShuffleMode(shuffleMode)
    }

    private fun updateRepeatMode(repeatMode: QueueManager.RepeatMode) {
        view?.setRepeatMode(repeatMode)
    }

    private fun updatePlaybackState(playbackState: PlaybackState) {
        view?.setPlaybackState(playbackState)
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
                    playlistRepository.removeSongsFromPlaylist(favoritesPlaylist, listOf(song))
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
                val artists = albumArtistRepository.getAlbumArtists(AlbumArtistQuery.AlbumArtistGroupKey(key = song.albumArtistGroupKey)).firstOrNull().orEmpty()
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

    override fun showLyrics() {
        queueManager.getCurrentItem()?.let { queueItem ->
            queueItem.song.lyrics?.let { lyrics ->
                view?.displayLyrics(lyrics)
            }
        }
    }

    override fun clearQueue() {
        playbackManager.clearQueue()
    }
}
