package com.simplecityapps.playback.androidauto

import com.simplecityapps.playback.chromecast.FakeSongRepository
import com.simplecityapps.playback.fakes.FakeAlbumArtistRepository
import com.simplecityapps.playback.fakes.FakeAlbumRepository
import com.simplecityapps.playback.fakes.FakePlaylistRepository
import com.simplecityapps.playback.fakes.testSong
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.AlbumIndex
import com.simplecityapps.shuttle.model.AlbumIndexProvider
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.identityTags
import com.simplecityapps.shuttle.model.withAlbumIdentities
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Test
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Android Auto's album and artist media ids under the album identity rule (#637), and the ids it kept from before. */
@RunWith(RobolectricTestRunner::class)
class MediaIdHelperAlbumKeyTest {
    // An untagged album of several artists: before #637 each track artist was its own album
    private val songs = listOf(
        testSong(1, album = "Drive OST", path = "/music/Drive/1.mp3").copy(artists = listOf("Kavinsky")),
        testSong(2, album = "Drive OST", path = "/music/Drive/2.mp3").copy(artists = listOf("College"))
    ).withAlbumIdentities()

    private val drive = AlbumGroupKey("drive ost", AlbumArtistGroupKey("various artists"), "dir:/music/Drive")

    private val album = Album(
        name = "Drive OST",
        albumArtist = "Various Artists",
        artists = listOf("Kavinsky", "College"),
        songCount = 2,
        duration = 0,
        year = null,
        playCount = 0,
        lastSongPlayed = null,
        lastSongCompleted = null,
        groupKey = drive,
        mediaProviders = listOf(MediaProviderType.Shuttle)
    )

    private val helper = MediaIdHelper(ApplicationProvider.getApplicationContext(), FakePlaylistRepository(), FakeAlbumArtistRepository(), FakeAlbumRepository(listOf(album)), FakeSongRepository(songs), AlbumIndexProvider { AlbumIndex(songs.map { it.identityTags }) })

    @Test
    fun `an album's id holds its identity, and names its songs`(): Unit = runBlocking {
        val item = helper.getChildren("media:/album_root/").single()

        item.mediaId shouldBe "media:/album_root/artist/various artists/album/drive ost/album_id/dir%3A%2Fmusic%2FDrive/songs/"
        helper.getChildren(item.mediaId).map { it.mediaId } shouldBe listOf("${item.mediaId}1", "${item.mediaId}2")
    }

    @Test
    fun `a song id kept from before the rule plays the album its song belongs to now`(): Unit = runBlocking {
        val queue = helper.getPlayQueue("media:/album_root/artist/college/album/drive ost/songs/2")!!

        queue.songs.map { it.id } shouldBe listOf(1L, 2L)
        queue.position shouldBe 1
    }

    @Test
    fun `an album artist id kept from before the rule lists the albums of the artist it moved to`(): Unit = runBlocking {
        helper.getChildren("media:/artist_root/artist/kavinsky/albums/").map { it.mediaMetadata.title.toString() } shouldBe listOf("Drive OST")
    }
}
