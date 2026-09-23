package com.simplecityapps.playback.exoplayer

import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.Player
import com.simplecityapps.playback.Playback
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.chromecast.CastPlayback
import com.simplecityapps.playback.dsp.replaygain.ReplayGain
import com.simplecityapps.playback.dsp.replaygain.ReplayGainAudioProcessor
import com.simplecityapps.playback.dsp.replaygain.replayGain
import com.simplecityapps.playback.mediasession.toRepeatMode
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.model.Song
import timber.log.Timber

class ExoPlayerPlayback(
    private val playerFactory: PlayerFactory,
    private val replayGainAudioProcessor: ReplayGainAudioProcessor,
    private val mediaResolver: MediaResolver
) : Playback {
    override var callback: Playback.Callback? = null

    override var isReleased: Boolean = true

    private var isPlaybackReady = false

    private val replayGainTracker get() = replayGainAudioProcessor.streamTracker

    private val eventListener by lazy {
        object : AudioPlayer.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean) {
                Timber.v("onPlayWhenReadyChanged(playWhenReady: $playWhenReady)")
                callback?.onPlaybackStateChanged(if (playWhenReady) PlaybackState.Playing else PlaybackState.Paused)
            }

            override fun onPlaybackStateChanged(state: Int) {
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

            override fun onMediaItemTransition(reason: Int) {
                val transitionReason = reason.toTransitionReason()
                Timber.v("onMediaItemTransition(reason: ${reason.toTransitionReason()})")

                replayGainTracker.setPlayingIndex(player.currentWindowIndex)

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

            override fun onPlayerError(error: Exception) {
                Timber.e(error, "onPlayerError()")
                callback?.onPlaybackStateChanged(PlaybackState.Paused)
            }
        }
    }

    private var settings = PlayerSettings()

    private var player: AudioPlayer = createPlayer()

    private fun createPlayer(): AudioPlayer = playerFactory.create().also { player -> settings.applyTo(player) }

    override suspend fun load(
        current: Song,
        next: Song?,
        seekPosition: Int,
        completion: (Result<Any?>) -> Unit
    ) {
        Timber.v("load(current: ${current.name}|${current.mimeType}, seekPosition: $seekPosition)")

        if (isReleased) {
            player = createPlayer()
            isReleased = false
        }

        player.removeListener(eventListener)
        player.pause()
        player.seekTo(0)

        callback?.onPlaybackStateChanged(PlaybackState.Loading)

        val media = mediaResolver.resolve(current)
        player.addListener(eventListener)
        // Tell the ReplayGain processor about the new item before the player starts decoding it.
        replayGainTracker.setPlaylist(listOf(current.replayGain))
        replayGainTracker.setPlayingIndex(0)
        player.setMediaItem(playerItem(media, current.replayGain))
        player.seekTo(seekPosition.toLong())
        player.prepare()

        if (media.isRemote) {
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

        val nextItem: PlayerItem? =
            song?.let {
                playerItem(mediaResolver.resolve(song), song.replayGain)
            }

        val count = player.mediaItemCount
        val currentIndex = player.currentWindowIndex

        // Shortcut if the track is already next, and last, in the playlist
        val nextIndex = currentIndex + 1
        if (count == nextIndex + 1 && player.getMediaItemAt(nextIndex) == nextItem) {
            return
        }

        // Remove every item after the current one. Normally there's at most one, but when ExoPlayer
        // wraps around under REPEAT_MODE_ALL (the next item wasn't queued in time), the current index
        // drops back to 0 and every earlier item is after it again.
        if (nextIndex < count) {
            player.removeMediaItems(nextIndex, count)
        }

        // Now insert our new next track
        nextItem?.let {
            player.addMediaItem(nextItem)
        }

        syncReplayGainPlaylist()
    }

    /**
     * Mirrors the player's playlist into the ReplayGain tracker, so the gain for the pre-buffered
     * next item is known before its audio reaches the processor.
     */
    private fun syncReplayGainPlaylist() {
        replayGainTracker.setPlaylist(
            (0 until player.mediaItemCount).map { index ->
                player.getMediaItemAt(index).replayGain
            }
        )
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
        settings = settings.copy(volume = volume)
        player.setVolume(volume)
    }

    override fun getResumeWhenSwitched(oldPlayback: Playback): Boolean = oldPlayback !is CastPlayback

    override fun setRepeatMode(repeatMode: QueueManager.RepeatMode) {
        settings = settings.copy(repeatMode = repeatMode.toRepeatMode())
        player.repeatMode = settings.repeatMode
        replayGainTracker.setRepeatMode(settings.repeatMode)
    }

    override fun setAudioSessionId(id: Int) {
        if (id != -1 && id != C.AUDIO_SESSION_ID_UNSET) {
            settings = settings.copy(audioSessionId = id)
            player.audioSessionId = id
        } else {
            Timber.e("Failed to set audio session id (sessionId: $id)")
        }
    }

    override fun getAudioSessionId(): Int = if (isReleased) settings.audioSessionId else player.audioSessionId

    /** Not part of [settings]: a rebuilt player starts at normal speed (see [PlayerSettings]). */
    override fun setPlaybackSpeed(multiplier: Float) {
        player.setPlaybackParameters(multiplier, multiplier)
    }

    override fun getPlaybackSpeed(): Float = player.playbackSpeed

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

    private fun playerItem(
        media: ResolvedMedia,
        replayGain: ReplayGain
    ): PlayerItem = PlayerItem(
        uri = media.uri,
        mimeType = media.mimeType,
        replayGain = replayGain
    )
}
