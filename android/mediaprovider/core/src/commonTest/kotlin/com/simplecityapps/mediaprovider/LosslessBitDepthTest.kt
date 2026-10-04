package com.simplecityapps.mediaprovider

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class LosslessBitDepthTest {
    @Test
    fun `a lossless codec keeps its bit depth`() {
        losslessBitDepth("FLAC", 24) shouldBe 24
        losslessBitDepth("alac", 16) shouldBe 16
        losslessBitDepth("ape", 16) shouldBe 16
        losslessBitDepth("wavpack", 24) shouldBe 24
        losslessBitDepth("tta", 24) shouldBe 24
        losslessBitDepth("truehd", 24) shouldBe 24
        losslessBitDepth("wmalossless", 24) shouldBe 24
    }

    @Test
    fun `wav and aiff pcm keep their bit depth`() {
        losslessBitDepth("pcm_s24le", 24) shouldBe 24
        losslessBitDepth("pcm_s16be", 16) shouldBe 16
    }

    @Test
    fun `dsd keeps its bit depth`() {
        losslessBitDepth("dsd_lsbf", 1) shouldBe 1
        losslessBitDepth("dsd_msbf_planar", 1) shouldBe 1
    }

    @Test
    fun `a lossy or unknown codec has none`() {
        losslessBitDepth("mp3", 16) shouldBe null
        losslessBitDepth("aac", 16) shouldBe null
        losslessBitDepth("opus", 32) shouldBe null
        losslessBitDepth("wmav2", 16) shouldBe null
        losslessBitDepth(null, 24) shouldBe null
    }

    @Test
    fun `a missing or non-positive depth is none`() {
        losslessBitDepth("flac", null) shouldBe null
        losslessBitDepth("flac", 0) shouldBe null
    }
}
