package com.simplecityapps.localmediaprovider.local.provider.taglib

import com.simplecityapps.mediaprovider.SongPathRemap
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlin.time.Instant
import org.junit.Test

class LegacySafSongsTest {
    private val legacySafSongs = LegacySafSongs(primaryStoragePath = "/storage/emulated/0")

    @Test
    fun `a primary storage song moves to the file at the same path`() {
        val song = song(id = 1, path = treeDocument("primary:Music", "primary:Music/Björk/Jóga.mp3"))
        val file = file(id = 10, path = "/storage/emulated/0/Music/Björk/Jóga.mp3")

        legacySafSongs.remaps(listOf(song), listOf(file, file(id = 11, path = "/storage/emulated/0/Music/Other.mp3"))) shouldBe
            listOf(SongPathRemap(songId = 1, path = file.path))
    }

    @Test
    fun `an SD card song moves to the file on the volume its document id names, whatever the case`() {
        val song = song(id = 1, path = treeDocument("04B9-1208:Music", "04B9-1208:Music/Album/01 Song.flac"))
        val primaryCopy = file(id = 10, path = "/storage/emulated/0/Music/Album/01 Song.flac")
        val sdCardFile = file(id = 11, path = "/storage/04b9-1208/Music/album/01 song.flac")

        legacySafSongs.remaps(listOf(song), listOf(primaryCopy, sdCardFile)) shouldBe listOf(SongPathRemap(songId = 1, path = sdCardFile.path))
    }

    @Test
    fun `a song in the home root moves to the primary volume's Documents folder`() {
        val song = song(id = 1, path = treeDocument("home:", "home:Audiobook.m4a"))
        val file = file(id = 10, path = "/storage/emulated/0/Documents/Audiobook.m4a")

        legacySafSongs.remaps(listOf(song), listOf(file)) shouldBe listOf(SongPathRemap(songId = 1, path = file.path))
    }

    @Test
    fun `when the old volume is gone, size, date and duration pick between copies at the same path`() {
        // The SD card was reformatted, so its id changed, and a copy of the album also sits on primary storage
        val song = song(id = 1, path = treeDocument("1111-2222:Music", "1111-2222:Music/Song.mp3"), size = 5_000, lastModified = 1_700_000_000_500, duration = 180_000)
        val primaryCopy = file(id = 10, path = "/storage/emulated/0/Music/Song.mp3", size = 4_000, lastModified = 1_700_000_000_000, duration = 180_000)
        val newSdCard = file(id = 11, path = "/storage/3333-4444/Music/Song.mp3", size = 5_000, lastModified = 1_700_000_000_000, duration = 180_900)

        legacySafSongs.remaps(listOf(song), listOf(primaryCopy, newSdCard)) shouldBe listOf(SongPathRemap(songId = 1, path = newSdCard.path))
    }

    @Test
    fun `an ambiguous path with no file of the same size, date and duration isn't matched`() {
        val song = song(id = 1, path = treeDocument("1111-2222:Music", "1111-2222:Music/Song.mp3"), size = 5_000)
        val otherVolume = file(id = 10, path = "/storage/emulated/0/Music/Song.mp3", size = 4_000)

        legacySafSongs.remaps(listOf(song), listOf(otherVolume)).shouldBeEmpty()
    }

    @Test
    fun `an ambiguous path matching two identical copies isn't matched`() {
        val song = song(id = 1, path = treeDocument("1111-2222:Music", "1111-2222:Music/Song.mp3"))
        val copies = listOf(file(id = 10, path = "/storage/emulated/0/Music/Song.mp3"), file(id = 11, path = "/storage/3333-4444/Music/Song.mp3"))

        legacySafSongs.remaps(listOf(song), copies).shouldBeEmpty()
    }

    @Test
    fun `a song whose file is gone isn't matched, even if a file elsewhere has the same size and date`() {
        val song = song(id = 1, path = treeDocument("primary:Music", "primary:Music/Deleted.mp3"))

        legacySafSongs.remaps(listOf(song), listOf(file(id = 10, path = "/storage/emulated/0/Music/Renamed.mp3"))).shouldBeEmpty()
    }

    @Test
    fun `songs already keyed by file path are left alone`() {
        val migrated = song(id = 1, path = "/storage/emulated/0/Music/Song.mp3")

        legacySafSongs.remaps(listOf(migrated), listOf(file(id = 10, path = migrated.path))).shouldBeEmpty()
    }

