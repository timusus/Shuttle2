package com.simplecityapps.provider.plex.http

import com.simplecityapps.mediaprovider.ClientIdentity

/** The `X-Plex-*` client identity headers Plex expects on every login and API request. */
fun plexClientHeaders(clientIdentity: ClientIdentity): Map<String, String> = mapOf(
    "X-Plex-Client-Identifier" to clientIdentity.id,
    "X-Plex-Product" to clientIdentity.clientName,
    "X-Plex-Version" to clientIdentity.version,
    "X-Plex-Platform" to "Android",
    "X-Plex-Device-Name" to clientIdentity.deviceName
)
