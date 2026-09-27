package com.simplecityapps.shuttle.entitlement

/**
 * Whether a new download from a remote server may start, starting the trial for a user who hasn't had one.
 * Existing downloads are never removed by this check.
 */
fun interface TryDownloadFromServer {
    suspend operator fun invoke(): Boolean
}
