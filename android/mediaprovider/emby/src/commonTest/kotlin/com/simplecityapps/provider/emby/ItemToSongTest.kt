package com.simplecityapps.provider.emby

import com.simplecityapps.networking.S2Json
import com.simplecityapps.provider.emby.http.Item
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.test.Test
import kotlin.time.Instant

class ItemToSongTest {
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
    fun `the song's dates are when it was added to the server`() {
        val song = parse(dateCreated = "2024-03-01T12:34:56.1234567Z").toSong()

        song.dateAdded shouldBe Instant.parse("2024-03-01T12:34:56.1234567Z")
        song.lastModified shouldBe Instant.parse("2024-03-01T12:34:56.1234567Z")
    }

    @Test
    fun `a missing or unreadable date is left for the importer to fill in`() {
        parse(dateCreated = null).toSong().run { lastModified to dateAdded } shouldBe (null to null)
        parse(dateCreated = "not a date").toSong().run { lastModified to dateAdded } shouldBe (null to null)
    }

    @Test
    fun `maps the album artists, the server's artist and album ids and the MusicBrainz ids`() {
        // As an Emby 4.9 server sends a track fetched with Fields=ProviderIds
        val item =
            S2Json.decodeFromString<Item>(
                """
                {"Name": "Song", "Id": "7", "AlbumId": "42", "AlbumArtist": "Various Artists",
                 "Artists": ["A", "B"], "ArtistItems": [{"Name": "A", "Id": "11"}, {"Name": "B", "Id": "12"}],
                 "AlbumArtists": [{"Name": "Various Artists", "Id": "10"}],
                 "ProviderIds": {
                   "MusicBrainzTrack": "5B11F4CE-A62D-471E-81FC-A69A8278C7DA",
                   "MusicBrainzAlbum": "a1b2c3d4-0000-4000-8000-000000000001",
                   "MusicBrainzReleaseGroup": "a1b2c3d4-0000-4000-8000-000000000002",
                   "MusicBrainzArtist": "a1b2c3d4-0000-4000-8000-000000000003",
                   "MusicBrainzAlbumArtist": "89ad4ac3-39f7-470e-963a-56509c546377"
                 }}
                """.trimIndent()
            )

        val song = item.toSong()

        song.artistsTag shouldBe listOf("A", "B")
        song.albumArtists shouldBe listOf("Various Artists")
        song.artistDisplay shouldBe null
        song.compilation shouldBe null
        song.mbTrackId shouldBe "5b11f4ce-a62d-471e-81fc-a69a8278c7da"
        song.mbAlbumId shouldBe "a1b2c3d4-0000-4000-8000-000000000001"
        song.mbReleaseGroupId shouldBe "a1b2c3d4-0000-4000-8000-000000000002"
        song.mbArtistIds shouldBe listOf("a1b2c3d4-0000-4000-8000-000000000003")
        song.mbAlbumArtistIds shouldBe listOf("89ad4ac3-39f7-470e-963a-56509c546377")
        song.serverAlbumId shouldBe "42"
        song.serverArtistIds shouldBe listOf("11", "12")
        song.serverAlbumArtistIds shouldBe listOf("10")
    }

    @Test
    fun `an item without MusicBrainz tags has no MusicBrainz ids`() {
        val song = parse().toSong()

        song.mbTrackId shouldBe null
        song.mbAlbumArtistIds shouldBe emptyList()
        song.serverAlbumArtistIds shouldBe emptyList()
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
                "\"AlbumId\": \"42\"",
                "\"AlbumArtist\": \"Artist\"",
                "\"Artists\": [\"Artist\"]",
                "\"IndexNumber\": 1",
                "\"ParentIndexNumber\": 1",
                "\"ProductionYear\": 2024",
                "\"Genres\": [\"Rock\"]",
                albumPrimaryImageTag?.let { tag -> "\"AlbumPrimaryImageTag\": \"$tag\"" },
                dateCreated?.let { date -> "\"DateCreated\": \"$date\"" }
            )
        return S2Json.decodeFromString<Item>(fields.joinToString(separator = ",", prefix = "{", postfix = "}"))
    }
}
