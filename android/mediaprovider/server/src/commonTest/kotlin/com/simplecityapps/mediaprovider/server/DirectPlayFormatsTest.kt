package com.simplecityapps.mediaprovider.server

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class DirectPlayFormatsTest {
    @Test
    fun `plays a known container as it is`() {
        DirectPlayFormats.isDecodable("flac", "flac") shouldBe true
        DirectPlayFormats.isDecodable("MP3", null) shouldBe true
    }

    @Test
    fun `transcodes an unknown container`() {
        DirectPlayFormats.isDecodable("wma", "wmav2") shouldBe false
    }

    @Test
    fun `transcodes alac even in a playable container`() {
        DirectPlayFormats.isDecodable("m4a", "ALAC") shouldBe false
    }

    @Test
    fun `plays a file with no container or codec as it is`() {
        DirectPlayFormats.isDecodable(null, null) shouldBe true
        DirectPlayFormats.isDecodable("", "") shouldBe true
    }

    @Test
    fun `every container Android asks the universal endpoint to direct-play is one the player plays directly`() {
        val universal = StreamProfile.Android.directPlayContainers.split(',')
            .map { entry -> entry.substringBefore('|') }
            .map { container -> if (container == "webma") "weba" else container }

        DirectPlayFormats.containers shouldContainAll universal
    }
}
