package com.simplecityapps.shuttle.ui.screens.playback.mini

import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.PlaybackWatcher
import com.simplecityapps.playback.PlaybackWatcherCallback
import com.simplecityapps.playback.queue.QueueChangeCallback
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueWatcher
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import timber.log.Timber
import javax.inject.Inject

class MiniPlayerPresenter @Inject constructor(
    private val playbackManager: PlaybackManager,
    private val playbackWatcher: PlaybackWatcher,
    private val queueManager: QueueManager,
    private val queueWatcher: QueueWatcher
) : BasePresenter<MiniPlayerContract.View>(),
    MiniPlayerContract.Presenter,
    PlaybackWatcherCallback,
    QueueChangeCallback {

    override fun bindView(view: MiniPlayerContract.View) {
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

        super.unbindView()
    }

    override fun togglePlayback() {
        playbackManager.togglePlayback()
    }

    override fun skipToNext() {
        playbackManager.skipToNext(ignoreRepeat = true)
    }

    override fun seekForward(seconds: Int) {
        Timber.v("seekForward() seconds: $seconds")
        playbackManager.getProgress()?.let { position ->
            playbackManager.seekTo(position + seconds * 1000)
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

    override fun onQueuePositionChanged(oldPosition: Int?, newPosition: Int?) {
        view?.setCurrentSong(queueManager.getCurrentItem()?.song)
    }

    // Private

    private fun updateProgress() {
        queueManager.getCurrentItem()?.song?.let { currentSong ->
            view?.setProgress(playbackManager.getProgress() ?: 0, currentSong.duration)
        }
    }
}
