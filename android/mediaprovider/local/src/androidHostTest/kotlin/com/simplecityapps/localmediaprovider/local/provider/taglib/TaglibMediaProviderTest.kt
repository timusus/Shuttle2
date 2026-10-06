package com.simplecityapps.localmediaprovider.local.provider.taglib

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simplecityapps.ktaglib.KTagLib
import com.simplecityapps.localmediaprovider.local.provider.testTagReadGuard
import com.simplecityapps.mediaprovider.FlowEvent
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.TagReadFile
import com.simplecityapps.mediaprovider.model.AudioFile
import com.simplecityapps.saf.DocumentNode
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import java.io.File
import java.io.FileNotFoundException
import java.util.Collections
import kotlin.time.Instant
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

private const val AUTHORITY = "com.android.externalstorage.documents"
private const val CLOUD_AUTHORITY = "com.example.cloud.documents"
private const val MODIFIED = 1_700_000_000_000

// Document ids whose children the fake documents provider was asked for
private val queried: MutableList<String> = Collections.synchronizedList(mutableListOf())

/** Music tree: a.mp3, b.mp3, c.mp3 (new), Skip/d.mp3 and Skip/Deep/e.mp3; Skip is an excluded folder. */
@RunWith(AndroidJUnit4::class)
class TaglibMediaProviderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferences = GeneralPreferenceManager(InMemoryKeyValueStore())
    private val tagReadGuard = testTagReadGuard(preferences)
    private val tree = DocumentsContract.buildTreeDocumentUri(AUTHORITY, "primary:Music")

    @Suppress("DEPRECATION")
    private val skipFolder = "${android.os.Environment.getExternalStorageDirectory().path}/Music/Skip"
    private val zeroTree = DocumentsContract.buildTreeDocumentUri(AUTHORITY, "primary:Zero")
    private val namesTree = DocumentsContract.buildTreeDocumentUri(AUTHORITY, "primary:Names")
    private val listsTree = DocumentsContract.buildTreeDocumentUri(AUTHORITY, "primary:Lists")

    // Read from the walk's concurrent workers
    private val read: MutableList<String> = Collections.synchronizedList(mutableListOf())

    // Names of files the scanner fails to read
    private val unreadable: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    private val scanner =
        object : FileScanner() {
            override suspend fun getAudioFile(
                context: Context,
                kTagLib: KTagLib,
                node: DocumentNode,
                path: String
            ): AudioFile? {
                read += node.displayName
                if (node.displayName in unreadable) return null
                return AudioFile(
                    path = path,
                    size = node.size,
                    lastModified = node.lastModified,
                    mimeType = node.mimeType,
                    title = "Read ${node.displayName}",
                    albumArtist = null,
                    artists = emptyList(),
                    album = null,
                    track = null,
                    trackTotal = null,
                    disc = null,
                    discTotal = null,
                    duration = null,
                    year = null,
                    genres = emptyList(),
                    replayGainTrack = null,
                    replayGainAlbum = null,
                    lyrics = null,
                    grouping = null,
                    bitRate = null,
                    bitDepth = null,
                    sampleRate = null,
                    channelCount = null
                )
            }
        }

    @Before
    fun registerDocumentsProvider() {
        Robolectric.setupContentProvider(FakeDocumentsProvider::class.java, AUTHORITY)
        Robolectric.setupContentProvider(FakeMediaProvider::class.java, "media")
        Robolectric.setupContentProvider(FakeDocumentsProvider::class.java, CLOUD_AUTHORITY)
        FakeMediaProvider.rows = emptyList()
        FakeMediaProvider.playlistRows = emptyList()
        FakeMediaProvider.playlistContents = emptyMap()
        queried.clear()
    }

    @Test
    fun `playlists in a shared storage tree come from MediaStore without walking the tree`() {
        FakeMediaProvider.playlistRows =
            listOf(
                "$primary/Music/Lists/a.m3u" to "a.m3u",
                "$primary/Music/top.M3U8" to "top.M3U8",
                // Outside the tree, one a folder whose name only starts the same
                "$primary/Other/b.m3u" to "b.m3u",
                "$primary/MusicBox/c.m3u" to "c.m3u"
            )

        val playlists = findPlaylists(trees = listOf(tree))

        playlists.map { it.externalId } shouldContainExactlyInAnyOrder
            listOf(
                fileId("$primary/Music/Lists/a.m3u"),
                fileId("$primary/Music/top.M3U8")
            )
        queried.toList() shouldBe emptyList()
    }

    @Test
    fun `a playlist MediaStore lists outside every granted folder is imported, its relative entries resolved against its folder`() {
        FakeMediaProvider.playlistRows = listOf("$primary/Playlists/mix.m3u" to "mix.m3u")
        FakeMediaProvider.playlistContents = mapOf("$primary/Playlists/mix.m3u" to "#EXTM3U\n../Music/a.mp3\nmissing.mp3\n")

        val playlists = findPlaylists(trees = emptyList())

        playlists.size shouldBe 1
        playlists.single().name shouldBe "mix"
        playlists.single().songs.map { it.path } shouldBe listOf("$primary/Music/a.mp3")
        // Known by its file path, the id a granted folder holding it gives too
        playlists.single().externalId shouldBe fileId("$primary/Playlists/mix.m3u")
    }

    @Test
    fun `a playlist MediaStore lists in an excluded folder isn't imported`() {
        FakeMediaProvider.playlistRows = listOf("$primary/Playlists/mix.m3u" to "mix.m3u", "$primary/Music/keep.m3u" to "keep.m3u")
        FakeMediaProvider.playlistContents = mapOf("$primary/Playlists/mix.m3u" to "../Music/a.mp3\n", "$primary/Music/keep.m3u" to "a.mp3\n")

        val playlists = runBlocking { provider(excludes = listOf("$primary/Playlists"), trees = emptyList()).playlists() }

        playlists.map { it.externalId } shouldBe listOf(fileId("$primary/Music/keep.m3u"))
    }

    @Test
    fun `an HLS index and a list of streams aren't imported as playlists`() {
        FakeMediaProvider.playlistRows =
            listOf(
                "$primary/Movies/index.m3u8" to "index.m3u8",
                "$primary/Music/radio.m3u" to "radio.m3u",
                "$primary/Music/mix.m3u" to "mix.m3u"
            )
        FakeMediaProvider.playlistContents =
            mapOf(
                // A segment beside the index whose name matches a song is still not a playlist entry
                "$primary/Movies/index.m3u8" to "#EXTM3U\n#EXT-X-TARGETDURATION:10\n../Music/a.mp3\n",
                "$primary/Music/radio.m3u" to "#EXTM3U\nhttps://host/a.mp3\nhttp://host/b.mp3\n",
                "$primary/Music/mix.m3u" to "a.mp3\nhttps://host/a.mp3\n"
            )

        val playlists = findPlaylists(trees = emptyList())

        playlists.map { it.externalId } shouldBe listOf(fileId("$primary/Music/mix.m3u"))
    }

    @Test
    fun `a playlist in an extra tree that can't be walked keeps the id the walk gave it, from MediaStore`() {
        val locked = DocumentsContract.buildTreeDocumentUri(AUTHORITY, "primary:Locked")
        FakeMediaProvider.playlistRows = listOf("$primary/Locked/x.m3u" to "x.m3u")
        FakeMediaProvider.playlistContents = mapOf("$primary/Locked/x.m3u" to "/storage/emulated/0/Music/a.mp3\n")
        val provider = provider(excludes = emptyList(), trees = listOf(locked), grantedTrees = listOf(locked))

        val playlists = runBlocking { provider.playlists() }

        playlists.map { it.externalId } shouldBe listOf(fileId("$primary/Locked/x.m3u"))
    }

    @Test
    fun `a relative entry names the song beside the playlist, not one of the same name elsewhere`() {
        FakeMediaProvider.playlistRows = listOf("$primary/Music/mix.m3u" to "mix.m3u")
        FakeMediaProvider.playlistContents = mapOf("$primary/Music/mix.m3u" to "a.mp3\n")
        val beside = mediaStoreSong("a.mp3", size = 10, lastModified = MODIFIED)
        val elsewhere = beside.copy(path = "$primary/Other/a.mp3")

        val playlists =
            runBlocking {
                provider(excludes = emptyList(), trees = emptyList(), grantedTrees = emptyList()).findPlaylists(listOf(elsewhere, beside))
                    .filterIsInstance<FlowEvent.Success<MediaImporter.PlaylistListing>>().first().result.playlists
            }

        playlists.single().songs.map { it.path } shouldBe listOf(beside.path)
    }

    @Test
    fun `a playlist both a granted folder and MediaStore give is one playlist, under its file path`() {
        FakeMediaProvider.playlistRows = listOf("$primary/Music/p.m3u" to "p.m3u")
        FakeMediaProvider.playlistContents = mapOf("$primary/Music/p.m3u" to "/storage/emulated/0/Music/a.mp3\n")
        // The extra tree is walked, so its playlist isn't taken from MediaStore's listing
        val provider = provider(excludes = emptyList(), trees = listOf(tree), grantedTrees = listOf(tree))

        val playlists = runBlocking { provider.playlists() }

        playlists.map { it.externalId } shouldBe listOf(fileId("$primary/Music/p.m3u"))
    }

    @Test
    fun `a playlist in a granted folder isn't imported again from MediaStore`() {
        FakeMediaProvider.playlistRows = listOf("$primary/Music/Lists/a.m3u" to "a.m3u")
        FakeMediaProvider.playlistContents = mapOf("$primary/Music/Lists/a.m3u" to "/storage/emulated/0/Music/a.mp3\n")

        val playlists = findPlaylists(trees = listOf(tree))

        playlists.map { it.externalId } shouldBe listOf(fileId("$primary/Music/Lists/a.m3u"))
    }

    @Test
    fun `an entry that names a place of its own isn't put under the playlist's folder`() {
        fun entry(location: String) = com.simplecityapps.shuttle.model.Entry(location, null, null, null)

        resolveEntry(entry("a.mp3"), "/sd/Lists").location shouldBe "/sd/Lists/a.mp3"
        resolveEntry(entry("/sd/Music/a.mp3"), "/sd/Lists").location shouldBe "/sd/Music/a.mp3"
        resolveEntry(entry("C:\\Music\\a.mp3"), "/sd/Lists").location shouldBe "C:\\Music\\a.mp3"
        resolveEntry(entry("https://host/a.mp3"), "/sd/Lists").location shouldBe "https://host/a.mp3"
        resolveEntry(entry("a.mp3"), null).location shouldBe "a.mp3"
    }

    @Test
    fun `playlists in a tree MediaStore can't see are found by walking it`() {
        val cloud = DocumentsContract.buildTreeDocumentUri(CLOUD_AUTHORITY, "cloud")

        val playlists = findPlaylists(trees = listOf(cloud))

        playlists.map { it.externalId } shouldBe listOf(DocumentsContract.buildDocumentUriUsingTree(cloud, "cloud/l.m3u").toString())
    }

    @Test
    fun `an extra tree the song scan walked isn't walked again for playlists`() {
        val provider = provider(excludes = emptyList(), trees = listOf(tree), grantedTrees = listOf(tree))
        runBlocking { provider.findSongs(emptyList()).filterIsInstance<FlowEvent.Success<List<Song>>>().first() }
        queried.clear()

        val playlists = runBlocking { provider.playlists() }

        playlists.map { it.externalId } shouldBe listOf(fileId("$primary/Music/p.m3u"))
        queried.toList() shouldBe emptyList()
    }

    @Test
    fun `an extra tree is walked for playlists, not looked up in MediaStore, which skips it`() {
        // As when the song scan's walk of it failed, or hasn't run
        FakeMediaProvider.playlistRows = listOf("$primary/Music/stale.m3u" to "stale.m3u")
        val provider = provider(excludes = emptyList(), trees = listOf(tree), grantedTrees = listOf(tree))

        val playlists = runBlocking { provider.playlists() }

        playlists.map { it.externalId } shouldBe listOf(fileId("$primary/Music/p.m3u"))
    }

    @Test
    fun `a playlist MediaStore lists that can't be opened is skipped, the others still import`() {
        FakeMediaProvider.playlistRows = listOf("$primary/Music/ghost.m3u" to "ghost.m3u", "$primary/Music/a.m3u" to "a.m3u")

        val playlists = findPlaylists(trees = listOf(tree))

        playlists.map { it.externalId } shouldBe listOf(fileId("$primary/Music/a.m3u"))
    }

    @Test
    fun `playlists in an excluded folder of an extra tree are still found`() {
        @Suppress("DEPRECATION")
        val hidden = "${android.os.Environment.getExternalStorageDirectory().path}/Lists/Hidden"
        val provider = provider(excludes = listOf(hidden), trees = listOf(listsTree), grantedTrees = listOf(listsTree))
        runBlocking { provider.findSongs(emptyList()).filterIsInstance<FlowEvent.Success<List<Song>>>().first() }

        val playlists = runBlocking { provider.playlists() }

        playlists.map { it.externalId } shouldContainExactlyInAnyOrder
            listOf(
                fileId("$primary/Lists/k.m3u"),
                fileId("$primary/Lists/Hidden/h.m3u")
            )
    }

    @Test
    fun `a findSongs that fails doesn't leave the last import's playlist walk in use`() {
        var calls = 0
        val provider =
            TaglibMediaProvider(context, kTagLibWithoutNativeLibrary(), scanner, tagReadGuard, grantedTrees = { listOf(tree) }) {
                // The second call is the failing findSongs' own, before it walks anything
                if (++calls == 2) throw IllegalStateException("no folders")
                ScannerFolders(extraTrees = listOf(tree))
            }
        runBlocking { provider.findSongs(emptyList()).filterIsInstance<FlowEvent.Success<List<Song>>>().first() }
        runCatching { runBlocking { provider.findSongs(emptyList()).toList() } }
        queried.clear()

        val playlists = runBlocking { provider.playlists() }

        playlists.map { it.externalId } shouldBe listOf(fileId("$primary/Music/p.m3u"))
        // Walked again, not read from the map of the import before
        queried.toList().isEmpty() shouldBe false
    }

    @Test
    fun `a walked tree that is no longer an extra tree is looked up in MediaStore`() {
        var extra = listOf(tree)
        val provider = TaglibMediaProvider(context, kTagLibWithoutNativeLibrary(), scanner, tagReadGuard, grantedTrees = { listOf(tree) }) { ScannerFolders(extraTrees = extra) }
        runBlocking { provider.findSongs(emptyList()).filterIsInstance<FlowEvent.Success<List<Song>>>().first() }
        extra = emptyList()
        FakeMediaProvider.playlistRows = listOf("$primary/Music/Lists/a.m3u" to "a.m3u")

        val playlists = runBlocking { provider.playlists() }

        playlists.map { it.externalId } shouldBe listOf(fileId("$primary/Music/Lists/a.m3u"))
    }

    @Test
    fun `a file that was imported before and can't be read now keeps its song, a new one is skipped`() {
        unreadable += listOf("b.mp3", "c.mp3")
        val existing = listOf(storedSong("b.mp3", size = 999, lastModified = MODIFIED))

        val songs = findSongs(existing, excludes = listOf(skipFolder))

        songs.map { it.name } shouldContainExactlyInAnyOrder listOf("Read a.mp3", "Stored b.mp3")
        songs.first { it.name == "Stored b.mp3" }.id shouldBe 0
    }

    @Test
    fun `a quarantined file isn't read and keeps its stored song while a new one is left out`() {
        val b = DocumentsContract.buildDocumentUriUsingTree(tree, "primary:Music/b.mp3").toString()
        val c = DocumentsContract.buildDocumentUriUsingTree(tree, "primary:Music/c.mp3").toString()
        preferences.quarantineTagRead(TagReadFile(b, 10, MODIFIED).key)
        preferences.quarantineTagRead(TagReadFile(c, 10, MODIFIED).key)
        val existing = listOf(storedSong("b.mp3", size = 999, lastModified = MODIFIED))
        val provider = provider(excludes = listOf(skipFolder), trees = listOf(tree))

        val songs = runBlocking { provider.findSongs(existing).filterIsInstance<FlowEvent.Success<List<Song>>>().first().result }

        songs.map { it.name } shouldContainExactlyInAnyOrder listOf("Read a.mp3", "Stored b.mp3")
        read shouldContainExactlyInAnyOrder listOf("a.mp3")
        provider.skippedFiles shouldBe setOf(b, c)
    }

    @Test
    fun `MediaStore covers shared storage, and another volume only while it indexes it`() {
        mediaStoreIndexesTree("primary:Music", emptySet()) shouldBe true
        mediaStoreIndexesTree("home:", emptySet()) shouldBe true
        mediaStoreIndexesTree("1234-ABCD:Music", setOf("external_primary", "1234-abcd")) shouldBe true
        // A USB drive MediaStore doesn't index, or any volume before Android 10, which can't say
        mediaStoreIndexesTree("1234-ABCD:Music", setOf("external_primary")) shouldBe false
        mediaStoreIndexesTree("1234-ABCD:Music", emptySet()) shouldBe false
    }

    @Test
    fun `an extra tree that can't be read is reported unreadable, while the others are still read and pruned`() {
        val locked = DocumentsContract.buildTreeDocumentUri(AUTHORITY, "primary:Locked")
        val provider = provider(excludes = listOf(skipFolder), trees = listOf(tree, locked))

        val songs = runBlocking { provider.findSongs(emptyList()).filterIsInstance<FlowEvent.Success<List<Song>>>().first().result }

        songs.map { it.name } shouldContainExactlyInAnyOrder listOf("Read a.mp3", "Read b.mp3", "Read c.mp3")
        provider.unreadableRoots shouldBe setOf("$locked/document/")
    }

    private fun findPlaylists(trees: List<Uri>): List<MediaImporter.PlaylistUpdateData> = runBlocking {
        provider(excludes = emptyList(), trees = emptyList(), grantedTrees = trees).playlists()
    }

    private suspend fun TaglibMediaProvider.playlists(): List<MediaImporter.PlaylistUpdateData> = findPlaylists(listOf(mediaStoreSong("a.mp3", size = 10, lastModified = MODIFIED)))
        .filterIsInstance<FlowEvent.Success<MediaImporter.PlaylistListing>>()
        .first()
        .result
        .playlists

    @Test
    fun `an unchanged MediaStore file is reused without being read, a changed one is read`() {
        FakeMediaProvider.rows = listOf(mediaRow(1, "m1.mp3", size = 10), mediaRow(2, "m2.mp3", size = 11))
        FakeMediaProvider.rows = FakeMediaProvider.rows + listOf(mediaRow(3, "m3.mp3", size = 12))
        val existing =
            listOf(
                mediaStoreSong("m1.mp3", size = 10, lastModified = MODIFIED),
                mediaStoreSong("m2.mp3", size = 999, lastModified = MODIFIED)
            )

        val songs = findSongs(existing, excludes = emptyList(), trees = emptyList())

        // The changed file is read through KTagLib, which a JVM test can't, so it fails and keeps its stored song; the new one fails and is skipped
        songs.map { it.name } shouldContainExactlyInAnyOrder listOf("Stored m1.mp3", "Stored m2.mp3")
        songs.all { it.id == 0L } shouldBe true
    }

    @Test
    fun `a file with no modified date is always read`() {
        val songs = findSongs(listOf(storedSong("zero.mp3", size = 10, lastModified = 0, folder = "Zero")), excludes = emptyList(), trees = listOf(zeroTree))

        read shouldBe listOf("zero.mp3")
        songs.single().name shouldBe "Read zero.mp3"
    }

    @Test
    fun `every file is read again while the stored tags are outdated`() {
        val songs = findSongs(listOf(storedSong("a.mp3", size = 10, lastModified = MODIFIED)), excludes = listOf(skipFolder), backfill = true)

        songs.first { it.path.endsWith("a.mp3") }.name shouldBe "Read a.mp3"
        read shouldContainExactlyInAnyOrder listOf("a.mp3", "b.mp3", "c.mp3")
    }

    @Test
    fun `no song in an excluded folder is read`() {
        findSongs(emptyList(), excludes = listOf(skipFolder))

        read shouldContainExactlyInAnyOrder listOf("a.mp3", "b.mp3", "c.mp3")
    }

    @Test
    fun `excluding a folder leaves a sibling that shares its name as a prefix`() {
        @Suppress("DEPRECATION")
        val foo = "${android.os.Environment.getExternalStorageDirectory().path}/Names/Foo"

        findSongs(emptyList(), excludes = listOf(foo), trees = listOf(namesTree))

        read shouldBe listOf("g.mp3")
    }

    @Test
    fun `a nested excluded folder is skipped with what's beneath it`() {
        @Suppress("DEPRECATION")
        val deep = "${android.os.Environment.getExternalStorageDirectory().path}/Music/Skip/Deep"

        findSongs(emptyList(), excludes = listOf(deep))

        read shouldContainExactlyInAnyOrder listOf("a.mp3", "b.mp3", "c.mp3", "d.mp3")
    }

    @Test
    fun `unchanged files are reused, changed and new files are read, deleted files and excluded folders are absent`() {
        val existing =
            listOf(
                storedSong("a.mp3", size = 10, lastModified = MODIFIED),
                // Size differs from the file's
                storedSong("b.mp3", size = 999, lastModified = MODIFIED),
                // Gone from disk
                storedSong("gone.mp3", size = 10, lastModified = MODIFIED)
            )

        val songs = findSongs(existing, excludes = listOf(skipFolder))

        songs.map { it.name } shouldContainExactlyInAnyOrder listOf("Stored a.mp3", "Read b.mp3", "Read c.mp3")
        read shouldContainExactlyInAnyOrder listOf("b.mp3", "c.mp3")
    }

    @Test
    fun `a file with a different modified date is read again`() {
        val songs = findSongs(listOf(storedSong("a.mp3", size = 10, lastModified = MODIFIED - 1)), excludes = listOf(skipFolder))

        songs.first { it.path.endsWith("a.mp3") }.name shouldBe "Read a.mp3"
    }

    @Test
    fun `without an exclude, every folder is walked`() {
        val songs = findSongs(emptyList(), excludes = emptyList())

        read shouldContainExactlyInAnyOrder listOf("a.mp3", "b.mp3", "c.mp3", "d.mp3", "e.mp3")
        songs.size shouldBe 5
    }

    private fun findSongs(
        existingSongs: List<Song>,
        excludes: List<String>,
        trees: List<Uri> = listOf(tree),
        backfill: Boolean = false
    ): List<Song> {
        val provider = provider(excludes, trees, backfill = backfill)
        return runBlocking {
            provider.findSongs(existingSongs)
                .filterIsInstance<FlowEvent.Success<List<Song>>>()
                .first()
                .result
        }
    }

    private fun provider(
        excludes: List<String>,
        trees: List<Uri>,
        backfill: Boolean = false,
        grantedTrees: List<Uri> = emptyList()
    ) = TaglibMediaProvider(context, kTagLibWithoutNativeLibrary(), scanner, tagReadGuard, backfillFileTags = { backfill }, grantedTrees = { grantedTrees }) {
        ScannerFolders(filter = FolderFilter(excludes = excludes), extraTrees = trees)
    }

    // KTagLib's constructor loads the native library, which a JVM test can't; the fake scanner never reads through it
    private fun kTagLibWithoutNativeLibrary(): KTagLib {
        val unsafe = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null) as sun.misc.Unsafe
        return unsafe.allocateInstance(KTagLib::class.java) as KTagLib
    }

    private fun mediaRow(
        id: Long,
        name: String,
        size: Long
    ) = arrayOf<Any>(id, "$primary/Music/$name", name, size, MODIFIED / 1000, "audio/mpeg", 1000L)

    private fun mediaStoreSong(
        name: String,
        size: Long,
        lastModified: Long
    ) = storedSong(name, size, lastModified).copy(path = "$primary/Music/$name")

    @Suppress("DEPRECATION")
    private val primary = android.os.Environment.getExternalStorageDirectory().path

    private fun fileId(path: String) = Uri.fromFile(File(path)).toString()

    private fun storedSong(
        name: String,
        size: Long,
        lastModified: Long,
        folder: String = "Music"
    ) = Song(
        id = 5,
        name = "Stored $name",
        albumArtist = null,
        artists = emptyList(),
        album = null,
        track = null,
        disc = null,
        duration = 0,
        date = null,
        genres = emptyList(),
        path = DocumentsContract.buildDocumentUriUsingTree(DocumentsContract.buildTreeDocumentUri(AUTHORITY, "primary:$folder"), "primary:$folder/$name").toString(),
        size = size,
        mimeType = "audio/mpeg",
        lastModified = Instant.fromEpochMilliseconds(lastModified),
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

    /** MediaStore's audio listing, over [rows] in the order of [MEDIA_STORE_AUDIO_PROJECTION]. */
    class FakeMediaProvider : ContentProvider() {
        companion object {
            var rows: List<Array<Any>> = emptyList()

            // Path and display name of the playlist files in the Files table
            var playlistRows: List<Pair<String, String>> = emptyList()

            // What the Files table's playlists read as, by path; a playlist not in it can't be opened
            var playlistContents: Map<String, String> = emptyMap()

            // How many times the audio table was listed
            val audioQueries = java.util.concurrent.atomic.AtomicInteger()
        }

        override fun onCreate() = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor = MatrixCursor(projection).also { cursor ->
            if (uri.pathSegments.contains("file")) {
                playlistRows.forEachIndexed { index, (path, name) ->
                    cursor.addRow(
                        projection!!.map { column ->
                            when (column) {
                                MediaStore.Files.FileColumns.DATA -> path
                                MediaStore.Files.FileColumns.DISPLAY_NAME -> name
                                MediaStore.Files.FileColumns._ID -> index.toLong()
                                MediaStore.Files.FileColumns.SIZE -> playlistContents[path]?.length?.toLong()
                                else -> null
                            }
                        }
                    )
                }
            } else {
                audioQueries.incrementAndGet()
                rows.forEach { row -> cursor.addRow(row) }
            }
        }

        override fun openFile(
            uri: Uri,
            mode: String
        ): ParcelFileDescriptor {
            val text = playlistRows.getOrNull(ContentUris.parseId(uri).toInt())?.let { (path, _) -> playlistContents[path] } ?: throw FileNotFoundException("Gone")
            val file = File.createTempFile("playlist", ".m3u").apply { writeText(text) }
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }

        override fun getType(uri: Uri): String? = null

        override fun insert(
            uri: Uri,
            values: ContentValues?
        ): Uri? = null

        override fun delete(
            uri: Uri,
            selection: String?,
            selectionArgs: Array<out String>?
        ) = 0

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?
        ) = 0
    }

    class FakeDocumentsProvider : ContentProvider() {
        private class Doc(val id: String, val dir: Boolean = false, val size: Long = 10, val modified: Long = MODIFIED)

        private val children =
            mapOf(
                "cloud" to listOf(Doc("cloud/l.m3u")),
                "primary:Music" to listOf(Doc("primary:Music/p.m3u"), Doc("primary:Music/a.mp3"), Doc("primary:Music/b.mp3", size = 10), Doc("primary:Music/c.mp3"), Doc("primary:Music/Skip", dir = true)),
                "primary:Music/Skip" to listOf(Doc("primary:Music/Skip/d.mp3"), Doc("primary:Music/Skip/Deep", dir = true)),
                "primary:Music/Skip/Deep" to listOf(Doc("primary:Music/Skip/Deep/e.mp3")),
                "primary:Lists" to listOf(Doc("primary:Lists/k.m3u"), Doc("primary:Lists/Hidden", dir = true)),
                "primary:Lists/Hidden" to listOf(Doc("primary:Lists/Hidden/h.m3u")),
                "primary:Zero" to listOf(Doc("primary:Zero/zero.mp3", modified = 0)),
                "primary:Names" to listOf(Doc("primary:Names/Foo", dir = true), Doc("primary:Names/Foobar", dir = true)),
                "primary:Names/Foo" to listOf(Doc("primary:Names/Foo/f.mp3")),
                "primary:Names/Foobar" to listOf(Doc("primary:Names/Foobar/g.mp3"))
            )

        override fun onCreate() = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor {
            val cursor = MatrixCursor(projection)
            val docs =
                if (uri.pathSegments.last() == "children") {
                    queried += DocumentsContract.getDocumentId(uri)
                    if (DocumentsContract.getDocumentId(uri) == "primary:Locked") throw SecurityException("Access revoked")
                    children[DocumentsContract.getDocumentId(uri)].orEmpty()
                } else {
                    listOf(Doc(DocumentsContract.getDocumentId(uri), dir = true))
                }
            for (doc in docs) {
                cursor.addRow(
                    projection!!.map { column ->
                        when (column) {
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID -> doc.id
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME -> doc.id.substringAfterLast('/').substringAfterLast(':')
                            DocumentsContract.Document.COLUMN_MIME_TYPE -> if (doc.dir) DocumentsContract.Document.MIME_TYPE_DIR else "audio/mpeg"
                            DocumentsContract.Document.COLUMN_LAST_MODIFIED -> doc.modified
                            DocumentsContract.Document.COLUMN_SIZE -> doc.size
                            else -> null
                        }
                    }
                )
            }
            return cursor
        }

        // Every document read is a playlist naming a.mp3
        override fun openFile(
            uri: Uri,
            mode: String
        ): ParcelFileDescriptor {
            if (uri.toString().contains("ghost")) throw FileNotFoundException("Gone")
            val file = File.createTempFile("playlist", ".m3u").apply { writeText("#EXTM3U\n/storage/emulated/0/Music/a.mp3\n") }
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }

        override fun getType(uri: Uri): String? = null

        override fun insert(
            uri: Uri,
            values: ContentValues?
        ): Uri? = null

        override fun delete(
            uri: Uri,
            selection: String?,
            selectionArgs: Array<out String>?
        ) = 0

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?
        ) = 0
    }
}
