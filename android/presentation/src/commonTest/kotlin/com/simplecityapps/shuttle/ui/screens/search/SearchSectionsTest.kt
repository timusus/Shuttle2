package com.simplecityapps.shuttle.ui.screens.search

import com.simplecityapps.createAlbum
import com.simplecityapps.createAlbumArtist
import com.simplecityapps.createSong
import com.simplecityapps.mediaprovider.search.SearchHit
import com.simplecityapps.mediaprovider.search.SearchQuery
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class SearchSectionsTest {
    private val query = SearchQuery.parse("x")

    private fun <T> hits(count: Int, make: (Int) -> T) = (0 until count).map { SearchHit(make(it), query) }

    private val songs = hits(8) { createSong(id = it.toLong(), name = "Song $it") }
    private val albums = hits(4) { createAlbum("Album $it", "Artist") }
    private val artists = hits(2) { createAlbumArtist(name = "Artist $it") }

    @Test
    fun `each section is capped at its limit with more to see`() {
        SearchResults(artists = artists, albums = albums, songs = songs).sections() shouldBe listOf(
            SearchSection(SearchCategory.Artists, from = 0, until = 2, total = 2),
            SearchSection(SearchCategory.Albums, from = 0, until = 3, total = 4),
            SearchSection(SearchCategory.Songs, from = 0, until = 5, total = 8),
        )
    }

    @Test
    fun `the top group leaves out its first hit`() {
        val sections = SearchResults(albums = albums, songs = songs, top = SearchCategory.Songs).sections()

        sections.last() shouldBe SearchSection(SearchCategory.Songs, from = 1, until = 6, total = 8)
    }

    @Test
    fun `a top group with only the top hit has no section`() {
        SearchResults(artists = artists.take(1), songs = songs, top = SearchCategory.Artists).sections().map { it.category } shouldBe listOf(SearchCategory.Songs)
    }

    @Test
    fun `the expanded section shows every hit`() {
        val songs = SearchResults(albums = albums, songs = songs).sections(expanded = SearchCategory.Songs).last()

        songs.until shouldBe 8
        songs.hasMore shouldBe false
    }

    @Test
    fun `the only section with results shows every hit`() {
        SearchResults(songs = songs).sections().single() shouldBe SearchSection(SearchCategory.Songs, from = 0, until = 8, total = 8)
    }

    @Test
    fun `hasMore is true only past the limit`() {
        SearchSection(SearchCategory.Albums, 0, 3, 4).hasMore shouldBe true
        SearchSection(SearchCategory.Albums, 0, 3, 3).hasMore shouldBe false
    }
}
