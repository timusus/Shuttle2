package com.simplecityapps.imageloading.coil.source

import com.simplecityapps.mediaprovider.RemoteArtworkProvider
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.SharedPreferencesKeyValueStore
import com.simplecityapps.shuttle.settings.ArtworkSettings
import com.simplecityapps.shuttle.settings.SettingsStore
import com.simplecityapps.shuttle.settings.defaultSharedPreferences
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Server songs without an image of their own show their album's cover (#1048). */
@RunWith(RobolectricTestRunner::class)
class MediaServerSongArtworkSourceTest {
    private val artworkSettings = ArtworkSettings(SettingsStore(SharedPreferencesKeyValueStore(RuntimeEnvironment.getApplication().defaultSharedPreferences().apply { edit().clear().commit() })))

    // Answers with the id of the song it was asked about, so a test sees which song the url was found through
    private val provider = object : RemoteArtworkProvider {
        override fun handles(scheme: String?) = true

        override suspend fun getAlbumArtworkUrl(song: Song): String? = "cover-of-${song.id}"

        override suspend fun getArtistArtworkUrl(
            song: Song,
            serverArtistId: String
        ): String? = null
    }

    @Test
    fun `a song without an image asks through a sibling that has one`() = runBlocking<Unit> {
        val bare = song(1, artworkVersion = null)
        val sibling = song(2, artworkVersion = "tag")

        MediaServerSongArtworkSource(artworkSettings, FakeSongRepository(listOf(bare, sibling)), provider).url(bare) shouldBe "cover-of-2"
    }

    @Test
    fun `a song with an image asks about itself`() = runBlocking<Unit> {
        val own = song(1, artworkVersion = "tag")
        val sibling = song(2, artworkVersion = "other")

        MediaServerSongArtworkSource(artworkSettings, FakeSongRepository(listOf(sibling, own)), provider).url(own) shouldBe "cover-of-1"
    }

    @Test
    fun `an album where no song has an image asks about the song itself`() = runBlocking<Unit> {
        val bare = song(1, artworkVersion = null)

        MediaServerSongArtworkSource(artworkSettings, FakeSongRepository(listOf(bare, song(2, artworkVersion = null))), provider).url(bare) shouldBe "cover-of-1"
    }

    private fun song(
        id: Long,
        artworkVersion: String?
    ) = Song(
        id = id,
        name = "Song $id",
        albumArtist = "Artist",
        artists = listOf("Artist"),
        album = "Album",
        track = 1,
        disc = 1,
        duration = 180_000,
        date = null,
        genres = emptyList(),
        path = "emby://item/$id",
        size = 0,
        mimeType = "audio/mpeg",
        lastModified = null,
        lastPlayed = null,
        lastCompleted = null,
        playCount = 0,
        playbackPosition = 0,
        blacklisted = false,
        mediaProvider = MediaProviderType.Emby,
        lyrics = null,
        grouping = null,
        bitRate = null,
        bitDepth = null,
        sampleRate = null,
        channelCount = null,
        artworkVersion = artworkVersion
    )
}
