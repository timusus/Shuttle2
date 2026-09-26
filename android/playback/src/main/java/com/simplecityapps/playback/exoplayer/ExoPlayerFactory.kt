package com.simplecityapps.playback.exoplayer

import android.content.Context
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.MediaSource
import com.simplecityapps.playback.OutputFormat
import com.simplecityapps.playback.dsp.crossfade.CapturingAudioOutputProvider
import com.simplecityapps.playback.dsp.crossfade.Crossfade
import com.simplecityapps.playback.dsp.crossfade.CrossfadeClippingMediaSourceFactory
import com.simplecityapps.playback.dsp.crossfade.CrossfadeMixer
import com.simplecityapps.playback.dsp.crossfade.TailDecoder
import com.simplecityapps.playback.dsp.replaygain.ReplayGainAudioProcessor
import com.simplecityapps.playback.engine.S2LoadErrorHandlingPolicy
import com.simplecityapps.playback.engine.SongUriResolver

/**
 * Builds the app's ExoPlayer, on the main looper: the extension renderers (FLAC, Opus) enabled, a [DefaultAudioSink]
 * running the ReplayGain, crossfade and equalizer processors (see [Crossfade]), and a [StreamSniffingMediaSourceFactory] so extensionless HLS
 * streams play, reading through [songUriResolver] so remote songs resolve their stream when they're opened. The
 * player reports the AudioTracks it opens to [audioTrackMonitor].
 *
 * The player handles audio focus and headphones being unplugged itself: it pauses when unplugged and on a permanent
 * focus loss (giving focus up), holds off on a transient loss until focus comes back, and ducks while another app may
 * play over it. It keeps focus through a pause, as Android asks of media apps, and gives it up when it stops; another
 * app asking for focus while it's paused gets it, and the player doesn't resume when that app is done.
 */
class ExoPlayerFactory(
    private val context: Context,
    private val equalizerAudioProcessor: EqualizerAudioProcessor,
    private val replayGainAudioProcessor: ReplayGainAudioProcessor,
    private val audioTrackMonitor: AudioTrackMonitor,
    private val songUriResolver: SongUriResolver,
    /** The crossfade length, 0 when it's off; read whenever the queue or the current item changes. */
    private val crossfadeDurationMs: () -> Long = { 0 },
    /** Builds the ExoPlayer around these renderers and sources. A test builds it on a fake clock. */
    private val buildPlayer: (RenderersFactory, MediaSource.Factory) -> ExoPlayer = { renderersFactory, mediaSourceFactory ->
        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLooper(Looper.getMainLooper())
            .build()
    }
) {
    /** Plays each item's clipped-off tail over the next item's head, between ReplayGain and the equalizer. */
    private val crossfadeMixer = CrossfadeMixer()

    private val renderersFactory by lazy {
        renderersFactory { context, enableFloatOutput, enableAudioOutputPlaybackParams ->
            DefaultAudioSink.Builder(context)
                // PCM output only, never passthrough, so every stream runs through the processors.
                .setAudioCapabilities(AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
                .setAudioProcessors(arrayOf(replayGainAudioProcessor, crossfadeMixer, equalizerAudioProcessor))
                .build()
        }
    }

    private val mediaSourceFactory by lazy {
        StreamSniffingMediaSourceFactory(songUriResolver.dataSourceFactory(DefaultDataSource.Factory(context)))
            .setLoadErrorHandlingPolicy(S2LoadErrorHandlingPolicy())
    }

    private fun renderersFactory(buildAudioSink: (Context, Boolean, Boolean) -> AudioSink): RenderersFactory = object : DefaultRenderersFactory(context) {
        @Suppress("DEPRECATION")
        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioOutputPlaybackParams: Boolean
        ): AudioSink = buildAudioSink(context, enableFloatOutput, enableAudioOutputPlaybackParams)
    }.apply {
        setExtensionRendererMode(EXTENSION_RENDERER_MODE_ON)
    }

    /** A player that decodes through the same renderers and sources as the playback player, into [processors], and outputs nothing. */
    private fun decoderPlayer(processors: Array<AudioProcessor>): ExoPlayer = buildPlayer(
        renderersFactory { context, enableFloatOutput, _ ->
            DefaultAudioSink.Builder(context)
                .setAudioOutputProvider(CapturingAudioOutputProvider())
                .setEnableFloatOutput(enableFloatOutput)
                .setAudioProcessors(processors)
                .build()
        },
        mediaSourceFactory
    )

    fun create(): ExoPlayer {
        val player = buildPlayer(renderersFactory, CrossfadeClippingMediaSourceFactory(mediaSourceFactory))
        val crossfade = Crossfade(player, crossfadeMixer, TailDecoder(::decoderPlayer, replayGainAudioProcessor), crossfadeDurationMs)
        player.setHandleAudioBecomingNoisy(true)
        player.setAudioAttributes(MUSIC, true)
        val owner = AudioTrackReopener(player)
        player.addAnalyticsListener(
            object : AnalyticsListener {
                override fun onAudioTrackInitialized(
                    eventTime: AnalyticsListener.EventTime,
                    audioTrackConfig: AudioSink.AudioTrackConfig
                ) {
                    audioTrackMonitor.onAudioTrackInitialized(owner, audioTrackConfig.toOutputFormat())
                }

                override fun onPlayerReleased(eventTime: AnalyticsListener.EventTime) {
                    audioTrackMonitor.onReleased(owner)
                    crossfade.release()
                }
            }
        )
        crossfade.attach()
        return player
    }
}

/** Makes [player] open a new AudioTrack. */
class AudioTrackReopener(private val player: Player) : AudioTrackMonitor.Owner {
    /**
     * A seek: the sink releases its AudioTrack on each flush and opens a new one. ExoPlayer ignores a seek to the
     * millisecond it's already at while ready or buffering, so this seeks 1 ms ahead. Ahead rather than behind:
     * while playing, [Player.getCurrentPosition] extrapolates past the position the playback thread last
     * recorded, so a seek to it, or to 1 ms before it, can land on that position and be dropped. At the last
     * millisecond it seeks 1 ms behind instead, since a seek to the duration ends the item.
     */
    override fun reopenAudioTrack() {
        val position = player.currentPosition
        val duration = player.duration
        val atEnd = duration != C.TIME_UNSET && position + 1 >= duration
        player.seekTo(if (atEnd) (position - 1).coerceAtLeast(0) else position + 1)
    }
}

/** The channel config is an `AudioFormat.CHANNEL_OUT_*` mask, one bit per channel. */
internal fun AudioSink.AudioTrackConfig.toOutputFormat() = OutputFormat(
    sampleRate = sampleRate,
    channelCount = Integer.bitCount(channelConfig),
    encoding = encoding
)

private val MUSIC: AudioAttributes = AudioAttributes.Builder()
    .setUsage(C.USAGE_MEDIA)
    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
    .build()
