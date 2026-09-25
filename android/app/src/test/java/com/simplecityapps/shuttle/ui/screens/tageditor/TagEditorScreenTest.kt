package com.simplecityapps.shuttle.ui.screens.tageditor

import androidx.compose.ui.test.junit4.createComposeRule
import com.simplecityapps.sampleSongs
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TagEditorScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val robot = TagEditorRobot(composeTestRule)

    @Test
    fun `one song shows all eleven fields with the song's tags`() {
        val song = sampleSongs(1).single()
        robot.setState(singleSongEditing(song))

        TagField.entries.forEach(robot::assertFieldShown)
        robot.assertFieldText(TagField.Title, song.name!!)
        robot.assertFieldText(TagField.Album, song.album!!)
        robot.assertSaveEnabled(false)
    }

    @Test
    fun `several songs hide title and track and show mixed fields as multiple values`() {
        robot.setState(batchEditing())

        robot.assertTextDisplayed("Editing 3 songs")
        robot.assertFieldNotShown(TagField.Title)
        robot.assertFieldNotShown(TagField.Track)
        robot.assertFieldShown(TagField.Album)
        robot.assertTextDisplayed("Multiple values")
    }

    @Test
    fun `songs that can't be edited are listed before saving`() {
        val state = batchEditing()
        robot.setState(state)

        robot.assertTextDisplayed("1 song can't be edited", substring = true)
        robot.assertTextDisplayed(state.skipped.single().name!!, substring = true)
    }

    @Test
    fun `typing into a field turns Save on and Save saves`() {
        robot.setState(singleSongEditing())

        robot.typeInto(TagField.Album, "New Album")
        robot.assertSaveEnabled(true)

        robot.clickSave()
        robot.saved shouldBe true
    }

    @Test
    fun `reset puts a changed field back`() {
        val song = sampleSongs(1).single()
        robot.setState(singleSongEditing(song))

        robot.typeInto(TagField.Album, "New Album")
        robot.resetField("Album")

        robot.assertFieldText(TagField.Album, song.album!!)
        robot.assertSaveEnabled(false)
    }

    @Test
    fun `back with unsaved changes asks first`() {
        robot.setState(singleSongEditing())
        robot.typeInto(TagField.Album, "New Album")

        robot.clickBack()
        robot.assertTextDisplayed("Discard your changes?")
        robot.navigatedUp shouldBe false

        robot.clickText("Keep editing")
        robot.navigatedUp shouldBe false
        robot.currentState.shouldBeInstanceOf<TagEditorUiState.Editing>().hasChanges shouldBe true

        robot.clickBack()
        robot.clickText("Discard")
        robot.navigatedUp shouldBe true
    }

    @Test
    fun `back without changes leaves straight away`() {
        robot.setState(singleSongEditing())

        robot.clickBack()

        robot.navigatedUp shouldBe true
    }

    @Test
    fun `reading and writing show their progress`() {
        robot.setState(readingTags)
        robot.assertTextDisplayed("Reading tags 1 / 3")

        robot.setState(writingTags())
        robot.assertTextDisplayed("Writing tags 0 / 1")
        robot.assertSaveEnabled(false)
    }

    @Test
    fun `unreadable songs say so and close`() {
        robot.setState(TagEditorUiState.Unreadable)

        robot.assertTextDisplayed("The selected song tags could not be read")
        robot.clickText("Close")
        robot.navigatedUp shouldBe true
    }
}
