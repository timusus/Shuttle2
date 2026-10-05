package com.simplecityapps.shuttle.settings

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/** The most a Jellyfin, Emby, Plex or Subsonic stream or download may use. [Original] fetches the file as it is. */
enum class StreamingQuality(val maxBitrateKbps: Int?) {
    Original(null),
    Kbps320(320),
    Kbps192(192),
    Kbps128(128)
}

/**
 * The codec a server transcodes to. [Auto] is each platform's own: AAC in HLS segments on Android, which stay
 * seekable, and MP3 on iOS. [Mp3] is the most compatible (Cast receivers, Sonos); [Opus] sounds best at a low bitrate.
 */
enum class TranscodeFormat {
    Auto,
    Opus,
    Aac,
    Mp3
}

/**
 * Settings > Sources > Streaming & downloads: the quality of streams from Jellyfin, Emby, Plex and Subsonic, one cap
 * for unmetered networks and one for metered, the codec a transcode is in, and the quality of downloads for offline
 * play. Mobile data defaults to 320 kbps rather than the original file; a stored choice, including an explicit
 * Original, is kept. Downloads keep the original by default.
 */
@SingleIn(AppScope::class)
class StreamingSettings @Inject constructor(
    store: SettingsStore
) {
    val unmeteredQuality = store.preference(UnmeteredQuality)
    val meteredQuality = store.preference(MeteredQuality)
    val downloadQuality = store.preference(DownloadQuality)
    val transcodeFormat = store.preference(Format)

    companion object {
        val UnmeteredQuality = streamingQuality("pref_streaming_quality_unmetered", default = StreamingQuality.Original)
        val MeteredQuality = streamingQuality("pref_streaming_quality_metered", default = StreamingQuality.Kbps320)
        val DownloadQuality = streamingQuality("pref_download_quality", default = StreamingQuality.Original)

        /** Stored by name, like the qualities. */
        val Format = Setting.string(
            key = "pref_transcode_format",
            default = TranscodeFormat.Auto,
            decode = { value -> TranscodeFormat.entries.firstOrNull { it.name == value } },
            encode = { format -> format.name }
        )

        /** Stored by name, so new qualities can go anywhere in the list. */
        private fun streamingQuality(key: String, default: StreamingQuality) = Setting.string(
            key = key,
            default = default,
            decode = { value -> StreamingQuality.entries.firstOrNull { it.name == value } },
            encode = { quality -> quality.name }
        )
    }
}
