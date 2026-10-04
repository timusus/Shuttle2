package com.simplecityapps.localmediaprovider.local.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simplecityapps.localmediaprovider.local.provider.mediastore.MediaStoreMediaProvider
import com.simplecityapps.mediaprovider.FlowEvent
import com.simplecityapps.mediaprovider.MediaProvider
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/**
 * The MediaStore provider fails an import it can't list MediaStore for, rather than reporting no songs and so removing them
 * all. (The scanner's TaglibMediaProvider does the same when it has no extra folders to read, but needs KTagLib's native
 * library, which doesn't load on the JVM; what it keeps when it reads only those is in StorageVolumesTest.)
 */
@RunWith(AndroidJUnit4::class)
class LocalProviderFailureTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val mediaStoreProvider = MediaStoreMediaProvider(context, { _, _ -> null }, GeneralPreferenceManager(InMemoryKeyValueStore()), testTagReadGuard())

    @Test
    fun `MediaStore returning no cursor fails the MediaStore import`() {
        mediaStore<NoCursorMediaStore>()

        lastEvent(mediaStoreProvider).shouldBeInstanceOf<FlowEvent.Failure>()
    }

    @Test
    fun `MediaStore refusing access fails the MediaStore import`() {
        mediaStore<DeniedMediaStore>()

        lastEvent(mediaStoreProvider).shouldBeInstanceOf<FlowEvent.Failure>()
    }

    @Test
    fun `songs on a volume that isn't mounted are reported unreadable`() {
        mediaStore<EmptyMediaStore>()

        lastEvent(mediaStoreProvider, existingSongs = listOf(song("/storage/ABCD-EF01/Music/a.mp3"))).shouldBeInstanceOf<FlowEvent.Success<*>>()

        mediaStoreProvider.unreadableRoots shouldBe setOf("/storage/ABCD-EF01/")
    }

    private inline fun <reified T : ContentProvider> mediaStore() {
        Robolectric.buildContentProvider(T::class.java).create(ProviderInfo().apply { authority = MediaStore.AUTHORITY })
    }

    private fun lastEvent(
        provider: MediaProvider,
        existingSongs: List<Song> = emptyList()
    ) = runBlocking { provider.findSongs(existingSongs).last() }

    private fun song(path: String) = Song(
        id = 1,
        name = "Song",
        albumArtist = null,
        artists = emptyList(),
        album = null,
        track = null,
        disc = null,
        duration = 0,
        date = null,
        genres = emptyList(),
        path = path,
        size = 0,
        mimeType = "audio/mpeg",
        lastModified = null,
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
        channelCount = null,
        artworkVersion = null
    )

    open class NoCursorMediaStore : ContentProvider() {
        override fun onCreate() = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor? = null

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

    class DeniedMediaStore : NoCursorMediaStore() {
        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor? = throw SecurityException("Permission denied")
    }

    class EmptyMediaStore : NoCursorMediaStore() {
        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor? = MatrixCursor(projection ?: arrayOf(MediaStore.Audio.Media._ID))
    }
}
