package com.simplecityapps.shuttle.scrobbling

import kotlinx.coroutines.flow.Flow

// The ListenBrainz account as Settings > Scrobbling sees it (#503), implemented in :android:scrobbling. Sign-in is
// a user token pasted from listenbrainz.org/settings, checked against ListenBrainz before it is kept.

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
