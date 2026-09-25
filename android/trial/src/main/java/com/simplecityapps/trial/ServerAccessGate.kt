package com.simplecityapps.trial

import javax.inject.Inject
import javax.inject.Singleton

/**
 * What feature code asks before it uses a remote server. Only streaming and new downloads from Jellyfin, Emby
 * and Plex are gated. Local playback, Chromecast, EQ, Android Auto and songs that are already downloaded are
 * never gated, so they must not call this.
 */
@Singleton
class ServerAccessGate
@Inject
constructor(
    private val entitlementRepository: EntitlementRepository
) {
    /** True if the user may stream a song from a remote server. A downloaded song plays without asking. */
    fun canStreamFromServer(): Boolean = entitlementRepository.entitlement.value.unlocksServers()

    /** True if the user may start a new download from a remote server. Existing downloads are never removed. */
    fun canDownloadFromServer(): Boolean = entitlementRepository.entitlement.value.unlocksServers()

    private fun Entitlement.unlocksServers(): Boolean = this is Entitlement.Pro || this is Entitlement.Trial
}
