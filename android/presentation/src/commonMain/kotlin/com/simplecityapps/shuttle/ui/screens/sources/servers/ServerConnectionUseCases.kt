package com.simplecityapps.shuttle.ui.screens.sources.servers

import com.simplecityapps.shuttle.server.CustomHeader
import com.simplecityapps.shuttle.server.ServerConnectionStore
import com.simplecityapps.shuttle.server.ServerOrigin
import dev.zacsweers.metro.Inject

/** The custom headers saved for the server at an address, for the sign-in's form to start from. */
class ReadServerHeaders @Inject constructor(
    private val serverConnections: ServerConnectionStore,
) {
    operator fun invoke(address: String): List<CustomHeader> = serverConnections.connection(address).headers
}

/**
 * Saves the headers a sign-in sends to [ServerOrigin] before it's contacted, and forgets any certificate it refused
 * before, so a refusal recorded from here on is this attempt's.
 */
class PrepareServerConnection @Inject constructor(
    private val serverConnections: ServerConnectionStore,
) {
    operator fun invoke(
        origin: ServerOrigin,
        headers: List<CustomHeader>,
    ) {
        serverConnections.setHeaders(origin, headers)
        serverConnections.clearRejectedCertificate(origin)
    }
}

/** Trusts the certificate with a fingerprint for one server alone. */
class TrustServerCertificate @Inject constructor(
    private val serverConnections: ServerConnectionStore,
) {
    operator fun invoke(
        origin: ServerOrigin,
        fingerprint: String,
    ) = serverConnections.trustCertificate(origin, fingerprint)
}

/** The fingerprint of the certificate a server last presented that the platform refused, if it did. */
class RejectedServerCertificate @Inject constructor(
    private val serverConnections: ServerConnectionStore,
) {
    operator fun invoke(origin: ServerOrigin): String? = serverConnections.rejectedCertificate(origin)
}

/** Forgets a server's custom headers and trusted certificate. */
class ForgetServerConnection @Inject constructor(
    private val serverConnections: ServerConnectionStore,
) {
    operator fun invoke(origin: ServerOrigin) = serverConnections.forget(origin)
}
