package com.simplecityapps.shuttle.ui.screens.sources.servers

import com.simplecityapps.shuttle.model.MediaProviderType
import javax.inject.Inject

/** Forgets a [type] server's saved login, once the user no longer wants the password remembered. */
class ForgetServerLogin @Inject constructor(
    private val authentications: Map<MediaProviderType, @JvmSuppressWildcards ServerAuthentication>,
) {
    operator fun invoke(type: MediaProviderType) = authentications.getValue(type).forgetLogin()
}
