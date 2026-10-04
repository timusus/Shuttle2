package com.simplecityapps.localmediaprovider.local.provider

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class TagLibBitDepthTest {
    @Test
    fun `a lossless file keeps its bits per sample`() {
        taglibBitDepth("Song.flac", 24) shouldBe 24
        taglibBitDepth("Song.FLAC", 16) shouldBe 16
        taglibBitDepth("Song.wav", 24) shouldBe 24
        taglibBitDepth("Song.aif", 16) shouldBe 16
        taglibBitDepth("Song.wv", 24) shouldBe 24
    }

    @Test
    fun `a lossy file has no bit depth`() {
        taglibBitDepth("Song.mp3", 16) shouldBe null
        taglibBitDepth("Song.ogg", 16) shouldBe null
        taglibBitDepth("Song.opus", 16) shouldBe null
        taglibBitDepth("Song.m4a", 16) shouldBe null
    }

    @Test
    fun `a missing or zero depth is null`() {
        taglibBitDepth("Song.flac", 0) shouldBe null
        taglibBitDepth("Song.flac", null) shouldBe null
    }
}
