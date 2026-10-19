package com.simplecityapps.shuttle.ui.screens.search

import com.simplecityapps.createAlbum
import com.simplecityapps.createAlbumArtist
import com.simplecityapps.createSong
import com.simplecityapps.mediaprovider.search.SearchHit
import com.simplecityapps.mediaprovider.search.SearchQuery
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.screens.search.SearchCategory.Albums
import com.simplecityapps.shuttle.ui.screens.search.SearchCategory.Artists
import com.simplecityapps.shuttle.ui.screens.search.SearchCategory.Songs
import com.simplecityapps.shuttle.ui.screens.search.SearchResultItem.Header
import com.simplecityapps.shuttle.ui.screens.search.SearchResultItem.Hit
import com.simplecityapps.shuttle.ui.screens.search.SearchResultItem.TopResult
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class SearchResultItemsTest {
    private val query = SearchQuery.parse("x")

    private fun <T> hits(count: Int, make: (Int) -> T) = (0 until count).map { SearchHit(make(it), query) }

    private val songs = hits(8) { createSong(id = it.toLong(), name = "Song $it") }
    private val albums = hits(2) { createAlbum("Album $it", "Artist") }
    private val artist = createAlbumArtist(name = "Artist")

    @Test
    fun `a long section shows its first five rows under a See all header`() {
        SearchResults(artists = listOf(SearchHit(artist, query)), songs = songs, top = Artists).resultItems() shouldBe listOf(
            TopResult(Artists),
            Header(Songs, seeAll = true),
            Hit(Songs, 0),
            Hit(Songs, 1),
            Hit(Songs, 2),
            Hit(Songs, 3),
            Hit(Songs, 4),
        )
    }

    @Test
    fun `See all shows the whole section without the link`() {
        val items = SearchResults(artists = listOf(SearchHit(artist, query)), songs = songs, top = Artists).resultItems(expanded = Songs)

        items[1] shouldBe Header(Songs, seeAll = false)
        items.drop(2) shouldBe (0 until 8).map { Hit(Songs, it) }
    }

    @Test
    fun `rows under the top result carry their index in the whole group`() {
        SearchResults(albums = albums, songs = songs.take(3), top = Songs).resultItems() shouldBe listOf(
            TopResult(Songs),
            Header(Albums, seeAll = false),
            Hit(Albums, 0),
            Hit(Albums, 1),
            Header(Songs, seeAll = false),
            Hit(Songs, 1),
            Hit(Songs, 2),
        )
    }

    @Test
    fun `no top result leads without a top category`() {
        SearchResults(albums = albums).resultItems().first() shouldBe Header(Albums, seeAll = false)
    }

    @Test
    fun `a selection is the hit at its index`() {
        val results = SearchResults(artists = listOf(SearchHit(artist, query)), albums = albums, songs = songs)

        results.selection(Artists, 0) shouldBe MediaSelection.AlbumArtists(artist)
        results.selection(Albums, 1) shouldBe MediaSelection.Albums(albums[1].item)
        results.selection(Songs, 3) shouldBe MediaSelection.Songs(songs[3].item)
    }

    @Test
    fun `an album artist counts albums`() {
        createAlbumArtist(name = "Nightjar", albumCount = 3, songCount = 30).resultCount() shouldBe ResultCount(ResultCount.Kind.Albums, 3)
    }

    @Test
    fun `a credit-only artist counts songs`() {
        createAlbumArtist(name = "Chris Martin", albumCount = 0, songCount = 2, appearsOnCount = 1).resultCount() shouldBe ResultCount(ResultCount.Kind.Songs, 2)
    }
}
