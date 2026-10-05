package com.simplecityapps.shuttle.analytics

import com.simplecityapps.shuttle.entitlement.Entitlement
import com.simplecityapps.shuttle.entitlement.ProSource
import com.simplecityapps.shuttle.model.MediaProviderType
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant

class MonetisationAnalyticsTest {
    private val events = mutableListOf<Pair<String, Map<String, Any>>>()
    private val registered = mutableMapOf<String, Any>()
    private var backendTakesEvents = true
    private val analytics = MonetisationAnalytics(
        object : Analytics {
            override val isCapturing: Boolean get() = backendTakesEvents

            override fun capture(
                event: String,
                properties: Map<String, Any>
            ) {
                events += event to properties
            }

            override fun register(
                name: String,
                value: Any
            ) {
                registered[name] = value
            }
        }
    )

    @Test
    fun `media_sources lists the source kinds sorted and both local scanners as one`() {
        analytics.mediaSourcesChanged(listOf(MediaProviderType.Plex, MediaProviderType.Shuttle, MediaProviderType.MediaStore, MediaProviderType.Jellyfin))

        registered shouldBe mapOf("media_sources" to "jellyfin,local,plex")
    }

    @Test
    fun `media_sources is none without a source and follows changes`() {
        analytics.mediaSourcesChanged(emptyList())
        registered["media_sources"] shouldBe "none"

        analytics.mediaSourcesChanged(listOf(MediaProviderType.Subsonic, MediaProviderType.Emby))
        registered["media_sources"] shouldBe "emby,subsonic"
    }

    @Test
    fun `entitlement_resolved names where the entitlement came from`() {
        listOf(
            Entitlement.Free(trialUsed = false),
            Entitlement.Free(trialUsed = true),
            Entitlement.Trial(Instant.fromEpochMilliseconds(0)),
            Entitlement.Pro(ProSource.Lifetime),
            Entitlement.Pro(ProSource.Subscription),
            Entitlement.Pro(ProSource.LegacyLifetime),
            Entitlement.Pro(ProSource.LegacySubscription),
            Entitlement.Pro(ProSource.Debug)
        ).forEach { analytics.entitlementResolved(it) shouldBe true }

        events.map { it.first }.toSet() shouldBe setOf("entitlement_resolved")
        events.map { it.second["source"] } shouldBe listOf("none", "none", "trial", "pro", "pro", "legacy", "legacy", "debug")
    }

    @Test
    fun `entitlement_resolved reports it was not taken while analytics isn't capturing`() {
        backendTakesEvents = false

        analytics.entitlementResolved(Entitlement.Free(trialUsed = false)) shouldBe false

        events shouldBe emptyList()
    }
}
