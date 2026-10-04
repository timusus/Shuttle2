package com.simplecityapps.provider.plex

import com.simplecityapps.mediaprovider.server.readFixture
import com.simplecityapps.networking.S2Json
import com.simplecityapps.provider.plex.http.AuthenticationResult
import com.simplecityapps.provider.plex.http.Metadata
import com.simplecityapps.provider.plex.http.QueryResult
import com.simplecityapps.shuttle.model.MediaProviderType
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant

/** The DTOs decode what a real Plex server and plex.tv send: the fixtures, extra fields, and fields left out or null. */
class PlexDtoTest {
    private fun fixture(name: String): String = readFixture("plex/$name")

    @Test
    fun `every query result fixture decodes`() {
        for (name in listOf("empty.json", "sections.json", "sections_no_music.json", "songs.json", "songs_page_1.json", "songs_page_2.json")) {
            S2Json.decodeFromString<QueryResult>(fixture(name))
        }
    }

    @Test
    fun `a sign-in result decodes - its numeric user id read as text`() {
        val user = S2Json.decodeFromString<AuthenticationResult>(fixture("sign_in.json")).user

        user.id shouldBe "12345678"
        user.authToken shouldBe "token-2"
    }

    @Test
    fun `a sign-in result with a string user id decodes`() {
        S2Json.decodeFromString<AuthenticationResult>("""{"user":{"id":"12345678","authToken":"token-2"}}""").user.id shouldBe "12345678"
    }

    @Test
    fun `a track with only a key and guid decodes - and maps to a song`() {
        val metadata = S2Json.decodeFromString<Metadata>("""{"key":"/library/metadata/1","guid":"plex://track/1"}""")

        metadata.media.shouldBeEmpty()
        metadata.duration.shouldBeNull()
        with(metadata.toSong(MediaProviderType.Plex, syncedAt = Instant.fromEpochSeconds(0))) {
            name.shouldBeNull()
            artists.shouldBeEmpty()
            duration shouldBe 0
            size shouldBe 0
            externalId.shouldBeNull()
        }
    }

    @Test
    fun `a part larger than 2 GB keeps its size`() {
        val metadata = S2Json.decodeFromString<Metadata>(
            """{"key":"/library/metadata/1","guid":"plex://track/1","Media":[{"id":1,"Part":[{"id":1,"key":"/library/parts/1/file.flac","size":3000000000}]}]}"""
        )

        metadata.toSong(MediaProviderType.Plex, syncedAt = Instant.fromEpochSeconds(0)).size shouldBe 3_000_000_000
    }

    @Test
    fun `a media container without metadata or directories decodes - and so does an empty response`() {
        with(S2Json.decodeFromString<QueryResult>("""{"MediaContainer":{"size":0}}""").mediaContainer) {
            metadata.shouldBeNull()
            directories.shouldBeNull()
            totalSize.shouldBeNull()
        }
        S2Json.decodeFromString<QueryResult>("""{}""").mediaContainer.metadata.shouldBeNull()
    }
}
