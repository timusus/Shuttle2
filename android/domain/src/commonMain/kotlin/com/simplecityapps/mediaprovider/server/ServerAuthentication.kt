package com.simplecityapps.mediaprovider.server

/** What a server's sign-in form submits. */
data class ServerLogin(
    val address: String,
    val username: String,
    val password: String,
)

/** A server's saved address and login, which its sign-in form starts from. */
data class SavedServerLogin(
    val address: String? = null,
    val username: String? = null,
    val password: String? = null,
)

/**
 * Signs in to one kind of media server, over its provider's authentication manager. Each provider binds its own,
 * keyed by its `MediaProviderType`. A [Result] failure's message is already fit to show the user.
 */
interface ServerAuthentication {
    fun savedLogin(): SavedServerLogin

    /** Saves [login]'s address, then authenticates with the server there. */
    suspend fun authenticate(login: ServerLogin): Result<Unit>

    fun rememberLogin(login: ServerLogin)

    /** Forgets the saved username and password, keeping the address and session. */
    fun forgetLogin()

    /** Forgets the server: its address, the saved login and the session. */
    fun forgetServer()
}
