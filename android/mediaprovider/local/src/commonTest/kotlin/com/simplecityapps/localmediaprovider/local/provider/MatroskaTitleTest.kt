package com.simplecityapps.localmediaprovider.local.provider

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class MatroskaTitleTest {
    @Test
    fun `reads the title from the segment info`() {
        matroskaFile(info = element(TITLE_ID, "My Song".encodeToByteArray())).let { matroskaTitle(it) } shouldBe "My Song"
    }

    @Test
    fun `reads a title after other info elements`() {
        val info = element(byteArrayOf(0x2A, 0xD7.toByte(), 0xB1.toByte()), byteArrayOf(0x0F, 0x42, 0x40)) + element(TITLE_ID, "Café".encodeToByteArray())

        matroskaTitle(matroskaFile(info = info)) shouldBe "Café"
    }

    @Test
    fun `a file with no title gives null`() {
        matroskaTitle(matroskaFile(info = element(byteArrayOf(0x2A, 0xD7.toByte(), 0xB1.toByte()), byteArrayOf(0x0F, 0x42, 0x40)))) shouldBe null
    }

    @Test
    fun `an unknown-size segment is read`() {
        val info = element(INFO_ID, element(TITLE_ID, "Live".encodeToByteArray()))
        val file = element(EBML_ID, byteArrayOf(0x42, 0x86.toByte(), 0x81.toByte(), 0x01)) + SEGMENT_ID + byteArrayOf(0x01, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()) + info

        matroskaTitle(file) shouldBe "Live"
    }

    @Test
    fun `not a matroska file or an empty one gives null`() {
        matroskaTitle(byteArrayOf()) shouldBe null
        matroskaTitle("ID3 not matroska at all".encodeToByteArray()) shouldBe null
    }

    @Test
    fun `a truncated file gives null`() {
        matroskaFile(info = element(TITLE_ID, "Cut off".encodeToByteArray())).let { matroskaTitle(it.copyOf(it.size - 3)) } shouldBe null
    }

    private fun matroskaFile(info: ByteArray): ByteArray = element(EBML_ID, byteArrayOf(0x42, 0x86.toByte(), 0x81.toByte(), 0x01)) + element(SEGMENT_ID, element(INFO_ID, info))

    // An element with a one-byte size (content under 127 bytes)
    private fun element(
        id: ByteArray,
        content: ByteArray
    ): ByteArray = id + byteArrayOf((0x80 or content.size).toByte()) + content

    private companion object {
        val EBML_ID = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte())
        val SEGMENT_ID = byteArrayOf(0x18, 0x53, 0x80.toByte(), 0x67)
        val INFO_ID = byteArrayOf(0x15, 0x49, 0xA9.toByte(), 0x66)
        val TITLE_ID = byteArrayOf(0x7B, 0xA9.toByte())
    }
}
