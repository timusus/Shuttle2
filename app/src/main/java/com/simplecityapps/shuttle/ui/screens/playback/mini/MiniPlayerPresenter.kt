package com.simplecityapps.shuttle.ui.screens.playback.mini

import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.playback.Playback
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.queue.QueueChangeCallback
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import javax.inject.Inject

class MiniPlayerPresenter @Inject constructor(
    private val playbackManager: PlaybackManager,
    private val queueManager: QueueManager
) : BasePresenter<MiniPlayerContract.View>(),
    MiniPlayerContract.Presenter,
    Playback.Callback,
    QueueChangeCallback,
    PlaybackManager.ProgressCallback {

    override fun togglePlayback() {
        playbackManager.togglePlayback()
    }

    override fun skipToNext() {
        playbackManager.skipToNext()
    }

    override fun bindView(view: MiniPlayerContract.View) {
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


    // Playback.Callback Implementation

    override fun onPlaystateChanged(isPlaying: Boolean) {
        view?.setPlayState(isPlaying)
    }

    override fun onPlaybackPrepared() {

    }

    override fun onPlaybackComplete(song: Song?) {

    }


    // PlaybackManager.ProgressCallback

    override fun onPregressChanged(position: Int, total: Int) {
        view?.setProgress(position, total)
    }


    // QueueChangeCallback Implementation

    override fun onQueueChanged() {

    }

    override fun onQueuePositionChanged() {
        view?.setCurrentSong(queueManager.getCurrentItem()?.song)
    }

    override fun onShuffleChanged() {

    }

    override fun onRepeatChanged() {

    }


    // Private

    private fun updateProgress() {
        queueManager.getCurrentItem()?.song?.let { currentSong ->
            view?.setProgress(playbackManager.getPosition() ?: 0, currentSong.duration.toInt())
        }
    }

}