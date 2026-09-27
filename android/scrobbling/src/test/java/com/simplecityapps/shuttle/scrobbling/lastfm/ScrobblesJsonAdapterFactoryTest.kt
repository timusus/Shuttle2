package com.simplecityapps.shuttle.scrobbling.lastfm

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test

class ScrobblesJsonAdapterFactoryTest {
    private val adapter = Moshi.Builder()
        .add(ScrobblesJsonAdapterFactory())
        .addLast(KotlinJsonAdapterFactory())
        .build()
        .adapter(LastFmScrobbleResponse::class.java)

    @Test
    fun `a single scrobble is a bare object, not a one-item array`() {
        val response = adapter.fromJson(
            """{"scrobbles":{"scrobble":{"ignoredMessage":{"code":"0"}}}}"""
        )

        response?.scrobbles?.scrobble?.size shouldBe 1
        response?.scrobbles?.scrobble?.single()?.isIgnored shouldBe false
    }

    @Test
    fun `multiple scrobbles are a real array`() {
        val response = adapter.fromJson(
            """{"scrobbles":{"scrobble":[{"ignoredMessage":{"code":"0"}},{"ignoredMessage":{"code":"1"}}]}}"""
        )

        val results = response?.scrobbles?.scrobble.orEmpty()
        results.size shouldBe 2
        results[0].isIgnored shouldBe false
        results[1].isIgnored shouldBe true
    }

    @Test
    fun `a null scrobble field parses to an empty list`() {
        val response = adapter.fromJson("""{"scrobbles":{"scrobble":null}}""")

        response?.scrobbles?.scrobble.orEmpty().shouldBeEmpty()
    }

    @Test
    fun `a top-level error response carries no scrobbles`() {
        val response = adapter.fromJson("""{"error":9,"message":"Invalid session key"}""")

        response?.scrobbles.shouldBeNull()
        response?.error shouldBe 9
        response?.message shouldBe "Invalid session key"
    }
}
