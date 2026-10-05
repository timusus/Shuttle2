package com.simplecityapps.localmediaprovider.local.data.room.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.localmediaprovider.local.data.room.entity.SongData
import com.simplecityapps.localmediaprovider.local.repository.createSongData
import com.simplecityapps.shuttle.model.MediaProviderType
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** [SongDataDao.countVisible] and [SongDataDao.countVisibleByProvider] count what the library shows: not excluded, not too short. */
@RunWith(AndroidJUnit4::class)
class SongDataDaoCountTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database = Room.inMemoryDatabaseBuilder(context, MediaDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val dao = database.songDataDao()

    @After
    fun tearDown() {
        database.close()
    }

    private fun song(
        provider: MediaProviderType,
        track: Int,
        durationMs: Int = 180_000,
        excluded: Boolean = false
    ): SongData = createSongData(album = "Album", track = track).copy(
        mediaProvider = provider,
        duration = durationMs,
        excluded = excluded,
        path = "/${provider.name}/$track.mp3"
    )

    private suspend fun seed() {
        dao.insert(
            listOf(
                song(MediaProviderType.Shuttle, 1),
                song(MediaProviderType.Shuttle, 2, excluded = true),
                song(MediaProviderType.Shuttle, 3, durationMs = 5_000),
                song(MediaProviderType.Shuttle, 4, durationMs = 0),
                song(MediaProviderType.Plex, 5),
                song(MediaProviderType.Plex, 6)
            )
        )
    }

    @Test
    fun `the count skips excluded songs and songs under the minimum length`() = runTest {
        seed()
        dao.countVisible(minDurationMs = 30_000).first() shouldBe 4
    }

    @Test
    fun `the per-provider counts apply the same filters`() = runTest {
        seed()
        dao.countVisibleByProvider(minDurationMs = 30_000).first().associate { it.mediaProvider to it.count } shouldBe
            mapOf(MediaProviderType.Shuttle to 2, MediaProviderType.Plex to 2)
    }

    @Test
    fun `a provider whose songs are all hidden has no count`() = runTest {
        dao.insert(listOf(song(MediaProviderType.Plex, 1, excluded = true)))
        dao.countVisibleByProvider(minDurationMs = 0).first() shouldBe emptyList()
    }
}
