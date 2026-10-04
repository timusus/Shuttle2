package com.simplecityapps.provider.jellyfin

import com.simplecityapps.networking.S2Json
import com.simplecityapps.provider.jellyfin.http.Item
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.test.Test
import kotlin.time.Instant

class ItemToSongTest {
    @Test
    fun `the album's image tag becomes the song's artwork version`() {
        parse(albumPrimaryImageTag = "tag-1").toSong(SYNCED_AT).artworkVersion shouldBe "tag-1"
    }

    @Test
    fun `the artwork version is stable across identical syncs and changes with the image tag`() {
        parse(albumPrimaryImageTag = "tag-1").toSong(SYNCED_AT).artworkVersion shouldBe parse(albumPrimaryImageTag = "tag-1").toSong(SYNCED_AT).artworkVersion
        parse(albumPrimaryImageTag = "tag-1").toSong(SYNCED_AT).artworkVersion shouldNotBe parse(albumPrimaryImageTag = "tag-2").toSong(SYNCED_AT).artworkVersion
    }

    @Test
    fun `an album without an image has no artwork version`() {
        parse(albumPrimaryImageTag = null).toSong(SYNCED_AT).artworkVersion shouldBe null
    }

    @Test
    fun `the song's dates are when it was added to the server`() {
        val song = parse(dateCreated = "2024-03-01T12:34:56.1234567Z").toSong(SYNCED_AT)

        song.dateAdded shouldBe Instant.parse("2024-03-01T12:34:56.1234567Z")
        song.lastModified shouldBe Instant.parse("2024-03-01T12:34:56.1234567Z")
    }

    @Test
    fun `a missing or unreadable date is left for the importer to fill in`() {
        parse(dateCreated = null).toSong(SYNCED_AT).run { lastModified to dateAdded } shouldBe (null to null)
        parse(dateCreated = "not a date").toSong(SYNCED_AT).run { lastModified to dateAdded } shouldBe (null to null)
    }

    @Test
    fun `maps the album artists - the server's artist and album ids and the MusicBrainz ids`() {
        // As a Jellyfin 10.10 server sends a track fetched with fields=ProviderIds
        val item =
            S2Json.decodeFromString<Item>(
                """
                {"Name": "Song", "Id": "item-1", "AlbumId": "album-1", "AlbumArtist": "Various Artists",
                 "Artists": ["A", "B"], "ArtistItems": [{"Name": "A", "Id": "artist-a"}, {"Name": "B", "Id": "artist-b"}],
                 "AlbumArtists": [{"Name": "Various Artists", "Id": "artist-va"}],
                 "ProviderIds": {
                   "MusicBrainzRecording": "5B11F4CE-A62D-471E-81FC-A69A8278C7DA",
                   "MusicBrainzTrack": "a1b2c3d4-0000-4000-8000-00000000000f",
                   "MusicBrainzAlbum": "a1b2c3d4-0000-4000-8000-000000000001",
                   "MusicBrainzReleaseGroup": "a1b2c3d4-0000-4000-8000-000000000002",
                   "MusicBrainzArtist": "a1b2c3d4-0000-4000-8000-000000000003/a1b2c3d4-0000-4000-8000-000000000004",
                   "MusicBrainzAlbumArtist": "89ad4ac3-39f7-470e-963a-56509c546377"
                 }}
                """.trimIndent()
            )

        val song = item.toSong(SYNCED_AT)

        song.artists shouldBe listOf("A", "B")
        song.artistsTag shouldBe listOf("A", "B")
        song.albumArtists shouldBe listOf("Various Artists")
        song.artistDisplay shouldBe null
        song.compilation shouldBe null
        song.mbTrackId shouldBe "5b11f4ce-a62d-471e-81fc-a69a8278c7da"
        song.mbAlbumId shouldBe "a1b2c3d4-0000-4000-8000-000000000001"
        song.mbReleaseGroupId shouldBe "a1b2c3d4-0000-4000-8000-000000000002"
        song.mbArtistIds shouldBe listOf("a1b2c3d4-0000-4000-8000-000000000003", "a1b2c3d4-0000-4000-8000-000000000004")
        song.mbAlbumArtistIds shouldBe listOf("89ad4ac3-39f7-470e-963a-56509c546377")
        song.serverAlbumId shouldBe "album-1"
        song.serverArtistIds shouldBe listOf("artist-a", "artist-b")
        song.serverAlbumArtistIds shouldBe listOf("artist-va")
    }

