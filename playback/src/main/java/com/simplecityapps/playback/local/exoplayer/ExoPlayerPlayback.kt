package com.simplecityapps.playback.local.exoplayer

import android.content.Context
import android.net.Uri
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
import com.google.android.exoplayer2.audio.AudioProcessor
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.playback.Playback
import timber.log.Timber

class ExoPlayerPlayback(
    context: Context,
    private val audioProcessor: AudioProcessor
) : Playback {

    override var callback: Playback.Callback? = null

    override var isReleased: Boolean = false

    private val player: ExoPlayer by lazy {
        initPlayer(context)
    }

    private val concatenatingMediaSource by lazy { ConcatenatingMediaSource() }
    private val dataSourceFactory by lazy { DefaultDataSourceFactory(context, Util.getUserAgent(context, "Shuttle")) }
    private val mediaSourceFactory by lazy { ProgressiveMediaSource.Factory(dataSourceFactory) }

    private var playWhenReady = false

    private var trackChangeListener: TrackChangeEventListener? = null

    private fun initPlayer(context: Context): ExoPlayer {

        val renderersFactory = object : DefaultRenderersFactory(context) {

            override fun buildAudioProcessors(): Array<AudioProcessor> {
                return super.buildAudioProcessors().plus(audioProcessor)
            }
        }

        renderersFactory.setExtensionRendererMode(EXTENSION_RENDERER_MODE_ON)

        return SimpleExoPlayer.Builder(context, renderersFactory).build()
    }

    override fun load(current: Song, next: Song?, seekPosition: Int, completion: (Result<Any?>) -> Unit) {
        Timber.v("load(current: ${current.name})")
        isReleased = false

        trackChangeListener =
            object : TrackChangeEventListener(player) {

                override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                    val state = playbackState.toState()
                    Timber.v("onPlayerStateChanged(playWhenReady: $playWhenReady, playbackState: ${state})")

                    if (playWhenReady != this@ExoPlayerPlayback.playWhenReady) {
                        callback?.onPlayStateChanged(playWhenReady)
                        this@ExoPlayerPlayback.playWhenReady = playWhenReady
                    }

                    if (state == PlaybackState.Ended) {
                        callback?.onPlayStateChanged(isPlaying = false)
                        callback?.onPlaybackComplete(trackWentToNext = false)
                        this@ExoPlayerPlayback.playWhenReady = false
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

        player.addListener(trackChangeListener!!)
        player.seekTo(seekPosition.toLong())

        Timber.v("MediaSource: Clearing")
        concatenatingMediaSource.clear()
        Timber.v("MediaSource: Adding song")
        concatenatingMediaSource.addMediaSource(mediaSourceFactory.createMediaSource(Uri.parse(current.path)))
        Timber.v("load() calling updateWindowIndex()")
        trackChangeListener?.updateWindowIndex()

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
                    Timber.v("MediaSource: We're at track 0. Remove track 1")
                    concatenatingMediaSource.removeMediaSource(1)
                } else if (currentWindowIndex == 1) {
                    // We're at the second track. Move the first
                    Timber.v("MediaSource: We're at track 1. Remove track at 0")
                    concatenatingMediaSource.removeMediaSource(0)
                }
            }
            Timber.v("MediaSource: adding song")
            concatenatingMediaSource.addMediaSource(mediaSourceFactory.createMediaSource(Uri.parse(song.path)))
        }

        // Let the track change listener know that the window index has changed, so we don't get a false 'track changed' call
        Timber.v("LoadNext() calling updateWindowIndex()")
        trackChangeListener?.updateWindowIndex()
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

    override fun getPosition(): Int? {
        return player.contentPosition.toInt()
    }

    override fun getDuration(): Int? {
        return player.duration.toInt()
    }

    override fun setVolume(volume: Float) {
        player.audioComponent?.volume = volume
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
            val discontinuityReason = reason.toDiscontinuityReason()
            Timber.v("onPositionDiscontinuity(reason: $discontinuityReason, currentWindowIndex: $currentWindowIndex, player.currentWindowIndex: ${player.currentWindowIndex})")

            if (currentWindowIndex != player.currentWindowIndex && discontinuityReason == DiscontinuityReason.PeriodTransition) {
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