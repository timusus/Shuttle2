package com.simplecityapps.localmediaprovider.local.provider.taglib

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simplecityapps.ktaglib.KTagLib
import com.simplecityapps.mediaprovider.FlowEvent
import com.simplecityapps.mediaprovider.model.AudioFile
import com.simplecityapps.saf.DocumentNode
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlin.time.Instant
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

private const val AUTHORITY = "com.android.externalstorage.documents"
private const val MODIFIED = 1_700_000_000_000

/** Music tree: a.mp3, b.mp3, c.mp3 (new), Skip/d.mp3 and Skip/Deep/e.mp3; Skip is an excluded folder. */
@RunWith(AndroidJUnit4::class)
class TaglibMediaProviderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val tree = DocumentsContract.buildTreeDocumentUri(AUTHORITY, "primary:Music")

    @Suppress("DEPRECATION")
    private val skipFolder = "${android.os.Environment.getExternalStorageDirectory().path}/Music/Skip"
    private val read = mutableListOf<String>()

    private val scanner =
        object : FileScanner() {
            override suspend fun getAudioFile(
                context: Context,
                kTagLib: KTagLib,
                node: DocumentNode
            ): AudioFile? {
                read += node.displayName
                return AudioFile(
                    path = node.uri.toString(),
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
        excludes: List<String>
    ): List<Song> {
        val provider =
            TaglibMediaProvider(context, kTagLibWithoutNativeLibrary(), scanner) {
                ScannerFolders(filter = FolderFilter(excludes = excludes), extraTrees = listOf(tree))
            }
        return runBlocking {
            provider.findSongs(existingSongs)
                .filterIsInstance<FlowEvent.Success<List<Song>>>()
                .first()
                .result
        }
    }

    // KTagLib's constructor loads the native library, which a JVM test can't; the fake scanner never reads through it
    private fun kTagLibWithoutNativeLibrary(): KTagLib {
        val unsafe = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null) as sun.misc.Unsafe
        return unsafe.allocateInstance(KTagLib::class.java) as KTagLib
    }

    private fun storedSong(
        name: String,
        size: Long,
        lastModified: Long
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
        path = DocumentsContract.buildDocumentUriUsingTree(tree, "primary:Music/$name").toString(),
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

    class FakeDocumentsProvider : ContentProvider() {
        private class Doc(val id: String, val dir: Boolean = false, val size: Long = 10)

        private val children =
            mapOf(
                "primary:Music" to listOf(Doc("primary:Music/a.mp3"), Doc("primary:Music/b.mp3", size = 10), Doc("primary:Music/c.mp3"), Doc("primary:Music/Skip", dir = true)),
                "primary:Music/Skip" to listOf(Doc("primary:Music/Skip/d.mp3"), Doc("primary:Music/Skip/Deep", dir = true)),
                "primary:Music/Skip/Deep" to listOf(Doc("primary:Music/Skip/Deep/e.mp3"))
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
                            DocumentsContract.Document.COLUMN_LAST_MODIFIED -> MODIFIED
                            DocumentsContract.Document.COLUMN_SIZE -> doc.size
                            else -> null
                        }
                    }
                )
            }
            return cursor
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
