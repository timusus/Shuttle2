package com.simplecityapps.shuttle.ui.shell

import com.simplecityapps.shuttle.ui.shell.player.PlayerLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class StatusBarAppearanceTest {
    @Test
    fun `light theme without an image gets dark icons`() {
        assertEquals(true, darkStatusBarIcons(PlayerLevel.Mini, overImage = false, darkTheme = false))
    }

    @Test
    fun `dark theme gets light icons`() {
        assertEquals(false, darkStatusBarIcons(PlayerLevel.Mini, overImage = false, darkTheme = true))
    }

    @Test
    fun `an image behind the bar forces light icons in a light theme`() {
        assertEquals(false, darkStatusBarIcons(PlayerLevel.Mini, overImage = true, darkTheme = false))
    }

    @Test
    fun `an expanded player ignores the screen beneath it`() {
        assertEquals(true, darkStatusBarIcons(PlayerLevel.Full, overImage = true, darkTheme = false))
    }

    @Test
    fun `the topmost registered request wins`() {
        val requests = StatusBarRequests()
        val first = StatusBarRequests.Request().apply { overImage = true }
        val second = StatusBarRequests.Request().apply { overImage = false }
        requests.add(first)
        requests.add(second)
        assertEquals(false, requests.overImage)
        requests.remove(second)
        assertEquals(true, requests.overImage)
    }
}
