package com.simplecityapps.shuttle.scrobbling.lastfm

import com.simplecityapps.shuttle.scrobbling.LastFmAccountState
import com.simplecityapps.shuttle.scrobbling.LastFmSignInResult
import com.simplecityapps.shuttle.scrobbling.queue.ScrobbleQueue
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Last.fm's [web sign-in](https://www.last.fm/api/webauth) (#503 slice 5): [startSignIn] fetches a token and returns
 * the last.fm page where the user approves S2; [finishSignIn] trades the approved token for a session once they
 * come back. The token waits in [LastFmSessionStore] meanwhile, so the process can die while the user is in the
 * browser.
 */
@SingleIn(AppScope::class)
class LastFmAuthenticator
@Inject
constructor(
    private val client: LastFmClient,
    private val sessionStore: LastFmSessionStore,
    private val scrobbleQueue: ScrobbleQueue
) {
    /** A resume and the "I've approved it" button can both finish at once; only one should trade the token. */
    private val finishing = Mutex()

    val state: Flow<LastFmAccountState> =
        if (!client.isConfigured) {
            flowOf(LastFmAccountState.Unavailable)
        } else {
            combine(sessionStore.session, sessionStore.pendingToken) { session, pendingToken ->
                when {
                    session != null -> LastFmAccountState.SignedIn(session.username)
                    pendingToken != null -> LastFmAccountState.AwaitingApproval
                    else -> LastFmAccountState.SignedOut
                }
            }
        }

    suspend fun startSignIn(): String? = when (val result = client.getToken()) {
        is LastFmResult.Success -> {
            sessionStore.savePendingToken(result.value)
            client.approvalUrl(result.value)
        }

        is LastFmResult.Error, LastFmResult.Unreachable -> null
    }

    suspend fun finishSignIn(): LastFmSignInResult = finishing.withLock {
        val token = sessionStore.pendingToken.value ?: return@withLock LastFmSignInResult.NotStarted
        when (val result = client.getSession(token)) {
            is LastFmResult.Success -> {
                sessionStore.signIn(result.value)
                scrobbleQueue.scheduleFlush()
                LastFmSignInResult.SignedIn
            }

            is LastFmResult.Error -> when (result.code) {
                LastFmError.UNAUTHORIZED_TOKEN -> LastFmSignInResult.NotApproved

                LastFmError.INVALID_TOKEN, LastFmError.TOKEN_EXPIRED -> {
                    sessionStore.savePendingToken(null)
                    LastFmSignInResult.Expired
                }

                else -> LastFmSignInResult.Failed
            }

            LastFmResult.Unreachable -> LastFmSignInResult.Failed
        }
    }

    suspend fun signOut() {
        sessionStore.signOut()
        scrobbleQueue.clear()
    }
}
