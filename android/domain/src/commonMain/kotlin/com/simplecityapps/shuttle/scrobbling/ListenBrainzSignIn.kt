package com.simplecityapps.shuttle.scrobbling

import kotlinx.coroutines.flow.Flow

// Sign-in checks the pasted token with ListenBrainz before keeping it.

sealed interface ListenBrainzAccountState {
    data object SignedOut : ListenBrainzAccountState

    data class SignedIn(val username: String) : ListenBrainzAccountState
}

enum class ListenBrainzSignInResult {
    SignedIn,

    /** ListenBrainz doesn't recognise the token. */
    InvalidToken,

    /** ListenBrainz couldn't be reached, so the token is unchecked and not kept. */
    Failed
}

fun interface ObserveListenBrainzAccount {
    operator fun invoke(): Flow<ListenBrainzAccountState>
}

/** Checks [token] with ListenBrainz and, if valid, signs in with it. */
fun interface SignInToListenBrainz {
    suspend operator fun invoke(token: String): ListenBrainzSignInResult
}

/** Forgets the ListenBrainz token, along with any scrobbles still waiting to be sent. */
fun interface SignOutOfListenBrainz {
    suspend operator fun invoke()
}
