package com.simplecityapps.playback.exoplayer

import android.content.Context
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
import com.google.android.exoplayer2.audio.AudioCapabilities
import com.google.android.exoplayer2.audio.AudioSink
import com.google.android.exoplayer2.audio.DefaultAudioSink
import com.simplecityapps.mediaprovider.MediaInfo
import com.simplecityapps.mediaprovider.MediaInfoProvider
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.playback.Playback
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.chromecast.CastPlayback
import com.simplecityapps.playback.dsp.replaygain.ReplayGainAudioProcessor
import com.simplecityapps.playback.mediasession.toRepeatMode
import com.simplecityapps.playback.queue.QueueManager
import timber.log.Timber

class ExoPlayerPlayback(
    context: Context,
    private val equalizerAudioProcessor: EqualizerAudioProcessor,
    private val replayGainAudioProcessor: ReplayGainAudioProcessor,
    private val mediaInfoProvider: MediaInfoProvider
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
                    callback?.onPlaybackStateChanged(if (playWhenReady) PlaybackState.Playing else PlaybackState.Paused)
                    this@ExoPlayerPlayback.playWhenReady = playWhenReady
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                super.onPlaybackStateChanged(state)

                val playbackState = state.toExoPlaybackState()
                Timber.v("onPlayerStateChanged(playWhenReady: $playWhenReady, playbackState: ${playbackState})")

                when (playbackState) {
                    ExoPlaybackState.Idle -> {

                    }
                    ExoPlaybackState.Buffering -> {

                    }
                    ExoPlaybackState.Ready -> {
                        isPlaybackReady = true
                        if (playWhenReady) {
                            callback?.onPlaybackStateChanged(PlaybackState.Playing)
                        } else {
                            callback?.onPlaybackStateChanged(PlaybackState.Paused)
                        }
                    }
                    ExoPlaybackState.Ended -> {
                        if (isPlaybackReady) {
                            player.playWhenReady = false
                            this@ExoPlayerPlayback.playWhenReady = false
                            callback?.onPlaybackStateChanged(PlaybackState.Paused)
                            callback?.onTrackEnded(false)
                            isPlaybackReady = false
                        }
                    }
                    ExoPlaybackState.Unknown -> {

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
                callback?.onPlaybackStateChanged(PlaybackState.Paused)
            }
        }
    }

    private fun initPlayer(context: Context): SimpleExoPlayer {
        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean, enableOffload: Boolean): AudioSink {
                return DefaultAudioSink(
                    AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES,
                    DefaultAudioSink.DefaultAudioProcessorChain(
                        equalizerAudioProcessor,
                        replayGainAudioProcessor
                    ).audioProcessors
                )
            }
        }

        renderersFactory.setExtensionRendererMode(EXTENSION_RENDERER_MODE_ON)

        val simpleExoPlayer = SimpleExoPlayer.Builder(context, renderersFactory).build()
        simpleExoPlayer.setWakeMode(C.WAKE_MODE_LOCAL)

        return simpleExoPlayer
    }

    override suspend fun load(current: Song, next: Song?, seekPosition: Int, completion: (Result<Any?>) -> Unit) {
        Timber.v("load(current: ${current.name}|${current.mimeType})")

        isReleased = false

        player.removeListener(eventListener)
        player.pause()
        player.seekTo(0)

        callback?.onPlaybackStateChanged(PlaybackState.Loading)

        val mediaInfo = mediaInfoProvider.getMediaInfo(current)
        player.addListener(eventListener)
        player.seekTo(seekPosition.toLong())
        player.setMediaItem(getMediaItem(mediaInfo))
        player.prepare()

        if (mediaInfo.isRemote) {
            player.setWakeMode(C.WAKE_MODE_NETWORK)
        } else {
            player.setWakeMode(C.WAKE_MODE_LOCAL)
        }

        completion(Result.success(null))

        loadNext(next)
    }

    override suspend fun loadNext(song: Song?) {
        Timber.v("loadNext(song: ${song?.name}|${song?.mimeType})")

        val nextMediaItem: MediaItem? = song?.let {
            getMediaItem(mediaInfoProvider.getMediaInfo(song))
        }

        val currentMediaItem = player.currentMediaItem
        val count = player.mediaItemCount
        var currentIndex = 0
        for (i in player.mediaItemCount -1 downTo 0) {
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

    override fun playBackState(): PlaybackState {
        return if (player.isPlaying || player.playWhenReady) {
            PlaybackState.Playing
        } else {
            PlaybackState.Paused
        }
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

    override fun setReplayGain(trackGain: Double?, albumGain: Double?) {
        Timber.v("setReplayGain(trackGain: $trackGain, albumGain: $albumGain)")
        replayGainAudioProcessor.trackGain = trackGain
        replayGainAudioProcessor.albumGain = albumGain
    }

    enum class ExoPlaybackState {
        Idle, Buffering, Ready, Ended, Unknown;
    }

    fun Int.toExoPlaybackState(): ExoPlaybackState {
        return when (this) {
            1 -> ExoPlaybackState.Idle
            2 -> ExoPlaybackState.Buffering
            3 -> ExoPlaybackState.Ready
            4 -> ExoPlaybackState.Ended
            else -> ExoPlaybackState.Unknown
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
    fun getMediaItem(mediaInfo: MediaInfo): MediaItem {
        return MediaItem.Builder()
            .setMimeType(mediaInfo.mimeType)
            .setUri(mediaInfo.path)
            .build()
    }
}