package com.simplecityapps.shuttle.shared.local

import com.simplecityapps.mediaprovider.FlowEvent
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
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
        var offloaded = listOf<String>()
        var unread = listOf<String>()
        val tags = mutableMapOf<String, IosLocalTags>()
        val reads = mutableListOf<String>()
        val imported = mutableListOf<IosLocalListing>()
        val downloads = mutableListOf<String>()

        override fun folders(): List<IosLocalFolder> = folders

        override fun addFolder(url: String): Boolean {
            if (!url.startsWith("file://")) return false
            folders += IosLocalFolder(id = "f${folders.size}", name = url.substringAfterLast('/'), path = url.removePrefix("file://"), hasAccess = true)
            return true
        }

        override fun removeFolder(id: String) {
            folders.removeAll { it.id == id }
        }

        override fun audioFiles(): IosLocalListing = IosLocalListing(
            files = files,
            offloaded = offloaded,
            folders = listOf(IosLocalFiles.DOCUMENTS) + folders.map { it.id },
            unread = unread + folders.filterNot { it.hasAccess }.map { it.id },
            fingerprint = "${files.hashCode()}"
        )

        override fun imported(listing: IosLocalListing) {
            imported += listing
        }

        override fun download(path: String) {
            downloads += path
        }

        override fun readTags(path: String): IosLocalTags? {
            reads += path
            return tags[path]
        }

        override fun fileUrl(path: String): String? = null
    }

    private val localFiles = FakeLocalFiles()
    private val preferences = GeneralPreferenceManager(InMemoryKeyValueStore()).apply { setSongTagsVersion(MediaProviderType.Shuttle.name, MediaImporter.SONG_TAGS_VERSION) }
    private val provider = IosLocalMediaProvider(localFiles, preferences)

    private suspend fun findSongs(existing: List<Song> = emptyList()): List<Song> = provider.findSongs(existing).filterIsInstance<FlowEvent.Success<List<Song>>>().first().result

    private fun song(path: String, title: String = "Title") = tags(title = title).toSong(IosLocalFileRef(path, 1, 1))

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
        song.mbTrackId shouldBe "11111111-2222-3333-4444-555555555555"
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
    fun songsPredatingThisBuildsTagsAreReadAgain() = runTest {
        val file = IosLocalFileRef("s2local://documents/a.flac", lastModifiedMs = 10, size = 100)
        localFiles.files = listOf(file)
        localFiles.tags[file.path] = tags(title = "A")
        val existing = findSongs()
        localFiles.reads.clear()

        preferences.setSongTagsVersion(MediaProviderType.Shuttle.name, MediaImporter.SONG_TAGS_VERSION - 1)
        localFiles.tags[file.path] = tags(title = "A, with every tag")

        findSongs(existing).single().name shouldBe "A, with every tag"
        localFiles.reads shouldContainExactly listOf(file.path)
    }

    @Test
    fun theListingCountsAsImportedOnlyOnceItsSongsAreStored() = runTest {
        localFiles.files = listOf(IosLocalFileRef("s2local://documents/a.flac", 1, 1))
        findSongs()
        localFiles.imported shouldBe emptyList()

        provider.songsStored()
        localFiles.imported.single().files shouldBe localFiles.files

        // Once only: a second store without a listing has nothing to record
        provider.songsStored()
        localFiles.imported.size shouldBe 1
    }

    @Test
    fun aFolderOutOfReachKeepsItsSongs() = runTest {
        localFiles.folders += IosLocalFolder(id = "gone", name = "Gone", path = "/x", hasAccess = false)
        localFiles.files = listOf(IosLocalFileRef("s2local://documents/c.flac", 1, 1))
        localFiles.tags["s2local://documents/c.flac"] = tags(title = "C")
        val kept = song("s2local://gone/a.flac", "Kept")
        val removed = song("s2local://documents/b.flac", "Removed")

        findSongs(listOf(kept, removed)).map { it.name } shouldBe listOf("Kept", "C")
    }

    @Test
    fun aFolderThatCouldntBeReadKeepsItsSongs() = runTest {
        localFiles.folders += IosLocalFolder(id = "music", name = "Music", path = "/x", hasAccess = true)
        localFiles.unread = listOf("music")
        val found = IosLocalFileRef("s2local://music/found.flac", 1, 1)
        val new = IosLocalFileRef("s2local://music/new.flac", 1, 1)
        localFiles.files = listOf(found, new)
        localFiles.tags[new.path] = tags(title = "New")
        val kept = song("s2local://music/a.flac", "Kept")
        val refound = song(found.path, "Found")

        // The files it got to before failing still import, and aren't kept twice
        findSongs(listOf(kept, refound)).map { it.name } shouldBe listOf("Kept", "Found", "New")
    }

    @Test
    fun aFolderThatListsNothingKeepsItsSongs() = runTest {
        localFiles.folders += IosLocalFolder(id = "music", name = "Music", path = "/x", hasAccess = true)
        val kept = listOf(song("s2local://music/a.flac", "A"), song("s2local://documents/b.flac", "B"))

        findSongs(kept) shouldBe kept
    }

    @Test
    fun aRemovedFolderLosesItsSongs() = runTest {
        findSongs(listOf(song("s2local://forgotten/a.flac"))) shouldBe emptyList()
    }

    @Test
    fun anOffloadedFileKeepsItsSongAndANewOneIsDownloaded() = runTest {
        val listed = IosLocalFileRef("s2local://documents/c.flac", 1, 1)
        localFiles.files = listOf(listed)
        localFiles.tags[listed.path] = tags(title = "C")
        localFiles.offloaded = listOf("s2local://documents/a.flac", "s2local://documents/new.flac")
        val offloaded = song("s2local://documents/a.flac", "A")
        val removed = song("s2local://documents/b.flac", "B")

        findSongs(listOf(offloaded, removed)).map { it.name } shouldBe listOf("A", "C")
        localFiles.downloads shouldContainExactly listOf("s2local://documents/new.flac")
        localFiles.reads shouldContainExactly listOf(listed.path)
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

    /** A file's tags as libavformat names them: a FLAC's Vorbis comments. */
    private fun tags(
        title: String? = "Title",
        artists: List<String> = listOf("Artist"),
        year: Int? = null,
        track: Int? = null,
        disc: Int? = null,
        durationMs: Long? = 1_000
    ) = IosLocalTags(
        tags =
            listOfNotNull(
                title?.let { IosLocalTag("title", it) },
                IosLocalTag("artist", artists.joinToString("; ")),
                IosLocalTag("album_artist", "Album Artist"),
                IosLocalTag("album", "Album"),
                track?.let { IosLocalTag("track", "$it") },
                disc?.let { IosLocalTag("disc", "$it") },
                year?.let { IosLocalTag("DATE", "$it") },
                IosLocalTag("GENRE", "Rock"),
                IosLocalTag("REPLAYGAIN_TRACK_GAIN", "-6.50 dB"),
                IosLocalTag("MUSICBRAINZ_TRACKID", "11111111-2222-3333-4444-555555555555")
            ),
        durationMs = durationMs,
        sampleRate = 44_100,
        channelCount = 2,
        bitDepth = 16,
        bitRate = 900,
        codec = "flac"
    )
}
