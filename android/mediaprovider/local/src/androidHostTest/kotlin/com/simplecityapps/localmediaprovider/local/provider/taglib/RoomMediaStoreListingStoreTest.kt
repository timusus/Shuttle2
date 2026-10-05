package com.simplecityapps.localmediaprovider.local.provider.taglib

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomMediaStoreListingStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database =
        Room.inMemoryDatabaseBuilder(context, MediaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    private val store = RoomMediaStoreListingStore(database.mediaStoreFileDao())

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `nothing is stored at first`() = runTest {
        store.load() shouldBe null
    }

    @Test
    fun `a whole listing replaces the one stored`() = runTest {
        store.save(MediaStoreListingChange.Whole("v1", listOf(row(1, 5), row(2, 6))))
        store.save(MediaStoreListingChange.Whole("v2", listOf(row(3, 1))))

        store.load() shouldBe StoredMediaStoreListing("v2", mapOf(3L to row(3, 1)))
    }

    @Test
    fun `a partial listing removes the rows gone and replaces those read again`() = runTest {
        store.save(MediaStoreListingChange.Whole("v1", listOf(row(1, 5), row(2, 6), row(3, 7))))

        store.save(MediaStoreListingChange.Partial("v1", deletes = setOf(2L), upserts = listOf(row(3, 9, path = "/music/moved.flac"), row(4, 10))))

        store.load() shouldBe
            StoredMediaStoreListing(
                "v1",
                mapOf(1L to row(1, 5), 3L to row(3, 9, path = "/music/moved.flac"), 4L to row(4, 10))
            )
    }

    private fun row(
        id: Long,
        generation: Long,
        path: String = "/music/$id.flac"
    ) = MediaStoreAudioRow(
        MediaStoreAudioFile(id = id, path = path, displayName = path.substringAfterLast('/'), size = 2_048, lastModified = 1_700_000_000_000, mimeType = "audio/flac", duration = 185_000),
        generation
    )
}
