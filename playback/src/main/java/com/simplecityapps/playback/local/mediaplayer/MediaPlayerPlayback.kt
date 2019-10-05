package com.simplecityapps.playback.local.mediaplayer

import android.content.Context
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.playback.Playback
import timber.log.Timber

class MediaPlayerPlayback(
    private val context: Context
) : Playback {

    private var currentMediaPlayerHelper = MediaPlayerHelper()

    private var nextMediaPlayerHelper = MediaPlayerHelper()

    override var callback: Playback.Callback? = null

    override var isReleased: Boolean = true

    init {
        currentMediaPlayerHelper.tag = "CurrentMediaPlayer"
        nextMediaPlayerHelper.tag = "NextMediaPlayer"
    }

    override fun load(current: Song, next: Song?, seekPosition: Int, completion: (Result<Any?>) -> Unit) {
        Timber.v("load(current: ${current.name})")
        isReleased = false
        currentMediaPlayerHelper.callback = currentPlayerCallback
        currentMediaPlayerHelper.load(context, current) { result ->
            result.onSuccess {
                seek(seekPosition)
                loadNext(next)
            }
            completion(result)
        }
    }

    override fun loadNext(song: Song?) {
        Timber.v("loadNext(song: ${song?.name})")
        song?.let { song ->
            nextMediaPlayerHelper.load(context, song) { result ->
                result.onSuccess { currentMediaPlayerHelper.setNextMediaPlayer(nextMediaPlayerHelper.mediaPlayer) }
                result.onFailure { error -> Timber.e("Failed to load next media player: $error") }
            }
        } ?: run {
            Timber.v("loadNext() next song null, releasing.")
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

    override fun release() {
        currentMediaPlayerHelper.callback = null
        currentMediaPlayerHelper.release()
        nextMediaPlayerHelper.callback = null
        nextMediaPlayerHelper.release()
        isReleased = true
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

        override fun onPlayStateChanged(isPlaying: Boolean) {
            callback?.onPlayStateChanged(isPlaying)
        }

        override fun onPlaybackComplete(trackWentToNext: Boolean) {
            if (nextMediaPlayerHelper.isReleased) {
                Timber.v("onPlaybackComplete() called. No next song")

                callback?.onPlayStateChanged(false)
                callback?.onPlaybackComplete(false)
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

                // Load next song
                nextMediaPlayerHelper = MediaPlayerHelper()
                nextMediaPlayerHelper.tag = "NextMediaPlayer"

                callback?.onPlaybackComplete(true)
            }
        }
    }
}