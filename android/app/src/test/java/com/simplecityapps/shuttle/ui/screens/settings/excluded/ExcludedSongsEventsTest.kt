package com.simplecityapps.shuttle.ui.screens.settings.excluded

import com.simplecityapps.createSong
import io.kotest.matchers.shouldBe
import org.junit.Test

class ExcludedSongsEventsTest {
    private val song = createSong(id = 2, name = "Beta").copy(blacklisted = true)

    @Test
    fun `opening a song's menu shows it`() {
        onSongMenuEvent(SongMenuEvent.Open(song)) shouldBe SongMenuStep(openSongId = 2L)
    }

    @Test
    fun `dismissing the menu closes it`() {
        onSongMenuEvent(SongMenuEvent.Dismiss) shouldBe SongMenuStep(openSongId = null)
    }

    @Test
    fun `including a song closes the menu and includes it`() {
        onSongMenuEvent(SongMenuEvent.Include(song)) shouldBe
            SongMenuStep(openSongId = null, event = ExcludedSongsEvent.Include(song))
    }

    @Test
    fun `each event reaches its own handler`() {
        val reached = mutableListOf<String>()
        val callbacks = ExcludedSongsCallbacks(
            onNavigateUp = { reached += "up" },
            onInclude = { reached += "include ${it.id}" },
            onIncludeAll = { reached += "include all" }
        )

        listOf(ExcludedSongsEvent.NavigateUp, ExcludedSongsEvent.Include(song), ExcludedSongsEvent.IncludeAll)
            .forEach(callbacks::onEvent)

        reached shouldBe listOf("up", "include 2", "include all")
    }
}
