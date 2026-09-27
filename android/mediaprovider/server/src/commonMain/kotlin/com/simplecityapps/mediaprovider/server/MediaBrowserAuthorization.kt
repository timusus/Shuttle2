package com.simplecityapps.mediaprovider.server

/**
 * The `MediaBrowser ...` client identity Jellyfin and Emby expect. Jellyfin sends it as `Authorization` on every
 * request, carrying the session's [token] once signed in (Jellyfin 12 rejects the legacy `X-Emby-Token` header);
 * Emby sends it without a token, as `Authorization` at login and `X-Emby-Authorization` after.
 */
fun mediaBrowserAuthorization(
    deviceId: String,
    token: String? = null,
    deviceName: String,
    version: String
): String = buildString {
    append("MediaBrowser Client=\"Shuttle2.0\", Device=\"$deviceName\", DeviceId=\"$deviceId\", Version=\"$version\"")
    if (token != null) {
        append(", Token=\"$token\"")
    }
}
