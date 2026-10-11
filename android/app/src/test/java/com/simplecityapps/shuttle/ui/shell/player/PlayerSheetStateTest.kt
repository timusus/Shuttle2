package com.simplecityapps.shuttle.ui.shell.player

import androidx.compose.animation.core.snap
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.runtime.mutableStateOf
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PlayerSheetStateTest {

    private fun state(
        mode: PlayerMode,
        hasQueue: Boolean?,
    ) = PlayerSheetState(AnchoredDraggableState(PlayerLevel.Hidden), mutableStateOf(mode), snap()).also { it.configure(mode, hasQueue) }

    @Test
    fun `moving to Full with an empty queue leaves the player hidden`() = runTest {
        val player = state(PlayerMode.Pane, hasQueue = false)

        player.moveTo(PlayerLevel.Full)

        player.level shouldBe PlayerLevel.Hidden
    }

    @Test
    fun `moving to Full before the queue has reported leaves the player hidden`() = runTest {
        val player = state(PlayerMode.Pane, hasQueue = null)

        player.moveTo(PlayerLevel.Full)

        player.level shouldBe PlayerLevel.Hidden
    }

    @Test
    fun `moving to Full with a queue raises the player`() = runTest {
        val player = state(PlayerMode.Pane, hasQueue = true)

        player.moveTo(PlayerLevel.Full)

        player.level shouldBe PlayerLevel.Full
    }
}
