package com.simplecityapps.shuttle.ui.shell.player

import com.simplecityapps.shuttle.ui.shell.player.PlayerLevel.Expanded
import com.simplecityapps.shuttle.ui.shell.player.PlayerLevel.Hidden
import com.simplecityapps.shuttle.ui.shell.player.PlayerLevel.Mini
import com.simplecityapps.shuttle.ui.shell.player.PlayerLevel.NowPlaying
import com.simplecityapps.shuttle.ui.shell.player.PlayerMode.CompactSheet
import com.simplecityapps.shuttle.ui.shell.player.PlayerMode.Pane
import com.simplecityapps.shuttle.ui.shell.player.PlayerMode.Sheet
import io.kotest.matchers.shouldBe
import org.junit.Test

class PlayerLevelTest {

    @Test
    fun `an empty queue allows only Hidden in every mode`() {
        PlayerMode.entries.forEach { mode -> playerLevels(mode, hasQueue = false) shouldBe setOf(Hidden) }
    }

    @Test
    fun `only a compact sheet resting below full height has an Expanded level`() {
        playerLevels(CompactSheet, hasQueue = true) shouldBe setOf(Mini, NowPlaying, Expanded)
        playerLevels(CompactSheet, hasQueue = true, partialRest = false) shouldBe setOf(Mini, NowPlaying)
        playerLevels(Pane, hasQueue = true) shouldBe setOf(Mini, NowPlaying)
        playerLevels(Sheet, hasQueue = true) shouldBe setOf(Mini, NowPlaying)
    }

    @Test
    fun `back steps down one level at a time and never reaches Hidden`() {
        Expanded.stepDown() shouldBe NowPlaying
        NowPlaying.stepDown() shouldBe Mini
        Mini.stepDown() shouldBe null
        Hidden.stepDown() shouldBe null
    }

    @Test
    fun `sheet to pane opens the pane`() {
        mapPlayerLevel(Mini, CompactSheet, Pane) shouldBe NowPlaying
        mapPlayerLevel(NowPlaying, Sheet, Pane) shouldBe NowPlaying
        mapPlayerLevel(Expanded, CompactSheet, Pane) shouldBe NowPlaying
    }

    @Test
    fun `pane to sheet always drops to Mini`() {
        listOf(Mini, NowPlaying).forEach { level ->
            mapPlayerLevel(level, Pane, CompactSheet) shouldBe Mini
            mapPlayerLevel(level, Pane, Sheet) shouldBe Mini
        }
    }

    @Test
    fun `compact to Medium folds Expanded into Now Playing and leaves other levels`() {
        mapPlayerLevel(Expanded, CompactSheet, Sheet) shouldBe NowPlaying
        mapPlayerLevel(NowPlaying, CompactSheet, Sheet) shouldBe NowPlaying
        mapPlayerLevel(Mini, CompactSheet, Sheet) shouldBe Mini
        mapPlayerLevel(NowPlaying, Sheet, CompactSheet) shouldBe NowPlaying
    }

    @Test
    fun `Hidden and same-mode levels never change`() {
        PlayerMode.entries.forEach { from ->
            PlayerMode.entries.forEach { to -> mapPlayerLevel(Hidden, from, to) shouldBe Hidden }
        }
        PlayerLevel.entries.forEach { level -> mapPlayerLevel(level, CompactSheet, CompactSheet) shouldBe level }
    }

    @Test
    fun `resolving against the allowed levels hides an emptied sheet and reveals Mini for a new queue`() {
        resolvePlayerLevel(NowPlaying, setOf(Hidden)) shouldBe Hidden
        resolvePlayerLevel(Hidden, setOf(Mini, NowPlaying, Expanded)) shouldBe Mini
        resolvePlayerLevel(Expanded, setOf(Mini, NowPlaying)) shouldBe NowPlaying
        resolvePlayerLevel(Expanded, setOf(Mini, NowPlaying, Expanded)) shouldBe Expanded
    }
}
