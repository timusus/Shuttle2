package com.simplecityapps.shuttle.shared.intents

import com.simplecityapps.createPlaylist
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.ui.actions.MediaAction
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.actions.ObservePlaylists
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class AppIntentLibraryTest {
    private val repository = FakePlaylistRepository()
    private val library = AppIntentLibrary(ObservePlaylists(repository))

    private val mix = createPlaylist(id = 1, name = "Mix")
    private val focus = createPlaylist(id = 2, name = "Focus")

    @Test
    fun playlistsAreTheLibrarysCurrentOnes() = runTest {
        repository.setPlaylists(listOf(mix, focus))

        library.playlists() shouldBe listOf(mix, focus)
    }

    @Test
    fun shuffleAllShufflesTheWholeLibrary() {
        library.shuffleAll() shouldBe MediaAction.Shuffle(MediaSelection.SongsMatching(SongQuery.All()))
    }

    @Test
    fun playPlaylistPlaysOrShufflesThePlaylistWithThatId() = runTest {
        repository.setPlaylists(listOf(mix, focus))

        library.playPlaylist(id = 2, shuffled = false) shouldBe MediaAction.Play(MediaSelection.Playlists(focus))
        library.playPlaylist(id = 2, shuffled = true) shouldBe MediaAction.Shuffle(MediaSelection.Playlists(focus))
    }

    @Test
    fun playPlaylistIsNullForAPlaylistThatsGone() = runTest {
        repository.setPlaylists(listOf(mix))

        library.playPlaylist(id = 2, shuffled = false).shouldBeNull()
    }
}
