package com.simplecityapps.shuttle.entitlement

import com.simplecityapps.shuttle.model.MediaProviderType

/**
 * Whether a remote server's sign-in may open; false once the gate has sent the user to the paywall instead. A refusal
 * for a known [type] is resumed with it ([ObserveAddServerResumes]).
 */
fun interface TryAddServer {
    operator fun invoke(type: MediaProviderType?): Boolean
}
