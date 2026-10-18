package com.simplecityapps.shuttle.ui.screens.sources.servers

import com.simplecityapps.mediaprovider.server.DiscoveredServer
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.server.CustomHeader
import com.simplecityapps.shuttle.server.ServerOrigin
import com.simplecityapps.shuttle.server.displayFingerprint
import com.simplecityapps.shuttle.ui.common.PendingEvent

/** A sign-in field the form won't submit empty. */
enum class ServerSignInField { Address, Username, Password }

/** What the user has typed into a server's sign-in form. */
data class ServerSignInForm(
    val address: String = "",
    val username: String = "",
    val password: String = "",
    val rememberPassword: Boolean = true,
    /** False while the saved password fills its field: it can't be revealed until it's cleared. */
    val passwordRevealable: Boolean = true,
    /** Required fields the user left empty when they last submitted, until they type into them. */
    val missing: Set<ServerSignInField> = emptySet(),
    /** Headers sent with every request to the server (a reverse proxy's access token, say), as typed. */
    val headers: List<CustomHeader> = emptyList(),
    /** Whether the Advanced section, with [headers], is open. */
    val showAdvanced: Boolean = false,
)

sealed interface ServerSignInStep {
    data object Form : ServerSignInStep

    data object Authenticating : ServerSignInStep

    /** Quick Connect's code is up, waiting for the user to approve it in another Jellyfin client. */
    data class AwaitingCode(val code: String) : ServerSignInStep

    /**
     * A sign-in PIN is up, waiting for the user to approve it on the web: at [authUrl] on this device, or by entering
     * [code] at [linkUrl] on another. Plex only.
     */
    data class AwaitingPin(val code: String, val authUrl: String, val linkUrl: String) : ServerSignInStep

    /** The PIN was approved, and the account has more than one server to choose from. */
    data class ChoosingServer(val servers: List<ServerChoice>) : ServerSignInStep

    data object Connected : ServerSignInStep

    data class Failed(val message: String) : ServerSignInStep

    /**
     * The server's certificate isn't one the device trusts (self-signed, say): the user can trust this exact one, by its
     * SHA-256 [fingerprint], for [origin] alone, or go back with [message].
     */
    data class UntrustedCertificate(val origin: ServerOrigin, val fingerprint: String, val message: String) : ServerSignInStep {
        val host: String get() = origin.toString()

        /** [fingerprint] as colon-separated pairs, for the user to compare with the server's. */
        val displayedFingerprint: String get() = displayFingerprint(fingerprint)
    }
}

data class ServerSignInUiState(
    val type: MediaProviderType,
    val form: ServerSignInForm = ServerSignInForm(),
    val step: ServerSignInStep = ServerSignInStep.Form,
    val events: List<PendingEvent<ServerSignInEvent>> = emptyList(),
    /** True for a Free user, who hasn't had the server trial yet or has used it up: streaming is S2 Pro. */
    val showProDisclosure: Boolean = false,
    /** True when [type]'s server at the typed address reports Quick Connect support. Jellyfin only. */
    val quickConnectEnabled: Boolean = false,
    /** Servers of [type] that answered on the local network. */
    val discoveredServers: List<DiscoveredServer> = emptyList(),
    /** True while the sign-in waits for the screen to ask the user for the local-network permission. */
    val localNetworkRequested: Boolean = false,
) {
    /** The discovered servers to offer as addresses: all but the one already typed. */
    val addressSuggestions: List<DiscoveredServer>
        get() = discoveredServers.filterNot { it.address.equals(form.address.trim().trimEnd('/'), ignoreCase = true) }

    /** Plex signs in with a plex.tv PIN and a choice of the account's servers, rather than an address and password. */
    val signsInWithPin: Boolean get() = type == MediaProviderType.Plex

    /** A Subsonic server takes an OpenSubsonic API key in the password field in place of a username and password. */
    val acceptsApiKey: Boolean get() = type == MediaProviderType.Subsonic
}

/** One of the account's servers, as [ServerSignInStep.ChoosingServer] lists it: the user's own, or shared with them. */
data class ServerChoice(val id: String, val name: String, val owned: Boolean)

sealed interface ServerSignInEvent {
    /** The server is signed in: the library can import from it. */
    data object Connected : ServerSignInEvent

    /** The success message has shown for long enough: the dialog can close. */
    data object Finished : ServerSignInEvent

    /** The sign-in PIN's web page, for the user to approve it on: open it in the browser. */
    data class OpenUrl(val url: String) : ServerSignInEvent
}
