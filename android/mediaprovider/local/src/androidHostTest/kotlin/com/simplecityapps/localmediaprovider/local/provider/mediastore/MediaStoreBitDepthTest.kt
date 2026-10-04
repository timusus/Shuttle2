package com.simplecityapps.localmediaprovider.local.provider.mediastore

import io.kotest.matchers.shouldBe
import org.junit.Test

class MediaStoreBitDepthTest {
    @Test
    fun `a lossless file keeps the bit depth MediaStore reports`() {
        mediaStoreBitDepth("audio/flac", 24) shouldBe 24
        mediaStoreBitDepth("audio/x-wav", 16) shouldBe 16
    }

    @Test
    fun `a lossy or ambiguous file has none, whatever MediaStore reports`() {
        mediaStoreBitDepth("audio/mpeg", 16) shouldBe null
        mediaStoreBitDepth("audio/mp4", 16) shouldBe null
        mediaStoreBitDepth("audio/ogg", 32) shouldBe null
    }

    @Test
    fun `an unknown or missing depth is none`() {
        mediaStoreBitDepth("audio/flac", null) shouldBe null
        mediaStoreBitDepth("audio/flac", 0) shouldBe null
        mediaStoreBitDepth(null, 24) shouldBe null
    }
}
