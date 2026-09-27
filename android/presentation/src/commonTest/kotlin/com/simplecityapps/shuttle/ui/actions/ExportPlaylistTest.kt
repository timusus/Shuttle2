package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.createSong
import com.simplecityapps.mediaprovider.M3uWriter
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class ExportPlaylistTest {
    private var written: Pair<String, String>? = null
    private var result: ExportPlaylist.Result = ExportPlaylist.Result.Success
    private val fileWriter = PlaylistFileWriter { destination, text ->
        written = destination to text
        result
    }
    private val exportPlaylist = ExportPlaylist(M3uWriter(), fileWriter)

    @Test
    fun `writes the songs as m3u to the destination`() = runTest {
        val outcome = exportPlaylist("Road Trip", listOf(createSong(id = 1, name = "One")), "content://picked-destination")

        outcome shouldBe ExportPlaylist.Result.Success
        written?.first shouldBe "content://picked-destination"
        written?.second shouldContain "#EXTM3U"
    }

    @Test
    fun `propagates a failure from the file writer`() = runTest {
        result = ExportPlaylist.Result.Failure("permission denied")

        val outcome = exportPlaylist("Road Trip", listOf(createSong(id = 1, name = "One")), "content://picked-destination")

        outcome shouldBe ExportPlaylist.Result.Failure("permission denied")
    }
}
