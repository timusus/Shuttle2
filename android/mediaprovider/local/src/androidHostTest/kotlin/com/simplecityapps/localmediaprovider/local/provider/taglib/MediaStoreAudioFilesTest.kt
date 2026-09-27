package com.simplecityapps.localmediaprovider.local.provider.taglib

import android.database.MatrixCursor
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaStoreAudioFilesTest {
    @Test
    fun `reads each row into an audio file`() {
        val cursor = cursorOf(arrayOf(7L, "/storage/emulated/0/Music/Album/01 Song.flac", "01 Song.flac", 2_048L, 1_700_000_000L, "audio/flac", 185_000L))

        cursor.readMediaStoreAudioFiles(FolderFilter()) shouldBe
            listOf(
                MediaStoreAudioFile(
                    id = 7,
                    path = "/storage/emulated/0/Music/Album/01 Song.flac",
                    displayName = "01 Song.flac",
                    size = 2_048,
                    lastModified = 1_700_000_000_000,
                    mimeType = "audio/flac",
                    duration = 185_000
                )
            )
    }

    @Test
    fun `the content uri is the file's row in MediaStore's external audio table`() {
        val file = MediaStoreAudioFile(id = 42, path = "/a.mp3", displayName = "a.mp3", size = 1, lastModified = 0, mimeType = null)

        file.contentUri.toString() shouldBe "content://media/external/audio/media/42"
    }

    @Test
    fun `a row with no path is skipped`() {
        val cursor =
            cursorOf(
                arrayOf(1L, null, "orphan.mp3", 1L, 0L, "audio/mpeg", null),
                arrayOf(2L, "/storage/emulated/0/Music/b.mp3", "b.mp3", 1L, 0L, "audio/mpeg", null)
            )

        cursor.readMediaStoreAudioFiles(FolderFilter()).map { it.id } shouldBe listOf(2L)
    }

    @Test
    fun `a missing display name falls back to the file name, which TagLib needs to detect the format`() {
        val cursor = cursorOf(arrayOf(3L, "/storage/emulated/0/Music/c.opus", null, 1L, 0L, null, null))

        val file = cursor.readMediaStoreAudioFiles(FolderFilter()).single()

        file.displayName shouldBe "c.opus"
        file.mimeType shouldBe null
    }

    @Test
    fun `rows outside the folder filter are left out`() {
        val cursor =
            cursorOf(
                arrayOf(1L, "/storage/emulated/0/Music/keep.mp3", "keep.mp3", 1L, 0L, "audio/mpeg", null),
                arrayOf(2L, "/storage/emulated/0/Music/Podcasts/skip.mp3", "skip.mp3", 1L, 0L, "audio/mpeg", null),
                arrayOf(3L, "/storage/04B9-1208/Music/sd.mp3", "sd.mp3", 1L, 0L, "audio/mpeg", null),
                arrayOf(4L, "/storage/emulated/0/Download/other.mp3", "other.mp3", 1L, 0L, "audio/mpeg", null)
            )
        val filter =
            FolderFilter(
                includes = listOf("/storage/emulated/0/Music", "/storage/04B9-1208/Music"),
                excludes = listOf("/storage/emulated/0/Music/Podcasts")
            )

        cursor.readMediaStoreAudioFiles(filter).map { it.id } shouldBe listOf(1L, 3L)
    }

    @Test
    fun `an empty cursor has no files`() {
        cursorOf().readMediaStoreAudioFiles(FolderFilter()).shouldBeEmpty()
    }

    private fun cursorOf(vararg rows: Array<Any?>): MatrixCursor = MatrixCursor(MEDIA_STORE_AUDIO_PROJECTION).apply { rows.forEach { row -> addRow(row) } }
}
