package com.simplecityapps.shuttle.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import com.simplecityapps.shuttle.ui.theme.ArtworkSeed
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ArtworkRoleTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val root = Color.Red
    private val artwork = Color.Blue

    private fun roleUnder(seed: ArtworkSeed, contrast: S2Contrast): Color {
        var role: Color? = null
        composeTestRule.setContent {
            S2Theme(darkTheme = false, contrast = contrast) {
                ArtworkTheme(seed) { role = artworkRole(root, artwork) }
            }
        }
        composeTestRule.waitForIdle()
        return role!!
    }

    @Test
    fun `without a seed the root colour is kept`() {
        roleUnder(ArtworkSeed.None, S2Contrast.Default) shouldBe root
    }

    @Test
    fun `at Medium contrast the root colour is kept despite a seed`() {
        roleUnder(ArtworkSeed.Available(0xFF1F3FA8.toInt()), S2Contrast.Medium) shouldBe root
    }

    @Test
    fun `at High contrast the root colour is kept despite a seed`() {
        roleUnder(ArtworkSeed.Available(0xFF1F3FA8.toInt()), S2Contrast.High) shouldBe root
    }

    @Test
    fun `a seed at Default contrast reaches the artwork role`() {
        roleUnder(ArtworkSeed.Available(0xFF1F3FA8.toInt()), S2Contrast.Default) shouldBe artwork
    }
}
