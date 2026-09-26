package com.simplecityapps.shuttle.ui.actions

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.simplecityapps.createSong
import com.simplecityapps.mediaprovider.PlaylistExporter
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExportPlaylistTest {

    private val exportPlaylist = ExportPlaylist(PlaylistExporter(ApplicationProvider.getApplicationContext()))

    @Test
    fun `writes the songs to the destination as m3u`() = runTest {
        val file = File.createTempFile("playlist", ".m3u")

        val result = exportPlaylist("Road Trip", listOf(createSong(id = 1, name = "One")), Uri.fromFile(file).toString())

        result shouldBe ExportPlaylist.Result.Success
        file.readText() shouldContain "#EXTM3U"
    }

    @Test
    fun `an unopenable destination fails`() = runTest {
        val result = exportPlaylist("Road Trip", listOf(createSong(id = 1, name = "One")), "content://no.such.authority/doc/1")

        result.shouldBeInstanceOf<ExportPlaylist.Result.Failure>()
    }
}
