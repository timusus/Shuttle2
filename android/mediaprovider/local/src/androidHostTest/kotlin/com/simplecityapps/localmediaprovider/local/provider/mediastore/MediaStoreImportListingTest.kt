package com.simplecityapps.localmediaprovider.local.provider.mediastore

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simplecityapps.localmediaprovider.local.provider.taglib.FakeMediaStore
import com.simplecityapps.localmediaprovider.local.provider.taglib.MediaStoreAudioFile
import com.simplecityapps.localmediaprovider.local.provider.taglib.MediaStoreAudioLister
import com.simplecityapps.localmediaprovider.local.provider.taglib.MediaStoreAudioRow
import com.simplecityapps.localmediaprovider.local.provider.testTagReadGuard
import com.simplecityapps.mediaprovider.FlowEvent
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.time.Instant
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/** An import lists MediaStore once: the remap's listing is the one findSongs takes. */
@RunWith(AndroidJUnit4::class)
class MediaStoreImportListingTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val source = FakeMediaStore().apply { put(MediaStoreAudioRow(file, generation = 0)) }
    private val preferences = GeneralPreferenceManager(InMemoryKeyValueStore()).apply { setSongTagsVersion(MediaProviderType.MediaStore.name, MediaImporter.SONG_TAGS_VERSION) }

    // Without a store the lister reads MediaStore whole on every list, so each list is one whole read
    private val provider = MediaStoreMediaProvider(context, { _, _ -> null }, preferences, testTagReadGuard(), MediaStoreAudioLister(source, store = null, incremental = false))

    @Test
    fun `a routine import lists MediaStore once`() {
        val songs = listOf(song())
        runBlocking { provider.remapLegacySongs(songs, thorough = false) }

        runBlocking { provider.findSongs(songs).last() }.shouldBeInstanceOf<FlowEvent.Success<List<Song>>>().result.map { it.path } shouldBe listOf(file.path)

        source.wholeReads shouldBe 1
    }

    @Test
    fun `a thorough import lists MediaStore once`() {
        val songs = listOf(song())
        runBlocking { provider.remapLegacySongs(songs, thorough = true) }

        runBlocking { provider.findSongsThoroughly(songs).last() }.shouldBeInstanceOf<FlowEvent.Success<List<Song>>>()

        source.wholeReads shouldBe 1
    }

    @Test
    fun `the next import lists MediaStore again`() {
        val songs = listOf(song())
        runBlocking {
            provider.remapLegacySongs(songs, thorough = false)
            provider.findSongs(songs).last()
            provider.findSongs(songs).last()
        }

        source.wholeReads shouldBe 2
    }

    private val file get() = MediaStoreAudioFile(id = 1, path = "/storage/emulated/0/Music/a.mp3", displayName = "a.mp3", size = 1_024, lastModified = 1_700_000_000_000, mimeType = "audio/mpeg", duration = 180_000)

    private fun song() = Song(
        id = 1,
        name = "Song",
        albumArtist = null,
        artists = emptyList(),
        album = null,
        track = null,
        disc = null,
        duration = 180_000,
        date = null,
        genres = emptyList(),
        path = file.path,
        size = file.size,
        mimeType = "audio/mpeg",
        lastModified = Instant.fromEpochMilliseconds(file.lastModified),
        lastPlayed = null,
        lastCompleted = null,
        playCount = 0,
        playbackPosition = 0,
        blacklisted = false,
        mediaProvider = MediaProviderType.MediaStore,
        lyrics = null,
        grouping = null,
        bitRate = null,
        bitDepth = null,
        sampleRate = null,
        channelCount = null
    )
}
