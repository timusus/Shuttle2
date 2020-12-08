package com.simplecityapps.playback.local.exoplayer

import android.content.Context
import android.net.Uri
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
import com.google.android.exoplayer2.audio.AudioCapabilities
import com.google.android.exoplayer2.audio.AudioProcessor
import com.google.android.exoplayer2.audio.AudioSink
import com.google.android.exoplayer2.audio.DefaultAudioSink
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.playback.Playback
import com.simplecityapps.playback.chromecast.CastPlayback
import com.simplecityapps.provider.emby.EmbyAuthenticationManager
import timber.log.Timber

class ExoPlayerPlayback(
    context: Context,
    private val audioProcessor: AudioProcessor,
    private val embyAuthenticationManager: EmbyAuthenticationManager
) : Playback {

    override var callback: Playback.Callback? = null

    override var isReleased: Boolean = false

    private val player: ExoPlayer by lazy {
        initPlayer(context)
    }

    private val concatenatingMediaSource by lazy { ConcatenatingMediaSource() }

    private val dataSourceFactory by lazy { DefaultDataSourceFactory(context, Util.getUserAgent(context, "Shuttle 2.0")) }

    private val mediaSourceFactory by lazy { DefaultMediaSourceFactory(dataSourceFactory) }

    private var playWhenReady = false

    private var isPlaybackReady = false

    private val trackChangeListener by lazy {

        object : TrackChangeEventListener(player) {
            override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                val state = playbackState.toState()
                Timber.v("onPlayerStateChanged(playWhenReady: $playWhenReady, playbackState: ${state})")

                if (playWhenReady != this@ExoPlayerPlayback.playWhenReady) {
                    callback?.onPlayStateChanged(playWhenReady)
                    this@ExoPlayerPlayback.playWhenReady = playWhenReady
                }

                if (state == PlaybackState.Ready) {
                    isPlaybackReady = true
                }

                if (state == PlaybackState.Ended) {
                    if (isPlaybackReady) {
                        player.playWhenReady = false
                        this@ExoPlayerPlayback.playWhenReady = false
                        callback?.onPlayStateChanged(isPlaying = false)
                        callback?.onPlaybackComplete(trackWentToNext = false)
                        isPlaybackReady = false
                    }
                }
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                super.onTimelineChanged(timeline, reason)

                updateWindowIndex()
            }

            override fun onTrackChanged() {
                Timber.v("onTrackChanged()")
                callback?.onPlaybackComplete(trackWentToNext = true)
            }

            override fun onPlayerError(error: ExoPlaybackException) {
                super.onPlayerError(error)

                Timber.e(error, "onPlayerError()")
            }
        }
    }

    private fun initPlayer(context: Context): ExoPlayer {

        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean, enableOffload: Boolean): AudioSink? {
                return DefaultAudioSink(AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES, DefaultAudioSink.DefaultAudioProcessorChain(audioProcessor).audioProcessors)
            }
        }

        renderersFactory.setExtensionRendererMode(EXTENSION_RENDERER_MODE_ON)

        val simpleExoPlayer = SimpleExoPlayer.Builder(context, renderersFactory).build()
        simpleExoPlayer.setWakeMode(C.WAKE_MODE_LOCAL)
        return simpleExoPlayer
    }

    override fun load(current: Song, next: Song?, seekPosition: Int, completion: (Result<Any?>) -> Unit) {
        Timber.v("load(current: ${current.name})")
        isReleased = false

        player.addListener(trackChangeListener)
        player.seekTo(seekPosition.toLong())

        concatenatingMediaSource.clear()

        val uri = Uri.parse(current.path)
        if (uri.scheme == "emby") {
            embyAuthenticationManager.getAuthenticatedCredentials()?.let { authenticatedCredentials ->
                embyAuthenticationManager.buildEmbyPath(
                    uri.pathSegments.last(),
                    authenticatedCredentials
                )?.let { path ->
                    concatenatingMediaSource.addMediaSource(
                        mediaSourceFactory.createMediaSource(MediaItem.fromUri(path))
                    )
                } ?: run {
                    completion(Result.failure(IllegalStateException("Failed to build emby path")))
                }
            } ?: run {
                completion(Result.failure(IllegalStateException("Failed to authenticate")))
                return@run
            }
        } else {
            concatenatingMediaSource.addMediaSource(mediaSourceFactory.createMediaSource(MediaItem.fromUri(current.path)))
        }

        Timber.v("load() calling updateWindowIndex()")
        trackChangeListener.updateWindowIndex()

        player.setMediaSource(concatenatingMediaSource)
        player.prepare(concatenatingMediaSource)

        completion(Result.success(null))

        loadNext(next)
    }

    override fun loadNext(song: Song?) {
        Timber.v("loadNext(${song?.name})")
        val currentWindowIndex = player.currentWindowIndex
        song?.let {
            if (concatenatingMediaSource.size > 1) {
                if (currentWindowIndex == 0) {
                    // We're at the first track. Remove the second
                    concatenatingMediaSource.removeMediaSource(1)
                } else if (currentWindowIndex == 1) {
                    // We're at the second track. Move the first
                    concatenatingMediaSource.removeMediaSource(0)
                }
            }
            concatenatingMediaSource.addMediaSource(mediaSourceFactory.createMediaSource(MediaItem.fromUri(song.path)))
        }

        // Let the track change listener know that the window index has changed, so we don't get a false 'track changed' call
        trackChangeListener.updateWindowIndex()
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


    enum class PlaybackState {
        Idle, Buffering, Ready, Ended, Unknown;
    }

    fun Int.toState(): PlaybackState {
        return when (this) {
            1 -> PlaybackState.Idle
            2 -> PlaybackState.Buffering
            3 -> PlaybackState.Ready
            4 -> PlaybackState.Ended
            else -> PlaybackState.Unknown
        }
    }


    /**
     * A custom [Player.EventListener] which calls [onTrackChanged] when appropriate.
     */
    abstract class TrackChangeEventListener(private val player: ExoPlayer) : Player.EventListener {
        private var currentWindowIndex: Int = 0

        fun updateWindowIndex() {
            currentWindowIndex = player.currentWindowIndex
        }

        override fun onPositionDiscontinuity(reason: Int) {
            if (currentWindowIndex != player.currentWindowIndex && reason.toDiscontinuityReason() == DiscontinuityReason.PeriodTransition) {
                onTrackChanged()
            }
        }

        abstract fun onTrackChanged()

        enum class DiscontinuityReason {
            PeriodTransition, Seek, SeekAdjustment, AdInsertion, Internal, Unknown
        }

        private fun Int.toDiscontinuityReason(): DiscontinuityReason {
            return when (this) {
                0 -> DiscontinuityReason.PeriodTransition
                1 -> DiscontinuityReason.Seek
                2 -> DiscontinuityReason.SeekAdjustment
                3 -> DiscontinuityReason.AdInsertion
                4 -> DiscontinuityReason.Internal
                else -> DiscontinuityReason.Unknown
            }
        }
    }
}