package com.simplecityapps.shuttle.ui.screens.playback

import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.playback.Playback
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.queue.QueueChangeCallback
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import timber.log.Timber
import javax.inject.Inject

class PlaybackPresenter @Inject constructor(
    private val playbackManager: PlaybackManager,
    private val queueManager: QueueManager
) : BasePresenter<PlaybackContract.View>(),
    PlaybackContract.Presenter,
    Playback.Callback,
    QueueChangeCallback,
    PlaybackManager.ProgressCallback {

    override fun bindView(view: PlaybackContract.View) {
        super.bindView(view)

        playbackManager.addCallback(this)
        queueManager.addCallback(this)
        playbackManager.addProgressCallback(this)

        // One time update of all UI components
        updateProgress()
        onQueueChanged()
        onQueuePositionChanged()
        onPlaystateChanged(playbackManager.isPlaying())
        onShuffleChanged()
        onRepeatChanged()
    }

    override fun unbindView() {
        playbackManager.removeCallback(this)
        queueManager.removeCallback(this)
        playbackManager.removeProgressCallback(this)

        super.unbindView()
    }


    // Private

    private fun updateProgress() {
        queueManager.getCurrentItem()?.song?.let { currentSong ->
            view?.setProgress(playbackManager.getPosition() ?: 0, currentSong.duration.toInt())
        }
    }


    // PlaybackContract.Presenter Implementation

    override fun togglePlayback() {
        playbackManager.togglePlayback()
    }

    override fun toggleShuffle() {
        queueManager.toggleShuffleMode()
    }

    override fun toggleRepeat() {
        queueManager.toggleRepeatMode()
    }

    override fun skipNext() {
        playbackManager.skipToNext(true)
    }

    override fun skipPrev() {
        playbackManager.skipToPrev()
    }

    override fun seek(fraction: Float) {
        queueManager.getCurrentItem()?.song?.let { currentSong ->
            playbackManager.seekTo((currentSong.duration * fraction).toInt())
        } ?: Timber.v("seek() failed, current song null")
    }


    // Playback.Callbacks Implementation

    override fun onPlaystateChanged(isPlaying: Boolean) {
        view?.setPlayState(isPlaying)
    }

    override fun onPlaybackPrepared() {

    }

    override fun onPlaybackComplete(song: Song?) {

    }


    // PlaybackManager.ProgressCallback

    override fun onProgressChanged(position: Int, total: Int) {
        view?.setProgress(position, total)
    }


    // QueueChangeCallback Implementation

    override fun onQueueChanged() {
        view?.setQueue(queueManager.getQueue(), queueManager.getCurrentPosition())
    }

    override fun onQueuePositionChanged() {
        view?.setCurrentSong(queueManager.getCurrentItem()?.song)
        view?.setQueuePosition(queueManager.getCurrentPosition(), queueManager.getSize())
    }

    override fun onShuffleChanged() {
        view?.setShuffleMode(queueManager.getShuffleMode())
    }

    override fun onRepeatChanged() {
        view?.setRepeatMode(queueManager.getRepeatMode())
    }
}