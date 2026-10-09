package com.simplecityapps.shuttle.ui.shell

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavKey
import com.simplecityapps.shuttle.model.SmartPlaylistId
import com.simplecityapps.shuttle.ui.screens.library.SmartPlaylistRoute
import io.kotest.matchers.shouldBe
import org.junit.Test

class ShellRequestTest {

    private fun navigator(): AppNavigator = AppNavigator(
        startTab = ShellTab.Home,
        stacks = ShellTab.entries.associateWith { mutableListOf<NavKey>(it.root) },
        selectedTab = mutableStateOf(ShellTab.Home),
    )

    @Test
    fun `the search shortcut asks for the Search tab`() {
        ShellRequest.fromShortcutAction("com.simplecityapps.shuttle.shortcuts.OPEN_SEARCH") shouldBe ShellRequest(ShellTab.Search)
    }

    @Test
    fun `the recently played shortcut asks for the history smart playlist on Home`() {
        ShellRequest.fromShortcutAction("com.simplecityapps.shuttle.shortcuts.OPEN_RECENTLY_PLAYED") shouldBe
            ShellRequest(ShellTab.Home, SmartPlaylistRoute(SmartPlaylistId.History.id))
    }

    @Test
    fun `other actions make no request`() {
        ShellRequest.fromShortcutAction("android.intent.action.MAIN") shouldBe null
        ShellRequest.fromShortcutAction(null) shouldBe null
    }

    @Test
    fun `showing a request with a route replaces the tab's stack above its root`() {
        val navigator = navigator()
        val history = SmartPlaylistRoute(SmartPlaylistId.History.id)
        navigator.open(AlbumRoute("a", "b"))

        navigator.show(ShellRequest(ShellTab.Home, history))

        navigator.stack(ShellTab.Home) shouldBe listOf(HomeRoute, history)
        navigator.selectedTab shouldBe ShellTab.Home
    }

    @Test
    fun `showing a tab request from another tab selects it at its root`() {
        val navigator = navigator()
        navigator.selectTab(ShellTab.Library)
        navigator.open(AlbumRoute("a", "b"))

        navigator.show(ShellRequest(ShellTab.Search))

        navigator.selectedTab shouldBe ShellTab.Search
        navigator.stack(ShellTab.Search) shouldBe listOf(SearchRoute)
    }
}
