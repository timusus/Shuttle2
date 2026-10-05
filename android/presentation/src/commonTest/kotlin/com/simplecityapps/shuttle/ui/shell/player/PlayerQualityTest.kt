package com.simplecityapps.shuttle.ui.shell.player

import com.simplecityapps.createSong
import com.simplecityapps.shuttle.streaming.DeliveredFormat
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class PlayerQualityTest {
    private val bare = createSong().copy(mimeType = "audio/flac", audioCodec = null, bitRate = null, bitDepth = null, sampleRate = null)

    @Test
    fun `a song with a bit depth reads codec depth and sample rate`() {
        bare.copy(bitDepth = 24, sampleRate = 96000).qualityLine() shouldBe "FLAC · 24-bit / 96 kHz"
        bare.copy(bitDepth = 16, sampleRate = 44100).qualityLine() shouldBe "FLAC · 16-bit / 44.1 kHz"
        bare.copy(bitDepth = 24).qualityLine() shouldBe "FLAC"
    }

    @Test
    fun `a song with only a bit rate reads codec and bit rate`() {
        bare.copy(mimeType = "audio/mpeg", bitRate = 320).qualityLine() shouldBe "MP3 · 320 kbps"
        bare.copy(mimeType = "audio/mpeg", bitRate = 320, sampleRate = 44100).qualityLine() shouldBe "MP3 · 320 kbps"
    }

    @Test
    fun `a song with only a sample rate reads codec and sample rate`() {
        bare.copy(mimeType = "audio/ogg", sampleRate = 48000).qualityLine() shouldBe "OGG · 48 kHz"
    }

    @Test
    fun `the source codec wins over the container's mime type`() {
        bare.copy(mimeType = "audio/mp4", audioCodec = "alac", bitDepth = 24, sampleRate = 48000).qualityLine() shouldBe "ALAC · 24-bit / 48 kHz"
    }

    @Test
    fun `a song with no format reads nothing`() {
        bare.copy(mimeType = "").qualityLine() shouldBe null
        bare.copy(mimeType = "", bitRate = 0, bitDepth = 0, sampleRate = 0).qualityLine() shouldBe null
    }

    @Test
    fun `a codec alone reads as itself`() {
        bare.qualityLine() shouldBe "FLAC"
    }

    @Test
    fun `a lossy codec ignores bit depth`() {
        bare.copy(mimeType = "audio/mpeg", bitRate = 320, bitDepth = 16, sampleRate = 44100).qualityLine() shouldBe "MP3 · 320 kbps"
        bare.copy(mimeType = "audio/mpeg", bitDepth = 16, sampleRate = 44100).qualityLine() shouldBe "MP3 · 44.1 kHz"
    }

    @Test
    fun `a lossless codec with a bit depth but no sample rate reads the bit rate`() {
        bare.copy(bitDepth = 24, sampleRate = 0, bitRate = 900).qualityLine() shouldBe "FLAC · 900 kbps"
        bare.copy(bitDepth = 24, sampleRate = 0).qualityLine() shouldBe "FLAC"
    }

    @Test
    fun `an unknown mime type with a codec reads the codec`() {
        bare.copy(mimeType = "", audioCodec = "opus", bitRate = 128).qualityLine() shouldBe "OPUS · 128 kbps"
    }

    @Test
    fun `22 point 05 kHz reads with its decimals`() {
        bare.copy(sampleRate = 22050).qualityLine() shouldBe "FLAC · 22.05 kHz"
    }

    @Test
    fun `a transcode reads as what the server delivers - not the file`() {
        val flac = bare.copy(bitDepth = 24, sampleRate = 96000)

        flac.qualityLine(DeliveredFormat("MP3", 128)) shouldBe "MP3 · 128 kbps"
        flac.qualityLine(DeliveredFormat("OPUS", null)) shouldBe "OPUS"
        flac.qualityLine(null) shouldBe "FLAC · 24-bit / 96 kHz"
    }

    @Test
    fun `a raw pcm codec reads as PCM with the depth from its name`() {
        bare.copy(mimeType = "audio/wav", audioCodec = "pcm_s16le", bitRate = 1411, sampleRate = 44100).qualityLine() shouldBe "PCM · 16-bit / 44.1 kHz"
        bare.copy(mimeType = "audio/wav", audioCodec = "pcm_s24le", sampleRate = 96000).qualityLine() shouldBe "PCM · 24-bit / 96 kHz"
        bare.copy(mimeType = "audio/wav", audioCodec = "pcm_f32le", sampleRate = 48000).qualityLine() shouldBe "PCM · 32-bit / 48 kHz"
        bare.copy(mimeType = "audio/wav", audioCodec = "pcm_u8", sampleRate = 22050).qualityLine() shouldBe "PCM · 8-bit / 22.05 kHz"
    }

    @Test
    fun `a raw pcm codec prefers the reported depth and falls back to the sample rate`() {
        bare.copy(audioCodec = "pcm_s16le", bitDepth = 24, sampleRate = 44100).qualityLine() shouldBe "PCM · 24-bit / 44.1 kHz"
        bare.copy(audioCodec = "pcm_dvd", sampleRate = 48000).qualityLine() shouldBe "PCM · 48 kHz"
    }
}
