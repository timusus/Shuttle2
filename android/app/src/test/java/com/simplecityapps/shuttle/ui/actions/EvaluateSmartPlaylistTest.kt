package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.createPlaylist
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.shuttle.smartplaylist.Limit
import com.simplecityapps.shuttle.smartplaylist.NumberCondition
import com.simplecityapps.shuttle.smartplaylist.NumberField
import com.simplecityapps.shuttle.smartplaylist.Rule
import com.simplecityapps.shuttle.smartplaylist.SmartRules
import com.simplecityapps.shuttle.smartplaylist.SmartSort
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class EvaluateSmartPlaylistTest {
    private val songRepository = FakeSongRepository().apply { applyQueryPredicates = true }
    private val playlistRepository = FakePlaylistRepository()
    private val evaluate = EvaluateSmartPlaylist(songRepository, ObserveFavouriteSongIds(playlistRepository))

    @Test
    fun `filters the library by the rules, then sorts and limits it`() = runTest {
        songRepository.setSongs((1..6).map { id -> createSong(id = id.toLong(), playCount = id) })
        val rules = SmartRules(
            rules = listOf(Rule.Number(NumberField.PlayCount, NumberCondition.GreaterThan(2))),
            sort = SmartSort.PlayCount,
            descending = true,
            limit = Limit.Songs(3),
        )

        evaluate(rules).filterNotNull().first().map { song -> song.id } shouldBe listOf(6L, 5L, 4L)
    }

    @Test
    fun `is null until the library has loaded`() = runTest {
        evaluate(SmartRules()).first() shouldBe null
    }

    @Test
    fun `a favourite rule picks the songs in the Favorites playlist`() = runTest {
        val favorites = createPlaylist(id = 1L)
        playlistRepository.favorites = favorites
        playlistRepository.setSongsForPlaylist(favorites, listOf(createSong(id = 2)))
        songRepository.setSongs((1..3).map { id -> createSong(id = id.toLong()) })

        // The favourites start empty until the playlist has been read
        evaluate(SmartRules(rules = listOf(Rule.Favourite()))).filterNotNull().first { songs -> songs.isNotEmpty() }.map { song -> song.id } shouldBe
            listOf(2L)
    }

    @Test
    fun `a random order holds for its seed as the library changes`() = runTest {
        val songs = (1..20).map { id -> createSong(id = id.toLong()) }
        val rules = SmartRules(sort = SmartSort.Random)
        songRepository.setSongs(songs)
        val first = evaluate(rules, seed = 7).filterNotNull().first()

        songRepository.setSongs(songs.reversed())

        evaluate(rules, seed = 7).filterNotNull().first() shouldBe first
    }
}
