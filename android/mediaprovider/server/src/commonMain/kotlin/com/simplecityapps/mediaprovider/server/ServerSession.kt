package com.simplecityapps.mediaprovider.server

import com.simplecityapps.mediaprovider.FlowEvent
import com.simplecityapps.mediaprovider.ImportPhase
import com.simplecityapps.mediaprovider.MessageProgress
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.networking.retrofit.error.RemoteServiceHttpError
import com.simplecityapps.shuttle.logging.Logger
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val logger = Logger.tagged("ServerSession")

/**
 * A sync's sign-in to a server: the [credentials] its requests are made with, and [reauthenticate] to sign in again when
 * the server rejects them mid-sync.
 */
class ServerSession<C : Any>(
    credentials: C,
    private val reauthenticate: suspend () -> C?
) {
    /** The credentials requests are made with: the sign-in the sync started with, or the one that replaced it after a 401. */
    var credentials: C = credentials
        private set

    private var reauthenticated = false
    private val lock = Mutex()

    /**
     * [request] with the current [credentials]. When the server answers 401, signs in again (once per session, however
     * many requests it rejects) and repeats [request] with the new credentials, so a session the server expired
     * mid-sync doesn't fail the sync. A request rejected after another one already signed in again is repeated with
     * those credentials; without a new sign-in, the 401 is the result.
     */
    suspend fun <T : Any> request(request: suspend (credentials: C) -> NetworkResult<T>): NetworkResult<T> {
        val rejected = credentials
        val result = request(rejected)
        if (!result.isUnauthorized()) return result
        val renewed =
            lock.withLock {
                when {
                    credentials != rejected -> credentials

                    reauthenticated -> null

                    else -> {
                        reauthenticated = true
                        logger.warn { "The server rejected the session; signing in again" }
                        reauthenticate()?.takeIf { it != rejected }?.also { credentials = it }
                    }
                }
            } ?: return result
        return request(renewed)
    }
}

private fun NetworkResult<*>.isUnauthorized(): Boolean = ((this as? NetworkResult.Failure)?.error as? RemoteServiceHttpError)?.httpStatusCode == HttpStatusCode.Unauthorized

/**
 * The skeleton a server sync runs in: fails straight away when no server [address] is set, otherwise reports that
 * it's connecting, signs in with [authenticate] and runs [body] with the session, or fails when signing in
 * does. The session signs in again with [authenticate] the first time the server rejects a [ServerSession.request] with a
 * 401, which finds the stored session cleared ([checkSession]) and so signs in with the saved login.
 */
fun <C : Any, T> withServerSession(
    strings: ServerStrings,
    address: String?,
    authenticate: suspend (address: String) -> C?,
    body: suspend FlowCollector<FlowEvent<T, MessageProgress>>.(address: String, session: ServerSession<C>) -> Unit
): Flow<FlowEvent<T, MessageProgress>> {
    if (address == null) {
        return flowOf(FlowEvent.Failure(strings.addressMissing))
    }

    return flow {
        emit(FlowEvent.Progress(MessageProgress(ImportPhase.Connecting, progress = null)))
        val credentials = authenticate(address)
        if (credentials == null) {
            emit(FlowEvent.Failure(strings.authenticationError))
        } else {
            body(address, ServerSession(credentials) { authenticate(address) })
        }
    }
}
