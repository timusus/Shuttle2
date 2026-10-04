package com.simplecityapps.localmediaprovider.local.provider.mediastore

import android.provider.MediaStore
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.junit.Test

class MediaStoreBitDepthTest {
    @Test
    fun `a lossless file keeps the bit depth MediaStore reports`() {
        mediaStoreBitDepth("audio/flac", 24) shouldBe 24
        mediaStoreBitDepth("audio/x-wav", 16) shouldBe 16
        mediaStoreBitDepth("audio/ape", 16) shouldBe 16
        mediaStoreBitDepth("audio/wavpack", 24) shouldBe 24
        mediaStoreBitDepth("audio/x-wavpack", 24) shouldBe 24
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

    @Test
    fun `the column exists from API 36, and on API 33 to 35 from SDK extension 15`() {
        hasBitsPerSampleColumn(sdkInt = 36, tiramisuExtensionVersion = 0) shouldBe true
        hasBitsPerSampleColumn(sdkInt = 35, tiramisuExtensionVersion = 15) shouldBe true
        hasBitsPerSampleColumn(sdkInt = 33, tiramisuExtensionVersion = 15) shouldBe true
        hasBitsPerSampleColumn(sdkInt = 35, tiramisuExtensionVersion = 14) shouldBe false
        hasBitsPerSampleColumn(sdkInt = 33, tiramisuExtensionVersion = 14) shouldBe false
        hasBitsPerSampleColumn(sdkInt = 32, tiramisuExtensionVersion = 0) shouldBe false
        hasBitsPerSampleColumn(sdkInt = 30, tiramisuExtensionVersion = 0) shouldBe false
    }

    @Test
    fun `the scan only asks for bits per sample where MediaStore has the column`() {
        mediaStoreSongProjection(hasDiscNumber = true, hasBitsPerSample = false) shouldNotContain "bits_per_sample"
        mediaStoreSongProjection(hasDiscNumber = true, hasBitsPerSample = true) shouldContain "bits_per_sample"
        mediaStoreSongProjection(hasDiscNumber = false, hasBitsPerSample = false).run {
            this shouldNotContain MediaStore.Audio.Media.DISC_NUMBER
            this shouldContain MediaStore.Audio.Media.MIME_TYPE
        }
    }
}
