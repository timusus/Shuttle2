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
 * When and how a sync brings a source up to date (#771): at most once per [MIN_INTERVAL] per source for a return to the app, incrementally where
 * the source can, and in full at least every [FULL_SYNC_INTERVAL] so songs deleted on the server leave the library.
 */
object SyncPolicy {
    /** A source synced more recently than this is left alone by a return to the app. */
    val MIN_INTERVAL = 15.minutes

    /** How long an incremental source goes without a full sync, which reads every song's tags again (an incremental one removes songs the server no longer has itself). */
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
        // A time in the future means the clock was set back since: it can't say how long ago the last sync was, so a full one
        // (which stores a time that can) replaces it. A hair ahead (drift) is read as now.
        if ((lastSyncStart != null && lastSyncStart - now > OVERLAP) || (lastFullSyncStart != null && lastFullSyncStart - now > OVERLAP)) return SyncPlan.Full
        // The daily sync isn't throttled: it runs at most daily already, and is what refreshes the playlists
        if (trigger == SyncTrigger.Foreground && lastSyncStart != null && now - lastSyncStart < MIN_INTERVAL) return null
        if (!incremental || songTagsOutdated) return SyncPlan.Full
        if (lastSyncStart == null || lastFullSyncStart == null || now - lastFullSyncStart >= FULL_SYNC_INTERVAL) return SyncPlan.Full
        return SyncPlan.Incremental(since = minOf(lastSyncStart, now) - OVERLAP)
    }
}
