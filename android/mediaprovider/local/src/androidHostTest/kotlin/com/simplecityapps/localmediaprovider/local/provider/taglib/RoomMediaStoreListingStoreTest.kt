package com.simplecityapps.localmediaprovider.local.provider.taglib

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.mediaprovider.SongPathRemap
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.shouldBe
import kotlin.time.Instant
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
    private val store = RoomMediaStoreListingStore(database.mediaStoreFileDao(), MediaProviderType.Shuttle)

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `each provider's listing is its own`() = runTest {
        val other = RoomMediaStoreListingStore(database.mediaStoreFileDao(), MediaProviderType.MediaStore)
        store.save(MediaStoreListingChange.Whole("v1", listOf(row(1, 5), row(2, 6))))
        other.save(MediaStoreListingChange.Whole("v2", listOf(row(3, 1))))

        store.save(MediaStoreListingChange.Partial("v1", deletes = setOf(1L, 3L), upserts = emptyList()))

        store.load() shouldBe StoredMediaStoreListing("v1", mapOf(2L to row(2, 6)))
        other.load() shouldBe StoredMediaStoreListing("v2", mapOf(3L to row(3, 1)))
    }

    @Test
    fun `with both local providers enabled, a moved file keeps each one's song`() = runTest {
        val source = FakeMediaStore()
        val taglib = MediaStoreAudioLister(source, store, incremental = true)
        val mediaStore = MediaStoreAudioLister(source, RoomMediaStoreListingStore(database.mediaStoreFileDao(), MediaProviderType.MediaStore), incremental = true)
        source.put(row(1, 5))
        listOf(taglib, mediaStore).forEach { lister ->
            lister.list(whole = false)
            lister.listingStored()
        }
        source.put(row(1, 9, path = "/music/moved.flac"))

        // The first to store its import moves the stored path on; the other still finds the move against its own
        taglib.list(whole = false)
        movedSongRemaps(listOf(song(10, "/music/1.flac", MediaProviderType.Shuttle)), taglib.moved()) shouldBe listOf(SongPathRemap(songId = 10, path = "/music/moved.flac"))
        taglib.listingStored()
        mediaStore.list(whole = false)
        movedSongRemaps(listOf(song(20, "/music/1.flac", MediaProviderType.MediaStore)), mediaStore.moved()) shouldBe listOf(SongPathRemap(songId = 20, path = "/music/moved.flac"))
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

    private fun song(
        id: Long,
        path: String,
        provider: MediaProviderType
    ) = Song(
        id = id,
        name = "Song $id",
        albumArtist = null,
        artists = emptyList(),
        album = null,
        track = null,
        disc = null,
        duration = 185_000,
        date = null,
        genres = emptyList(),
        path = path,
        size = 2_048,
        mimeType = "audio/flac",
        lastModified = Instant.fromEpochMilliseconds(1_700_000_000_000),
        lastPlayed = null,
        lastCompleted = null,
        playCount = 3,
        playbackPosition = 0,
        blacklisted = false,
        mediaProvider = provider,
        lyrics = null,
        grouping = null,
        bitRate = null,
        bitDepth = null,
        sampleRate = null,
        channelCount = null
    )
}
