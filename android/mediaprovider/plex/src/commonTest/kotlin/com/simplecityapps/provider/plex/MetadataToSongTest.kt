package com.simplecityapps.provider.plex

import com.simplecityapps.networking.S2Json
import com.simplecityapps.provider.plex.http.Metadata
import com.simplecityapps.shuttle.model.MediaProviderType
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant

class MetadataToSongTest {
    @Test
    fun `the song's dates are when it was added to the server`() {
        val song = parse(addedAt = 1_700_000_000, updatedAt = 1_800_000_000).toSong(MediaProviderType.Plex)

        song.dateAdded shouldBe Instant.fromEpochSeconds(1_700_000_000)
        song.lastModified shouldBe Instant.fromEpochSeconds(1_700_000_000)
    }

    @Test
    fun `the song's modified date falls back to when it was last updated - its date added doesn't`() {
        val song = parse(addedAt = null, updatedAt = 1_800_000_000).toSong(MediaProviderType.Plex)

        song.lastModified shouldBe Instant.fromEpochSeconds(1_800_000_000)
        song.dateAdded shouldBe null
    }

    @Test
    fun `a song with no dates is left for the importer to fill in`() {
        parse(addedAt = null, updatedAt = null).toSong(MediaProviderType.Plex).run { lastModified to dateAdded } shouldBe (null to null)
    }

    @Test
    fun `plex songs carry no artwork version`() {
        parse(addedAt = 1_700_000_000, updatedAt = null).toSong(MediaProviderType.Plex).artworkVersion shouldBe null
    }

    @Test
    fun `the song's audio codec comes from its Media entry`() {
        parse(addedAt = null, updatedAt = null, media = "{\"id\": 1, \"audioCodec\": \"alac\", \"Part\": []}").toSong(MediaProviderType.Plex).audioCodec shouldBe "alac"
    }

    @Test
    fun `a song with no Media entries has no audio codec`() {
        parse(addedAt = null, updatedAt = null).toSong(MediaProviderType.Plex).audioCodec shouldBe null
    }

    private fun parse(
        addedAt: Long?,
        updatedAt: Long?,
        media: String? = null
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
                updatedAt?.let { seconds -> "\"updatedAt\": $seconds" }
            )
        return S2Json.decodeFromString<Metadata>(fields.joinToString(separator = ",", prefix = "{", postfix = "}"))
    }
}
