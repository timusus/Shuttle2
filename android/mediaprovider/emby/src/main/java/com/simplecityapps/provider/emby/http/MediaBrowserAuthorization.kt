package com.simplecityapps.provider.emby.http

/** The `MediaBrowser ...` client identity Emby expects, sent as `Authorization` at login and `X-Emby-Authorization` after. */
fun mediaBrowserAuthorization(
    deviceId: String,
    deviceName: String,
    version: String
): String = "MediaBrowser Client=\"Shuttle2.0\", Device=\"$deviceName\", DeviceId=\"$deviceId\", Version=\"$version\""
