package com.simplecityapps.shuttle.streaming

import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class DeliveredFormatsTest {
    private val formats = DeliveredFormats()

    @Test
    fun aTranscodeIsRecordedByPath() {
        formats.record("jellyfin://item/1", DeliveredFormat("MP3", 128))

        formats.byPath.value shouldBe mapOf("jellyfin://item/1" to DeliveredFormat("MP3", 128))
    }

    @Test
    fun theOriginalClearsTheTranscodeItReplaced() {
        formats.record("jellyfin://item/1", DeliveredFormat("MP3", 128))
        formats.record("jellyfin://item/1", null)

        formats.byPath.value.shouldBeEmpty()
    }
}
