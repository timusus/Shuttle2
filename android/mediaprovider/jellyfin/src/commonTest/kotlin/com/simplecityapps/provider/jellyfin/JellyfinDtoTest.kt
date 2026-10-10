package com.simplecityapps.provider.jellyfin

import com.simplecityapps.mediaprovider.server.mediabrowser.AuthenticationResult
import com.simplecityapps.mediaprovider.server.mediabrowser.Item
import com.simplecityapps.mediaprovider.server.mediabrowser.QueryResult
import com.simplecityapps.mediaprovider.server.mediabrowser.User
import com.simplecityapps.mediaprovider.server.readFixture
import com.simplecityapps.networking.S2Json
import com.simplecityapps.provider.jellyfin.http.QuickConnectResult
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** The DTOs decode what a real Jellyfin server sends: the fixtures, extra fields, and fields left out or null. */
class JellyfinDtoTest {
    private fun fixture(name: String): String = readFixture("jellyfin/$name")

    @Test
    fun `every query result fixture decodes`() {
        for (name in listOf("songs.json", "songs_page_1.json", "songs_page_2.json", "playlists.json", "playlist_1_items.json", "long_playlist.json", "long_playlist_items_page_1.json", "long_playlist_items_page_2.json", "empty.json")) {
            S2Json.decodeFromString<QueryResult>(fixture(name))
        }
    }

    @Test
    fun `every item fixture decodes`() {
        S2Json.decodeFromString<Item>(fixture("item.json")).albumId shouldBe "album-1"
        S2Json.decodeFromString<Item>(fixture("loose_item.json")).albumId.shouldBeNull()
    }

    @Test
    fun `sign-in results decode - ignoring the session and server ids`() {
        S2Json.decodeFromString<AuthenticationResult>(fixture("authenticate.json")).accessToken shouldBe "token-2"
        S2Json.decodeFromString<AuthenticationResult>(fixture("quickconnect_authenticate.json")).user.policy?.enableContentDownloading shouldBe true
        S2Json.decodeFromString<User>(fixture("me.json")).id shouldBe "user-1"
    }

    @Test
    fun `quick connect results decode`() {
        S2Json.decodeFromString<QuickConnectResult>(fixture("quickconnect_initiate.json")).code shouldBe "123456"
        S2Json.decodeFromString<QuickConnectResult>(fixture("quickconnect_pending.json")).authenticated shouldBe false
        S2Json.decodeFromString<QuickConnectResult>(fixture("quickconnect_authenticated.json")).authenticated shouldBe true
        S2Json.decodeFromString<Boolean>(fixture("quickconnect_enabled_true.json")) shouldBe true
    }

    @Test
    fun `an item with only an id decodes - its lists empty`() {
        val item = S2Json.decodeFromString<Item>("""{"Id":"item-1"}""")

        item.name.shouldBeNull()
        item.artists.shouldBeEmpty()
        item.genres.shouldBeEmpty()
    }

    @Test
    fun `null lists decode as empty`() {
        val item = S2Json.decodeFromString<Item>("""{"Id":"item-1","Artists":null,"ArtistItems":null,"Genres":null}""")

        item.artists.shouldBeEmpty()
        item.artistItems.shouldBeEmpty()
    }

    @Test
    fun `a user without a policy - and a query result without items - decode`() {
        S2Json.decodeFromString<User>("""{"Id":"user-1"}""").policy.shouldBeNull()
        S2Json.decodeFromString<QueryResult>("""{}""") shouldBe QueryResult(items = emptyList(), totalRecordCount = 0)
    }
}
