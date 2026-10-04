package com.simplecityapps.shuttle.scrobbling

import kotlinx.coroutines.flow.Flow

// The Last.fm account as Settings > Scrobbling sees it (#503), implemented in :android:scrobbling. Sign-in is
// Last.fm's browser approval (docs/architecture/scrobbling.md): start it, send the user to the returned page,
// then finish it once they come back.

sealed interface LastFmAccountState {
    /** This build has no Last.fm API key (a fork or F-Droid build), so Last.fm is hidden. */
    data object Unavailable : LastFmAccountState

    data object SignedOut : LastFmAccountState

    /** Sign-in started: the user is approving S2 on last.fm, or has yet to come back and finish. */
    data object AwaitingApproval : LastFmAccountState

    data class SignedIn(val username: String) : LastFmAccountState
}

enum class LastFmSignInResult {
    SignedIn,

    /** The user hasn't approved S2 on last.fm yet; sign-in is still waiting. */
    NotApproved,

    /** The approval request expired or was rejected; sign-in has to start again. */
    Expired,

    /** Last.fm couldn't be reached or returned an error; sign-in is still waiting, so it can be tried again. */
    Failed,

    /** No sign-in was waiting. */
    NotStarted
}

/** Whether this build has a Last.fm API key and shared secret; without them Last.fm is hidden. */
fun interface IsLastFmConfigured {
    operator fun invoke(): Boolean
}

fun interface ObserveLastFmAccount {
    operator fun invoke(): Flow<LastFmAccountState>
}

/** Starts sign-in; returns the last.fm page where the user approves S2, or null if Last.fm couldn't be reached. */
fun interface StartLastFmSignIn {
    suspend operator fun invoke(): String?
}

/** Finishes a sign-in the user approved on last.fm. */
fun interface FinishLastFmSignIn {
    suspend operator fun invoke(): LastFmSignInResult
}

/** Forgets the Last.fm session, along with any scrobbles still waiting to be sent. */
fun interface SignOutOfLastFm {
    suspend operator fun invoke()
}
