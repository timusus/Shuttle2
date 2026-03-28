package com.simplecityapps.shuttle.fake

import com.simplecityapps.playback.audiofocus.AudioFocusHelper

class FakeAudioFocusHelper : AudioFocusHelper {
    override var listener: AudioFocusHelper.Listener? = null
    override var enabled: Boolean = true
    override var resumeOnFocusGain: Boolean = true

    override fun requestAudioFocus(): Boolean = true
    override fun abandonAudioFocus() {}
}
