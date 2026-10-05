package com.simplecityapps.provider.plex.http

import kotlinx.serialization.Serializable

/** A plex.tv sign-in PIN. [authToken] is the account's token, once the user has approved [code]. */
@Serializable
data class Pin(
    val id: Long,
    val code: String,
    val authToken: String? = null,
    /** Seconds until the PIN runs out. */
    val expiresIn: Int? = null
)
