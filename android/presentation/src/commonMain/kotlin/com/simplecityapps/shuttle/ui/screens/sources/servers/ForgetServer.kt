package com.simplecityapps.shuttle.ui.screens.sources.servers

import com.simplecityapps.mediaprovider.server.ServerAuthentication
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import dev.zacsweers.metro.Inject

/** Forgets [MediaProviderType]'s server (its address, saved login and session, and what Sources kept of its imports), as removing it from Sources does. */
class ForgetServer @Inject constructor(
    private val authentications: Map<MediaProviderType, ServerAuthentication>,
    private val generalPreferenceManager: GeneralPreferenceManager,
) {
    operator fun invoke(type: MediaProviderType) {
        authentications[type]?.forgetServer()
        generalPreferenceManager.clearSourceState(type.name)
    }
}
