package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.settings.StreamingSettings
import dev.zacsweers.metro.Inject

/** Whether the active network costs the user money per byte (mobile data, a metered hotspot). */
fun interface MeteredNetwork {
    fun isMetered(): Boolean
}

/**
 * The bitrate cap for a stream opened now: the metered or unmetered [StreamingSettings] quality, whichever network
 * is active. Read when a song's stream URL is built, so a network change applies from the next song.
 */
class StreamingBitrateCap @Inject constructor(
    private val streamingSettings: StreamingSettings,
    private val meteredNetwork: MeteredNetwork
) {
    /** The cap in kbps, or null to stream the original file. */
    fun maxBitrateKbps(): Int? {
        val quality = if (meteredNetwork.isMetered()) streamingSettings.meteredQuality else streamingSettings.unmeteredQuality
        return quality.value.maxBitrateKbps
    }
}
