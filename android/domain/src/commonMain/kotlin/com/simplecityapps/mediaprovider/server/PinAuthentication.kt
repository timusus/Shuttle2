package com.simplecityapps.mediaprovider.server

/**
 * A sign-in PIN waiting for the user to approve it: in the browser at [authUrl] on this device, or by entering [code]
 * at [linkUrl] on any other. It runs out after [expiresInSeconds].
 */
data class SignInPin(
    val id: Long,
    val code: String,
    val authUrl: String,
    val linkUrl: String,
    val expiresInSeconds: Int,
)

/**
 * One way to reach an [AccountServer]: its [uri], whether that's on the local network or through the provider's relay,
 * and the plain [address] (an IP) and [port] behind it, when the provider says.
 */
data class ServerConnection(
    val uri: String,
    val local: Boolean = false,
    val relay: Boolean = false,
    val address: String? = null,
    val port: Int? = null,
)

/**
 * A server on the signed-in account: the user's own ([owned]) or one shared with them. [accessToken] is the token this
 * server takes from the user, which isn't the account's.
 */
data class AccountServer(
    val id: String,
    val name: String,
    val owned: Boolean,
    val accessToken: String,
    val connections: List<ServerConnection>,
)

/**
 * Signs in to a provider's account with a PIN the user approves on the provider's website, then to one of the
 * account's servers, over its provider's authentication manager. Plex only. A [Result] failure's message is already
 * fit to show the user.
 */
interface PinAuthentication {
    suspend fun createPin(): Result<SignInPin>

    /** The account's token once the user has approved [pin]; null while it's still waiting. */
    suspend fun checkPin(pin: SignInPin): Result<String?>

    /** The servers the account signed in with [accountToken] can play from. */
    suspend fun servers(accountToken: String): Result<List<AccountServer>>

    /** Whether [server] is the one signed in now, at the saved address or under the saved session. */
    fun isSignedInTo(server: AccountServer): Boolean

    /** Finds a connection to [server] that answers, then saves it and the server's token as the session. */
    suspend fun connect(server: AccountServer): Result<Unit>
}
