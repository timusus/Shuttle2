package com.simplecityapps.shuttle.ui.screens.sources.servers

import com.simplecityapps.mediaprovider.server.ServerAuthentication
import com.simplecityapps.shuttle.model.MediaProviderType
import dev.zacsweers.metro.Inject

/** A [type] server's saved account as "user@host" for Sources' rows, or whatever of the two it has; null without either. */
class ReadServerAccount @Inject constructor(
    private val authentications: Map<MediaProviderType, ServerAuthentication>,
) {
    operator fun invoke(type: MediaProviderType): String? {
        val login = authentications[type]?.savedLogin() ?: return null
        val user = login.username?.takeIf { it.isNotBlank() }
        val host = login.address?.let(::serverHost)
        return when {
            user != null && host != null -> "$user@$host"
            else -> user ?: host
        }
    }
}

/** [address] without its scheme, path and trailing slash: "https://music.example.com:8096/jellyfin/" is "music.example.com:8096". */
internal fun serverHost(address: String): String? = address.trim()
    .substringAfter("://")
    .substringBefore('/')
    .takeIf { it.isNotEmpty() }
