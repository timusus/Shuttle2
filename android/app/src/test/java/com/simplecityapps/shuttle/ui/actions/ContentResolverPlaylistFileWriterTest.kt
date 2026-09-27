package com.simplecityapps.shuttle.ui.actions

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.File
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ContentResolverPlaylistFileWriterTest {

    private val fileWriter = ContentResolverPlaylistFileWriter(ApplicationProvider.getApplicationContext(), UnconfinedTestDispatcher())

    @Test
    fun `writes the text to the destination`() = runTest {
        val file = File.createTempFile("playlist", ".m3u")

        val result = fileWriter.write(Uri.fromFile(file).toString(), "#EXTM3U\n")

        result shouldBe ExportPlaylist.Result.Success
        file.readText() shouldContain "#EXTM3U"
    }

    @Test
    fun `an unopenable destination fails`() = runTest {
        val result = fileWriter.write("content://no.such.authority/doc/1", "#EXTM3U\n")

        result.shouldBeInstanceOf<ExportPlaylist.Result.Failure>()
    }
}
