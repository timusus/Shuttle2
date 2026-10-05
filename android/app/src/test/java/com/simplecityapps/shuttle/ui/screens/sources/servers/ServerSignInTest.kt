package com.simplecityapps.shuttle.ui.screens.sources.servers

import androidx.compose.ui.test.junit4.createComposeRule
import com.simplecityapps.mediaprovider.server.DiscoveredServer
import com.simplecityapps.shuttle.model.MediaProviderType
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ServerSignInTest {
    @get:Rule
    val rule = createComposeRule()

    private val robot = ServerSignInRobot(rule)

    @Test
    fun `Jellyfin asks for an address, username and password`() {
        robot.setContent(serverSignInForm(MediaProviderType.Jellyfin))

        robot.assertTextDisplayed("Jellyfin Media Server")
        robot.assertTextDisplayed("e.g. http://my.server.com:8080")
        robot.assertTextDisplayed("Remember password")
        robot.assertFieldCount(3)
    }

    @Test
    fun `Emby shows its own title`() {
        robot.setContent(serverSignInForm(MediaProviderType.Emby))

        robot.assertTextDisplayed("Emby Media Server")
    }

    @Test
    fun `Plex asks for no address or password - it signs in with plex_tv`() {
        robot.setContent(serverSignInForm(MediaProviderType.Plex))

        robot.assertTextDisplayed("Plex Media Server")
        robot.assertTextDisplayed("Sign in to your Plex account in the browser, then come back here. Shuttle Music connects to your Plex Media Server for you.")
        robot.assertFieldCount(0)
        robot.clickText("Sign in with Plex")

        robot.authenticated shouldBe 1
    }

    @Test
    fun `awaiting the Plex PIN shows the code - where to enter it - and reopens the browser`() {
        robot.setContent(serverSignInAwaitingPin())

        robot.assertTextDisplayed("H7KQ")
        robot.assertTextDisplayed("Approve the sign-in in your browser. On another device, go to plex.tv/link and enter this code:")
        robot.clickText("Open browser")
        robot.clickText("Cancel")

        robot.openedUrls shouldBe listOf("https://app.plex.tv/auth#?code=H7KQ")
        robot.pinCancelled shouldBe 1
    }

    @Test
    fun `the account's servers are listed - a shared one says so - and tapping one chooses it`() {
        robot.setContent(serverSignInChoosingServer(ServerChoice("home", "Home", owned = true), ServerChoice("friend", "Sam's Server", owned = false)))

        robot.assertTextDisplayed("Choose a server")
        robot.assertTextCount("Shared with you", 1)
        robot.clickText("Sam's Server")

        robot.chosenServers shouldBe listOf("friend")
    }

    @Test
    fun `Subsonic says an API key can stand in for the username and password`() {
        robot.setContent(serverSignInForm(MediaProviderType.Subsonic))

        robot.assertTextDisplayed("Navidrome / Subsonic server")
        robot.assertTextDisplayed("e.g. http://my.server.com:4533")
        robot.assertTextDisplayed("Leave empty to sign in with an API key as the password")
        robot.assertFieldCount(3)
    }

    @Test
    fun `the fields show what's filled in and report typing`() {
        robot.setContent(serverSignInForm(MediaProviderType.Emby, ServerSignInForm(address = "http://emby:8096", username = "sam")))

        robot.assertTextDisplayed("http://emby:8096")
        robot.assertTextDisplayed("sam")
        robot.typeInto("Password", "secret")

        // A controlled field reports each edit; the state here never changes, so only the first matters.
        robot.passwords.first() shouldBe "secret"
    }

    @Test
    fun `missing fields say they're required`() {
        robot.setContent(serverSignInForm(form = ServerSignInForm(missing = setOf(ServerSignInField.Address, ServerSignInField.Username))))

        robot.assertTextNotDisplayed("e.g. http://my.server.com:8080")
        robot.assertTextCount("Required", 2)
    }

    @Test
    fun `a saved password can't be revealed until it's cleared`() {
        robot.setContent(serverSignInForm(form = ServerSignInForm(password = "secret", passwordRevealable = false)))
        robot.assertRevealPasswordShown(false)
    }

    @Test
    fun `a typed password can be revealed`() {
        robot.setContent(serverSignInForm())
        robot.assertRevealPasswordShown(true)
    }

    @Test
    fun `the remember password switch reports its change`() {
        robot.setContent(serverSignInForm())

        robot.toggleRememberPassword()

        robot.rememberPassword shouldBe listOf(false)
    }

    @Test
    fun `Authenticate and Close are forwarded`() {
        robot.setContent(serverSignInForm())

        robot.clickText("Authenticate")
        robot.clickText("Close")

        robot.authenticated shouldBe 1
        robot.dismissed shouldBe 1
    }

    @Test
    fun `a Free user sees the streaming disclosure before connecting`() {
        robot.setContent(serverSignInForm(showProDisclosure = true))

        robot.assertTextDisplayed("Streaming from Jellyfin, Emby, Plex and Navidrome is part of Shuttle Music Pro. Free for 14 days.")
    }

    @Test
    fun `a Trial or Pro user doesn't see the disclosure`() {
        robot.setContent(serverSignInForm(showProDisclosure = false))

        robot.assertTextNotDisplayed("Streaming from Jellyfin, Emby, Plex and Navidrome is part of Shuttle Music Pro. Free for 14 days.")
    }

    @Test
    fun `while authenticating the form gives way to progress`() {
        robot.setContent(serverSignInAuthenticating())

        robot.assertTextDisplayed("Authenticating…")
        robot.assertFieldCount(0)
        robot.assertAuthenticateEnabled(false)
        robot.assertTextDisplayed("Close")
    }

    @Test
    fun `a successful sign-in says so`() {
        robot.setContent(serverSignInConnected())

        robot.assertTextDisplayed("Authentication Successful")
        robot.assertAuthenticateEnabled(false)
    }

    @Test
    fun `a failed sign-in shows why and offers a retry`() {
        robot.setContent(serverSignInFailed("An error occurred. (401)"))

        robot.assertTextDisplayed("An error occurred. (401)")
        robot.assertFieldCount(0)
        robot.clickText("Retry")

        robot.retried shouldBe 1
    }

    @Test
    fun `Quick Connect isn't offered until the server reports it enabled`() {
        robot.setContent(serverSignInForm(quickConnectEnabled = false))
        robot.assertTextNotDisplayed("Use Quick Connect")
    }

    @Test
    fun `Quick Connect is offered once the server reports it enabled`() {
        robot.setContent(serverSignInForm(quickConnectEnabled = true))
        robot.assertTextDisplayed("Use Quick Connect")
    }

    @Test
    fun `starting Quick Connect is forwarded`() {
        robot.setContent(serverSignInForm(quickConnectEnabled = true))

        robot.clickText("Use Quick Connect")

        robot.quickConnectStarted shouldBe 1
    }

    @Test
    fun `awaiting approval shows the code and a cancel action`() {
        robot.setContent(serverSignInAwaitingCode("123456"))

        robot.assertTextDisplayed("123456")
        robot.assertFieldCount(0)
        robot.clickText("Cancel")

        robot.quickConnectCancelled shouldBe 1
    }

    @Test
    fun `servers on the local network are offered - and tapping one fills in its address`() {
        robot.setContent(serverSignInForm(discoveredServers = listOf(DiscoveredServer("Living Room", "http://192.168.1.10:8096"))))

        robot.assertTextDisplayed("On your network")
        robot.clickText("Living Room")

        robot.addresses shouldBe listOf("http://192.168.1.10:8096")
    }

    @Test
    fun `with nothing found on the network no suggestions show`() {
        robot.setContent(serverSignInForm())
        robot.assertTextNotDisplayed("On your network")
    }
}
