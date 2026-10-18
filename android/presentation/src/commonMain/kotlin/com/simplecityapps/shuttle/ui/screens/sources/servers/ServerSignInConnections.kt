package com.simplecityapps.shuttle.ui.screens.sources.servers

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.server.CustomHeader
import com.simplecityapps.shuttle.server.ServerConnectionStore
import com.simplecityapps.shuttle.server.ServerOrigin

internal class SignInConnections(
    private val type: MediaProviderType,
    private val readServerLogin: ReadServerLogin,
    private val prepareServerConnection: PrepareServerConnection,
    private val serverConnections: ServerConnectionStore,
    initial: ServerOrigin?,
) {
    /**
     * The servers whose custom headers or trusted certificate this sign-in may have saved: the one it opened with and
     * each address it tried. Only the server's saved address keeps them (see [forgetUnused]).
     */
    private val touchedOrigins = listOfNotNull(initial).toMutableSet()

    /**
     * Saves the [headers] for the server at [address] before it's contacted, so the sign-in sends them, and forgets
     * any certificate it refused before: a refusal recorded from here on is this attempt's.
     */
    fun prepare(address: String, headers: List<CustomHeader>): ServerOrigin? {
        val origin = ServerOrigin.parse(address) ?: return null
        forgetUnused(inUse = origin)
        touchedOrigins += origin
        prepareServerConnection(origin, headers)
        return origin
    }

    /**
     * Forgets the headers and trusted certificate this sign-in saved for any server but the one whose address is saved
     * (which removing the server forgets, [ForgetServer]) and [inUse]: an address the user tried and moved on from, or
     * the server another one replaced, keeps no proxy token or trusted certificate behind.
     */
    fun forgetUnused(inUse: ServerOrigin? = null) {
        val saved = readServerLogin(type).address?.let(ServerOrigin::parse)
        val unused = touchedOrigins.filter { it != saved && it != inUse }
        unused.forEach { serverConnections.forget(it) }
        touchedOrigins -= unused.toSet()
    }

    /** The step a failed sign-in to [origin] shows: the certificate to trust when it refused one, else [message]. */
    fun failedStep(origin: ServerOrigin?, message: String): ServerSignInStep {
        val fingerprint = origin?.let { serverConnections.rejectedCertificate(it) }
        return if (fingerprint != null) ServerSignInStep.UntrustedCertificate(origin, fingerprint, message) else ServerSignInStep.Failed(message)
    }

    fun trust(untrusted: ServerSignInStep.UntrustedCertificate) {
        serverConnections.trustCertificate(untrusted.origin, untrusted.fingerprint)
    }
}
