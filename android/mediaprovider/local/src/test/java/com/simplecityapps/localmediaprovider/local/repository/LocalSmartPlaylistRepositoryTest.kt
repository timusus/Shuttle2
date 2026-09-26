package com.simplecityapps.localmediaprovider.local.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.localmediaprovider.local.data.room.entity.SmartPlaylistData
import com.simplecityapps.shuttle.model.UserSmartPlaylist
import com.simplecityapps.shuttle.smartplaylist.DateCondition
import com.simplecityapps.shuttle.smartplaylist.DateField
import com.simplecityapps.shuttle.smartplaylist.Limit
import com.simplecityapps.shuttle.smartplaylist.NumberCondition
import com.simplecityapps.shuttle.smartplaylist.NumberField
import com.simplecityapps.shuttle.smartplaylist.Rule
import com.simplecityapps.shuttle.smartplaylist.SmartRules
import com.simplecityapps.shuttle.smartplaylist.SmartSort
import io.kotest.matchers.shouldBe
import java.util.Date
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalSmartPlaylistRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database = Room.inMemoryDatabaseBuilder(context, MediaDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val now = Instant.parse("2026-09-26T10:15:30.123456Z")
    private val repository = LocalSmartPlaylistRepository(
        database.smartPlaylistDao(),
        object : Clock {
            override fun now(): Instant = now
        }
    )

    private val neverPlayed = SmartRules(rules = listOf(Rule.Number(NumberField.PlayCount, NumberCondition.Is(0))))
    private val addedThisMonth = SmartRules(
        rules = listOf(Rule.Date(DateField.DateAdded, DateCondition.InTheLast(30))),
        sort = SmartSort.DateAdded,
        descending = true,
        limit = Limit.Songs(100)
    )

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `a created smart playlist reads back with its rules`() = runTest {
        val created = repository.create("Never played", neverPlayed)

        created shouldBe UserSmartPlaylist(created.id, "Never played", neverPlayed, Instant.parse("2026-09-26T10:15:30.123Z"))
        repository.getSmartPlaylist(created.id).first() shouldBe created
        repository.getSmartPlaylists().first() shouldBe listOf(created)
    }

    @Test
    fun `smart playlists come by name, ignoring case`() = runTest {
        repository.create("b", neverPlayed)
        repository.create("C", neverPlayed)
        repository.create("A", addedThisMonth)

        repository.getSmartPlaylists().first().map(UserSmartPlaylist::name) shouldBe listOf("A", "b", "C")
    }

    @Test
    fun `an update saves the name and rules`() = runTest {
        val created = repository.create("Never played", neverPlayed)

        repository.update(created.copy(name = "Added this month", rules = addedThisMonth))

        repository.getSmartPlaylist(created.id).first() shouldBe created.copy(name = "Added this month", rules = addedThisMonth)
    }

    @Test
    fun `a deleted smart playlist is gone`() = runTest {
        val kept = repository.create("Kept", neverPlayed)
        val deleted = repository.create("Deleted", neverPlayed)

        repository.delete(deleted.id)

        repository.getSmartPlaylist(deleted.id).first() shouldBe null
        repository.getSmartPlaylists().first() shouldBe listOf(kept)
    }

    @Test
    fun `a smart playlist whose rules don't decode is left out`() = runTest {
        val kept = repository.create("Kept", neverPlayed)
        val unreadable = database.smartPlaylistDao().insert(
            SmartPlaylistData(name = "From a newer version", rulesJson = """{"version":2,"rules":{"rules":[{"type":"rating","stars":5}]}}""", createdAt = Date(0))
        )

        repository.getSmartPlaylists().first() shouldBe listOf(kept)
        repository.getSmartPlaylist(unreadable).first() shouldBe null
    }
}
