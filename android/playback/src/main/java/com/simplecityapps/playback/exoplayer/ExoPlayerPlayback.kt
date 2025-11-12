package com.simplecityapps.playback.exoplayer

import android.content.Context
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.audio.AudioCapabilities
import com.google.android.exoplayer2.audio.AudioSink
import com.google.android.exoplayer2.audio.DefaultAudioSink
import com.simplecityapps.mediaprovider.MediaInfo
import com.simplecityapps.mediaprovider.MediaInfoProvider
import com.simplecityapps.playback.Playback
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.chromecast.CastPlayback
import com.simplecityapps.playback.dsp.replaygain.ReplayGainAudioProcessor
import com.simplecityapps.playback.mediasession.toRepeatMode
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.model.Song
import timber.log.Timber

class ExoPlayerPlayback(
    private val context: Context,
    private val equalizerAudioProcessor: EqualizerAudioProcessor,
    private val replayGainAudioProcessor: ReplayGainAudioProcessor,
    private val mediaInfoProvider: MediaInfoProvider
) : Playback {
    override var callback: Playback.Callback? = null

    override var isReleased: Boolean = true

    private var isPlaybackReady = false

    private val eventListener by lazy {
        object : Player.Listener {
            override fun onPlayWhenReadyChanged(
                playWhenReady: Boolean,
                reason: Int
            ) {
                super.onPlayWhenReadyChanged(playWhenReady, reason)
                Timber.v("onPlayWhenReadyChanged(playWhenReady: $playWhenReady)")
                callback?.onPlaybackStateChanged(if (playWhenReady) PlaybackState.Playing else PlaybackState.Paused)
            }

            override fun onPlaybackStateChanged(state: Int) {
                super.onPlaybackStateChanged(state)

                val playbackState = state.toExoPlaybackState()
                Timber.v("onPlaybackStateChanged(playbackState: $playbackState)")

                when (playbackState) {
                    ExoPlaybackState.Idle -> {
                    }
                    ExoPlaybackState.Buffering -> {
                    }
                    ExoPlaybackState.Ready -> {
                        isPlaybackReady = true
                        if (player.playWhenReady) {
                            callback?.onPlaybackStateChanged(PlaybackState.Playing)
                        } else {
                            callback?.onPlaybackStateChanged(PlaybackState.Paused)
                        }
                    }
                    ExoPlaybackState.Ended -> {
                        if (isPlaybackReady) {
                            player.playWhenReady = false
                            callback?.onPlaybackStateChanged(PlaybackState.Paused)
                            callback?.onTrackEnded(false)
                            isPlaybackReady = false
                        }
                    }
                    ExoPlaybackState.Unknown -> {
                    }
                }
            }

            override fun onMediaItemTransition(
                mediaItem: MediaItem?,
                reason: Int
            ) {
                super.onMediaItemTransition(mediaItem, reason)

                val transitionReason = reason.toTransitionReason()
                Timber.v("onMediaItemTransition(reason: ${reason.toTransitionReason()})")

                // Update ReplayGain immediately from the MediaItem's tag to ensure correct gain for this track
                mediaItem?.localConfiguration?.tag?.let { tag ->
                    if (tag is ReplayGainTag) {
                        Timber.v("Updating ReplayGain from MediaItem tag: track=${tag.trackGain}, album=${tag.albumGain}")
                        replayGainAudioProcessor.trackGain = tag.trackGain
                        replayGainAudioProcessor.albumGain = tag.albumGain
                    }
                }

                when (transitionReason) {
                    TransitionReason.Repeat -> callback?.onTrackEnded(true)
                    TransitionReason.Auto -> callback?.onTrackEnded(true)
                    TransitionReason.Seek,
                    TransitionReason.PlaylistChanged,
                    TransitionReason.Unknown
                    -> {
                        // Nothing to do
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                super.onPlayerError(error)

                Timber.e(error, "onPlayerError()")
                callback?.onPlaybackStateChanged(PlaybackState.Paused)
            }
        }
    }

    private val renderersFactory by lazy {
        object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
                enableOffload: Boolean
            ): AudioSink = DefaultAudioSink(
                AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES,
                DefaultAudioSink.DefaultAudioProcessorChain(
                    equalizerAudioProcessor,
                    replayGainAudioProcessor
                ).audioProcessors
            )
        }.apply {
            setExtensionRendererMode(EXTENSION_RENDERER_MODE_ON)
        }
    }

    private var player: SimpleExoPlayer = SimpleExoPlayer.Builder(context, renderersFactory).build()

    init {
        // Give the ReplayGainAudioProcessor access to the player so it can query currentMediaItem
        replayGainAudioProcessor.player = player
    }

    override suspend fun load(
        current: Song,
        next: Song?,
        seekPosition: Int,
        completion: (Result<Any?>) -> Unit
    ) {
        Timber.v("load(current: ${current.name}|${current.mimeType}, seekPosition: $seekPosition)")

        if (isReleased) {
            player = SimpleExoPlayer.Builder(context, renderersFactory).build()
            replayGainAudioProcessor.player = player
            isReleased = false
        }

        player.removeListener(eventListener)
        player.pause()
        player.seekTo(0)

        callback?.onPlaybackStateChanged(PlaybackState.Loading)

        val mediaInfo = mediaInfoProvider.getMediaInfo(current)
        player.addListener(eventListener)
        val mediaItem = getMediaItem(mediaInfo, current.replayGainTrack, current.replayGainAlbum)
        player.setMediaItem(mediaItem)
        // Set ReplayGain immediately for the current track in case onMediaItemTransition doesn't fire
        replayGainAudioProcessor.trackGain = current.replayGainTrack
        replayGainAudioProcessor.albumGain = current.replayGainAlbum
        player.seekTo(seekPosition.toLong())
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

        if (player.repeatMode == Player.REPEAT_MODE_ONE) {
            return
        }

        val nextMediaItem: MediaItem? =
            song?.let {
                getMediaItem(mediaInfoProvider.getMediaInfo(song), song.replayGainTrack, song.replayGainAlbum)
            }

        val count = player.mediaItemCount
        val currentIndex = player.currentWindowIndex

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

    override fun playBackState(): PlaybackState = if (player.isPlaying || player.playWhenReady) {
        PlaybackState.Playing
    } else {
        PlaybackState.Paused
    }

    override fun seek(position: Int) {
        player.seekTo(position.toLong())
    }

    override fun getProgress(): Int = player.contentPosition.toInt()

    override fun getDuration(): Int? = player.duration.takeIf { duration -> duration != C.TIME_UNSET }?.toInt()

    override fun setVolume(volume: Float) {
        player.audioComponent?.volume = volume
    }

    override fun getResumeWhenSwitched(oldPlayback: Playback): Boolean = oldPlayback !is CastPlayback

    override fun setRepeatMode(repeatMode: QueueManager.RepeatMode) {
        player.repeatMode = repeatMode.toRepeatMode()
    }

    override fun setAudioSessionId(id: Int) {
        if (id != -1) {
            player.audioSessionId = id
        } else {
            Timber.e("Failed to set audio session id (sessionId: -1)")
        }
    }

    override fun setReplayGain(
        trackGain: Double?,
        albumGain: Double?
    ) {
        Timber.v("setReplayGain(trackGain: $trackGain, albumGain: $albumGain)")
        replayGainAudioProcessor.trackGain = trackGain
        replayGainAudioProcessor.albumGain = albumGain
    }

    override fun setPlaybackSpeed(multiplier: Float) {
        player.setPlaybackParameters(PlaybackParameters(multiplier, multiplier))
    }

    override fun getPlaybackSpeed(): Float = player.playbackParameters.speed

    enum class ExoPlaybackState {
        Idle,
        Buffering,
        Ready,
        Ended,
        Unknown
    }

    fun Int.toExoPlaybackState(): ExoPlaybackState = when (this) {
        1 -> ExoPlaybackState.Idle
        2 -> ExoPlaybackState.Buffering
        3 -> ExoPlaybackState.Ready
        4 -> ExoPlaybackState.Ended
        else -> ExoPlaybackState.Unknown
    }

    enum class TransitionReason {
        Repeat,
        Auto,
        Seek,
        PlaylistChanged,
        Unknown
    }

    fun Int.toTransitionReason(): TransitionReason = when (this) {
        0 -> TransitionReason.Repeat
        1 -> TransitionReason.Auto
        2 -> TransitionReason.Seek
        3 -> TransitionReason.PlaylistChanged
        else -> TransitionReason.Unknown
    }

    data class ReplayGainTag(val trackGain: Double?, val albumGain: Double?)

    @Throws(IllegalStateException::class)
    fun getMediaItem(
        mediaInfo: MediaInfo,
        trackGain: Double? = null,
        albumGain: Double? = null
    ): MediaItem = MediaItem.Builder()
        .setMimeType(mediaInfo.mimeType)
        .setUri(mediaInfo.path)
        .setTag(ReplayGainTag(trackGain, albumGain))
        .build()
}
