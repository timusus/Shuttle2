package com.simplecityapps.shuttle.ui.screens.playback.mini

import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.playback.queue.QueueState
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import javax.inject.Inject
import timber.log.Timber

class MiniPlayerPresenter
@Inject
constructor(
    private val playbackManager: PlaybackOperations,
    private val queueManager: QueueOperations
) : BasePresenter<MiniPlayerContract.View>(),
    MiniPlayerContract.Presenter {
    override fun bindView(view: MiniPlayerContract.View) {
        super.bindView(view)

        // Progress and playback state are drawn from live reads, which are at least as new as the flows'
        // values, so the flows are snapshotted before those reads and a change after it is still delivered.
        val queueState = queueManager.queueStateFlow.value
        val initialPlaybackState = playbackManager.playbackStateFlow.value
        val initialProgress = playbackManager.progressFlow.value

        // One time update of all UI components
        updateProgress(queueState)
        view.setCurrentSong(queueState.currentItem?.song)
        view.setPlaybackState(playbackManager.playbackState())

        collectChanges(playbackManager.playbackStateFlow, initialPlaybackState) { _, playbackState -> this.view?.setPlaybackState(playbackState) }
        collectChanges(playbackManager.progressFlow, initialProgress) { _, progress ->
            progress?.let { this.view?.setProgress(progress.position, progress.duration) }
        }
        collectChanges(queueManager.queueStateFlow, queueState) { previous, current ->
            if (current.currentItem != previous.currentItem || current.currentPosition != previous.currentPosition) {
                this.view?.setCurrentSong(current.currentItem?.song)
            }
        }
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

    // Private

    private fun updateProgress(queueState: QueueState) {
        queueState.currentItem?.song?.let { currentSong ->
            view?.setProgress(playbackManager.getProgress() ?: 0, currentSong.duration)
        }
    }
}
