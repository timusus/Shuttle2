package com.simplecityapps.shuttle.scrobbling.listenbrainz

import com.simplecityapps.shuttle.scrobbling.ListenBrainzAccountState
import com.simplecityapps.shuttle.scrobbling.ListenBrainzSignInResult
import com.simplecityapps.shuttle.scrobbling.queue.QueuedScrobbleEntity
import com.simplecityapps.shuttle.scrobbling.queue.ScrobbleQueue
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** ListenBrainz sign-in: the user pastes a token, which is kept only once ListenBrainz says it's valid. */
@SingleIn(AppScope::class)
class ListenBrainzAuthenticator
@Inject
constructor(
    private val client: ListenBrainzClient,
    private val sessionStore: ListenBrainzSessionStore,
    private val scrobbleQueue: ScrobbleQueue
) {
    val state: Flow<ListenBrainzAccountState> = sessionStore.account.map {
        if (it == null) ListenBrainzAccountState.SignedOut else ListenBrainzAccountState.SignedIn(it.username)
    }

    suspend fun signIn(token: String): ListenBrainzSignInResult {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) return ListenBrainzSignInResult.InvalidToken
        return when (val result = client.validateToken(trimmed)) {
            is ListenBrainzResult.Success -> {
                // A forced sign-out keeps the queue; another account must not inherit those scrobbles.
                val previous = sessionStore.lastUsername
                if (previous != null && previous != result.value.username) scrobbleQueue.clear(QueuedScrobbleEntity.SERVICE_LISTENBRAINZ)
                sessionStore.signIn(ListenBrainzAccount(token = trimmed, username = result.value.username))
                scrobbleQueue.scheduleFlush()
                ListenBrainzSignInResult.SignedIn
            }

            ListenBrainzResult.InvalidToken -> ListenBrainzSignInResult.InvalidToken

            ListenBrainzResult.Rejected, ListenBrainzResult.Unreachable -> ListenBrainzSignInResult.Failed
        }
    }

    suspend fun signOut() {
        sessionStore.signOut()
        scrobbleQueue.clear(QueuedScrobbleEntity.SERVICE_LISTENBRAINZ)
    }
}
