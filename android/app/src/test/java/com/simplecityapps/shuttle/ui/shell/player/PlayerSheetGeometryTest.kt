package com.simplecityapps.shuttle.ui.shell.player

import io.kotest.matchers.shouldBe
import org.junit.Test

class PlayerSheetGeometryTest {

    // H = 2000, N = 200, M = 100: Hidden 2000, Mini 1700, Full 0.
    private val geometry = PlayerSheetGeometry(height = 2000f, navBarHeight = 200f, miniHeight = 100f)

    @Test
    fun `anchors follow the spec`() {
        geometry.offsetOf(PlayerLevel.Hidden) shouldBe 2000f
        geometry.offsetOf(PlayerLevel.Mini) shouldBe 1700f
        geometry.offsetOf(PlayerLevel.Full) shouldBe 0f
    }

    @Test
    fun `fractions run 0 to 1 between their anchors and clamp outside`() {
        geometry.reveal(2000f) shouldBe 0f
        geometry.reveal(1850f) shouldBe 0.5f
        geometry.reveal(0f) shouldBe 1f
        geometry.expand(1700f) shouldBe 0f
        geometry.expand(850f) shouldBe 0.5f
        geometry.expand(0f) shouldBe 1f
    }

    @Test
    fun `the sheet never rises above the top edge`() {
        geometry.sheetTop(-100f) shouldBe 0f
        geometry.sheetTop(1700f) shouldBe 1700f
    }

    @Test
    fun `nav bar, mini player and scrim track expand`() {
        geometry.navBarTranslation(1700f) shouldBe 0f
        geometry.navBarTranslation(850f) shouldBe 100f
        geometry.navBarTranslation(0f) shouldBe 200f
        geometry.miniAlpha(1700f) shouldBe 1f
        geometry.miniAlpha(0f) shouldBe 0f
        geometry.scrimAlpha(1700f) shouldBe 0f
        geometry.scrimAlpha(0f) shouldBe PlayerSheetGeometry.MaxScrimAlpha
        geometry.nowPlayingAlpha(1700f) shouldBe 0f
        geometry.nowPlayingAlpha(0f) shouldBe 1f
    }

    @Test
    fun `the mini player stops taking taps past half way`() {
        geometry.miniInteractive(0.5f) shouldBe true
        geometry.miniInteractive(0.51f) shouldBe false
    }

    @Test
    fun `content starts under the status bar once the edge rises into it`() {
        geometry.contentTop(850f, statusBar = 100f) shouldBe 0f
        geometry.contentTop(40f, statusBar = 100f) shouldBe 60f
        geometry.contentTop(0f, statusBar = 100f) shouldBe 100f
    }

    @Test
    fun `corners round while the sheet rises and flatten as the edge meets the status bar`() {
        geometry.cornerRadius(1700f, statusBar = 100f, corner = 50f) shouldBe 0f
        geometry.cornerRadius(850f, statusBar = 100f, corner = 50f) shouldBe 25f
        geometry.cornerRadius(125f, statusBar = 100f, corner = 50f) shouldBe 50f * geometry.expand(125f) * 0.5f
        geometry.cornerRadius(0f, statusBar = 100f, corner = 50f) shouldBe 0f
    }

    @Test
    fun `destination padding follows reveal only`() {
        geometry.contentBottomPadding(reveal = 0f) shouldBe 200f
        geometry.contentBottomPadding(reveal = 1f) shouldBe 300f
    }
}
