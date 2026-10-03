package com.simplecityapps.shuttle.settings

/** Now Playing's own preferences. */
object PlayerSettings {
    /** The seek bar's end label shows the time left ("-3:12") rather than the song's length; tapping the label flips it. */
    val ShowRemainingTime = Setting.boolean("pref_player_show_remaining_time", true)
}
