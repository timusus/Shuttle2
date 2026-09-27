package com.simplecityapps.shuttle.settings

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/** The most a Jellyfin, Emby or Plex stream may use. [Original] streams the file as it is. */
enum class StreamingQuality(val maxBitrateKbps: Int?) {
    Original(null),
    Kbps320(320),
    Kbps192(192),
    Kbps128(128)
}

/** Settings > Sources: streaming quality from Jellyfin, Emby and Plex, one cap for unmetered networks and one for metered. */
@SingleIn(AppScope::class)
class StreamingSettings @Inject constructor(
    store: SettingsStore
) {
    val unmeteredQuality = store.preference(UnmeteredQuality)
    val meteredQuality = store.preference(MeteredQuality)

    companion object {
        val UnmeteredQuality = streamingQuality("pref_streaming_quality_unmetered")
        val MeteredQuality = streamingQuality("pref_streaming_quality_metered")

        /** Stored by name, so new qualities can go anywhere in the list. */
        private fun streamingQuality(key: String) = Setting.string(
            key = key,
            default = StreamingQuality.Original,
            decode = { value -> StreamingQuality.entries.firstOrNull { it.name == value } },
            encode = { quality -> quality.name }
        )
    }
}
