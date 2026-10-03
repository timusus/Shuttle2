package com.simplecityapps.shuttle.shared.local

import com.simplecityapps.mediaprovider.FlowEvent
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.screens.sources.FolderKind
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

class IosLocalMediaProviderTest {
    private class FakeLocalFiles : IosLocalFiles {
        var folders = mutableListOf<IosLocalFolder>()
        var files = listOf<IosLocalFileRef>()
        val tags = mutableMapOf<String, IosLocalTags>()
        val reads = mutableListOf<String>()

        override fun folders(): List<IosLocalFolder> = folders

        override fun addFolder(url: String): Boolean {
            if (!url.startsWith("file://")) return false
            folders += IosLocalFolder(id = "f${folders.size}", name = url.substringAfterLast('/'), path = url.removePrefix("file://"), hasAccess = true)
            return true
        }

        override fun removeFolder(id: String) {
            folders.removeAll { it.id == id }
        }

        override fun audioFiles(): List<IosLocalFileRef> = files

        override fun readTags(path: String): IosLocalTags? {
            reads += path
            return tags[path]
        }

        override fun fileUrl(path: String): String? = null
    }

    private val localFiles = FakeLocalFiles()
    private val provider = IosLocalMediaProvider(localFiles)

    private suspend fun findSongs(existing: List<Song> = emptyList()): List<Song> = provider.findSongs(existing).filterIsInstance<FlowEvent.Success<List<Song>>>().first().result

    @Test
    fun aFilesTagsBecomeItsSong() = runTest {
        val file = IosLocalFileRef("s2local://documents/Rock/03 Song.flac", lastModifiedMs = 1_700_000_000_000, size = 1234)
        localFiles.files = listOf(file)
        localFiles.tags[file.path] = tags(title = "Song", artists = listOf("A", "B"), year = 1997, track = 3, disc = 1, durationMs = 201_500)

        val song = findSongs().single()

        song.name shouldBe "Song"
        song.artists shouldBe listOf("A", "B")
        song.artistDisplay shouldBe "A; B"
        song.albumArtist shouldBe "Album Artist"
        song.album shouldBe "Album"
        song.track shouldBe 3
        song.disc shouldBe 1
        song.date shouldBe LocalDate(1997, 1, 1)
        song.duration shouldBe 201_500
        song.genres shouldBe listOf("Rock")
        song.path shouldBe file.path
        song.size shouldBe 1234
        song.lastModified shouldBe Instant.fromEpochMilliseconds(1_700_000_000_000)
        song.mimeType shouldBe "audio/flac"
        song.mediaProvider shouldBe MediaProviderType.Shuttle
        song.replayGainTrack shouldBe -6.5
        song.audioCodec shouldBe "flac"
        song.bitDepth shouldBe 16
        song.sampleRate shouldBe 44_100
        song.artworkVersion shouldBe "1700000000000"
        song.mbTrackId shouldBe "mbid"
    }

    @Test
    fun anUntitledFileIsNamedAfterItself() = runTest {
        val file = IosLocalFileRef("s2local://documents/04 Untitled.mp3", 1, 1)
        localFiles.files = listOf(file)
        localFiles.tags[file.path] = tags(title = null)

        findSongs().single().name shouldBe "04 Untitled"
    }

    @Test
    fun aFileThatIsntAudioIsLeftOut() = runTest {
        localFiles.files = listOf(IosLocalFileRef("s2local://documents/broken.mp3", 1, 1))

        findSongs() shouldBe emptyList()
    }

    @Test
    fun anUnchangedFileKeepsItsSongWithoutBeingReadAgain() = runTest {
        val unchanged = IosLocalFileRef("s2local://documents/a.flac", lastModifiedMs = 10, size = 100)
        val edited = IosLocalFileRef("s2local://documents/b.flac", lastModifiedMs = 20, size = 200)
        localFiles.files = listOf(unchanged, edited)
        localFiles.tags[unchanged.path] = tags(title = "A")
        localFiles.tags[edited.path] = tags(title = "B")
        val existing = findSongs().map { it.copy(id = it.name.hashCode().toLong(), playCount = 3) }
        localFiles.reads.clear()

        localFiles.files = listOf(unchanged, edited.copy(lastModifiedMs = 21))
        localFiles.tags[edited.path] = tags(title = "B, retagged")
        val songs = findSongs(existing)

        localFiles.reads shouldContainExactly listOf(edited.path)
        songs[0] shouldBeSameInstanceAs existing[0]
        songs[1].name shouldBe "B, retagged"
    }

    @Test
    fun aFolderOutOfReachKeepsItsSongs() = runTest {
        localFiles.folders += IosLocalFolder(id = "gone", name = "Gone", path = "/x", hasAccess = false)
        val kept = IosLocalFileRef("s2local://gone/a.flac", 1, 1).let { file -> tags(title = "Kept").toSong(file) }
        val removed = IosLocalFileRef("s2local://documents/b.flac", 1, 1).let { file -> tags(title = "Removed").toSong(file) }

        findSongs(listOf(kept, removed)) shouldBe listOf(kept)
    }

    @Test
    fun pickedFoldersAreExtras() {
        val store = IosScannerFolderStore(localFiles)

        store.add(FolderKind.Exclude, "file:///Music") shouldBe false
        store.add(FolderKind.Extra, "file:///Music") shouldBe true
        val folder = store.folders.value.extras.single()
        folder.name shouldBe "Music"
        folder.uri shouldBe "f0"

        store.remove(FolderKind.Extra, folder)
        store.folders.value.extras shouldBe emptyList()
    }

    private fun tags(
        title: String? = "Title",
        artists: List<String> = listOf("Artist"),
        year: Int? = null,
        track: Int? = null,
        disc: Int? = null,
        durationMs: Long? = 1_000
    ) = IosLocalTags(
        title = title,
        artists = artists,
        artistDisplay = artists.joinToString("; "),
        artistsTag = emptyList(),
        albumArtist = "Album Artist",
        albumArtists = emptyList(),
        album = "Album",
        track = track,
        disc = disc,
        year = year,
        genres = listOf("Rock"),
        replayGainTrack = -6.5,
        replayGainAlbum = null,
        lyrics = null,
        grouping = null,
        compilation = null,
        mbTrackId = "mbid",
        mbAlbumId = null,
        mbReleaseGroupId = null,
        mbArtistIds = emptyList(),
        mbAlbumArtistIds = emptyList(),
        durationMs = durationMs,
        sampleRate = 44_100,
        channelCount = 2,
        bitDepth = 16,
        bitRate = 900,
        codec = "flac"
    )
}