    @Test
    fun `when overlapping folder grants stored a file twice, the most played row keeps it and the rest are duplicates`() {
        val fromMusic = song(id = 1, path = treeDocument("primary:Music", "primary:Music/Album/Song.mp3"), playCount = 2)
        val fromAlbum = song(id = 2, path = treeDocument("primary:Music/Album", "primary:Music/Album/Song.mp3"), playCount = 7)
        val file = file(id = 10, path = "/storage/emulated/0/Music/Album/Song.mp3")

        legacySafSongs.remaps(listOf(fromMusic, fromAlbum), listOf(file)) shouldBe listOf(SongPathRemap(songId = 2, path = file.path, duplicateIds = listOf(1)))
    }

    @Test
    fun `a Downloads document with a raw path moves to that path`() {
        val song = song(id = 1, path = "content://com.android.providers.downloads.documents/document/raw%3A%2Fstorage%2Femulated%2F0%2FDownload%2FSong.mp3")
        val file = file(id = 10, path = "/storage/emulated/0/Download/Song.mp3")

        legacySafSongs.remaps(listOf(song), listOf(file)) shouldBe listOf(SongPathRemap(songId = 1, path = file.path))
    }

    @Test
    fun `a Downloads document with a MediaStore id moves to that row only if it's still the same file`() {
        val song = song(id = 1, path = "content://com.android.providers.downloads.documents/document/msf%3A10")
        val sameFile = file(id = 10, path = "/storage/emulated/0/Download/Song.mp3")

        legacySafSongs.remaps(listOf(song), listOf(sameFile)) shouldBe listOf(SongPathRemap(songId = 1, path = sameFile.path))
        legacySafSongs.remaps(listOf(song), listOf(sameFile.copy(size = 1))).shouldBeEmpty()
    }

    @Test
    fun `a document from another provider matches the one file with its size, date and duration`() {
        val song = song(id = 1, path = "content://com.example.cloud/tree/root/document/abc123", size = 9_000)
        val files = listOf(file(id = 10, path = "/storage/emulated/0/Music/A.mp3", size = 9_000), file(id = 11, path = "/storage/emulated/0/Music/B.mp3", size = 1_000))

        legacySafSongs.remaps(listOf(song), files) shouldBe listOf(SongPathRemap(songId = 1, path = "/storage/emulated/0/Music/A.mp3"))
    }

    @Test
    fun `decodes each kind of document id`() {
        legacyLocation(treeDocument("primary:Music", "primary:Music/a+b%.mp3")) shouldBe LegacyLocation.OnVolume("primary", "Music/a+b%.mp3")
        legacyLocation(treeDocument("04B9-1208:", "04B9-1208:Song.mp3")) shouldBe LegacyLocation.OnVolume("04B9-1208", "Song.mp3")
        legacyLocation("content://com.android.providers.downloads.documents/document/42") shouldBe LegacyLocation.Unknown
        legacyLocation("/storage/emulated/0/Music/Song.mp3") shouldBe null
        legacyLocation("content://media/external/audio/media/5") shouldBe null
    }

    @Test
    fun `remaps every matched song in one pass`() {
        val songs = (1L..3L).map { id -> song(id = id, path = treeDocument("primary:Music", "primary:Music/$id.mp3")) }
        val files = (1L..3L).map { id -> file(id = id + 10, path = "/storage/emulated/0/Music/$id.mp3") }

        legacySafSongs.remaps(songs, files).map { remap -> remap.songId to remap.path } shouldContainExactlyInAnyOrder
            (1L..3L).map { id -> id to "/storage/emulated/0/Music/$id.mp3" }
    }

    // Encodes the way android.net.Uri does for DocumentsContract.buildDocumentUriUsingTree
    private fun treeDocument(
        treeId: String,
        documentId: String
    ): String = "content://com.android.externalstorage.documents/tree/${encode(treeId)}/document/${encode(documentId)}"

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun file(
        id: Long,
        path: String,
        size: Long = 5_000,
        lastModified: Long = 1_700_000_000_000,
        duration: Long? = 180_000
    ) = MediaStoreAudioFile(id = id, path = path, displayName = path.substringAfterLast('/'), size = size, lastModified = lastModified, mimeType = "audio/mpeg", duration = duration)

    private fun song(
        id: Long,
        path: String,
        size: Long = 5_000,
        lastModified: Long = 1_700_000_000_000,
        duration: Int = 180_000,
        playCount: Int = 0
    ) = Song(
        id = id,
        name = "Song $id",
        albumArtist = null,
        artists = emptyList(),
        album = null,
        track = null,
        disc = null,
        duration = duration,
        date = null,
        genres = emptyList(),
        path = path,
        size = size,
        mimeType = "audio/mpeg",
        lastModified = Instant.fromEpochMilliseconds(lastModified),
        lastPlayed = null,
        lastCompleted = null,
        playCount = playCount,
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
}
