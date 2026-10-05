package com.simplecityapps.shuttle.ui.screens.sources

import androidx.compose.ui.test.junit4.createComposeRule
import com.simplecityapps.shuttle.model.MediaProviderType
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ServerTypePickerTest {
    @get:Rule
    val rule = createComposeRule()

    private val robot = ServerTypePickerRobot(rule)

    @Test
    fun `shows a row for each server type`() {
        robot.setContent()

        robot.assertTextDisplayed("Connect a server")
        robot.assertTextDisplayed("Jellyfin")
        robot.assertTextDisplayed("Emby")
        robot.assertTextDisplayed("Plex")
        robot.assertTextDisplayed("Navidrome / Subsonic")
    }

    @Test
    fun `choosing a type reports it`() {
        robot.setContent()

        robot.clickText("Plex")

        robot.selected shouldBe listOf(MediaProviderType.Plex)
    }

    @Test
    fun `choosing Subsonic reports it`() {
        robot.setContent()

        robot.clickText("Navidrome / Subsonic")

        robot.selected shouldBe listOf(MediaProviderType.Subsonic)
    }
}
