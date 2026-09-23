package com.simplecityapps.playback.fakes

import com.simplecityapps.playback.audiofocus.AudioFocusHelper

class FakeAudioFocusHelper : AudioFocusHelper {
    override fun requestAudioFocus(): Boolean = true

    override fun abandonAudioFocus() {}

    override var listener: AudioFocusHelper.Listener? = null
    override var enabled: Boolean = true
    override var resumeOnFocusGain: Boolean = false
}
