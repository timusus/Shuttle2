package com.simplecityapps.mediaprovider

import com.simplecityapps.mediaprovider.DeleteGuard.Companion.decide
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class DeleteGuardTest {
    private val store = InMemoryKeyValueStore()
    private val preferences = GeneralPreferenceManager(store)

    @Test
    fun `a few songs gone are deleted`() {
        val deletes = songs(1L..3L)

        val decision = decide(existingCount = 4, foundCount = 1, deletes = deletes, unreadableRoots = emptySet(), heldLastPass = emptySet())

        decision.apply shouldBe deletes
        decision.heldMassRemoval.shouldBeEmpty()
    }

    @Test
    fun `more than the threshold but only half the library is deleted`() {
        val deletes = songs(1L..25L)

        decide(existingCount = 50, foundCount = 25, deletes = deletes, unreadableRoots = emptySet(), heldLastPass = emptySet()).apply shouldBe deletes
    }

    @Test
    fun `most of a library gone is held back`() {
        val deletes = songs(1L..26L)

        val decision = decide(existingCount = 50, foundCount = 24, deletes = deletes, unreadableRoots = emptySet(), heldLastPass = emptySet())

        decision.apply.shouldBeEmpty()
        decision.heldMassRemoval shouldBe deletes
    }

    @Test
    fun `most of a small library gone is deleted, being no more than the threshold`() {
        val deletes = songs(1L..DeleteGuard.MIN_GUARDED_DELETES.toLong())

        decide(existingCount = 25, foundCount = 5, deletes = deletes, unreadableRoots = emptySet(), heldLastPass = emptySet()).apply shouldBe deletes
    }

    @Test
    fun `a source that found nothing keeps even a small library`() {
        val deletes = songs(1L..2L)

        val decision = decide(existingCount = 2, foundCount = 0, deletes = deletes, unreadableRoots = emptySet(), heldLastPass = emptySet())

        decision.apply.shouldBeEmpty()
        decision.heldMassRemoval shouldBe deletes
    }

    @Test
    fun `a mass removal applies only what the last pass held back too`() {
        val deletes = songs(1L..30L)

        val decision = decide(existingCount = 40, foundCount = 10, deletes = deletes, unreadableRoots = emptySet(), heldLastPass = (1L..20L).toSet())

        decision.apply shouldBe songs(1L..20L)
        decision.heldMassRemoval shouldBe songs(21L..30L)
    }

    @Test
    fun `songs under an unreadable root are kept, and don't count towards a mass removal`() {
        val unreadable = songs(1L..30L, root = "/storage/0000-0000/")
        val readable = songs(31L..32L)

        val decision =
            decide(existingCount = 40, foundCount = 8, deletes = unreadable + readable, unreadableRoots = setOf("/storage/0000-0000/"), heldLastPass = emptySet())

        decision.apply shouldBe readable
        decision.heldUnreadable shouldBe 30
        decision.heldMassRemoval.shouldBeEmpty()
    }

    @Test
    fun `the guard holds a mass removal for one pass per source`() {
        val guard = DeleteGuard(preferences)
        val deletes = songs(1L..30L)

        guard.deletesToApply(MediaProviderType.Shuttle, existingCount = 40, foundCount = 10, deletes = deletes, unreadableRoots = emptySet()).apply.shouldBeEmpty()
        // Another source's pass doesn't confirm it
        guard.deletesToApply(MediaProviderType.MediaStore, existingCount = 40, foundCount = 10, deletes = deletes, unreadableRoots = emptySet()).apply.shouldBeEmpty()

        guard.deletesToApply(MediaProviderType.Shuttle, existingCount = 40, foundCount = 10, deletes = deletes, unreadableRoots = emptySet()).apply shouldBe deletes
    }

    @Test
    fun `a pass that removes nothing much forgets what the last one held back`() {
        val guard = DeleteGuard(preferences)
        val deletes = songs(1L..30L)

        guard.deletesToApply(MediaProviderType.Shuttle, existingCount = 40, foundCount = 10, deletes = deletes, unreadableRoots = emptySet())
        guard.deletesToApply(MediaProviderType.Shuttle, existingCount = 40, foundCount = 40, deletes = emptyList(), unreadableRoots = emptySet())

        guard.deletesToApply(MediaProviderType.Shuttle, existingCount = 40, foundCount = 10, deletes = deletes, unreadableRoots = emptySet()).apply.shouldBeEmpty()
    }

    @Test
    fun `a held mass removal outlives the guard, and the next guard's pass applies it`() {
        val deletes = songs(1L..30L)
        DeleteGuard(preferences).deletesToApply(MediaProviderType.Shuttle, existingCount = 40, foundCount = 10, deletes = deletes, unreadableRoots = emptySet()).apply.shouldBeEmpty()

        // A new process: only the preferences are left
        DeleteGuard(GeneralPreferenceManager(store)).deletesToApply(MediaProviderType.Shuttle, existingCount = 40, foundCount = 10, deletes = deletes, unreadableRoots = emptySet()).apply shouldBe deletes
    }

    @Test
    fun `an incomplete listing applies no deletes and awaits a full pass`() {
        val deletes = songs(1L..3L) + songs(4L..5L, root = "/storage/1234-ABCD/")

        val decision = DeleteGuard.decide(existingCount = 40, foundCount = 35, deletes = deletes, unreadableRoots = setOf("/storage/1234-ABCD/"), heldLastPass = emptySet(), listingComplete = false)

        decision.apply.shouldBeEmpty()
        decision.heldUnreadable shouldBe 2
        decision.heldIncomplete shouldBe 3
        decision.awaitsFullPass shouldBe true
    }

    @Test
    fun `an incomplete listing neither confirms nor forgets the mass removal held before it`() {
        val guard = DeleteGuard(preferences)
        val deletes = songs(1L..30L)

        guard.deletesToApply(MediaProviderType.Jellyfin, existingCount = 40, foundCount = 10, deletes = deletes, unreadableRoots = emptySet()).apply.shouldBeEmpty()
        guard.deletesToApply(MediaProviderType.Jellyfin, existingCount = 40, foundCount = 10, deletes = deletes, unreadableRoots = emptySet(), listingComplete = false).apply.shouldBeEmpty()

        guard.deletesToApply(MediaProviderType.Jellyfin, existingCount = 40, foundCount = 10, deletes = deletes, unreadableRoots = emptySet()).apply shouldBe deletes
    }

    private fun songs(
        ids: LongRange,
        root: String = "/storage/emulated/0/"
    ) = ids.map { id ->
        Song(
            id = id,
            name = "Song $id",
            albumArtist = null,
            artists = emptyList(),
            album = null,
            track = null,
            disc = null,
            duration = 0,
            date = null,
            genres = emptyList(),
            path = "${root}Music/$id.mp3",
            size = 0,
            mimeType = "audio/mpeg",
            lastModified = null,
            lastPlayed = null,
            lastCompleted = null,
            playCount = 0,
            playbackPosition = 0,
            blacklisted = false,
            mediaProvider = MediaProviderType.Shuttle,
            lyrics = null,
            grouping = null,
            bitRate = null,
            bitDepth = null,
            sampleRate = null,
            channelCount = null,
            artworkVersion = null
        )
    }
}
