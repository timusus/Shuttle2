package com.simplecityapps.shuttle.ui.screens.sources.servers

import com.simplecityapps.mediaprovider.server.ServerAuthentication
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.server.ServerConnectionStore
import com.simplecityapps.shuttle.server.ServerOrigin
import dev.zacsweers.metro.Inject

/** Forgets [MediaProviderType]'s server (its address, saved login and session, custom headers and trusted certificate, and what Sources kept of its imports), as removing it from Sources does. */
class ForgetServer @Inject constructor(
    private val authentications: Map<MediaProviderType, ServerAuthentication>,
    private val generalPreferenceManager: GeneralPreferenceManager,
    private val serverConnections: ServerConnectionStore,
) {
    operator fun invoke(type: MediaProviderType) {
        val authentication = authentications[type]
        authentication?.savedLogin()?.address?.let(ServerOrigin::parse)?.let(serverConnections::forget)
        authentication?.forgetServer()
        generalPreferenceManager.clearSourceState(type.name)
    }
}
