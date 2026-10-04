package com.simplecityapps.shuttle.scrobbling.lastfm

/**
 * The Last.fm API application's key and shared secret (#503), read from `BuildConfig` in `:android:app` (the
 * only module with the key) and provided into this module's graph from there, since `:android:scrobbling`
 * never depends on `:android:app`.
 */
data class LastFmCredentials(
    val apiKey: String,
    val sharedSecret: String
) {
    /** False for a build without the API key and secret (a fork or F-Droid build): Last.fm is hidden there. */
    val isConfigured: Boolean get() = apiKey.isNotBlank() && sharedSecret.isNotBlank()
}
