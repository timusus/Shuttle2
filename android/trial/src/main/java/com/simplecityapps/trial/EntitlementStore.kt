package com.simplecityapps.trial

import com.simplecityapps.shuttle.entitlement.CachedPro
import com.simplecityapps.shuttle.entitlement.ProFeature
import com.simplecityapps.shuttle.entitlement.ProSource
import com.simplecityapps.shuttle.entitlement.TrialDisclosureStore
import com.simplecityapps.shuttle.persistence.KeyValueStore
import kotlin.time.Instant

/**
 * Persists what [EntitlementRepository] needs across launches, and the trial disclosure still to show
 * ([TrialDisclosureStore]), which lives beside the trial it discloses.
 */
interface EntitlementStore : TrialDisclosureStore {
    /** When the server trial started. Written once; a user gets one trial. */
    var serverTrialStartedAt: Instant?

    var cachedPro: CachedPro?

    /** Whether `entitlement_resolved` has been logged, so it's logged once per install. */
    var entitlementResolvedLogged: Boolean
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

    override var entitlementResolvedLogged: Boolean
        get() = store.getBoolean(KEY_ENTITLEMENT_RESOLVED_LOGGED, false)
        set(value) = store.edit { putBoolean(KEY_ENTITLEMENT_RESOLVED_LOGGED, value) }

    override var pendingDisclosure: ProFeature?
        get() = store.getString(KEY_PENDING_TRIAL_DISCLOSURE, null)?.let { name -> ProFeature.entries.firstOrNull { it.name == name } }
        set(value) = store.edit { putString(KEY_PENDING_TRIAL_DISCLOSURE, value?.name) }

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
        private const val KEY_ENTITLEMENT_RESOLVED_LOGGED = "entitlement_resolved_logged"
        private const val KEY_PENDING_TRIAL_DISCLOSURE = "pending_trial_disclosure"
    }
}
