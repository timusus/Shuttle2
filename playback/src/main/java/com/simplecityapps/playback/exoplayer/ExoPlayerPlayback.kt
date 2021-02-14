package com.simplecityapps.playback.exoplayer

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
import com.google.android.exoplayer2.audio.AudioCapabilities
import com.google.android.exoplayer2.audio.AudioProcessor
import com.google.android.exoplayer2.audio.AudioSink
import com.google.android.exoplayer2.audio.DefaultAudioSink
import com.simplecityapps.mediaprovider.MediaPathProvider
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.playback.Playback
import com.simplecityapps.playback.chromecast.CastPlayback
import com.simplecityapps.playback.mediasession.toRepeatMode
import com.simplecityapps.playback.queue.QueueManager
import timber.log.Timber

class ExoPlayerPlayback(
    context: Context,
    private val audioProcessor: AudioProcessor,
    private val mediaPathProvider: MediaPathProvider
) : Playback {

    override var callback: Playback.Callback? = null

    override var isReleased: Boolean = false

    private val player: SimpleExoPlayer by lazy {
        initPlayer(context)
    }

    private var playWhenReady = false

    private var isPlaybackReady = false

    private val eventListener by lazy {
        object : Player.EventListener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                super.onPlayWhenReadyChanged(playWhenReady, reason)

                if (playWhenReady != this@ExoPlayerPlayback.playWhenReady) {
                    callback?.onPlayStateChanged(playWhenReady)
                    this@ExoPlayerPlayback.playWhenReady = playWhenReady
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                super.onPlaybackStateChanged(state)

                val playbackState = state.toPlaybackState()
                Timber.v("onPlayerStateChanged(playWhenReady: $playWhenReady, playbackState: ${playbackState})")

                if (playbackState == PlaybackState.Ready) {
                    isPlaybackReady = true
                }

                if (playbackState == PlaybackState.Ended) {
                    if (isPlaybackReady) {
                        player.playWhenReady = false
                        this@ExoPlayerPlayback.playWhenReady = false
                        callback?.onPlayStateChanged(isPlaying = false)
                        callback?.onTrackEnded(false)
                        isPlaybackReady = false
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)

                val transitionReason = reason.toTransitionReason()
                Timber.v("onMediaItemTransition(reason: ${reason.toTransitionReason()})")

                when (transitionReason) {
                    TransitionReason.Repeat -> callback?.onTrackEnded(true)
                    TransitionReason.Auto -> callback?.onTrackEnded(true)
                    TransitionReason.Seek,
                    TransitionReason.PlaylistChanged,
                    TransitionReason.Unknown -> {
                        // Nothing to do
                    }
                }
            }

            override fun onPlayerError(error: ExoPlaybackException) {
                super.onPlayerError(error)

                Timber.e(error, "onPlayerError()")
            }
        }
    }

    private fun initPlayer(context: Context): SimpleExoPlayer {

        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean, enableOffload: Boolean): AudioSink {
                return DefaultAudioSink(AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES, DefaultAudioSink.DefaultAudioProcessorChain(audioProcessor).audioProcessors)
            }
        }

        renderersFactory.setExtensionRendererMode(EXTENSION_RENDERER_MODE_ON)

        val simpleExoPlayer = SimpleExoPlayer.Builder(context, renderersFactory).build()
        simpleExoPlayer.setWakeMode(C.WAKE_MODE_LOCAL)

        return simpleExoPlayer
    }

    override fun load(current: Song, next: Song?, seekPosition: Int, completion: (Result<Any?>) -> Unit) {
        Timber.v("load(current: ${current.name}|${current.mimeType})")
        isReleased = false

        player.addListener(eventListener)
        player.seekTo(seekPosition.toLong())
        val uri = current.path.toUri()
        val mediaItem = getMediaItem(uri)
        player.setMediaItem(mediaItem)
        player.prepare()

        if (mediaPathProvider.isRemote(uri)) {
            player.setWakeMode(C.WAKE_MODE_NETWORK)
        } else {
            player.setWakeMode(C.WAKE_MODE_LOCAL)
        }

        completion(Result.success(null))

        loadNext(next)
    }

    override fun loadNext(song: Song?) {
        Timber.v("loadNext(song: ${song?.name}|${song?.mimeType})")

        val nextMediaItem = song?.let { getMediaItem(song.path.toUri()) }

        val currentMediaItem = player.currentMediaItem
        val count = player.mediaItemCount
        var currentIndex = 0
        for (i in 0 until player.mediaItemCount) {
            if (player.getMediaItemAt(i) == currentMediaItem) {
                currentIndex = i
                break
            }
        }

        // Shortcut if the track is already next in the queue
        val nextIndex = currentIndex + 1
        if (count > nextIndex) {
            if (player.getMediaItemAt(nextIndex) == nextMediaItem) {
                return
            }
        }

        // Remove any songs after the current media item
        if (currentIndex < count - 1) {
            player.removeMediaItems(count - 1, count)
        }

        // Now insert our new next track
        nextMediaItem?.let {
            player.addMediaItem(nextMediaItem)
        }
    }

    override fun play() {
        Timber.v("play()")
        player.playWhenReady = true
    }

    override fun pause() {
        Timber.v("pause()")
        player.playWhenReady = false
    }

    override fun release() {
        player.release()
        isReleased = true
    }

    override fun isPlaying(): Boolean {
        return player.isPlaying || player.playWhenReady
    }

    override fun seek(position: Int) {
        player.seekTo(position.toLong())
    }

    override fun getProgress(): Int {
        return player.contentPosition.toInt()
    }

    override fun getDuration(): Int? {
        return player.duration.takeIf { duration -> duration != C.TIME_UNSET }?.toInt()
    }

    override fun setVolume(volume: Float) {
        player.audioComponent?.volume = volume
    }

    override fun getResumeWhenSwitched(oldPlayback: Playback): Boolean {
        return oldPlayback !is CastPlayback
    }

    override fun setRepeatMode(repeatMode: QueueManager.RepeatMode) {
        player.repeatMode = repeatMode.toRepeatMode()
    }

    override fun setAudioSessionId(sessionId: Int) {
        if (sessionId != -1) {
            player.audioSessionId = sessionId
        } else {
            Timber.e("Failed to set audio session id (sessionId: -1)")
        }
    }


    enum class PlaybackState {
        Idle, Buffering, Ready, Ended, Unknown;
    }

    fun Int.toPlaybackState(): PlaybackState {
        return when (this) {
            1 -> PlaybackState.Idle
            2 -> PlaybackState.Buffering
            3 -> PlaybackState.Ready
            4 -> PlaybackState.Ended
            else -> PlaybackState.Unknown
        }
    }

    enum class TransitionReason {
        Repeat, Auto, Seek, PlaylistChanged, Unknown;
    }

    fun Int.toTransitionReason(): TransitionReason {
        return when (this) {
            0 -> TransitionReason.Repeat
            1 -> TransitionReason.Auto
            2 -> TransitionReason.Seek
            3 -> TransitionReason.PlaylistChanged
            else -> TransitionReason.Unknown
        }
    }

    @Throws(IllegalStateException::class)
    fun getMediaItem(uri: Uri): MediaItem {
        return MediaItem.fromUri(mediaPathProvider.getPath(uri))
    }
}