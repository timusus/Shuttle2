package com.simplecityapps.provider.plex

import com.simplecityapps.networking.S2Json
import com.simplecityapps.provider.plex.http.Metadata
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.serverArtistId
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant

class MetadataToSongTest {
    @Test
    fun `a track with its own artist credits it - with the album's artist and ids and its MusicBrainz recording`() {
        // As a Plex Media Server sends a track fetched with includeGuids=1
        val metadata =
            S2Json.decodeFromString<Metadata>(
                """
                {"key": "/library/metadata/101", "guid": "plex://track/1", "ratingKey": "101",
                 "title": "Song", "originalTitle": "A feat. B", "grandparentTitle": "Various Artists",
                 "parentRatingKey": "100", "grandparentRatingKey": "99",
                 "Guid": [{"id": "mbid://5B11F4CE-A62D-471E-81FC-A69A8278C7DA"}, {"id": "plex://track/1"}]}
                """.trimIndent()
            )

        val song = metadata.toSong(MediaProviderType.Plex, SYNCED_AT)

        song.artists shouldBe listOf("A feat. B")
        song.artistsTag shouldBe listOf("A feat. B")
        song.artistDisplay shouldBe "A feat. B"
        song.albumArtist shouldBe "Various Artists"
        song.albumArtists shouldBe listOf("Various Artists")
        song.serverAlbumId shouldBe "100"
        song.serverArtistIds shouldBe emptyList()
        song.serverAlbumArtistIds shouldBe listOf("99")
        song.mbTrackId shouldBe "5b11f4ce-a62d-471e-81fc-a69a8278c7da"
        song.mbAlbumId shouldBe null
        song.compilation shouldBe null
    }

    @Test
    fun `a track by its album's artist is credited to it - with the album artist's id`() {
        val metadata =
            S2Json.decodeFromString<Metadata>(
                """{"key": "/library/metadata/101", "guid": "plex://track/1", "grandparentTitle": "Radiohead", "parentRatingKey": "100", "grandparentRatingKey": "99"}"""
            )

        val song = metadata.toSong(MediaProviderType.Plex, SYNCED_AT)

        song.artists shouldBe listOf("Radiohead")
        song.artistDisplay shouldBe "Radiohead"
        song.serverArtistIds shouldBe listOf("99")
        song.serverAlbumArtistIds shouldBe listOf("99")
        song.serverAlbumId shouldBe "100"
        song.mbTrackId shouldBe null
    }

    @Test
    fun `an album artist naming several artists keeps its id for the album artist - and pairs none with the split track artists`() {
        val metadata =
            S2Json.decodeFromString<Metadata>(
                """{"key": "/library/metadata/101", "grandparentTitle": "A; B", "parentRatingKey": "100", "grandparentRatingKey": "99"}"""
            )

        val song = metadata.toSong(MediaProviderType.Plex, SYNCED_AT)

        song.artistsTag shouldBe listOf("A", "B")
        song.serverArtistIds shouldBe emptyList()
        song.serverAlbumArtistIds shouldBe listOf("99")
        // #637: the album artist's id is the one Plex artist "A; B", which has no page of its own now that the album's artists are A and B
        song.albumArtistKeys.map { key -> song.serverArtistId(key) } shouldBe listOf(null, null)
        song.artistCredits.map { credit -> credit.name to song.serverArtistId(credit.groupKey) } shouldBe listOf("A" to null, "B" to null)
        // The album keeps its key: the album artist isn't split, and the album is the server's
        song.albumArtist shouldBe "A; B"
        song.serverAlbumId shouldBe "100"
    }

    @Test
    fun `an artist string naming several artists is split as the other sources split it`() {
        val metadata =
            S2Json.decodeFromString<Metadata>(
                """{"key": "/library/metadata/101", "originalTitle": "A; B / C", "grandparentTitle": "AC/DC | Queen"}"""
            )

        val song = metadata.toSong(MediaProviderType.Plex, SYNCED_AT)

        song.artists shouldBe listOf("A", "B", "C")
        song.artistsTag shouldBe listOf("A", "B", "C")
        song.albumArtists shouldBe listOf("AC/DC", "Queen")
        song.artistDisplay shouldBe "A; B / C"
    }

    @Test
    fun `a track with no track or disc number has none rather than zero`() {
        val song = S2Json.decodeFromString<Metadata>("""{"key": "/library/metadata/1"}""").toSong(MediaProviderType.Plex, SYNCED_AT)

        song.track shouldBe null
        song.disc shouldBe null
    }

    @Test
    fun `a track's track and disc numbers are kept`() {
        val song = S2Json.decodeFromString<Metadata>("""{"key": "/library/metadata/1", "index": 3, "parentIndex": 2}""").toSong(MediaProviderType.Plex, SYNCED_AT)

        song.track shouldBe 3
        song.disc shouldBe 2
    }

    @Test
    fun `a track without a duration takes its media's`() {
        val song = S2Json.decodeFromString<Metadata>("""{"key": "/library/metadata/1", "Media": [{"id": 1}, {"id": 2, "duration": 215000}]}""").toSong(MediaProviderType.Plex, SYNCED_AT)

        song.duration shouldBe 215_000
    }

    @Test
    fun `a track's own duration wins over its media's`() {
        val song = S2Json.decodeFromString<Metadata>("""{"key": "/library/metadata/1", "duration": 180000, "Media": [{"id": 1, "duration": 215000}]}""").toSong(MediaProviderType.Plex, SYNCED_AT)

        song.duration shouldBe 180_000
    }

