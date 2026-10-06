package com.simplecityapps.shuttle.settings

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/** What Now Playing, the notification, the lock screen and Android Auto show for a song. Stored by ordinal, so the order is fixed. */
enum class NowPlayingImage {
    /** The song's own album art. */
    AlbumArt,

    /** The song's album artist's image, the same for every song by them; the song's own art where they have none. */
    ArtistImage
}

@SingleIn(AppScope::class)
class ArtworkSettings @Inject constructor(
    store: SettingsStore
) {
    val wifiOnly = store.preference(WifiOnly)
    val localOnly = store.preference(LocalOnly)
    val mediaSessionArtwork = store.preference(MediaSessionArtwork)
    val nowPlayingArtwork = store.preference(NowPlayingArtworkSource)

    companion object {
        /** Remote artwork waits for an unmetered network. */
        val WifiOnly = Setting.boolean("artwork_wifi_only", true)

        /** Never fetch artwork from remote sources. */
        val LocalOnly = Setting.boolean("artwork_local_only", false)

        /** Hand artwork to the media session (lock screen, notification, Android Auto). */
        val MediaSessionArtwork = Setting.boolean("media_session_artwork", true)

        /** Which image stands for the playing song on the player, mini player and media session (#952). */
        val NowPlayingArtworkSource = Setting.enumOrdinalString("pref_now_playing_artwork", NowPlayingImage.AlbumArt, NowPlayingImage.entries)
    }
}
