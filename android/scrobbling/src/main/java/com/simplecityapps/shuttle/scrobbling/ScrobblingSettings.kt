package com.simplecityapps.shuttle.scrobbling

import com.simplecityapps.shuttle.settings.Setting
import com.simplecityapps.shuttle.settings.SettingsStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/** Settings > Playback & sound > Scrobbling (#503). The signed-in accounts live in encrypted prefs, not here. */
@SingleIn(AppScope::class)
class ScrobblingSettings @Inject constructor(
    store: SettingsStore
) {
    val scrobbleServerStreams = store.preference(ScrobbleServerStreams)

    companion object {
        /**
         * Scrobble Jellyfin, Emby and Plex songs even while S2 reports their plays to the server, which may
         * scrobble them itself. Off by default (owner decision, 2026-09-27), so a server plugin doesn't double count.
         */
        val ScrobbleServerStreams = Setting.boolean("pref_scrobble_server_streams", false)
    }
}