    @Test
    fun `the bit depth is the audio stream's - for a lossless codec only`() {
        fun bitDepth(codec: String) = S2Json.decodeFromString<Metadata>(
            """
            {"key": "/library/metadata/101", "guid": "plex://track/1", "Media": [{"audioCodec": "$codec", "Part": [{"key": "/p", "Stream": [
              {"streamType": 1, "bitDepth": 8}, {"streamType": 2, "codec": "$codec", "bitDepth": 24}]}]}]}
            """.trimIndent()
        ).toSong(MediaProviderType.Plex, SYNCED_AT).bitDepth

        bitDepth("flac") shouldBe 24
        bitDepth("mp3") shouldBe null
    }

    @Test
    fun `a track without streams has no bit depth`() {
        parse(addedAt = null, updatedAt = null).toSong(MediaProviderType.Plex, SYNCED_AT).bitDepth shouldBe null
    }

    @Test
    fun `the song's dates are when it was added to the server`() {
        val song = parse(addedAt = 1_700_000_000, updatedAt = 1_800_000_000).toSong(MediaProviderType.Plex, SYNCED_AT)

        song.dateAdded shouldBe Instant.fromEpochSeconds(1_700_000_000)
        song.lastModified shouldBe Instant.fromEpochSeconds(1_700_000_000)
    }

    @Test
    fun `the song's modified date falls back to when it was last updated - its date added doesn't`() {
        val song = parse(addedAt = null, updatedAt = 1_800_000_000).toSong(MediaProviderType.Plex, SYNCED_AT)

        song.lastModified shouldBe Instant.fromEpochSeconds(1_800_000_000)
        song.dateAdded shouldBe null
    }

    @Test
    fun `a song with no dates is left for the importer to fill in`() {
        parse(addedAt = null, updatedAt = null).toSong(MediaProviderType.Plex, SYNCED_AT).run { lastModified to dateAdded } shouldBe (null to null)
    }

    @Test
    fun `plex songs carry no artwork version`() {
        parse(addedAt = 1_700_000_000, updatedAt = null).toSong(MediaProviderType.Plex, SYNCED_AT).artworkVersion shouldBe null
    }

    @Test
    fun `the song's audio codec comes from its Media entry`() {
        parse(addedAt = null, updatedAt = null, media = "{\"id\": 1, \"audioCodec\": \"alac\", \"Part\": []}").toSong(MediaProviderType.Plex, SYNCED_AT).audioCodec shouldBe "alac"
    }

    @Test
    fun `a song with no Media entries has no audio codec`() {
        parse(addedAt = null, updatedAt = null).toSong(MediaProviderType.Plex, SYNCED_AT).audioCodec shouldBe null
    }

    @Test
    fun `a track rated 5 stars is a favourite from when it was rated`() {
        parse(addedAt = null, updatedAt = null, userRating = 10.0, lastRatedAt = 1_759_305_600).toSong(MediaProviderType.Plex, SYNCED_AT).favouritedAt shouldBe
            Instant.fromEpochSeconds(1_759_305_600)
    }

    @Test
    fun `a track rated 5 stars with no rating time is a favourite as of the sync`() {
        parse(addedAt = null, updatedAt = null, userRating = 10.0).toSong(MediaProviderType.Plex, SYNCED_AT).favouritedAt shouldBe SYNCED_AT
    }

    @Test
    fun `a track rated below 5 stars or not rated isn't a favourite`() {
        parse(addedAt = null, updatedAt = null, userRating = 6.0, lastRatedAt = 1_759_305_600).toSong(MediaProviderType.Plex, SYNCED_AT).favouritedAt shouldBe null
        parse(addedAt = null, updatedAt = null).toSong(MediaProviderType.Plex, SYNCED_AT).favouritedAt shouldBe null
    }

    @Test
    fun `the track's view count and last viewed time become the song's plays and last played`() {
        val metadata = S2Json.decodeFromString<Metadata>("""{"key": "/library/metadata/1", "viewCount": 4, "lastViewedAt": 1790000000}""")

        val song = metadata.toSong(MediaProviderType.Plex, SYNCED_AT)

        song.playCount shouldBe 4
        song.lastPlayed shouldBe Instant.fromEpochSeconds(1790000000)
    }

    @Test
    fun `a track never viewed has no plays and no last played time`() {
        val song = S2Json.decodeFromString<Metadata>("""{"key": "/library/metadata/1"}""").toSong(MediaProviderType.Plex, SYNCED_AT)

        song.playCount shouldBe 0
        song.lastPlayed shouldBe null
    }

    private fun parse(
        addedAt: Long?,
        updatedAt: Long?,
        media: String? = null,
        userRating: Double? = null,
        lastRatedAt: Long? = null
    ): Metadata {
        val fields =
            listOfNotNull(
                "\"key\": \"/library/metadata/1\"",
                "\"type\": \"track\"",
                "\"guid\": \"plex://track/1\"",
                "\"index\": 1",
                "\"parentIndex\": 1",
                "\"title\": \"Song\"",
                "\"duration\": 180000",
                "\"parentTitle\": \"Album\"",
                "\"grandparentTitle\": \"Artist\"",
                "\"parentYear\": 2024",
                "\"Media\": [${media.orEmpty()}]",
                addedAt?.let { seconds -> "\"addedAt\": $seconds" },
                updatedAt?.let { seconds -> "\"updatedAt\": $seconds" },
                userRating?.let { rating -> "\"userRating\": $rating" },
                lastRatedAt?.let { seconds -> "\"lastRatedAt\": $seconds" }
            )
        return S2Json.decodeFromString<Metadata>(fields.joinToString(separator = ",", prefix = "{", postfix = "}"))
    }

    private companion object {
        val SYNCED_AT = Instant.parse("2026-10-04T09:00:00Z")
    }
}
