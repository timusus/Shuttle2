package com.simplecityapps.provider.emby

import com.simplecityapps.provider.emby.http.Item
import com.squareup.moshi.Moshi
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.time.Instant
import org.junit.Test

class ItemToSongTest {
    private val adapter = Moshi.Builder().build().adapter(Item::class.java)

    @Test
    fun `the album's image tag becomes the song's artwork version`() {
        parse(albumPrimaryImageTag = "tag-1").toSong().artworkVersion shouldBe "tag-1"
    }

    @Test
    fun `the artwork version is stable across identical syncs and changes with the image tag`() {
        parse(albumPrimaryImageTag = "tag-1").toSong().artworkVersion shouldBe parse(albumPrimaryImageTag = "tag-1").toSong().artworkVersion
        parse(albumPrimaryImageTag = "tag-1").toSong().artworkVersion shouldNotBe parse(albumPrimaryImageTag = "tag-2").toSong().artworkVersion
    }

    @Test
    fun `an album without an image has no artwork version`() {
        parse(albumPrimaryImageTag = null).toSong().artworkVersion shouldBe null
    }

    @Test
    fun `the song's date is when it was added to the server`() {
        parse(dateCreated = "2024-03-01T12:34:56.1234567Z").toSong().lastModified shouldBe Instant.parse("2024-03-01T12:34:56.1234567Z")
    }

    @Test
    fun `a missing or unreadable date is left for the importer to fill in`() {
        parse(dateCreated = null).toSong().lastModified shouldBe null
        parse(dateCreated = "not a date").toSong().lastModified shouldBe null
    }

    private fun parse(
        albumPrimaryImageTag: String? = "tag-1",
        dateCreated: String? = "2024-03-01T12:34:56.0000000Z"
    ): Item {
        val fields =
            listOfNotNull(
                "\"Name\": \"Song\"",
                "\"Id\": \"item-1\"",
                "\"RunTimeTicks\": 1800000000",
                "\"Album\": \"Album\"",
                "\"AlbumId\": 42",
                "\"AlbumArtist\": \"Artist\"",
                "\"Artists\": [\"Artist\"]",
                "\"IndexNumber\": 1",
                "\"ParentIndexNumber\": 1",
                "\"ProductionYear\": 2024",
                "\"Genres\": [\"Rock\"]",
                albumPrimaryImageTag?.let { tag -> "\"AlbumPrimaryImageTag\": \"$tag\"" },
                dateCreated?.let { date -> "\"DateCreated\": \"$date\"" }
            )
        return adapter.fromJson(fields.joinToString(separator = ",", prefix = "{", postfix = "}"))!!
    }
}
