package com.simplecityapps.shuttle.ui.shell.player

import com.simplecityapps.shuttle.ui.shell.player.PlayerLevel.Hidden
import com.simplecityapps.shuttle.ui.shell.player.PlayerLevel.Mini
import com.simplecityapps.shuttle.ui.shell.player.PlayerLevel.NowPlaying
import com.simplecityapps.shuttle.ui.shell.player.PlayerLevel.Queue
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
    fun `a queue allows Mini to Queue on compact and in the pane, and no Queue level on Medium and Expanded`() {
        playerLevels(CompactSheet, hasQueue = true) shouldBe setOf(Mini, NowPlaying, Queue)
        playerLevels(Pane, hasQueue = true) shouldBe setOf(Mini, NowPlaying, Queue)
        playerLevels(Sheet, hasQueue = true) shouldBe setOf(Mini, NowPlaying)
    }

    @Test
    fun `back steps down one level at a time and never reaches Hidden`() {
        Queue.stepDown() shouldBe NowPlaying
        NowPlaying.stepDown() shouldBe Mini
        Mini.stepDown() shouldBe null
        Hidden.stepDown() shouldBe null
    }

    @Test
    fun `sheet to pane opens the pane, keeping Queue`() {
        mapPlayerLevel(Mini, CompactSheet, Pane) shouldBe NowPlaying
        mapPlayerLevel(NowPlaying, Sheet, Pane) shouldBe NowPlaying
        mapPlayerLevel(Queue, CompactSheet, Pane) shouldBe Queue
    }

    @Test
    fun `pane to sheet always drops to Mini`() {
        listOf(Mini, NowPlaying, Queue).forEach { level ->
            mapPlayerLevel(level, Pane, CompactSheet) shouldBe Mini
            mapPlayerLevel(level, Pane, Sheet) shouldBe Mini
        }
    }

    @Test
    fun `compact to Medium folds Queue into Now Playing and leaves other levels`() {
        mapPlayerLevel(Queue, CompactSheet, Sheet) shouldBe NowPlaying
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
        resolvePlayerLevel(Hidden, setOf(Mini, NowPlaying, Queue)) shouldBe Mini
        resolvePlayerLevel(Queue, setOf(Mini, NowPlaying)) shouldBe NowPlaying
        resolvePlayerLevel(Queue, setOf(Mini, NowPlaying, Queue)) shouldBe Queue
    }
}
