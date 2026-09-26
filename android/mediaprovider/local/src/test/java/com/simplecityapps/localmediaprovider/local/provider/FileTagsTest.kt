package com.simplecityapps.localmediaprovider.local.provider

import io.kotest.matchers.shouldBe
import org.junit.Test

class FileTagsTest {
    @Test
    fun `maps the tags TagLib reads from a Matroska file tagged by ffmpeg`() {
        // What TagLib's Matroska PropertyMap holds for ffmpeg's untargeted simple tags: standard names are translated
        // (PART_NUMBER to TRACKNUMBER), others such as ALBUM and ALBUM_ARTIST pass through as they are
        val tags =
            mapOf(
                "ARTIST" to listOf("Gapless Artist"),
                "ALBUM_ARTIST" to listOf("Gapless Album Artist"),
                "ALBUM" to listOf("Gapless Album"),
                "TRACKNUMBER" to listOf("2/5"),
                "DATE" to listOf("2023")
            ).toFileTags()

        tags.title shouldBe null
        tags.artists shouldBe listOf("Gapless Artist")
        tags.albumArtist shouldBe "Gapless Album Artist"
        tags.album shouldBe "Gapless Album"
        tags.track shouldBe 2
        tags.trackTotal shouldBe 5
        tags.year shouldBe "2023"
    }

    @Test
    fun `maps a Matroska file's album-level tags`() {
        // TITLE and ARTIST targeted at the album (TargetTypeValue 50) come out of TagLib as ALBUM and ALBUMARTIST
        val tags =
            mapOf(
                "TITLE" to listOf("Track Title"),
                "ALBUM" to listOf("Album Title"),
                "ALBUMARTIST" to listOf("Album Artist"),
                "DISCNUMBER" to listOf("1")
            ).toFileTags()

        tags.title shouldBe "Track Title"
        tags.album shouldBe "Album Title"
        tags.albumArtist shouldBe "Album Artist"
        tags.disc shouldBe 1
    }

    @Test
    fun `an untagged file has no tag values`() {
        val tags = emptyMap<String, List<String>>().toFileTags()

        tags.title shouldBe null
        tags.album shouldBe null
        tags.artists shouldBe emptyList()
        tags.track shouldBe null
    }

    @Test
    fun parseDate() {
        "2004".parseDate() shouldBe "2004"
        "2010-00-00".parseDate() shouldBe "2010"
        "2020-04-03T07:00:00Z".parseDate() shouldBe "2020"
        "99".parseDate() shouldBe null
    }
}
