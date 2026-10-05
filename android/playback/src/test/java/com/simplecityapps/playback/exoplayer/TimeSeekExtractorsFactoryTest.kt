package com.simplecityapps.playback.exoplayer

import android.net.Uri
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.media3.test.utils.FakeExtractorInput
import androidx.media3.test.utils.FakeExtractorOutput
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Constant-bitrate seeking only for the streams that seek by time; every other MP3 seeks as Media3 has it. */
// Robolectric: Media3's extractors log through android.util.Log.
@RunWith(RobolectricTestRunner::class)
class TimeSeekExtractorsFactoryTest {
    private val factory = TimeSeekExtractorsFactory { uri -> uri.scheme == "subsonic" }

    @Test
    fun `a time-seekable stream whose seek frame has no table seeks by its constant bitrate`() {
        seekMap(Uri.parse("subsonic://song/1")).isSeekable shouldBe true
    }

    @Test
    fun `any other MP3 whose seek frame has no table isn't seekable`() {
        seekMap(Uri.parse("file:///music/song.mp3")).isSeekable shouldBe false
    }

    private fun seekMap(uri: Uri): SeekMap {
        val extractor = factory.createExtractors(uri, emptyMap()).filterIsInstance<Mp3Extractor>().single()
        val output = FakeExtractorOutput()
        extractor.init(output)
        val input = FakeExtractorInput.Builder().setData(mp3WithXingFrameWithoutTable()).build()
        val positionHolder = PositionHolder()
        while (output.seekMap == null) {
            extractor.read(input, positionHolder) shouldBe Extractor.RESULT_CONTINUE
        }
        return output.seekMap!!
    }

    /**
     * 128 kbps, 44.1 kHz joint-stereo MPEG-1 Layer III frames of silence, the first carrying a Xing frame that gives a
     * frame count but no byte count or table of contents.
     */
    private fun mp3WithXingFrameWithoutTable(): ByteArray {
        val frameSize = 417
        val frames = 100
        val data = ByteArray(frameSize * frames)
        for (frame in 0 until frames) {
            byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x64).copyInto(data, frame * frameSize)
        }
        // After the 4-byte header and 32 bytes of side information: "Xing", flags (frames only), the frame count
        "Xing".encodeToByteArray().copyInto(data, 36)
        byteArrayOf(0, 0, 0, 1).copyInto(data, 40)
        byteArrayOf(0, 0, 0, frames.toByte()).copyInto(data, 44)
        return data
    }
}
