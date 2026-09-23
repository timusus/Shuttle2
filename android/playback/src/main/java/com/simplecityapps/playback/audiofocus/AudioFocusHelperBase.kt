package com.simplecityapps.playback.audiofocus

import android.content.Context
import android.media.AudioManager
import androidx.core.content.getSystemService
import com.simplecityapps.playback.PlaybackState

/**
 * Turns audio focus changes into [AudioFocusHelper.Listener] calls. Whether playback should resume
 * after a transient loss is decided from the listener's playback state at the moment focus is lost.
 */
abstract class AudioFocusHelperBase(
    audioManager: Lazy<AudioManager?>
) : AudioFocusHelper,
    AudioManager.OnAudioFocusChangeListener {
    constructor(context: Context) : this(lazy { context.getSystemService<AudioManager>() })

    internal val audioManager: AudioManager? by audioManager

    override var resumeOnFocusGain: Boolean = false

    private val isPlaying: Boolean
        get() = when (listener?.playbackStateFlow?.value) {
            PlaybackState.Loading, PlaybackState.Playing -> true
            else -> false
        }

    internal val focusLock = Any()

    internal var playbackDelayed = false

    internal var playbackNowAuthorized = false

    override var listener: AudioFocusHelper.Listener? = null

    override var enabled: Boolean = true

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
}
