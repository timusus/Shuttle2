package com.simplecityapps.shuttle.ui.screens.sources.servers

import androidx.compose.ui.test.junit4.createComposeRule
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
        robot.assertTextNotDisplayed("2FA Code")
    }

    @Test
    fun `Emby shows its own title`() {
        robot.setContent(serverSignInForm(MediaProviderType.Emby))

        robot.assertTextDisplayed("Emby Media Server")
    }

    @Test
    fun `Plex also asks for an optional two-factor code`() {
        robot.setContent(serverSignInForm(MediaProviderType.Plex))

        robot.assertTextDisplayed("Plex Media Server")
        robot.assertTextDisplayed("e.g. http://my.plex.server.com:32400")
        robot.assertTextDisplayed("2FA Code")
        robot.assertTextDisplayed("Optional")
        robot.assertFieldCount(4)
    }

    @Test
    fun `the fields show what's filled in and report typing`() {
        robot.setContent(serverSignInForm(MediaProviderType.Plex, ServerSignInForm(address = "http://plex:32400", username = "sam")))

        robot.assertTextDisplayed("http://plex:32400")
        robot.assertTextDisplayed("sam")
        robot.typeInto("Password", "secret")
        robot.typeInto("2FA Code", "123456")

        // A controlled field reports each edit; the state here never changes, so only the first matters.
        robot.passwords.first() shouldBe "secret"
        robot.authCodes.first() shouldBe "123456"
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
}
