package com.simplecityapps.localmediaprovider.local.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simplecityapps.localmediaprovider.local.data.room.dao.toSong
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.localmediaprovider.local.data.room.database.trackingIdentityChanges
import com.simplecityapps.localmediaprovider.local.data.room.entity.IDENTITY_GENERATION_TABLE
import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.AlbumIndex
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** The library's one album index is built once, and again only when the library's album identities change (#688). */
@RunWith(AndroidJUnit4::class)
class LibraryAlbumIndexTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database = Room.inMemoryDatabaseBuilder(context, MediaDatabase::class.java).trackingIdentityChanges()
        .allowMainThreadQueries()
        .build()
    private val dao = database.songDataDao()
    private var builds = 0
    private val index = LibraryAlbumIndex(database.invalidationTracker.createFlow(IDENTITY_GENERATION_TABLE), dao::identityGeneration) {
        builds++
        dao.identityData()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun TestScope.songs() = LocalSongRepository(backgroundScope, dao, index)

    private suspend fun TestScope.library(): List<Song> = songs().loadSongs(SongQuery.All())

    private suspend fun TestScope.importBlue(): List<Song> {
        songs().insert(listOf(createSongData(album = "Blue", track = 1), createSongData(album = "Blue", track = 2)).map { it.toSong() }, MediaProviderType.Shuttle)
        return library()
    }

    /** Asserts [write] leaves the index as it was, built once. */
    private suspend fun unchangedBy(write: suspend () -> Unit) {
        val before = index.albumIndex()
        write()
        index.albumIndex() shouldBeSameInstanceAs before
    }

    /** Asserts [write] rebuilds the index, and returns the new one. */
    private suspend fun rebuiltBy(write: suspend () -> Unit): AlbumIndex {
        val before = index.albumIndex()
        val built = builds
        write()
        return index.albumIndex().also {
            it shouldNotBeSameInstanceAs before
            builds shouldBe built + 1
        }
    }

    @Test
    fun `plays, favourites and exclusion leave the index as it was`() = runTest {
        val (first, second) = importBlue()
        val repository = songs()

        unchangedBy { repository.recordPlayedThrough(first) }
        unchangedBy { repository.setPlaybackPosition(second, 30_000) }
        unchangedBy { repository.setFavourite(listOf(first), favourite = true) }
        unchangedBy { repository.setExcluded(listOf(second), excluded = true) }
        unchangedBy { repository.update(first.copy(playCount = 9, name = "Renamed")) }
        builds shouldBe 1
    }

    @Test
    fun `an import, a tag edit and a delete each rebuild it`() = runTest {
        val (first, second) = importBlue()
        val repository = songs()
        val joni = AlbumArtistGroupKey("artist")

        rebuiltBy { repository.insert(listOf(createSongData(album = "Hejira").toSong()), MediaProviderType.Shuttle) }
            .songIds(AlbumGroupKey("hejira", joni)).size shouldBe 1
        rebuiltBy { repository.update(first.copy(album = "Court and Spark")) }
            .songIds(AlbumGroupKey("court and spark", joni)) shouldBe listOf(first.id)
        rebuiltBy { repository.remove(second) }
            .songIds(AlbumGroupKey("blue", joni)) shouldBe emptyList()
    }

    @Test
    fun `its updates follow identity changes, not plays`() = runTest {
        val (first) = importBlue()
        val current = index.updates.first()

        songs().recordPlayedThrough(first)
        index.updates.first() shouldBeSameInstanceAs current

        songs().update(first.copy(album = "Hejira"))
        index.updates.first().songIds(AlbumGroupKey("hejira", AlbumArtistGroupKey("artist"))) shouldBe listOf(first.id)
    }

    @Test
    fun `a database opened without the triggers rebuilds it on every read rather than going stale`() = runTest {
        val bare = Room.inMemoryDatabaseBuilder(context, MediaDatabase::class.java).allowMainThreadQueries().build()
        val bareIndex = bare.libraryAlbumIndex()
        bare.songDataDao().insert(listOf(createSongData(album = "Blue")))
        bareIndex.albumIndex().identities.size shouldBe 1

        bare.songDataDao().insert(listOf(createSongData(album = "Hejira")))

        bareIndex.albumIndex().identities.size shouldBe 2
        bare.close()
    }
}
