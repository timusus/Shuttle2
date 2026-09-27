package com.simplecityapps.shuttle.ui.screens.sources.servers

import com.simplecityapps.mediaprovider.server.QuickConnectAuthentication
import com.simplecityapps.shuttle.model.MediaProviderType
import dev.zacsweers.metro.Inject

/** Whether [type]'s server at [address] supports Quick Connect sign-in. False, with no network call, for a type without one. */
class CheckQuickConnectAvailable @Inject constructor(
    private val authentications: Map<MediaProviderType, @JvmSuppressWildcards QuickConnectAuthentication>,
) {
    suspend operator fun invoke(type: MediaProviderType, address: String): Boolean {
        val authentication = authentications[type] ?: return false
        return runCatching { authentication.isEnabled(address) }.getOrDefault(false)
    }
}
