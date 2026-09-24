package com.simplecityapps.playback

import android.media.AudioFormat
import io.kotest.matchers.shouldBe
import org.junit.Test

/** Picking the USB mixer format that carries the player's output unchanged. */
class BitPerfectFormatTest {
    private val output44k = OutputFormat(44_100, 2, AudioFormat.ENCODING_PCM_16BIT)

    private fun bitPerfect(
        sampleRate: Int,
        channelCount: Int = 2,
        encoding: Int = AudioFormat.ENCODING_PCM_16BIT
    ) = MixerFormat(sampleRate, channelCount, encoding, isBitPerfect = true)

    @Test
    fun `selects the bit-perfect format matching the output exactly`() {
        val candidates = listOf(bitPerfect(48_000), bitPerfect(44_100), bitPerfect(96_000))

        selectBitPerfectFormat(candidates, output44k) shouldBe bitPerfect(44_100)
    }

    @Test
    fun `ignores a matching format that isn't bit-perfect`() {
        val mixed = MixerFormat(44_100, 2, AudioFormat.ENCODING_PCM_16BIT, isBitPerfect = false)

        selectBitPerfectFormat(listOf(mixed), output44k) shouldBe null
        selectBitPerfectFormat(listOf(mixed, bitPerfect(44_100)), output44k) shouldBe bitPerfect(44_100)
    }

    @Test
    fun `selects nothing when the rate, channel count or encoding differs`() {
        val candidates = listOf(
            bitPerfect(48_000),
            bitPerfect(44_100, channelCount = 1),
            bitPerfect(44_100, encoding = AudioFormat.ENCODING_PCM_24BIT_PACKED)
        )

        selectBitPerfectFormat(candidates, output44k) shouldBe null
    }

    @Test
    fun `selects nothing from a device offering no formats`() {
        selectBitPerfectFormat(emptyList(), output44k) shouldBe null
    }
}
