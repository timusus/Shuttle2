package com.simplecityapps.localmediaprovider.local.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.localmediaprovider.local.data.room.entity.PlayEventData
import com.simplecityapps.localmediaprovider.local.data.room.entity.ResumePointData
import com.simplecityapps.mediaprovider.SongPathRemap
import com.simplecityapps.shuttle.model.MediaProviderType
import io.kotest.matchers.shouldBe
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SongPathRemapHistoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database = Room.inMemoryDatabaseBuilder(context, MediaDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `a remap carries the song's play events and resume points to its new path`() = runTest {
        val old = "content://tree/document/primary%3AMusic%2FA.mp3"
        val new = "/storage/emulated/0/Music/A.mp3"
        val songId = database.songDataDao().insert(listOf(createSongData(album = "A").copy(path = old))).single()
        val provider = MediaProviderType.Shuttle
        val now = Instant.fromEpochMilliseconds(0)
        database.playEventDao().insert(PlayEventData(provider, old, now, 1, true, 8, 3, "album", "a"))
        database.playEventDao().insert(PlayEventData(MediaProviderType.Jellyfin, old, now, 1, true, 8, 3, "album", "a"))
        database.resumePointDao().upsert(ResumePointData("album", "a", provider, old, 5_000, 0, 1, false, false, now))

        database.songDataDao().remapPaths(listOf(SongPathRemap(songId, new)), provider) { _, _ -> } shouldBe listOf(SongPathRemap(songId, new))

        database.query("SELECT mediaProvider, songPath FROM play_events ORDER BY id", null).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(1)) }
        } shouldBe listOf(new, old)
        database.resumePointDao().getByContextIds(listOf("a")).single().point.songPath shouldBe new
    }
}
