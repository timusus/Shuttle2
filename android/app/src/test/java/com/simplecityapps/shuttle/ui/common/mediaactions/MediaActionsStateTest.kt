package com.simplecityapps.shuttle.ui.common.mediaactions

import com.simplecityapps.createAlbum
import com.simplecityapps.createAlbumArtist
import com.simplecityapps.shuttle.ui.actions.MediaAction
import com.simplecityapps.shuttle.ui.actions.MediaActionType
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import io.kotest.matchers.shouldBe
import org.junit.Test

/** What tapping each row of a detail screen's actions sheet does with the screen's album or artist. */
class MediaActionsStateTest {
    private val dispatched = mutableListOf<MediaAction>()
    private val state = MediaActionsState { dispatched += it }

    private val album = MediaSelection.Albums(createAlbum(name = "Phase Garden"))
    private val artist = MediaSelection.AlbumArtists(createAlbumArtist(name = "Juniper Static"))

    @Test
    fun `an album's play next, add to queue and edit tags run on the whole album`() {
        state.perform(MediaActionType.PlayNext, album)
        state.perform(MediaActionType.AddToQueue, album)
        state.perform(MediaActionType.EditTags, album)

        dispatched shouldBe listOf(MediaAction.PlayNext(album), MediaAction.AddToQueue(album), MediaAction.EditTags(album))
    }

    @Test
    fun `an artist's play next and edit tags run on every song by the artist`() {
        state.perform(MediaActionType.PlayNext, artist)
        state.perform(MediaActionType.EditTags, artist)

        dispatched shouldBe listOf(MediaAction.PlayNext(artist), MediaAction.EditTags(artist))
    }

    @Test
    fun `add to playlist opens the playlist picker for the album`() {
        state.perform(MediaActionType.AddToPlaylist, album)

        state.playlistPicker shouldBe album
        dispatched shouldBe emptyList()
    }
}
