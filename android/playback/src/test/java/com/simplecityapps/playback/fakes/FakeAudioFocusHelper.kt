package com.simplecityapps.playback.fakes

import com.simplecityapps.playback.audiofocus.AudioFocusHelper

/** Grants focus unless [grantFocus] is false, counting requests and abandons. */
class FakeAudioFocusHelper(
    var grantFocus: Boolean = true
) : AudioFocusHelper {
    var requests = 0
        private set

    var abandons = 0
        private set

    override fun requestAudioFocus(): Boolean {
        requests++
        return grantFocus
    }

    override fun abandonAudioFocus() {
        abandons++
    }

    override var listener: AudioFocusHelper.Listener? = null
    override var enabled: Boolean = true
    override var resumeOnFocusGain: Boolean = false
}
