package com.simplecityapps.shuttle.ui.screens.sources.servers

import com.simplecityapps.mediaprovider.server.ServerAuthentication
import com.simplecityapps.shuttle.model.MediaProviderType
import dev.zacsweers.metro.Inject

/** Forgets a [type] server's saved login, once the user no longer wants the password remembered. */
class ForgetServerLogin @Inject constructor(
    private val authentications: Map<MediaProviderType, ServerAuthentication>,
) {
    operator fun invoke(type: MediaProviderType) = authentications.getValue(type).forgetLogin()
}
