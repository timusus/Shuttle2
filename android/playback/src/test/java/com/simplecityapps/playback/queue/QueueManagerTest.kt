package com.simplecityapps.playback.queue

import android.content.SharedPreferences
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

/**
 * Add to Queue and Play Next must append/insert songs in selection order, whether shuffle is on
 * or off (#248) - the shuffle queue used to re-shuffle newly added items instead of preserving
 * the order they were selected in.
 */
class QueueManagerTest {
    private val queueWatcher = QueueWatcher()
    private val queueManager = QueueManager(queueWatcher, GeneralPreferenceManager(FakeSharedPreferences()))

    @Before
    fun setUp() {
        runBlocking { queueManager.setQueue(listOf(createSong(1), createSong(2), createSong(3))) }
    }

    @Test
    fun `addToQueue appends songs in selection order with shuffle off`() {
        queueManager.addToQueue(listOf(createSong(4), createSong(5)))

        queueManager.getQueue(QueueManager.ShuffleMode.Off).map { it.song.id } shouldBe listOf(1L, 2L, 3L, 4L, 5L)
    }

    @Test
    fun `addToQueue appends songs in selection order with shuffle on`() {
        runBlocking { queueManager.setShuffleMode(QueueManager.ShuffleMode.On, reshuffle = true) }
        val queueBeforeAdd = queueManager.getQueue(QueueManager.ShuffleMode.On).map { it.song.id }

        // 10 songs added in a fixed order: a shuffle matching this order by chance is ~1 in 10!.
        val addedIds = (100L..109L).toList()
        queueManager.addToQueue(addedIds.map { createSong(it) })

        val queueAfterAdd = queueManager.getQueue(QueueManager.ShuffleMode.On).map { it.song.id }
        queueAfterAdd shouldBe queueBeforeAdd + addedIds
    }

    @Test
    fun `addToNext inserts songs in selection order right after the current item with shuffle off`() {
        queueManager.addToNext(listOf(createSong(4), createSong(5)))

        queueManager.getQueue(QueueManager.ShuffleMode.Off).map { it.song.id } shouldBe listOf(1L, 4L, 5L, 2L, 3L)
    }

    @Test
    fun `addToNext inserts songs in selection order right after the current item with shuffle on`() {
        runBlocking { queueManager.setShuffleMode(QueueManager.ShuffleMode.On, reshuffle = true) }
        // The current item sits at the head of a freshly generated shuffle queue.
        val currentId = queueManager.getCurrentItem()!!.song.id
        queueManager.getQueue(QueueManager.ShuffleMode.On).first().song.id shouldBe currentId

        queueManager.addToNext(listOf(createSong(4), createSong(5)))

        val shuffleQueue = queueManager.getQueue(QueueManager.ShuffleMode.On).map { it.song.id }
        shuffleQueue.take(3) shouldBe listOf(currentId, 4L, 5L)
    }

    @Test
    fun `addToNext inserts relative to the current item's own position in each list when shuffle is on`() {
        // A larger queue so skipping forward is virtually certain to land on an item whose
        // shuffle-list index and base-list index diverge (chance of never diverging is ~1 in 10!).
        val songs = (200L..209L).map { createSong(it) }
        runBlocking { queueManager.setQueue(songs) }
        runBlocking { queueManager.setShuffleMode(QueueManager.ShuffleMode.On, reshuffle = true) }

        var baseIndexOfCurrent: Int
        var shuffleIndexOfCurrent: Int
        do {
            queueManager.skipToNext()
            val current = queueManager.getCurrentItem()!!
            baseIndexOfCurrent = queueManager.getQueue(QueueManager.ShuffleMode.Off).indexOfFirst { it.song.id == current.song.id }
            shuffleIndexOfCurrent = queueManager.getQueue(QueueManager.ShuffleMode.On).indexOfFirst { it.song.id == current.song.id }
        } while (baseIndexOfCurrent == shuffleIndexOfCurrent && shuffleIndexOfCurrent < songs.size - 1)
        shuffleIndexOfCurrent shouldNotBe baseIndexOfCurrent

        queueManager.addToNext(listOf(createSong(4), createSong(5)))

        val baseQueue = queueManager.getQueue(QueueManager.ShuffleMode.Off).map { it.song.id }
        baseQueue.subList(baseIndexOfCurrent + 1, baseIndexOfCurrent + 3) shouldBe listOf(4L, 5L)

        val shuffleQueue = queueManager.getQueue(QueueManager.ShuffleMode.On).map { it.song.id }
        shuffleQueue.subList(shuffleIndexOfCurrent + 1, shuffleIndexOfCurrent + 3) shouldBe listOf(4L, 5L)
    }

    private fun createSong(id: Long) = Song(
        id = id,
        name = "Song $id",
        albumArtist = null,
        artists = emptyList(),
        album = null,
        track = null,
        disc = null,
        duration = 180_000,
        date = null,
        genres = emptyList(),
        path = "/music/song$id.mp3",
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
        channelCount = null
    )

    private class FakeSharedPreferences : SharedPreferences {
        private val values = mutableMapOf<String, Any?>()

        override fun getAll(): Map<String, *> = values

        override fun getString(
            key: String,
            defValue: String?
        ): String? = values[key] as String? ?: defValue

        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(
            key: String,
            defValues: Set<String>?
        ): Set<String>? = values[key] as Set<String>? ?: defValues

        override fun getInt(
            key: String,
            defValue: Int
        ): Int = values[key] as Int? ?: defValue

        override fun getLong(
            key: String,
            defValue: Long
        ): Long = values[key] as Long? ?: defValue

        override fun getFloat(
            key: String,
            defValue: Float
        ): Float = values[key] as Float? ?: defValue

        override fun getBoolean(
            key: String,
            defValue: Boolean
        ): Boolean = values[key] as Boolean? ?: defValue

        override fun contains(key: String): Boolean = key in values

        override fun edit(): SharedPreferences.Editor = Editor()

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {}

        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {}

        private inner class Editor : SharedPreferences.Editor {
            override fun putString(
                key: String,
                value: String?
            ) = apply { values[key] = value }

            override fun putStringSet(
                key: String,
                values: Set<String>?
            ) = apply { this@FakeSharedPreferences.values[key] = values }

            override fun putInt(
                key: String,
                value: Int
            ) = apply { values[key] = value }

            override fun putLong(
                key: String,
                value: Long
            ) = apply { values[key] = value }

            override fun putFloat(
                key: String,
                value: Float
            ) = apply { values[key] = value }

            override fun putBoolean(
                key: String,
                value: Boolean
            ) = apply { values[key] = value }

            override fun remove(key: String) = apply { values.remove(key) }

            override fun clear() = apply { values.clear() }

            override fun commit(): Boolean = true

            override fun apply() {}
        }
    }
}
