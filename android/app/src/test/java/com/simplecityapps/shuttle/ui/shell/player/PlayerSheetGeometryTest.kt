package com.simplecityapps.shuttle.ui.shell.player

import io.kotest.matchers.shouldBe
import org.junit.Test

class PlayerSheetGeometryTest {

    // H = 2000, N = 200, M = 100, Q = 1500: Hidden 2000, Mini 1700, NowPlaying 0, Queue -1500.
    private val geometry = PlayerSheetGeometry(height = 2000f, navBarHeight = 200f, miniHeight = 100f, queueTravel = 1500f)

    @Test
    fun `anchors follow the spec`() {
        geometry.offsetOf(PlayerLevel.Hidden) shouldBe 2000f
        geometry.offsetOf(PlayerLevel.Mini) shouldBe 1700f
        geometry.offsetOf(PlayerLevel.NowPlaying) shouldBe 0f
        geometry.offsetOf(PlayerLevel.Queue) shouldBe -1500f
    }

    @Test
    fun `fractions run 0 to 1 between their anchors and clamp outside`() {
        geometry.reveal(2000f) shouldBe 0f
        geometry.reveal(1850f) shouldBe 0.5f
        geometry.reveal(0f) shouldBe 1f
        geometry.expand(1700f) shouldBe 0f
        geometry.expand(850f) shouldBe 0.5f
        geometry.expand(-1500f) shouldBe 1f
        geometry.queue(0f) shouldBe 0f
        geometry.queue(-750f) shouldBe 0.5f
        geometry.queue(1700f) shouldBe 0f
    }

    @Test
    fun `the sheet never rises above the top edge`() {
        geometry.sheetTop(-1500f) shouldBe 0f
        geometry.sheetTop(1700f) shouldBe 1700f
    }

    @Test
    fun `nav bar, mini player and scrim track expand`() {
        geometry.navBarTranslation(1700f) shouldBe 0f
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
    fun `the queue level pushes now playing up and pulls the queue panel in`() {
        geometry.nowPlayingTranslation(0f) shouldBe 0f
        geometry.nowPlayingTranslation(-1500f) shouldBe -1500f
        geometry.queuePanelTranslation(0f) shouldBe 1500f
        geometry.queuePanelTranslation(-1500f) shouldBe 0f
    }

    @Test
    fun `destination padding follows reveal only`() {
        geometry.contentBottomPadding(reveal = 0f) shouldBe 200f
        geometry.contentBottomPadding(reveal = 1f) shouldBe 300f
    }
}
