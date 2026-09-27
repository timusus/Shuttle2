package com.simplecityapps.localmediaprovider.local.data.room.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.localmediaprovider.local.data.room.entity.SongData
import com.simplecityapps.shuttle.model.MediaProviderType
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.util.Date
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** #497 slice 1: a remote-provider song's favourite/unfavourite enqueues a `pending_favourites` row, in the same transaction. */
@RunWith(AndroidJUnit4::class)
class SongDataDaoFavouriteOutboxTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database = Room.inMemoryDatabaseBuilder(context, MediaDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val dao = database.songDataDao()

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `favouriting a remote song enqueues a pending favourite`() = runTest {
        val song = insertSong(MediaProviderType.Jellyfin, externalId = "item-1")

        dao.setFavourite(listOf(song), true)

        val pending = dao.getPendingFavourites()
        pending.map { it.songId } shouldBe listOf(song.id)
        pending.single().run {
            mediaProvider shouldBe MediaProviderType.Jellyfin
            externalId shouldBe "item-1"
            favourite shouldBe true
        }
    }

    @Test
    fun `unfavouriting a remote song enqueues a pending unfavourite`() = runTest {
        val song = insertSong(MediaProviderType.Plex, externalId = "item-2")
        dao.setFavourite(listOf(song), true)

        dao.setFavourite(listOf(song), false)

        val pending = dao.getPendingFavourites()
        pending.map { it.songId } shouldBe listOf(song.id)
        pending.single().favourite shouldBe false
    }

    @Test
    fun `undoing a remove enqueues a pending favourite again (#564)`() = runTest {
        val song = insertSong(MediaProviderType.Emby, externalId = "item-3")
        dao.setFavourite(listOf(song), true)
        val originalFavouritedSong = dao.get().single().toSong()
        dao.setFavourite(listOf(originalFavouritedSong), false)

        dao.setFavourite(listOf(originalFavouritedSong), true)

        dao.getPendingFavourites().single().favourite shouldBe true
    }

    @Test
    fun `a later toggle before a flush overwrites the earlier pending row, not adds a second one`() = runTest {
        val song = insertSong(MediaProviderType.Jellyfin, externalId = "item-4")

        dao.setFavourite(listOf(song), true)
        dao.setFavourite(listOf(song), false)

        val pending = dao.getPendingFavourites()
        pending.size shouldBe 1
        pending.single().favourite shouldBe false
    }

    @Test
    fun `a local song never enqueues a pending favourite`() = runTest {
        val song = insertSong(MediaProviderType.Shuttle, externalId = null)

        dao.setFavourite(listOf(song), true)
        dao.setFavourite(listOf(song), false)

        dao.getPendingFavourites().shouldBeEmpty()
    }

    private suspend fun insertSong(
        mediaProvider: MediaProviderType,
        externalId: String?
    ): com.simplecityapps.shuttle.model.Song {
        dao.insert(
            listOf(
                SongData(
                    name = "Song",
                    track = 1,
                    disc = 1,
                    duration = 180_000,
                    year = null,
                    genres = emptyList(),
                    path = "/music/${mediaProvider.name}.mp3",
                    albumArtist = "Artist",
                    artists = listOf("Artist"),
                    album = "Album",
                    size = 0,
                    mimeType = "audio/mpeg",
                    lastModified = Date(0),
                    lyrics = null,
                    grouping = null,
                    bitRate = null,
                    bitDepth = null,
                    sampleRate = null,
                    channelCount = null,
                    externalId = externalId,
                    mediaProvider = mediaProvider
                )
            )
        )
        return dao.get().single().toSong()
    }
}
