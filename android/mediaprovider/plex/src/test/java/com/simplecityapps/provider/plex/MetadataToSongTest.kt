package com.simplecityapps.provider.plex

import com.simplecityapps.provider.plex.http.Metadata
import com.simplecityapps.shuttle.model.MediaProviderType
import com.squareup.moshi.Moshi
import io.kotest.matchers.shouldBe
import kotlin.time.Instant
import org.junit.Test

class MetadataToSongTest {
    private val adapter = Moshi.Builder().build().adapter(Metadata::class.java)

    @Test
    fun `the song's date is when it was added to the server`() {
        parse(addedAt = 1_700_000_000, updatedAt = 1_800_000_000).toSong(MediaProviderType.Plex).lastModified shouldBe
            Instant.fromEpochSeconds(1_700_000_000)
    }

    @Test
    fun `the song's date falls back to when it was last updated`() {
        parse(addedAt = null, updatedAt = 1_800_000_000).toSong(MediaProviderType.Plex).lastModified shouldBe
            Instant.fromEpochSeconds(1_800_000_000)
    }

    @Test
    fun `a song with no dates is left for the importer to fill in`() {
        parse(addedAt = null, updatedAt = null).toSong(MediaProviderType.Plex).lastModified shouldBe null
    }

    @Test
    fun `plex songs carry no artwork version`() {
        parse(addedAt = 1_700_000_000, updatedAt = null).toSong(MediaProviderType.Plex).artworkVersion shouldBe null
    }

    private fun parse(
        addedAt: Long?,
        updatedAt: Long?
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
                "\"Media\": []",
                addedAt?.let { seconds -> "\"addedAt\": $seconds" },
                updatedAt?.let { seconds -> "\"updatedAt\": $seconds" }
            )
        return adapter.fromJson(fields.joinToString(separator = ",", prefix = "{", postfix = "}"))!!
    }
}
