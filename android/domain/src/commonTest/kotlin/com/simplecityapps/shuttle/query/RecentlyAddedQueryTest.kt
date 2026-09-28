package com.simplecityapps.shuttle.query

import com.simplecityapps.mediaprovider.repository.songs.comparator
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.smartplaylist.song
import io.kotest.matchers.collections.shouldContainExactly
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

class RecentlyAddedQueryTest {
    private val now = Clock.System.now()
    private val query = SongQuery.RecentlyAdded(days = 14)

    private fun List<Song>.recentlyAdded() = filter(query.predicate).sortedWith(query.sortOrder.comparator)

    @Test
    fun `keeps songs added within the window - most recently added first`() {
        val older = song(id = 1, name = "Older", dateAdded = now - 10.days)
        val newest = song(id = 2, name = "Newest", dateAdded = now - 1.days)
        val middle = song(id = 3, name = "Middle", dateAdded = now - 5.days)
        val outside = song(id = 4, name = "Outside", dateAdded = now - 30.days)
        val undated = song(id = 5, name = "Undated", dateAdded = null)

        listOf(older, newest, middle, outside, undated).recentlyAdded() shouldContainExactly listOf(newest, middle, older)
    }

    @Test
    fun `a song whose tags were just edited but was added long ago isn't recently added`() {
        val tagEdited = song(id = 1, dateAdded = now - 300.days, lastModified = now - 1.days)
        val added = song(id = 2, dateAdded = now - 2.days, lastModified = now - 200.days)

        listOf(tagEdited, added).recentlyAdded() shouldContainExactly listOf(added)
    }
}
