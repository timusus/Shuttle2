package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.model.MediaProviderType

/** How one [MediaImporter.sync] run went, so its caller needn't infer it from the import states. */
sealed interface SyncResult {
    /** Nothing ran: no sources, a full import due first, another import running, or every source synced recently. */
    data object Skipped : SyncResult

    /** The sync ran, and each source in it ended as [providers] says. */
    data class Ran(val providers: Map<MediaProviderType, ProviderSyncOutcome>) : SyncResult

    /** The sync itself threw, rather than one source's import. */
    data class Aborted(val cause: Exception) : SyncResult

    /** Whether trying again soon might do better: a source or the sync failed in a way that isn't final. */
    val shouldRetry: Boolean
        get() = when (this) {
            Skipped -> false
            is Ran -> providers.values.any { it is ProviderSyncOutcome.Failed && it.retryable }
            is Aborted -> true
        }
}

/** How one source's part of a sync ended. */
sealed interface ProviderSyncOutcome {
    data object Success : ProviderSyncOutcome

    /** It failed: [cause] is what it threw, if it threw. [retryable] says a later attempt might succeed. */
    data class Failed(
        val message: String,
        val retryable: Boolean = true,
        val cause: Exception? = null
    ) : ProviderSyncOutcome

    /** It was stopped, as when its source was removed, so it says nothing of how the source stands. */
    data object Cancelled : ProviderSyncOutcome
}
