package com.simplecityapps.playback.audiofocus

import android.content.Context
import android.media.AudioManager
import androidx.core.content.getSystemService

abstract class AudioFocusHelperBase(
    private val context: Context
) : AudioFocusHelper, AudioManager.OnAudioFocusChangeListener {

    internal val audioManager: AudioManager? by lazy {
        context.getSystemService<AudioManager>()
    }

    private var resumeOnFocusGain: Boolean = false

    internal val focusLock = Any()

    internal var playbackDelayed = false

    internal var playbackNowAuthorized = false

    override var listener: AudioFocusHelper.Listener? = null

    override var isPlaying: Boolean = false

    override fun onAudioFocusChange(focusChange: Int) {
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
                listener?.duck()
            }
        }
    }

    fun restoreVolumeAndPlay() {
        listener?.restoreVolumeAndplay()
    }

    fun pause() {
        listener?.pause()
    }
}