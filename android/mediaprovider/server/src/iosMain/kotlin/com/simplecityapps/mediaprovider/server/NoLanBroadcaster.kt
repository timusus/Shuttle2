package com.simplecityapps.mediaprovider.server

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/**
 * iOS finds nothing: sending a UDP broadcast needs Apple's `com.apple.developer.networking.multicast` entitlement, which
 * the app doesn't have, so the sign-in form there offers no suggestions.
 */
@ContributesBinding(AppScope::class)
@Inject
class NoLanBroadcaster : LanBroadcaster {
    override suspend fun broadcast(
        message: String,
        port: Int,
        listenMillis: Long,
    ): List<String> = emptyList()
}
