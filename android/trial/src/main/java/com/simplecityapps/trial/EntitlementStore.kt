package com.simplecityapps.trial

import com.simplecityapps.shuttle.entitlement.CachedPro
import com.simplecityapps.shuttle.entitlement.ProSource
import com.simplecityapps.shuttle.persistence.KeyValueStore
import kotlin.time.Instant

/** Persists what [EntitlementRepository] needs across launches. */
interface EntitlementStore {
    /** When the server trial started. Written once; a user gets one trial. */
    var serverTrialStartedAt: Instant?

    var cachedPro: CachedPro?
}

/**
 * Backed by its own store ([PREFERENCES_NAME] on Android), which Android auto-backup restores on reinstall, so
 * reinstalling doesn't reset the trial for users with backup on.
 */
class KeyValueEntitlementStore(
    private val store: KeyValueStore
) : EntitlementStore {
    override var serverTrialStartedAt: Instant?
        get() = store.getInstant(KEY_SERVER_TRIAL_STARTED_AT)
        set(value) = store.edit { putInstant(KEY_SERVER_TRIAL_STARTED_AT, value) }

    override var cachedPro: CachedPro?
        get() {
            val source = store.getString(KEY_CACHED_PRO_SOURCE, null)?.let { name -> ProSource.entries.firstOrNull { it.name == name } }
            val seenAt = store.getInstant(KEY_CACHED_PRO_SEEN_AT)
            return if (source != null && seenAt != null) CachedPro(source, seenAt) else null
        }
        set(value) = store.edit {
            putString(KEY_CACHED_PRO_SOURCE, value?.source?.name)
            putInstant(KEY_CACHED_PRO_SEEN_AT, value?.seenAt)
        }

    private fun KeyValueStore.getInstant(key: String): Instant? = if (contains(key)) Instant.fromEpochMilliseconds(getLong(key, 0)) else null

    private fun KeyValueStore.Editor.putInstant(
        key: String,
        value: Instant?
    ) {
        if (value == null) remove(key) else putLong(key, value.toEpochMilliseconds())
    }

    companion object {
        const val PREFERENCES_NAME = "entitlement"
        private const val KEY_SERVER_TRIAL_STARTED_AT = "server_trial_started_at"
        private const val KEY_CACHED_PRO_SOURCE = "cached_pro_source"
        private const val KEY_CACHED_PRO_SEEN_AT = "cached_pro_seen_at"
    }
}
