package com.simplecityapps.imageloading.coil.source

import androidx.test.core.app.ApplicationProvider
import com.simplecityapps.mediaprovider.TagReadFile
import com.simplecityapps.mediaprovider.TagReadGuard
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EmbeddedArtworkSourceTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val preferences = GeneralPreferenceManager(InMemoryKeyValueStore())
    private var reads = 0
    private val tagReader = ArtworkTagReader { _, _ -> byteArrayOf(1, 2, 3).also { reads++ } }
    private val guard = TagReadGuard(Files.createTempDirectory("tag-reads").toFile(), preferences, { null })
    private val source = EmbeddedSongArtworkSource(context, tagReader, guard)

    private fun songAt(file: File) = song(file.path, size = file.length())

    @Test
    fun `a file TagLib hasn't crashed on has its artwork read`() = runBlocking<Unit> {
        val file = folder.newFile("a.mp3").apply { writeText("audio") }

        source.open(songAt(file)).shouldNotBeNull()
    }

    @Test
    fun `a quarantined file returns no artwork without reaching TagLib`() = runBlocking<Unit> {
        val file = folder.newFile("bad.mp3").apply { writeText("audio") }
        val song = songAt(file)
        preferences.quarantineTagRead(TagReadFile(song.path, song.size, 0L).key)

        source.open(song).shouldBeNull()
        reads shouldBe 0
    }

    @Test
    fun `a file replaced since it was quarantined is read again`() = runBlocking<Unit> {
        val file = folder.newFile("fixed.mp3").apply { writeText("audio") }
        val song = songAt(file)
        preferences.quarantineTagRead(TagReadFile(song.path, size = song.size + 1, lastModified = 0L).key)

        source.open(song).shouldNotBeNull()
    }

    private fun song(
        path: String,
        size: Long
    ) = Song(
        id = 0,
        name = "Song",
        albumArtist = "Artist",
        artists = listOf("Artist"),
        album = "Album",
        track = 1,
        disc = 1,
        duration = 180_000,
        date = null,
        genres = emptyList(),
        path = path,
        size = size,
        mimeType = "audio/mpeg",
        lastModified = null,
        lastPlayed = null,
        lastCompleted = null,
        playCount = 0,
        playbackPosition = 0,
        blacklisted = false,
        externalId = null,
        mediaProvider = MediaProviderType.Shuttle,
        lyrics = null,
        grouping = null,
        bitRate = null,
        bitDepth = null,
        sampleRate = null,
        channelCount = null
    )
}
