package com.simplecityapps.shuttle.persistence

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant

/**
 * The keys, defaults and forms [SourceStateStore] saves under, pinned so values saved by an older build survive the
 * split from [GeneralPreferenceManager] (#584).
 */
class SourceStateStoreTest {
    private val store = InMemoryKeyValueStore()
    private val sourceState = SourceStateStore(store)

    @Test
    fun `nothing saved reads as the defaults`() {
        sourceState.songTagsVersion("Jellyfin") shouldBe 0
        sourceState.songTagsRescanVersion shouldBe 0
        sourceState.lastMediaImportDate.shouldBeNull()
        sourceState.lastSyncStart("Jellyfin").shouldBeNull()
        sourceState.lastFullSyncStart("Jellyfin").shouldBeNull()
        sourceState.sourceReachability("Jellyfin").shouldBeNull()
        sourceState.sourceUpdated("Jellyfin").shouldBeNull()
    }

    @Test
    fun `a source's updated time is kept per source`() {
        sourceState.setSourceUpdated("Jellyfin", Instant.fromEpochMilliseconds(1_000))

        sourceState.sourceUpdated("Jellyfin") shouldBe Instant.fromEpochMilliseconds(1_000)
        sourceState.sourceUpdated("Plex").shouldBeNull()
    }

    @Test
    fun `a source's reachability is kept per source - a success clears its error`() {
        val failed = SourceReachability("Can't reach the server", Instant.fromEpochMilliseconds(1_000))
        sourceState.setSourceReachability("Jellyfin", failed)

        sourceState.sourceReachability("Jellyfin") shouldBe failed
        sourceState.sourceReachability("Plex").shouldBeNull()

        val reached = SourceReachability(null, Instant.fromEpochMilliseconds(2_000))
        sourceState.setSourceReachability("Jellyfin", reached)
        sourceState.sourceReachability("Jellyfin") shouldBe reached
    }

    @Test
    fun `sync starts are kept per source`() {
        sourceState.setLastSyncStart("Jellyfin", Instant.fromEpochMilliseconds(1_000))
        sourceState.setLastFullSyncStart("Plex", Instant.fromEpochMilliseconds(2_000))

        sourceState.lastSyncStart("Jellyfin") shouldBe Instant.fromEpochMilliseconds(1_000)
        sourceState.lastSyncStart("Plex").shouldBeNull()
        sourceState.lastFullSyncStart("Plex") shouldBe Instant.fromEpochMilliseconds(2_000)
        sourceState.lastFullSyncStart("Jellyfin").shouldBeNull()

        sourceState.setLastSyncStart("Jellyfin", null)
        sourceState.lastSyncStart("Jellyfin").shouldBeNull()
    }

    @Test
    fun `each value is saved under the key and in the form it always was`() {
        sourceState.setSongTagsVersion("Jellyfin", 1)
        sourceState.songTagsRescanVersion = 1
        sourceState.lastMediaImportDate = Instant.fromEpochMilliseconds(1_700_000_000_003)

        store.values shouldBe mapOf(
            "song_tags_version_Jellyfin" to 1,
            "song_tags_rescan_version" to 1,
            "pref_media_last_rescan_date" to 1_700_000_000_003L
        )
    }

    @Test
    fun `a date saved as epoch millis by an older build reads back`() {
        val saved = SourceStateStore(InMemoryKeyValueStore(mapOf("pref_media_last_rescan_date" to 1_700_000_000_003L)))

        saved.lastMediaImportDate shouldBe Instant.fromEpochMilliseconds(1_700_000_000_003)
    }

    @Test
    fun `clearing a date removes its key`() {
        sourceState.lastMediaImportDate = Instant.fromEpochMilliseconds(1)

        sourceState.lastMediaImportDate = null

        store.values shouldBe emptyMap()
    }

    @Test
    fun `held deletes are saved per source as sorted base-36 gaps - and an unreadable value holds none`() {
        sourceState.setHeldDeletes("Shuttle", setOf(40L, 3L, 1_000_000L))
        store.getString("held_deletes_Shuttle", null) shouldBe "3,11,lfko"
        sourceState.heldDeletes("Shuttle") shouldBe setOf(3L, 40L, 1_000_000L)
        sourceState.heldDeletes("Jellyfin") shouldBe emptySet()

        sourceState.setHeldDeletes("Shuttle", emptySet())
        store.contains("held_deletes_Shuttle") shouldBe false

        store.edit { putString("held_deletes_Shuttle", "3,!,4") }
        sourceState.heldDeletes("Shuttle") shouldBe emptySet()
    }
}
