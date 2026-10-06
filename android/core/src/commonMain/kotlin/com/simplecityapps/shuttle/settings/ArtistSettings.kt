package com.simplecityapps.shuttle.settings

/** The Artists list's own preferences (#637). */
object ArtistSettings {
    /**
     * The Artists list also shows the artists who are only credited on others' albums (a featured or compilation
     * artist), not just album artists. Off by default, so a library of compilations doesn't bury the album artists.
     */
    val ShowCreditedArtists = Setting.boolean("pref_artists_show_credited", false)
}
