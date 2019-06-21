package com.simplecityapps.playback.local.mediaplayer

import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.playback.Playback
import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.playback.queue.QueueManager
import timber.log.Timber

class MediaPlayerPlayback(
    private val queueManager: QueueManager
) : Playback {

    private var currentMediaPlayerHelper = MediaPlayerHelper()

    private var nextMediaPlayerHelper = MediaPlayerHelper()

    private var currentQueueItem: QueueItem? = null

    private var nextQueueItem: QueueItem? = null

    override var callback: Playback.Callback? = null

    init {
        currentMediaPlayerHelper.tag = "CurrentMediaPlayer"
        nextMediaPlayerHelper.tag = "NextMediaPlayer"
    }

    override fun load(completion: (Result<Boolean>) -> Unit) {
        Timber.v("load()")

        val currentQueueItem = queueManager.getCurrentItem()

        attemptLoad(1) { result ->
            result.onSuccess {
                loadNext()
                completion(result)
            }
            result.onFailure { error ->
                // Our load attempts may have caused us to skip through the queue. Since none of those subsequent attempts have succeeded,
                // we may as well restore the previous queue position.
                currentQueueItem?.let { currentQueueItem ->
                    queueManager.setCurrentItem(currentQueueItem)
                }

                Timber.e("load() failed. Error: $error")
                completion(result)
            }
        }
    }

    private fun attemptLoad(attempt: Int = 1, completion: (Result<Boolean>) -> Unit) {
        Timber.v("Attempting load ($attempt)")
        loadCurrent { result ->
            result.onSuccess { completion(Result.success(attempt == 1)) }
            result.onFailure { error ->
                // Attempt to load the next item in the queue. If there is no next item, or we're on repeat, call completion(error).
                queueManager.getNext(false)?.let { nextQueueItem ->
                    if (nextQueueItem != currentQueueItem) {
                        queueManager.skipToNext(true)
                        attemptLoad(attempt + 1, completion)
                    } else {
                        completion(Result.failure(error))
                    }
                } ?: run {
                    completion(Result.failure(error))
                }
            }
        }
    }

    private fun loadCurrent(completion: (Result<Any?>) -> Unit) {
        Timber.v("loadCurrent()")
        currentMediaPlayerHelper.callback = currentPlayerCallback
        currentQueueItem = queueManager.getCurrentItem()
        currentQueueItem?.let { currentQueueItem ->
            currentMediaPlayerHelper.load(currentQueueItem.song, completion)
        } ?: run {
            completion(Result.failure(Error("Load failed: Current song null")))
        }
    }

    override fun loadNext() {
        Timber.v("loadNext()")
        nextQueueItem = queueManager.getNext()
        nextQueueItem?.let { nextQueueItem ->
            nextMediaPlayerHelper.load(nextQueueItem.song) { result ->
                result.onSuccess { currentMediaPlayerHelper.setNextMediaPlayer(nextMediaPlayerHelper.mediaPlayer) }
                result.onFailure { error -> Timber.e("Failed to load next media player: $error") }
            }
        } ?:run {
            Timber.v("loadNext() next song null")
            nextMediaPlayerHelper.release()
            currentMediaPlayerHelper.setNextMediaPlayer(null)
        }
    }

    override fun play() {
        Timber.v("play()")
        currentMediaPlayerHelper.play()
    }

    override fun pause() {
        Timber.v("pause()")
        currentMediaPlayerHelper.pause()

        // In case we're in the process of transitioning to the next music player when pause is called, pause it too.
        nextMediaPlayerHelper.pause()
    }

    override fun isPlaying(): Boolean {
        return currentMediaPlayerHelper.isPlaying()
    }

    override fun seek(position: Int) {
        currentMediaPlayerHelper.seek(position)
    }

    override fun getPosition(): Int? {
        return currentMediaPlayerHelper.getPosition()
    }

    override fun getDuration(): Int? {
        return currentMediaPlayerHelper.getDuration()
    }

    override fun setVolume(volume: Float) {
        currentMediaPlayerHelper.volume = volume
        nextMediaPlayerHelper.volume = volume
    }

    private val currentPlayerCallback = object : Playback.Callback {

        override fun onPlaystateChanged(isPlaying: Boolean) {
            callback?.onPlaystateChanged(isPlaying)
        }

        override fun onPlaybackComplete(song: Song) {

            if (nextQueueItem == null) {
                Timber.v("onPlaybackComplete() called. No next song")

                callback?.onPlaystateChanged(false)
            } else {
                Timber.v("onPlaybackComplete() called. Loading next song")

                // Release current media player
                Timber.v("Releasing current player")
                currentMediaPlayerHelper.callback = null
                currentMediaPlayerHelper.mediaPlayer?.reset()
                currentMediaPlayerHelper.mediaPlayer?.release()

                // Make next media player current
                Timber.v("Setting next player as current player")
                currentMediaPlayerHelper = nextMediaPlayerHelper
                currentMediaPlayerHelper.tag = "CurrentMediaPlayer"
                currentMediaPlayerHelper.callback = this

                // Update queue
                Timber.v("Updating queue")
                currentQueueItem = nextQueueItem
                currentQueueItem?.let { currentQueueItem ->
                    queueManager.setCurrentItem(currentQueueItem)
                }

                // Load next song
                nextMediaPlayerHelper = MediaPlayerHelper()
                nextMediaPlayerHelper.tag = "NextMediaPlayer"

                loadNext()
            }

            callback?.onPlaybackComplete(song)
        }
    }
}