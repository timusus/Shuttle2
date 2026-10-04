package com.simplecityapps.shuttle.scrobbling.lastfm

import com.simplecityapps.networking.S2Json
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.serialization.decodeFromString

class LastFmScrobbleResponseTest {
    private fun decode(json: String) = S2Json.decodeFromString<LastFmScrobbleResponse>(json)

    @Test
    fun `a single scrobble is a bare object - not a one-item array`() {
        val response = decode("""{"scrobbles":{"scrobble":{"ignoredMessage":{"code":"0"}}}}""")

        response.scrobbles?.scrobble?.size shouldBe 1
        response.scrobbles?.scrobble?.single()?.ignoredMessage?.code shouldBe "0"
    }

    @Test
    fun `multiple scrobbles are a real array`() {
        val response = decode(
            """{"scrobbles":{"scrobble":[{"ignoredMessage":{"code":"0"}},{"ignoredMessage":{"code":"1"}}]}}"""
        )

        val results = response.scrobbles?.scrobble.orEmpty()
        results.size shouldBe 2
        results[0].ignoredMessage?.code shouldBe "0"
        results[1].ignoredMessage?.code shouldBe "1"
    }

    @Test
    fun `a null scrobble field parses to an empty list`() {
        val response = decode("""{"scrobbles":{"scrobble":null}}""")

        response.scrobbles?.scrobble.orEmpty().shouldBeEmpty()
    }

    @Test
    fun `a top-level error response carries no scrobbles`() {
        val response = decode("""{"error":9,"message":"Invalid session key"}""")

        response.scrobbles.shouldBeNull()
        response.error shouldBe 9
        response.message shouldBe "Invalid session key"
    }
}
