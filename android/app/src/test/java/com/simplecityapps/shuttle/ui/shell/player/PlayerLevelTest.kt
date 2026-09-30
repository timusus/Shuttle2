package com.simplecityapps.shuttle.ui.shell.player

import com.simplecityapps.shuttle.ui.shell.player.PlayerLevel.Full
import com.simplecityapps.shuttle.ui.shell.player.PlayerLevel.Hidden
import com.simplecityapps.shuttle.ui.shell.player.PlayerLevel.Mini
import com.simplecityapps.shuttle.ui.shell.player.PlayerMode.CompactSheet
import com.simplecityapps.shuttle.ui.shell.player.PlayerMode.Pane
import com.simplecityapps.shuttle.ui.shell.player.PlayerMode.Sheet
import io.kotest.matchers.shouldBe
import org.junit.Test

class PlayerLevelTest {

    @Test
    fun `an empty queue allows only Hidden, and a queue Mini and Full`() {
        playerLevels(hasQueue = false) shouldBe setOf(Hidden)
        playerLevels(hasQueue = true) shouldBe setOf(Mini, Full)
    }

    @Test
    fun `back steps Full down to Mini and never reaches Hidden`() {
        Full.stepDown() shouldBe Mini
        Mini.stepDown() shouldBe null
        Hidden.stepDown() shouldBe null
    }

    @Test
    fun `sheet to pane opens the pane`() {
        mapPlayerLevel(Mini, CompactSheet, Pane) shouldBe Full
        mapPlayerLevel(Full, Sheet, Pane) shouldBe Full
    }

    @Test
    fun `pane to sheet always drops to Mini`() {
        listOf(Mini, Full).forEach { level ->
            mapPlayerLevel(level, Pane, CompactSheet) shouldBe Mini
            mapPlayerLevel(level, Pane, Sheet) shouldBe Mini
        }
    }

    @Test
    fun `compact to Medium and back keeps the level`() {
        mapPlayerLevel(Full, CompactSheet, Sheet) shouldBe Full
        mapPlayerLevel(Mini, CompactSheet, Sheet) shouldBe Mini
        mapPlayerLevel(Full, Sheet, CompactSheet) shouldBe Full
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
        resolvePlayerLevel(Full, setOf(Hidden)) shouldBe Hidden
        resolvePlayerLevel(Hidden, setOf(Mini, Full)) shouldBe Mini
        resolvePlayerLevel(Full, setOf(Mini, Full)) shouldBe Full
    }
}
