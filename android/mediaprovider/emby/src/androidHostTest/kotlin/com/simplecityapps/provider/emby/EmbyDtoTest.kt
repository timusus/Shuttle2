package com.simplecityapps.provider.emby

import com.simplecityapps.networking.S2Json
import com.simplecityapps.provider.emby.http.AuthenticationResult
import com.simplecityapps.provider.emby.http.Item
import com.simplecityapps.provider.emby.http.QueryResult
import com.simplecityapps.provider.emby.http.User
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test

/** The DTOs decode what a real Emby server sends: the fixtures, extra fields, and fields left out or null. */
class EmbyDtoTest {
    private fun fixture(name: String): String = checkNotNull(javaClass.classLoader.getResource("emby/$name")) { "No fixture $name" }.readText()

    @Test
    fun `every query result fixture decodes`() {
        for (name in listOf("songs.json", "songs_page_1.json", "songs_page_2.json", "playlists.json", "playlist_1_items.json", "long_playlist.json", "long_playlist_items_page_1.json", "long_playlist_items_page_2.json", "empty.json")) {
            S2Json.decodeFromString<QueryResult>(fixture(name))
        }
    }

    @Test
    fun `every item fixture decodes, with Emby's numeric ids as strings`() {
        val item = S2Json.decodeFromString<Item>(fixture("item.json"))

        item.albumId shouldBe "201"
        item.artistItems.first().id shouldBe "301"
        S2Json.decodeFromString<Item>(fixture("loose_item.json")).albumId.shouldBeNull()
    }

    @Test
    fun `sign-in results decode, ignoring the session and server ids`() {
        S2Json.decodeFromString<AuthenticationResult>(fixture("authenticate.json")).accessToken shouldBe "token-2"
        S2Json.decodeFromString<User>(fixture("me.json")).policy?.enableContentDownloading shouldBe true
    }

    @Test
    fun `an item with only an id decodes, its lists empty`() {
        val item = S2Json.decodeFromString<Item>("""{"Id":"1"}""")

        item.name.shouldBeNull()
        item.artists.shouldBeEmpty()
        item.genres.shouldBeEmpty()
    }

    @Test
    fun `null lists decode as empty`() {
        val item = S2Json.decodeFromString<Item>("""{"Id":"1","Artists":null,"ArtistItems":null,"Genres":null}""")

        item.artists.shouldBeEmpty()
        item.artistItems.shouldBeEmpty()
    }

    @Test
    fun `a user without a policy, and a query result without items, decode`() {
        S2Json.decodeFromString<User>("""{"Id":"user-1"}""").policy.shouldBeNull()
        S2Json.decodeFromString<QueryResult>("""{}""") shouldBe QueryResult(items = emptyList(), totalRecordCount = 0)
    }
}
