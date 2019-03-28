package com.simplecityapps.playback.audiofocus

import android.content.Context
import android.media.AudioManager
import androidx.core.content.getSystemService
import com.simplecityapps.playback.PlaybackWatcher
import com.simplecityapps.playback.PlaybackWatcherCallback

abstract class AudioFocusHelperBase(
    private val context: Context,
    playbackWatcher: PlaybackWatcher
) : AudioFocusHelper,
    AudioManager.OnAudioFocusChangeListener,
    PlaybackWatcherCallback {

    internal val audioManager: AudioManager? by lazy {
        context.getSystemService<AudioManager>()
    }

    private var resumeOnFocusGain: Boolean = false

    private var isPlaying: Boolean = false

    internal val focusLock = Any()

    internal var playbackDelayed = false

    internal var playbackNowAuthorized = false

    override var listener: AudioFocusHelper.Listener? = null

    init {
        playbackWatcher.addCallback(this)
    }

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


    // PlaybackWatcherCallback Implementation

    override fun onPlaystateChanged(isPlaying: Boolean) {
        this.isPlaying = isPlaying
    }
}