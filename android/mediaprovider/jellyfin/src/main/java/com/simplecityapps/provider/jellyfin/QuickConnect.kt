package com.simplecityapps.provider.jellyfin

/** The code the user enters in another Jellyfin client, and the secret used to poll and redeem it. */
data class QuickConnectCode(val code: String, val secret: String)

enum class QuickConnectPollState { Pending, Authenticated, Denied }
