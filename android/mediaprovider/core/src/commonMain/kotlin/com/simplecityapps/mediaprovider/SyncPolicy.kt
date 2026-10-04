package com.simplecityapps.mediaprovider

import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** What asks for a sync (#771). */
enum class SyncTrigger {
    /** The app came to the foreground: only sources that sync incrementally (the servers) are brought up to date. */
    Foreground,

    /** The scheduled background sync: every source, those that can't sync incrementally (this device's) in full. */
    Periodic
}

/**
 * When and how a sync brings a source up to date (#771): at most once per [MIN_INTERVAL] per source, incrementally where
 * the source can, and in full at least every [FULL_SYNC_INTERVAL] so songs deleted on the server leave the library.
 */
object SyncPolicy {
    /** A source synced more recently than this is left alone. */
    val MIN_INTERVAL = 15.minutes

    /** How long an incremental source goes without a full sync, which is what removes songs the server no longer has. */
    val FULL_SYNC_INTERVAL = 7.days

    /**
     * How far before the last sync's start an incremental sync asks from, so a server clock behind ours doesn't hide a
     * change. Fetching a song twice is harmless: it's updated in place by path.
     */
    val OVERLAP = 10.minutes

    /**
     * How to sync a source at [now] for [trigger], or null to leave it: [incremental] is whether it's an
     * [IncrementalMediaProvider]; [lastSyncStart] and [lastFullSyncStart] are when its last successful sync and full sync
     * started; [songTagsOutdated] is whether its songs lack tags this build reads, which only a full sync fills in.
     */
    fun plan(
        trigger: SyncTrigger,
        incremental: Boolean,
        lastSyncStart: Instant?,
        lastFullSyncStart: Instant?,
        songTagsOutdated: Boolean,
        now: Instant
    ): SyncPlan? {
        if (!incremental && trigger == SyncTrigger.Foreground) return null
        if (lastSyncStart != null && now - lastSyncStart < MIN_INTERVAL) return null
        if (!incremental || songTagsOutdated) return SyncPlan.Full
        if (lastSyncStart == null || lastFullSyncStart == null || now - lastFullSyncStart >= FULL_SYNC_INTERVAL) return SyncPlan.Full
        return SyncPlan.Incremental(since = lastSyncStart - OVERLAP)
    }
}
