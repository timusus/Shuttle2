package com.simplecityapps.mediaprovider

import kotlin.time.Instant

/** How an import brings one source's songs up to date (#771). */
sealed interface SyncPlan {
    /** Every song: the listing replaces what's stored, so songs the source no longer has are removed. */
    data object Full : SyncPlan

    /** Only what changed on an [IncrementalMediaProvider] at or after [since], stored over the last import without removing anything. */
    data class Incremental(val since: Instant) : SyncPlan
}
