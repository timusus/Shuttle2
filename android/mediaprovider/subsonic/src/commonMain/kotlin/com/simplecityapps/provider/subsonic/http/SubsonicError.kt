package com.simplecityapps.provider.subsonic.http

import com.simplecityapps.networking.retrofit.error.RemoteServiceHttpError
import io.ktor.http.HttpStatusCode

/**
 * A `status: "failed"` response. Subsonic servers send these with HTTP 200, so each is given the HTTP status that means
 * the same thing, which the shared server code acts on: a rejected sign-in ([WrongCredentials], [InvalidApiKey]) is a
 * 401, which signs the session out, and [NotAuthorized] a 403.
 */
sealed class SubsonicError(
    val code: Int,
    val serverMessage: String?,
    status: HttpStatusCode
) : RemoteServiceHttpError(status, body = serverMessage) {
    override val message: String get() = "Subsonic error $code: ${serverMessage.orEmpty()}"

    /** 40: wrong username or password. */
    class WrongCredentials(message: String?) : SubsonicError(40, message, HttpStatusCode.Unauthorized)

    /** 41: token authentication isn't supported for this user (an LDAP account, say); the password has to be sent. */
    class TokenAuthNotSupported(message: String?) : SubsonicError(41, message, HttpStatusCode.BadRequest)

    /** 42: the server doesn't support the authentication mechanism used (an API key, on a server without them). */
    class AuthMechanismNotSupported(message: String?) : SubsonicError(42, message, HttpStatusCode.BadRequest)

    /** 43: both an API key and a username were sent. */
    class ConflictingAuth(message: String?) : SubsonicError(43, message, HttpStatusCode.BadRequest)

    /** 44: the API key isn't valid. */
    class InvalidApiKey(message: String?) : SubsonicError(44, message, HttpStatusCode.Unauthorized)

    /** 50: the user isn't allowed to do this. */
    class NotAuthorized(message: String?) : SubsonicError(50, message, HttpStatusCode.Forbidden)

    /** 70: the requested item doesn't exist. */
    class NotFound(message: String?) : SubsonicError(70, message, HttpStatusCode.NotFound)

    /** 0, 10, 20, 30, or a code this client doesn't know. */
    class Other(code: Int, message: String?) : SubsonicError(code, message, HttpStatusCode.BadRequest)

    companion object {
        fun of(error: ErrorDto?): SubsonicError {
            val message = error?.message
            return when (error?.code) {
                40 -> WrongCredentials(message)
                41 -> TokenAuthNotSupported(message)
                42 -> AuthMechanismNotSupported(message)
                43 -> ConflictingAuth(message)
                44 -> InvalidApiKey(message)
                50 -> NotAuthorized(message)
                70 -> NotFound(message)
                else -> Other(error?.code ?: 0, message)
            }
        }
    }
}
