package com.simplecityapps.shuttle.shared.platform

import com.simplecityapps.shuttle.ui.actions.ExportPlaylist
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class FilePlaylistFileWriterTest {
    private val writer = FilePlaylistFileWriter(Dispatchers.Default)
    private val path = NSTemporaryDirectory() + "FilePlaylistFileWriterTest.m3u"

    @AfterTest
    fun removeTheFile() {
        NSFileManager.defaultManager.removeItemAtPath(path, error = null)
    }

    @Test
    fun writesTheTextToThePathAsUtf8() = runTest {
        val text = "#EXTM3U\n#EXTINF:215,Sigur Rós - Hoppípolla\n/music/hoppipolla.flac\n"

        writer.write(path, text) shouldBe ExportPlaylist.Result.Success

        NSString.stringWithContentsOfFile(path, encoding = NSUTF8StringEncoding, error = null) shouldBe text
    }

    @Test
    fun aPathItCantWriteIsAFailure() = runTest {
        writer.write(NSTemporaryDirectory() + "no-such-folder/playlist.m3u", "#EXTM3U\n")
            .shouldBeInstanceOf<ExportPlaylist.Result.Failure>()
    }
}
