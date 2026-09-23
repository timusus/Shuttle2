package com.simplecityapps.provider.jellyfin.http

import com.jaredrummler.android.device.DeviceName

/**
 * Builds the `Authorization: MediaBrowser ...` header Jellyfin expects. Jellyfin 12 rejects the
 * legacy `X-Emby-Token` header, so every authenticated request carries the token here instead.
 */
fun mediaBrowserAuthorization(
    deviceId: String,
    token: String? = null,
    deviceName: String = defaultDeviceName(),
    version: String = "1.0"
): String = buildString {
    append("MediaBrowser Client=\"Shuttle2.0\", Device=\"$deviceName\", DeviceId=\"$deviceId\", Version=\"$version\"")
    if (token != null) {
        append(", Token=\"$token\"")
    }
}

private fun defaultDeviceName(): String = try {
    DeviceName.getDeviceName()
} catch (e: Exception) {
    "Unknown"
}
