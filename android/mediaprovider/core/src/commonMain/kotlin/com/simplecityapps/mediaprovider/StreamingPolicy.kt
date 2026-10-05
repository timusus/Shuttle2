package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.settings.StreamingSettings
import com.simplecityapps.shuttle.settings.TranscodeFormat
import com.simplecityapps.shuttle.streaming.DeliveredFormat
import com.simplecityapps.shuttle.streaming.DeliveredFormats
import dev.zacsweers.metro.Inject

/** Whether the active network costs the user money per byte (mobile data, a metered hotspot). */
fun interface MeteredNetwork {
    fun isMetered(): Boolean
}

/**
 * How a remote song streams and downloads, from [StreamingSettings]: the bitrate cap for a stream opened now (the
 * metered or unmetered quality, whichever network is active), the cap for a download, and the codec a transcode is in.
 * Read when a song's stream or download URL is built, so a network or settings change applies from the next song. A
 * provider reports what each stream it opens delivers ([streamOpened]), for Now Playing's badge ([DeliveredFormats]).
 */
class StreamingPolicy @Inject constructor(
    private val streamingSettings: StreamingSettings,
    private val deliveredFormats: DeliveredFormats,
    private val meteredNetwork: MeteredNetwork
) {
    /** The stream cap in kbps, or null to stream the original file. */
    fun maxBitrateKbps(): Int? {
        val quality = if (meteredNetwork.isMetered()) streamingSettings.meteredQuality else streamingSettings.unmeteredQuality
        return quality.value.maxBitrateKbps
    }

    /** The download cap in kbps, or null to download the original file. Whatever the network: a download is kept. */
    fun downloadMaxBitrateKbps(): Int? = streamingSettings.downloadQuality.value.maxBitrateKbps

    /** The codec a transcode, streamed or downloaded, is in. */
    fun transcodeFormat(): TranscodeFormat = streamingSettings.transcodeFormat.value

    /** Records that [path]'s stream, as just opened, is [delivered]: a transcode, or the original file when it's null. */
    fun streamOpened(
        path: String,
        delivered: DeliveredFormat?
    ) = deliveredFormats.record(path, delivered)
}
