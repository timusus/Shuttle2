package com.simplecityapps.trial

import com.simplecityapps.shuttle.entitlement.CachedPro
import com.simplecityapps.shuttle.entitlement.ProSource
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The keys and forms [KeyValueEntitlementStore] saves under, pinned so a saved trial and Pro status survive (#584). */
class KeyValueEntitlementStoreTest {
    private val store = InMemoryKeyValueStore()
    private val entitlementStore = KeyValueEntitlementStore(store)

    @Test
    fun `the trial start and cached Pro are saved under their keys, as epoch millis and the source's name`() {
        entitlementStore.serverTrialStartedAt = Instant.fromEpochMilliseconds(1_000)
        entitlementStore.cachedPro = CachedPro(ProSource.entries.first(), Instant.fromEpochMilliseconds(2_000))

        assertEquals(
            mapOf(
                "server_trial_started_at" to 1_000L,
                "cached_pro_source" to ProSource.entries.first().name,
                "cached_pro_seen_at" to 2_000L
            ),
            store.values
        )
        assertEquals("entitlement", KeyValueEntitlementStore.PREFERENCES_NAME)
    }

    @Test
    fun `the entitlement_resolved flag defaults to false and is saved under its key`() {
        assertEquals(false, entitlementStore.entitlementResolvedLogged)

        entitlementStore.entitlementResolvedLogged = true

        assertEquals(mapOf("entitlement_resolved_logged" to true), store.values)
    }

    @Test
    fun `values saved by an older build read back`() {
        val saved = InMemoryKeyValueStore(
            mapOf(
                "server_trial_started_at" to 1_000L,
                "cached_pro_source" to ProSource.entries.last().name,
                "cached_pro_seen_at" to 2_000L
            )
        )

        val entitlementStore = KeyValueEntitlementStore(saved)

        assertEquals(Instant.fromEpochMilliseconds(1_000), entitlementStore.serverTrialStartedAt)
        assertEquals(CachedPro(ProSource.entries.last(), Instant.fromEpochMilliseconds(2_000)), entitlementStore.cachedPro)
    }

    @Test
    fun `clearing cached Pro removes its keys`() {
        entitlementStore.cachedPro = CachedPro(ProSource.entries.first(), Instant.fromEpochMilliseconds(2_000))

        entitlementStore.cachedPro = null

        assertEquals(emptyMap<String, Any>(), store.values)
        assertNull(entitlementStore.serverTrialStartedAt)
    }
}
