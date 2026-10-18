package com.simplecityapps.shuttle.scrobbling.lastfm

/** The Last.fm API key and secret, provided from `:android:app`, the only module that has them. */
data class LastFmCredentials(
    val apiKey: String,
    val sharedSecret: String
) {
    /** False for a build without the API key and secret (a fork or F-Droid build): Last.fm is hidden there. */
    val isConfigured: Boolean get() = apiKey.isNotBlank() && sharedSecret.isNotBlank()
}
