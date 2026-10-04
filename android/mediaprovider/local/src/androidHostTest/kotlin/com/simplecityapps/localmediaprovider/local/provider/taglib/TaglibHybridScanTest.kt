package com.simplecityapps.localmediaprovider.local.provider.taglib

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simplecityapps.ktaglib.KTagLib
import com.simplecityapps.mediaprovider.FlowEvent
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.model.AudioFile
import com.simplecityapps.saf.DocumentNode
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import java.io.File
import java.util.Collections
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

private const val AUTHORITY = "com.android.externalstorage.documents"

// Whole seconds plus a part MediaStore doesn't keep, so a walk's date for an unchanged file only matches a stored one cut to seconds
private const val MODIFIED = 1_700_000_000_123

/**
 * The include tree primary:Music holds a.mp3 (which MediaStore lists, unless a test empties its listing) and, in a
 * `.nomedia` folder MediaStore skips, Hidden/b.mp3 and Hidden/x.m3u.
 */
@RunWith(AndroidJUnit4::class)
class TaglibHybridScanTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val tree = DocumentsContract.buildTreeDocumentUri(AUTHORITY, "primary:Music")

    @Suppress("DEPRECATION")
    private val primary = android.os.Environment.getExternalStorageDirectory().path

    // Read from the scan's concurrent workers
    private val read: MutableList<String> = Collections.synchronizedList(mutableListOf())

    private val scanner =
        object : FileScanner() {
            override suspend fun getAudioFile(
                context: Context,
                kTagLib: KTagLib,
                node: DocumentNode,
                path: String
            ): AudioFile {
                read += node.displayName
                return audioFile(path, node)
            }
        }

    @Before
    fun setUp() {
        Robolectric.setupContentProvider(Documents::class.java, AUTHORITY)
        Robolectric.setupContentProvider(TaglibMediaProviderTest.FakeMediaProvider::class.java, "media")
        TaglibMediaProviderTest.FakeMediaProvider.rows = listOf(arrayOf(1L, "$primary/Music/a.mp3", "a.mp3", 10L, MODIFIED / 1000, "audio/mpeg", 1000L))
        TaglibMediaProviderTest.FakeMediaProvider.playlistRows = emptyList()
        Documents.files.clear()
        Documents.files += listOf("primary:Music/a.mp3", "primary:Music/Hidden/b.mp3", "primary:Music/Hidden/x.m3u")
        Documents.listed.clear()
        Documents.lookedUp.clear()
        Documents.lookupFails = false
    }

    @Test
    fun `a routine import trusts MediaStore and doesn't walk the folder`() {
        val songs = provider().findSongs(listOf(stored("a.mp3"))).songs()

        songs.map { it.path } shouldBe listOf("$primary/Music/a.mp3")
        Documents.listed.toList() shouldBe emptyList()
    }

    @Test
    fun `a folder MediaStore lists nothing in is walked on a routine import`() {
        TaglibMediaProviderTest.FakeMediaProvider.rows = emptyList()

        val songs = provider().findSongs(emptyList()).songs()

        songs.map { it.path } shouldContainExactlyInAnyOrder listOf("$primary/Music/a.mp3", "$primary/Music/Hidden/b.mp3")
    }

    @Test
    fun `a thorough import walks the folder and finds a file MediaStore and the walk both list once`() {
        val songs = provider().findSongsThoroughly(listOf(stored("a.mp3"))).songs()

        songs.map { it.path } shouldContainExactlyInAnyOrder listOf("$primary/Music/a.mp3", "$primary/Music/Hidden/b.mp3")
        // a.mp3 is MediaStore's, unchanged, so only the file the walk added is read
        read.toList() shouldBe listOf("b.mp3")
        Documents.listed.toList() shouldContainExactlyInAnyOrder listOf("primary:Music", "primary:Music/Hidden")
    }

    @Test
    fun `an unchanged file a walk found is reused, compared at the whole seconds MediaStore keeps`() {
        TaglibMediaProviderTest.FakeMediaProvider.rows = emptyList()

        val songs = provider().findSongsThoroughly(listOf(stored("a.mp3"), stored("Hidden/b.mp3"))).songs()

        songs.map { it.name } shouldContainExactlyInAnyOrder listOf("Stored a.mp3", "Stored Hidden/b.mp3")
        read.toList() shouldBe emptyList()
    }

    @Test
    fun `a song only a walk found stays through routine imports until its file is gone`() {
        val existing = listOf(stored("a.mp3"), stored("Hidden/b.mp3"))

        provider().findSongs(existing).songs().map { it.path } shouldContainExactlyInAnyOrder existing.map { it.path }
        // Looked up on its own, not by walking the folder
        Documents.listed.toList() shouldBe emptyList()
        Documents.lookedUp.toList() shouldBe listOf("primary:Music/Hidden/b.mp3")

        Documents.files -= "primary:Music/Hidden/b.mp3"

        provider().findSongs(existing).songs().map { it.path } shouldBe listOf("$primary/Music/a.mp3")
    }

    @Test
    fun `a song only a walk found is kept when its document can't be looked up`() {
        Documents.lookupFails = true
        val existing = listOf(stored("a.mp3"), stored("Hidden/b.mp3"))

        provider().findSongs(existing).songs().map { it.path } shouldContainExactlyInAnyOrder existing.map { it.path }
    }

    @Test
    fun `a playlist in a folder MediaStore skips is found by a thorough import's walk`() {
        val provider = provider()
        val songs = provider.findSongsThoroughly(listOf(stored("a.mp3"))).songs()

        val playlists =
            runBlocking {
                provider.findPlaylists(songs)
                    .filterIsInstance<FlowEvent.Success<List<MediaImporter.PlaylistUpdateData>>>()
                    .first()
                    .result
            }

        playlists.map { it.externalId } shouldBe listOf(DocumentsContract.buildDocumentUriUsingTree(tree, "primary:Music/Hidden/x.m3u").toString())
    }

    private fun provider() = TaglibMediaProvider(context, kTagLibWithoutNativeLibrary(), scanner, grantedTrees = { listOf(tree) }, mountedRoots = { setOf("$primary/") }) {
        ScannerFolders(filter = FolderFilter(includes = listOf("$primary/Music")), includeTrees = listOf(tree))
    }

    private fun Flow<FlowEvent<List<Song>, *>>.songs(): List<Song> = runBlocking {
        filterIsInstance<FlowEvent.Success<List<Song>>>().first().result
    }

    // KTagLib's constructor loads the native library, which a JVM test can't; the fake scanner never reads through it
    private fun kTagLibWithoutNativeLibrary(): KTagLib {
        val unsafe = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null) as sun.misc.Unsafe
        return unsafe.allocateInstance(KTagLib::class.java) as KTagLib
    }

    private fun stored(name: String) = Song(
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
        path = "$primary/Music/$name",
        size = 10,
        mimeType = "audio/mpeg",
        lastModified = Instant.fromEpochMilliseconds(MODIFIED / 1000 * 1000),
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

    private fun audioFile(
        path: String,
        node: DocumentNode
    ) = AudioFile(
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

    /** The external storage documents provider over [files], whose folders are those the ids imply. */
    class Documents : ContentProvider() {
        companion object {
            val files: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

            // Folders whose children were listed, and single documents looked up
            val listed: MutableList<String> = Collections.synchronizedList(mutableListOf())
            val lookedUp: MutableList<String> = Collections.synchronizedList(mutableListOf())

            // A lookup fails as it does when access to the tree was lost
            @Volatile
            var lookupFails = false
        }

        override fun onCreate() = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor? {
            val cursor = MatrixCursor(projection)
            val documentId = DocumentsContract.getDocumentId(uri)
            val folders = files.flatMap { file -> file.split('/').dropLast(1).runningReduce { folder, name -> "$folder/$name" } }.toSet()
            if (uri.pathSegments.last() == "children") {
                listed += documentId
                (files + folders).filter { id -> id != documentId && id.substringBeforeLast('/') == documentId }.forEach { id -> cursor.addRow(row(projection!!, id, id in folders)) }
            } else if (documentId in folders) {
                cursor.addRow(row(projection!!, documentId, dir = true))
            } else {
                lookedUp += documentId
                if (lookupFails) throw SecurityException("Access revoked")
                // As the real provider does for a document whose file is gone
                if (documentId !in files) return null
                cursor.addRow(row(projection!!, documentId, dir = false))
            }
            return cursor
        }

        private fun row(
            projection: Array<out String>,
            id: String,
            dir: Boolean
        ): Array<Any?> = projection.map<String, Any?> { column ->
            when (column) {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID -> id

                DocumentsContract.Document.COLUMN_DISPLAY_NAME -> id.substringAfterLast('/')

                DocumentsContract.Document.COLUMN_MIME_TYPE -> if (dir) {
                    DocumentsContract.Document.MIME_TYPE_DIR
                } else if (id.endsWith(".m3u")) {
                    "audio/x-mpegurl"
                } else {
                    "audio/mpeg"
                }

                DocumentsContract.Document.COLUMN_LAST_MODIFIED -> MODIFIED

                DocumentsContract.Document.COLUMN_SIZE -> 10L

                else -> null
            }
        }.toTypedArray()

        // Every document read is a playlist naming a.mp3
        override fun openFile(
            uri: Uri,
            mode: String
        ): ParcelFileDescriptor {
            @Suppress("DEPRECATION")
            val song = "${android.os.Environment.getExternalStorageDirectory().path}/Music/a.mp3"
            val file = File.createTempFile("playlist", ".m3u").apply { writeText("#EXTM3U\n$song\n") }
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
