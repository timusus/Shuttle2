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

    @Test
    fun theMostRecentlyRecordedPathsAreKept() {
        repeat(DeliveredFormats.MAX_ENTRIES + 5) { formats.record("jellyfin://item/$it", DeliveredFormat("MP3", 128)) }

        formats.byPath.value.size shouldBe DeliveredFormats.MAX_ENTRIES
        formats.byPath.value.containsKey("jellyfin://item/0") shouldBe false
        formats.byPath.value.containsKey("jellyfin://item/${DeliveredFormats.MAX_ENTRIES + 4}") shouldBe true
    }

    @Test
    fun rerecordingAPathKeepsItNewest() {
        formats.record("a", DeliveredFormat("MP3", 128))
        repeat(DeliveredFormats.MAX_ENTRIES - 1) { formats.record("p$it", DeliveredFormat("MP3", 128)) }
        formats.record("a", DeliveredFormat("AAC", 128))
        formats.record("last", DeliveredFormat("MP3", 128))

        formats.byPath.value["a"] shouldBe DeliveredFormat("AAC", 128)
    }
}
