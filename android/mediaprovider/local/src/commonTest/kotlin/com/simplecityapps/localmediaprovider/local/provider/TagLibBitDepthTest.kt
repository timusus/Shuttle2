package com.simplecityapps.localmediaprovider.local.provider

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class TagLibBitDepthTest {
    @Test
    fun `a lossless codec keeps its bits per sample`() {
        taglibBitDepth("flac", 24) shouldBe 24
        taglibBitDepth("wav", 24) shouldBe 24
        taglibBitDepth("aiff", 16) shouldBe 16
        taglibBitDepth("wavpack", 24) shouldBe 24
        taglibBitDepth("ape", 16) shouldBe 16
        taglibBitDepth("dsd", 1) shouldBe 1
    }

    @Test
    fun `ALAC in an m4a keeps its bit depth`() {
        taglibBitDepth("alac", 24) shouldBe 24
        taglibBitDepth("alac", 16) shouldBe 16
    }

    @Test
    fun `AAC has no bit depth even though TagLib reports 16`() {
        taglibBitDepth("aac", 16) shouldBe null
    }

    @Test
    fun `an unknown codec has no bit depth`() {
        taglibBitDepth(null, 16) shouldBe null
    }

    @Test
    fun `a lossy codec has no bit depth`() {
        taglibBitDepth("mp3", 16) shouldBe null
        taglibBitDepth("vorbis", 16) shouldBe null
        taglibBitDepth("opus", 16) shouldBe null
    }

    @Test
    fun `a missing or zero depth is null`() {
        taglibBitDepth("flac", 0) shouldBe null
        taglibBitDepth("flac", null) shouldBe null
    }
}
