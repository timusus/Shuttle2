package com.simplecityapps.playback.local.exoplayer

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
import com.google.android.exoplayer2.audio.AudioCapabilities
import com.google.android.exoplayer2.audio.AudioProcessor
import com.google.android.exoplayer2.audio.AudioSink
import com.google.android.exoplayer2.audio.DefaultAudioSink
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

    private var playWhenReady = false

    private var isPlaybackReady = false

    private val trackChangeListener by lazy {

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

                val playbackState = state.toState()
                Timber.v("onPlayerStateChanged(playWhenReady: $playWhenReady, playbackState: ${playbackState})")

                if (playbackState == PlaybackState.Ready) {
                    isPlaybackReady = true
                }

                if (playbackState == PlaybackState.Ended) {
                    if (isPlaybackReady) {
                        player.playWhenReady = false
                        this@ExoPlayerPlayback.playWhenReady = false
                        callback?.onPlayStateChanged(isPlaying = false)
                        callback?.onPlaybackComplete(trackWentToNext = false)
                        isPlaybackReady = false
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                Timber.v("onMediaItemTransition(reason: $reason)")
                when (reason) {
                    Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> callback?.onPlaybackComplete(trackWentToNext = true)
                    Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT,
                    Player.MEDIA_ITEM_TRANSITION_REASON_SEEK,
                    Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> {
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

    private fun initPlayer(context: Context): ExoPlayer {

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
        Timber.v("load(current: ${current.name})")
        isReleased = false

        player.addListener(trackChangeListener)
        player.seekTo(seekPosition.toLong())
        player.setMediaItem(getMediaItem(current.path.toUri()))
        player.prepare()

        completion(Result.success(null))

        loadNext(next)
    }

    override fun loadNext(song: Song?) {
        Timber.v("loadNext(${song?.name})")
        // Remove any songs after the current media item
        val currentMediaItem = player.currentMediaItem
        val count = player.mediaItemCount
        var currentIndex = 0
        for (i in 0 until player.mediaItemCount) {
            if (player.getMediaItemAt(i) == currentMediaItem) {
                currentIndex = i
                break
            }
        }
        if (currentIndex < count - 1) {
            player.removeMediaItems(count - 1, count)
        }

        // Now we can insert our new next track
        song?.let {
            player.addMediaItem(getMediaItem(song.path.toUri()))
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

    @Throws(IllegalStateException::class)
    fun getMediaItem(uri: Uri): MediaItem {
        // Todo: This could be delegated to another class, to remove knowledge of Emby from ExoPlayerPlayback
        if (uri.scheme == "emby") {
            embyAuthenticationManager.getAuthenticatedCredentials()?.let { authenticatedCredentials ->
                embyAuthenticationManager.buildEmbyPath(
                    uri.pathSegments.last(),
                    authenticatedCredentials
                )?.let { path ->
                    return MediaItem.fromUri(path)
                } ?: run {
                    throw IllegalStateException("Failed to build emby path")
                }
            } ?: run {
                throw IllegalStateException("Failed to authenticate")
            }
        } else {
            return MediaItem.fromUri(uri)
        }
    }
}