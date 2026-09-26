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
    fun `UTF-8 tags decode as written`() {
        val tags =
            mapOf(
                "TITLE" to listOf("Chanson d’un jour d’hiver"),
                "ARTIST" to listOf("Ñengo Flow;坂本龍一"),
                "ALBUM" to listOf("東京 🎵")
            ).toFileTags()

        tags.title shouldBe "Chanson d’un jour d’hiver"
        tags.artists shouldBe listOf("Ñengo Flow", "坂本龍一")
        tags.album shouldBe "東京 🎵"
    }

    @Test
    fun `UTF-8 written into a Latin-1 tag is decoded as UTF-8`() {
        // ID3v1, and ID3v2 frames declared ISO-8859-1, are often written with UTF-8 bytes; TagLib decodes them a byte per
        // character, so "Ñengo Flow" arrives as "Ã\u0091engo Flow"
        val tags =
            mapOf(
                "TITLE" to listOf("Chanson d’un jour d’hiver".asLatin1()),
                "ARTIST" to listOf("Ñengo Flow".asLatin1()),
                "ALBUM" to listOf("東京 🎵".asLatin1()),
                "GENRE" to listOf("Música Latina".asLatin1())
            ).toFileTags()

        tags.title shouldBe "Chanson d’un jour d’hiver"
        tags.artists shouldBe listOf("Ñengo Flow")
        tags.album shouldBe "東京 🎵"
        tags.genres shouldBe listOf("Música Latina")
    }

    @Test
    fun `Latin-1 text stays as it is`() {
        val tags =
            mapOf(
                "TITLE" to listOf("Café Brûlé"),
                "ARTIST" to listOf("Björk"),
                "ALBUM" to listOf("Señor ¿Qué?")
            ).toFileTags()

        tags.title shouldBe "Café Brûlé"
        tags.artists shouldBe listOf("Björk")
        tags.album shouldBe "Señor ¿Qué?"
    }

    private fun String.asLatin1() = String(toByteArray(Charsets.UTF_8), Charsets.ISO_8859_1)

    @Test
    fun parseDate() {
        "2004".parseDate() shouldBe "2004"
        "2010-00-00".parseDate() shouldBe "2010"
        "2020-04-03T07:00:00Z".parseDate() shouldBe "2020"
        "99".parseDate() shouldBe null
    }
}
