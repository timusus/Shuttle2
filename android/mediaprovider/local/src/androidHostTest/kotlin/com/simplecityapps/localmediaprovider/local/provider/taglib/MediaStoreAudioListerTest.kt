package com.simplecityapps.localmediaprovider.local.provider.taglib

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MediaStoreAudioListerTest {
    private val source = FakeMediaStore()
    private val store = FakeListingStore()

    @Test
    fun `without a stored listing, MediaStore is read whole and the listing kept once the songs are stored`() = runTest {
        source.put(row(1, generation = 5), row(2, generation = 6))
        val lister = lister()

        lister.list(whole = false)!!.map { it.id } shouldContainExactlyInAnyOrder listOf(1L, 2L)
        source.wholeReads shouldBe 1
        store.saved shouldBe null

        lister.listingStored()

        store.listing!!.rows.keys shouldContainExactlyInAnyOrder listOf(1L, 2L)
        store.listing!!.version shouldBe "v1"
    }

    @Test
    fun `with nothing changed, only the generations are read and the stored rows make the listing`() = runTest {
        source.put(row(1, generation = 5), row(2, generation = 6))
        storeListing(row(1, generation = 5), row(2, generation = 6))

        val files = lister().list(whole = false)!!

        files.map { it.id } shouldContainExactlyInAnyOrder listOf(1L, 2L)
        source.wholeReads shouldBe 0
        source.changedAfterReads shouldBe 0
        source.idReads.shouldBeEmpty()
    }

    @Test
    fun `a changed row is read again from those above the newest generation stored`() = runTest {
        source.put(row(1, generation = 5), row(2, generation = 9, size = 4_096))
        storeListing(row(1, generation = 5), row(2, generation = 6, size = 2_048))
        val lister = lister()

        val files = lister.list(whole = false)!!

        files.single { it.id == 2L }.size shouldBe 4_096
        source.wholeReads shouldBe 0
        source.changedAfter shouldBe listOf(6L)
        source.idReads.shouldBeEmpty()

        lister.listingStored()
        val saved = store.saved.shouldBeInstanceOf<MediaStoreListingChange.Partial>()
        saved.upserts.map { it.file.id } shouldBe listOf(2L)
        saved.deletes.shouldBeEmpty()
        store.listing!!.rows.getValue(2).generation shouldBe 9
    }

    @Test
    fun `a new row is added to the listing`() = runTest {
        source.put(row(1, generation = 5), row(3, generation = 7))
        storeListing(row(1, generation = 5))

        lister().list(whole = false)!!.map { it.id } shouldContainExactlyInAnyOrder listOf(1L, 3L)
    }

    @Test
    fun `a row gone from MediaStore leaves the listing, found by the id set alone`() = runTest {
        source.put(row(1, generation = 5))
        storeListing(row(1, generation = 5), row(2, generation = 6))
        val lister = lister()

        lister.list(whole = false)!!.map { it.id } shouldBe listOf(1L)
        source.changedAfterReads shouldBe 0

        lister.listingStored()
        store.saved.shouldBeInstanceOf<MediaStoreListingChange.Partial>().deletes shouldBe setOf(2L)
        store.listing!!.rows.keys shouldBe setOf(1L)
    }

    @Test
    fun `a row whose generation is below the newest stored, a volume mounted again say, is read by its id`() = runTest {
        source.put(row(1, generation = 50), row(2, generation = 3))
        storeListing(row(1, generation = 50))

        val files = lister().list(whole = false)!!

        files.map { it.id } shouldContainExactlyInAnyOrder listOf(1L, 2L)
        source.idReads shouldBe listOf(setOf(2L))
        source.wholeReads shouldBe 0
    }

    @Test
    fun `too many rows to read by id are read with the whole listing`() = runTest {
        val remounted = (2L..MAX_ROWS_READ_BY_ID + 2L).map { id -> row(id, generation = 3) }
        source.put(row(1, generation = 50), *remounted.toTypedArray())
        storeListing(row(1, generation = 50))

        lister().list(whole = false)!!.size shouldBe remounted.size + 1

        source.wholeReads shouldBe 1
        source.idReads.shouldBeEmpty()
    }

    @Test
    fun `a changed row removed before it could be read again leaves the listing`() = runTest {
        source.put(row(1, generation = 5), row(2, generation = 9))
        source.vanishOnRead = setOf(2L)
        storeListing(row(1, generation = 5), row(2, generation = 6))

        lister().list(whole = false)!!.map { it.id } shouldBe listOf(1L)
    }

    @Test
    fun `a new MediaStore version reads the listing whole and replaces the stored one`() = runTest {
        source.put(row(10, generation = 1))
        storeListing(row(1, generation = 5), row(2, generation = 6))
        source.version = "v2"
        val lister = lister()

        lister.list(whole = false)!!.map { it.id } shouldBe listOf(10L)
        source.wholeReads shouldBe 1
        source.generationReads shouldBe 0

        lister.listingStored()
        store.saved.shouldBeInstanceOf<MediaStoreListingChange.Whole>()
        store.listing shouldBe StoredMediaStoreListing("v2", mapOf(10L to row(10, generation = 1)))
    }

    @Test
    fun `a whole read ignores the stored listing and replaces it`() = runTest {
        source.put(row(1, generation = 5), row(2, generation = 9, size = 4_096))
        // A stored row with the same generation is trusted by a partial read: a whole one reads it again regardless
        storeListing(row(1, generation = 5), row(2, generation = 9, size = 2_048))
        val lister = lister()

        lister.list(whole = true)!!.single { it.id == 2L }.size shouldBe 4_096
        source.wholeReads shouldBe 1

        lister.listingStored()
        store.saved.shouldBeInstanceOf<MediaStoreListingChange.Whole>()
    }

    @Test
    fun `before API 30 MediaStore is read whole, without generations, and nothing is kept`() = runTest {
        source.put(row(1, generation = 5))
        storeListing(row(1, generation = 5))
        val lister = lister(incremental = false)

        lister.list(whole = false)!!.map { it.id } shouldBe listOf(1L)
        source.wholeReads shouldBe 1
        source.wholeReadsWithGeneration shouldBe 0
        source.generationReads shouldBe 0

        lister.listingStored()
        store.saved shouldBe null
    }

    @Test
    fun `without a MediaStore version nothing is kept`() = runTest {
        source.put(row(1, generation = 5))
        source.version = null
        val lister = lister()

        lister.list(whole = false)!!.map { it.id } shouldBe listOf(1L)
        lister.listingStored()

        store.saved shouldBe null
    }

    @Test
    fun `an import that doesn't store its songs keeps the last listing, which the next read compares against`() = runTest {
        source.put(row(1, generation = 5), row(2, generation = 9))
        storeListing(row(1, generation = 5), row(2, generation = 6))
        val lister = lister()

        lister.list(whole = false)
        // No listingStored: the import failed
        lister.list(whole = false)!!.map { it.id } shouldContainExactlyInAnyOrder listOf(1L, 2L)

        source.changedAfter shouldBe listOf(6L, 6L)
    }

    @Test
    fun `a MediaStore that can't be queried lists nothing and keeps nothing`() = runTest {
        storeListing(row(1, generation = 5))
        source.unreadable = true
        val lister = lister()

        lister.list(whole = false) shouldBe null
        lister.listingStored()

        store.saved shouldBe null
    }

    @Test
    fun `a stored listing that fails to load is read whole`() = runTest {
        source.put(row(1, generation = 5))
        store.failLoad = true

        lister().list(whole = false)!!.map { it.id } shouldBe listOf(1L)

        source.wholeReads shouldBe 1
    }

    @Test
    fun `a row that kept its id at another path is a moved file`() = runTest {
        source.put(row(1, generation = 9, path = "/storage/emulated/0/Albums/1.mp3", duration = 180_000))
        storeListing(row(1, generation = 5, duration = 180_000))
        val lister = lister()

        lister.list(whole = false)

        lister.moved().map { it.oldPath to it.file.path } shouldBe listOf("/storage/emulated/0/Music/1.mp3" to "/storage/emulated/0/Albums/1.mp3")
    }

    @Test
    fun `a moved file is found by a whole read too`() = runTest {
        source.put(row(1, generation = 9, path = "/storage/emulated/0/Albums/1.mp3"))
        storeListing(row(1, generation = 5))
        val lister = lister()

        lister.list(whole = true)

        lister.moved().map { it.file.id } shouldBe listOf(1L)
    }

    @Test
    fun `a file that changed size or duration under a kept id is not a moved file`() = runTest {
        source.put(
            row(1, generation = 9, path = "/storage/emulated/0/Albums/1.mp3", size = 2_048),
            row(2, generation = 9, path = "/storage/emulated/0/Albums/2.mp3", duration = 300_000)
        )
        storeListing(row(1, generation = 5), row(2, generation = 5, duration = 180_000))
        val lister = lister()

        lister.list(whole = false)

        lister.moved().shouldBeEmpty()
    }

    @Test
    fun `nothing is moved when MediaStore was rebuilt`() = runTest {
        source.version = "v2"
        source.put(row(1, generation = 9, path = "/storage/emulated/0/Albums/1.mp3"))
        storeListing(row(1, generation = 5))
        val lister = lister()

        lister.list(whole = false)

        lister.moved().shouldBeEmpty()
    }

    private fun lister(incremental: Boolean = true) = MediaStoreAudioLister(source, store, incremental)

    private fun storeListing(vararg rows: MediaStoreAudioRow) {
        store.listing = StoredMediaStoreListing("v1", rows.associateBy { it.file.id })
    }

    private fun row(
        id: Long,
        generation: Long,
        size: Long = 1_024,
        path: String = "/storage/emulated/0/Music/$id.mp3",
        duration: Long? = null
    ) = MediaStoreAudioRow(
        MediaStoreAudioFile(id = id, path = path, displayName = "$id.mp3", size = size, lastModified = 1_700_000_000_000, mimeType = "audio/mpeg", duration = duration),
        generation
    )

    private class FakeListingStore : MediaStoreListingStore {
        var listing: StoredMediaStoreListing? = null
        var saved: MediaStoreListingChange? = null
        var failLoad = false

        override suspend fun load(): StoredMediaStoreListing? {
            if (failLoad) throw IllegalStateException("database closed")
            return listing
        }

        override suspend fun save(change: MediaStoreListingChange) {
            saved = change
            listing =
                when (change) {
                    is MediaStoreListingChange.Whole -> StoredMediaStoreListing(change.version, change.rows.associateBy { it.file.id })

                    is MediaStoreListingChange.Partial ->
                        StoredMediaStoreListing(
                            change.version,
                            listing!!.rows.filterKeys { id -> id !in change.deletes } + change.upserts.associateBy { it.file.id }
                        )
                }
        }
    }
}
