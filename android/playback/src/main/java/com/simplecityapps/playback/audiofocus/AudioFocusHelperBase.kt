package com.simplecityapps.playback.audiofocus

import android.content.Context
import android.media.AudioManager
import androidx.core.content.getSystemService
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.PlaybackWatcher
import com.simplecityapps.playback.PlaybackWatcherCallback

abstract class AudioFocusHelperBase(
    private val context: Context,
    playbackWatcher: PlaybackWatcher
) : AudioFocusHelper,
    AudioManager.OnAudioFocusChangeListener,
    PlaybackWatcherCallback {
    internal val audioManager: AudioManager? by lazy {
        context.getSystemService()
    }

    override var resumeOnFocusGain: Boolean = false

    private var isPlaying: Boolean = false

    internal val focusLock = Any()

    internal var playbackDelayed = false

    internal var playbackNowAuthorized = false

    override var listener: AudioFocusHelper.Listener? = null

    override var enabled: Boolean = true

    init {
        playbackWatcher.addCallback(this)
    }

    override fun onAudioFocusChange(focusChange: Int) {
        if (!enabled) return

        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN ->
                if (playbackDelayed || resumeOnFocusGain) {
                    synchronized(focusLock) {
                        playbackDelayed = false
                        resumeOnFocusGain = false
                    }
                    restoreVolumeAndPlay()
                }
            AudioManager.AUDIOFOCUS_LOSS -> {
                synchronized(focusLock) {
                    resumeOnFocusGain = false
                    playbackDelayed = false
                }
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                synchronized(focusLock) {
                    resumeOnFocusGain = isPlaying
                    playbackDelayed = false
                }
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                duck()
            }
        }
    }

    fun duck() {
        if (enabled) {
            listener?.duck()
        }
    }

    fun restoreVolumeAndPlay() {
        if (enabled) {
            listener?.restoreVolumeAndPlay()
        }
    }

    fun pause() {
        if (enabled) {
            listener?.pause()
        }
    }

    // PlaybackWatcherCallback Implementation

    override fun onPlaybackStateChanged(playbackState: PlaybackState) {
        this.isPlaying = playbackState == PlaybackState.Loading || playbackState == PlaybackState.Playing
    }
}
