package com.simplecityapps.shuttle.settings

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArtworkSettings @Inject constructor(
    store: SettingsStore
) {
    val wifiOnly = store.preference(WifiOnly)
    val localOnly = store.preference(LocalOnly)
    val mediaSessionArtwork = store.preference(MediaSessionArtwork)

    companion object {
        /** Remote artwork waits for an unmetered network. */
        val WifiOnly = Setting.boolean("artwork_wifi_only", true)

        /** Never fetch artwork from remote sources. */
        val LocalOnly = Setting.boolean("artwork_local_only", false)

        /** Hand artwork to the media session (lock screen, notification, Android Auto). */
        val MediaSessionArtwork = Setting.boolean("media_session_artwork", true)
    }
}
