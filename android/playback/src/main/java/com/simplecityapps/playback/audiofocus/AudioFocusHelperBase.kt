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
                pauseForFocusLoss()
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                synchronized(focusLock) {
                    resumeOnFocusGain = isPlaying
                    playbackDelayed = false
                }
                pauseForFocusLoss()
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

    fun pauseForFocusLoss() {
        if (enabled) {
            listener?.pauseForFocusLoss()
        }
    }

    /**
     * Interprets an Api26 [AudioManager.requestAudioFocus] result: a delayed result means the eventual
     * focus gain should resume playback, anything else means the request is settled now. Pulled up from
     * the Api26 subclass so it's unit-testable without a real AudioFocusRequest.
     *
     * @return true if focus was granted immediately
     */
    internal fun onFocusRequestResult(result: Int?): Boolean {
        synchronized(focusLock) {
            playbackDelayed = result == AudioManager.AUDIOFOCUS_REQUEST_DELAYED
            return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }
}
