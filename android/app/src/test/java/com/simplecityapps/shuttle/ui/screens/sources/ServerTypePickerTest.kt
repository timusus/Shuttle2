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
    fun `each row says what the server is`() {
        robot.setContent()

        robot.assertTextDisplayed("Free, open-source media server")
        robot.assertTextDisplayed("Media server with optional Emby Premiere")
        robot.assertTextDisplayed("Plex Media Server, through your Plex account")
        robot.assertTextDisplayed("Navidrome, gonic, Airsonic and other Subsonic-compatible music servers")
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
