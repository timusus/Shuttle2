package com.simplecityapps.mediaprovider.server

/** A Quick Connect code shown to the user, and the secret used to poll and redeem it. */
data class QuickConnectCode(val code: String, val secret: String)

/** Quick Connect's poll state: whether the code has been approved, denied, or is still pending. */
enum class QuickConnectPollState { Pending, Authenticated, Denied }

/**
 * Signs in to a server with Quick Connect, over its provider's authentication manager. Jellyfin only.
 * A [Result] failure's message is already fit to show the user.
 */
interface QuickConnectAuthentication {
    suspend fun isEnabled(address: String): Boolean

    suspend fun initiate(address: String): Result<QuickConnectCode>

    suspend fun poll(address: String, secret: String): Result<QuickConnectPollState>

    /** Saves [address], then redeems [secret] for a token the same way a password sign-in's token is stored. */
    suspend fun authenticate(address: String, secret: String): Result<Unit>
}
