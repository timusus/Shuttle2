package com.simplecityapps.playback.exoplayer

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.simplecityapps.playback.dsp.replaygain.ReplayGain
import com.simplecityapps.playback.dsp.replaygain.ReplayGainAudioProcessor

/**
 * Builds [ExoAudioPlayer]s with the extension renderers (FLAC, Opus) enabled, the audio sink
 * chain ([ReplayGainAudioSink] around a [DefaultAudioSink] running the equalizer and ReplayGain
 * processors), and a [StreamSniffingMediaSourceFactory] so extensionless HLS streams play.
 */
class ExoPlayerFactory(
    private val context: Context,
    private val equalizerAudioProcessor: EqualizerAudioProcessor,
    private val replayGainAudioProcessor: ReplayGainAudioProcessor
) : PlayerFactory {
    private val replayGainTracker get() = replayGainAudioProcessor.streamTracker

    private val renderersFactory by lazy {
        object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioOutputPlaybackParams: Boolean
            ): AudioSink = ReplayGainAudioSink(
                @Suppress("DEPRECATION")
                DefaultAudioSink.Builder(context)
                    // PCM output only, never passthrough, so every stream runs through the processors.
                    .setAudioCapabilities(AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
                    .setAudioProcessors(arrayOf(equalizerAudioProcessor, replayGainAudioProcessor))
                    .build(),
                replayGainTracker
            )
        }.apply {
            setExtensionRendererMode(EXTENSION_RENDERER_MODE_ON)
        }
    }

    override fun create(): AudioPlayer = ExoAudioPlayer(
        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(StreamSniffingMediaSourceFactory(DefaultDataSource.Factory(context)))
            .build()
    )
}

/** Forwards each [AudioPlayer] call to [player], mapping [PlayerItem]s to and from [MediaItem]s. */
class ExoAudioPlayer(private val player: ExoPlayer) : AudioPlayer {
    private val exoListeners = mutableMapOf<AudioPlayer.Listener, Player.Listener>()

    override var playWhenReady: Boolean
        get() = player.playWhenReady
        set(value) {
            player.playWhenReady = value
        }

    override val isPlaying: Boolean get() = player.isPlaying

    override var repeatMode: Int
        get() = player.repeatMode
        set(value) {
            player.repeatMode = value
        }

    override var audioSessionId: Int
        get() = player.audioSessionId
        set(value) {
            player.audioSessionId = value
        }

    override val mediaItemCount: Int get() = player.mediaItemCount

    override val currentWindowIndex: Int get() = player.currentMediaItemIndex

    override val contentPosition: Long get() = player.contentPosition

    override val duration: Long get() = player.duration

    override val playbackSpeed: Float get() = player.playbackParameters.speed

    override fun addListener(listener: AudioPlayer.Listener) {
        player.addListener(exoListener(listener))
    }

    override fun removeListener(listener: AudioPlayer.Listener) {
        player.removeListener(exoListener(listener))
    }

    private fun exoListener(listener: AudioPlayer.Listener): Player.Listener = exoListeners.getOrPut(listener) {
        object : Player.Listener {
            override fun onPlayWhenReadyChanged(
                playWhenReady: Boolean,
                reason: Int
            ) {
                super.onPlayWhenReadyChanged(playWhenReady, reason)
                listener.onPlayWhenReadyChanged(playWhenReady)
            }

            override fun onPlaybackStateChanged(state: Int) {
                super.onPlaybackStateChanged(state)
                listener.onPlaybackStateChanged(state)
            }

            override fun onMediaItemTransition(
                mediaItem: MediaItem?,
                reason: Int
            ) {
                super.onMediaItemTransition(mediaItem, reason)
                listener.onMediaItemTransition(reason)
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                super.onPositionDiscontinuity(oldPosition, newPosition, reason)
                listener.onPositionDiscontinuity(reason)
            }

            override fun onPlayerError(error: PlaybackException) {
                super.onPlayerError(error)
                listener.onPlayerError(error)
            }
        }
    }

    override fun pause() {
        player.pause()
    }

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    override fun setMediaItem(item: PlayerItem) {
        player.setMediaItem(item.toMediaItem())
    }

    override fun addMediaItem(item: PlayerItem) {
        player.addMediaItem(item.toMediaItem())
    }

    override fun getMediaItemAt(index: Int): PlayerItem = player.getMediaItemAt(index).toPlayerItem()

    override fun removeMediaItems(
        fromIndex: Int,
        toIndex: Int
    ) {
        player.removeMediaItems(fromIndex, toIndex)
    }

    override fun prepare() {
        player.prepare()
    }

    override fun setWakeMode(wakeMode: Int) {
        player.setWakeMode(wakeMode)
    }

    override fun setVolume(volume: Float) {
        player.volume = volume
    }

    override fun setPlaybackParameters(
        speed: Float,
        pitch: Float
    ) {
        player.setPlaybackParameters(PlaybackParameters(speed, pitch))
    }

    override fun release() {
        player.release()
    }

    private fun PlayerItem.toMediaItem(): MediaItem = MediaItem.Builder()
        .setMimeType(mimeType)
        .setUri(uri)
        .setTag(replayGain)
        .build()

    /** Every item in the playlist was built by [toMediaItem], so it always has a local configuration. */
    private fun MediaItem.toPlayerItem(): PlayerItem {
        val properties = checkNotNull(localConfiguration) { "MediaItem has no local configuration" }
        return PlayerItem(
            uri = properties.uri.toString(),
            mimeType = properties.mimeType,
            replayGain = properties.tag as? ReplayGain
        )
    }
}