    @Test
    fun `an item without MusicBrainz tags or artist items maps to empty lists and no ids`() {
        val song = parse().toSong(SYNCED_AT)

        song.mbTrackId shouldBe null
        song.mbArtistIds shouldBe emptyList()
        song.serverArtistIds shouldBe emptyList()
        song.serverAlbumArtistIds shouldBe emptyList()
        song.albumArtists shouldBe emptyList()
        song.serverAlbumId shouldBe "album-1"
    }

    @Test
    fun `a lossless audio stream's bit depth becomes the song's`() {
        parse(mediaStreams = """[{"Type": "Video", "Codec": "mjpeg", "BitDepth": 8}, {"Type": "Audio", "Codec": "flac", "BitDepth": 24}]""")
            .toSong(SYNCED_AT).bitDepth shouldBe 24
    }

    @Test
    fun `a lossy audio stream has no bit depth even when the server reports one`() {
        parse(mediaStreams = """[{"Type": "Audio", "Codec": "mp3", "BitDepth": 16}]""").toSong(SYNCED_AT).bitDepth shouldBe null
        parse(mediaStreams = """[{"Type": "Audio", "Codec": "opus", "BitDepth": 32}]""").toSong(SYNCED_AT).bitDepth shouldBe null
    }

    @Test
    fun `a song without streams or a bit depth has none`() {
        parse().toSong(SYNCED_AT).bitDepth shouldBe null
        parse(mediaStreams = """[{"Type": "Audio", "Codec": "flac"}]""").toSong(SYNCED_AT).bitDepth shouldBe null
    }

    @Test
    fun `a favourite on the server is a favourite as of the sync`() {
        parse(isFavorite = true).toSong(SYNCED_AT).favouritedAt shouldBe SYNCED_AT
    }

    @Test
    fun `a song the server doesn't hold as a favourite isn't one`() {
        parse(isFavorite = false).toSong(SYNCED_AT).favouritedAt shouldBe null
        parse(isFavorite = null).toSong(SYNCED_AT).favouritedAt shouldBe null
    }

    private fun parse(
        albumPrimaryImageTag: String? = "tag-1",
        dateCreated: String? = "2024-03-01T12:34:56.0000000Z",
        mediaStreams: String? = null,
        isFavorite: Boolean? = null
    ): Item {
        val fields =
            listOfNotNull(
                "\"Name\": \"Song\"",
                "\"Id\": \"item-1\"",
                "\"RunTimeTicks\": 1800000000",
                "\"Album\": \"Album\"",
                "\"AlbumId\": \"album-1\"",
                "\"AlbumArtist\": \"Artist\"",
                "\"Artists\": [\"Artist\"]",
                "\"IndexNumber\": 1",
                "\"ParentIndexNumber\": 1",
                "\"ProductionYear\": 2024",
                "\"Genres\": [\"Rock\"]",
                albumPrimaryImageTag?.let { tag -> "\"AlbumPrimaryImageTag\": \"$tag\"" },
                dateCreated?.let { date -> "\"DateCreated\": \"$date\"" },
                mediaStreams?.let { streams -> "\"MediaStreams\": $streams" },
                isFavorite?.let { favourite -> "\"UserData\": {\"IsFavorite\": $favourite}" }
            )
        return S2Json.decodeFromString<Item>(fields.joinToString(separator = ",", prefix = "{", postfix = "}"))
    }

    private companion object {
        val SYNCED_AT = Instant.parse("2026-10-04T09:00:00Z")
    }
}
