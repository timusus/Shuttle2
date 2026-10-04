package com.simplecityapps.mediaprovider.server

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class DirectPlayFormatsTest {
    @Test
    fun `plays a known container as it is`() {
        DirectPlayFormats.Android.isDecodable("flac", "flac") shouldBe true
        DirectPlayFormats.Android.isDecodable("MP3", null) shouldBe true
    }

    @Test
    fun `transcodes an unknown container`() {
        DirectPlayFormats.Android.isDecodable("wma", "wmav2") shouldBe false
        DirectPlayFormats.Ios.isDecodable("wma", "wmav2") shouldBe false
    }

    @Test
    fun `transcodes alac even in a playable container on Android`() {
        DirectPlayFormats.Android.isDecodable("m4a", "ALAC") shouldBe false
    }

    @Test
    fun `plays alac and aiff as they are on iOS`() {
        DirectPlayFormats.Ios.isDecodable("m4a", "ALAC") shouldBe true
        DirectPlayFormats.Ios.isDecodable("aiff", "pcm") shouldBe true
    }

    @Test
    fun `transcodes codecs the iOS FFmpeg build has no decoder for - even in a container it demuxes`() {
        listOf("ac3", "EAC3", "dts", "truehd", "wmav2", "wmapro", "ape", "wavpack", "adpcm_ima_wav").forEach { codec ->
            DirectPlayFormats.Ios.isDecodable("mka", codec) shouldBe false
        }
        DirectPlayFormats.Ios.isDecodable("mka", "opus") shouldBe true
    }

    @Test
    fun `plays a file with no container or codec as it is`() {
        DirectPlayFormats.Android.isDecodable(null, null) shouldBe true
        DirectPlayFormats.Android.isDecodable("", "") shouldBe true
    }

    @Test
    fun `every container a platform asks the universal endpoint to direct-play is one its player plays directly`() {
        listOf(StreamProfile.Android, StreamProfile.Ios).forEach { profile ->
            val universal = profile.directPlayContainers.split(',')
                .map { entry -> entry.substringBefore('|') }
                .map { container -> if (container == "webma") "weba" else container }
                // Jellyfin's names for a matroska file
                .filterNot { container -> container == "matroska" }

            profile.directPlayFormats.containers shouldContainAll universal
        }
    }
}
