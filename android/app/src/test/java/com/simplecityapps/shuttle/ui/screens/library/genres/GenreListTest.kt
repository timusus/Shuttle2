package com.simplecityapps.shuttle.ui.screens.library.genres

import androidx.compose.ui.test.junit4.createComposeRule
import com.simplecityapps.createGenre
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.shuttle.model.MediaProviderType
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GenreListTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val robot = GenreListRobot(composeTestRule)

    // region View state rendering

    @Test
    fun `loading state shows loading indicator`() {
        robot.setContent(loadingGenreList)
        robot.assertTextDisplayed("Loading…")
    }

    @Test
    fun `scanning state shows scan progress message`() {
        robot.setContent(scanningGenreList(Progress(10, 100)))
        robot.assertTextDisplayed("Scanning your library")
    }

    @Test
    fun `scanning state with null progress shows scan message`() {
        robot.setContent(scanningGenreList())
        robot.assertTextDisplayed("Scanning your library")
    }

    @Test
    fun `ready state with empty genres shows empty message`() {
        robot.setContent(emptyGenreList())
        robot.assertTextDisplayed("No genres")
    }

    @Test
    fun `ready state shows genre name`() {
        robot.setContent(readyGenreList(genres = listOf(createGenre(name = "Electronic"))))
        robot.assertTextDisplayed("Electronic")
    }

    @Test
    fun `ready state shows song count for single song`() {
        robot.setContent(readyGenreList(genres = listOf(createGenre(name = "Ambient", songCount = 1))))
        robot.assertTextDisplayed("1 song")
    }

    @Test
    fun `ready state shows song count for multiple songs`() {
        robot.setContent(readyGenreList(genres = listOf(createGenre(name = "Rock", songCount = 245))))
        robot.assertTextDisplayed("245 songs")
    }

    @Test
    fun `ready state shows multiple genres`() {
        robot.setContent(
            readyGenreList(
                genres = listOf(
                    createGenre(name = "Rock"),
                    createGenre(name = "Jazz"),
                    createGenre(name = "Blues"),
                )
            )
        )
        robot.assertTextDisplayed("Rock")
        robot.assertTextDisplayed("Jazz")
        robot.assertTextDisplayed("Blues")
    }

    // endregion

    // region Callbacks

    @Test
    fun `clicking genre invokes onSelectGenre`() {
        val genre = createGenre(name = "Rock")
        robot.setContent(readyGenreList(genres = listOf(genre)))
        robot.clickText("Rock")
        robot.lastSelectedGenre shouldBe genre
    }

    // endregion

    // region Context menu (rendered via GenreListItem — see GenreListRobot doc)

    @Test
    fun `context menu shows standard items for local genre`() {
        robot.setItemContent(genre = createGenre(mediaProviders = listOf(MediaProviderType.Shuttle)))
        robot.openContextMenu()

        robot.assertTextDisplayed("Play")
        robot.assertTextDisplayed("Add to Queue")
        robot.assertTextDisplayed("Add to Playlist")
        robot.assertTextDisplayed("Play Next")
        robot.assertTextDisplayed("Exclude")
        robot.assertTextDisplayed("Edit Tags")
    }

    @Test
    fun `context menu hides Edit Tags when any provider does not support it`() {
        robot.setItemContent(
            genre = createGenre(mediaProviders = listOf(MediaProviderType.Shuttle, MediaProviderType.Jellyfin)),
        )
        robot.openContextMenu()
        robot.assertTextNotDisplayed("Edit Tags")
    }

    @Test
    fun `context menu hides Edit Tags for remote-only provider`() {
        robot.setItemContent(genre = createGenre(mediaProviders = listOf(MediaProviderType.Plex)))
        robot.openContextMenu()
        robot.assertTextNotDisplayed("Edit Tags")
    }

    @Test
    fun `context menu invokes onPlayGenre`() {
        val genre = createGenre(name = "Jazz")
        robot.setItemContent(genre = genre)
        robot.openContextMenu()
        robot.clickMenuItem("Play")
        robot.lastPlayedGenre shouldBe genre
    }

    @Test
    fun `context menu invokes onAddToQueue`() {
        val genre = createGenre(name = "Blues")
        robot.setItemContent(genre = genre)
        robot.openContextMenu()
        robot.clickMenuItem("Add to Queue")
        robot.lastAddedToQueue shouldBe genre
    }

    @Test
    fun `context menu invokes onPlayNext`() {
        val genre = createGenre(name = "Pop")
        robot.setItemContent(genre = genre)
        robot.openContextMenu()
        robot.clickMenuItem("Play Next")
        robot.lastPlayNext shouldBe genre
    }

    @Test
    fun `context menu invokes onExclude`() {
        val genre = createGenre(name = "Country")
        robot.setItemContent(genre = genre)
        robot.openContextMenu()
        robot.clickMenuItem("Exclude")
        robot.lastExcluded shouldBe genre
    }

    @Test
    fun `context menu invokes onEditTags for editable provider`() {
        val genre = createGenre(name = "Reggae", mediaProviders = listOf(MediaProviderType.Shuttle))
        robot.setItemContent(genre = genre)
        robot.openContextMenu()
        robot.clickMenuItem("Edit Tags")
        robot.lastEditTags shouldBe genre
    }

    // endregion
}
