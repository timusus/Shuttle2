package com.simplecityapps.shuttle.ui.screens.library

import com.simplecityapps.shuttle.model.SmartPlaylistId
import com.simplecityapps.shuttle.ui.text.StringKey
import io.kotest.matchers.shouldBe
import org.junit.Test

class LibraryRoutesTest {

    @Test
    fun `every smart playlist has a route that resolves back to it`() {
        SmartPlaylistId.entries.map { SmartPlaylistId.fromId(it.smartPlaylist.route().smartPlaylistId) } shouldBe SmartPlaylistId.entries
    }

    @Test
    fun `every smart playlist has a display name`() {
        SmartPlaylistId.History.nameKey shouldBe StringKey.PLAYLIST_TITLE_HISTORY
        SmartPlaylistId.RecentlyAdded.nameKey shouldBe StringKey.PLAYLIST_TITLE_RECENTLY_ADDED
        SmartPlaylistId.MostPlayed.nameKey shouldBe StringKey.PLAYLIST_TITLE_MOST_PLAYED
        SmartPlaylistId.Favourites.nameKey shouldBe StringKey.PLAYLIST_TITLE_FAVORITES
    }
}
