package com.simplecityapps.mediaprovider

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class LosslessBitDepthTest {
    @Test
    fun `a lossless codec keeps its bit depth`() {
        losslessBitDepth("FLAC", 24) shouldBe 24
        losslessBitDepth("alac", 16) shouldBe 16
        losslessBitDepth("pcm_s24le", 24) shouldBe 24
    }

    @Test
    fun `a lossy or unknown codec has none`() {
        losslessBitDepth("mp3", 16) shouldBe null
        losslessBitDepth("aac", 16) shouldBe null
        losslessBitDepth("opus", 32) shouldBe null
        losslessBitDepth(null, 24) shouldBe null
    }

    @Test
    fun `a missing or non-positive depth is none`() {
        losslessBitDepth("flac", null) shouldBe null
        losslessBitDepth("flac", 0) shouldBe null
    }
}
