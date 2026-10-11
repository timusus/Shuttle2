package com.simplecityapps.shuttle.ui.shell

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import com.simplecityapps.shuttle.ui.screens.settings.EqualizerRoute
import com.simplecityapps.shuttle.ui.screens.settings.SettingsDestinationRoute
import com.simplecityapps.shuttle.ui.screens.settings.ThisDeviceRoute
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsDestination
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AppNavigatorTest {

    private fun navigator(startTab: ShellTab = ShellTab.Home): AppNavigator = AppNavigator(
        startTab = startTab,
        stacks = ShellTab.entries.associateWith { mutableListOf<NavKey>(it.root) },
        selectedTab = mutableStateOf(startTab),
    )

    private val album = AlbumRoute(albumKey = "a", albumArtistKey = "b")
    private val sources = SettingsDestinationRoute(SettingsDestination.Sources)
    private val privacy = SettingsDestinationRoute(SettingsDestination.Privacy)

    @Test
    fun `back after show replaced the stack the player opened from does not raise the player`() {
        val navigator = navigator()
        navigator.openFromPlayer(sources)
        navigator.open(privacy)

        navigator.show(ShellRequest(ShellTab.Home, album))
        navigator.back()

        navigator.playerReturnPending shouldBe false
    }

    @Test
    fun `back after replaceAbove dropped the route the player opened does not raise the player`() {
        val navigator = navigator()
        navigator.open(album)
        navigator.openFromPlayer(sources)

        navigator.replaceAbove(HomeRoute, privacy)
        navigator.back()

        navigator.playerReturnPending shouldBe false
    }

    @Test
    fun `back after reselecting the tab to its root does not raise the player`() {
        val navigator = navigator()
        navigator.openFromPlayer(sources)
        navigator.open(privacy)

        navigator.selectTab(ShellTab.Home)
        navigator.open(album)
        navigator.back()

        navigator.playerReturnPending shouldBe false
    }

    @Test
    fun `back from a route the player opened still raises the player`() {
        val navigator = navigator()
        navigator.openFromPlayer(sources)

        navigator.back()

        navigator.playerReturnPending shouldBe true
    }

    @Test
    fun `tabHolding finds the tab holding the route of a nav entry from its content key`() {
        val navigator = navigator()
        navigator.selectTab(ShellTab.Library)
        navigator.open(album)

        fun keyOf(route: NavKey) = NavEntry(route) {}.contentKey
        navigator.tabHolding(keyOf(album)) shouldBe ShellTab.Library
        navigator.tabHolding(keyOf(HomeRoute)) shouldBe ShellTab.Home
        navigator.tabHolding(keyOf(privacy)) shouldBe null
        navigator.tabHolding(null) shouldBe null
    }

    @Test
    fun `open pushes onto the selected tab only`() {
        val navigator = navigator()
        navigator.selectTab(ShellTab.Library)
        navigator.open(album)

        navigator.stack(ShellTab.Library) shouldBe listOf(LibraryRoute, album)
        navigator.stack(ShellTab.Home) shouldBe listOf(HomeRoute)
    }

    @Test
    fun `a utility route on top hides the navigation until it is popped, keeping the tab's stack beneath it`() {
        val navigator = navigator()
        navigator.selectTab(ShellTab.Library)
        navigator.open(album)
        navigator.showsNavigation shouldBe true

        navigator.open(SettingsRoute)
        navigator.open(EqualizerRoute)
        navigator.showsNavigation shouldBe false

        navigator.back()
        navigator.showsNavigation shouldBe false
        navigator.back()
        navigator.showsNavigation shouldBe true
        navigator.selectedTab shouldBe ShellTab.Library
        navigator.stack(ShellTab.Library) shouldBe listOf(LibraryRoute, album)
    }

    @Test
    fun `leaving a tab pops its utility routes and whatever was opened from them, keeping the tab's own screens`() {
        val navigator = navigator()
        navigator.selectTab(ShellTab.Library)
        navigator.open(album)
        navigator.open(SettingsRoute)
        navigator.open(EqualizerRoute)
        navigator.open(album)

        navigator.selectTab(ShellTab.Search)
        navigator.stack(ShellTab.Library) shouldBe listOf(LibraryRoute, album)

        navigator.selectTab(ShellTab.Library)
        navigator.showsNavigation shouldBe true
    }

    @Test
    fun `leaving the start tab pops its utility routes too, and re-selecting the current tab pops it to its root`() {
        val navigator = navigator()
        navigator.open(SettingsRoute)
        navigator.selectTab(ShellTab.Search)
        navigator.stack(ShellTab.Home) shouldBe listOf(HomeRoute)

        navigator.open(SettingsRoute)
        navigator.selectTab(ShellTab.Search)
        navigator.stack(ShellTab.Search) shouldBe listOf(SearchRoute)
        navigator.showsNavigation shouldBe true
    }

    @Test
    fun `back from another tab's utility route closes it before returning to the start tab`() {
        val navigator = navigator()
        navigator.selectTab(ShellTab.Library)
        navigator.open(SettingsRoute)

        navigator.back() shouldBe true
        navigator.selectedTab shouldBe ShellTab.Library
        navigator.back() shouldBe true
        navigator.selectedTab shouldBe ShellTab.Home
        navigator.stack(ShellTab.Library) shouldBe listOf(LibraryRoute)
    }

    @Test
    fun `the display shows the start tab followed by the selected tab`() {
        val navigator = navigator()
        navigator.visibleTabs shouldBe listOf(ShellTab.Home)
        navigator.selectTab(ShellTab.Search)
        navigator.visibleTabs shouldBe listOf(ShellTab.Home, ShellTab.Search)
    }

    @Test
    fun `back pops the selected tab, then returns to the start tab, then reports nothing left`() {
        val navigator = navigator()
        navigator.selectTab(ShellTab.Library)
        navigator.open(album)

        navigator.back() shouldBe true
        navigator.stack(ShellTab.Library) shouldBe listOf(LibraryRoute)
        navigator.selectedTab shouldBe ShellTab.Library

        navigator.back() shouldBe true
        navigator.selectedTab shouldBe ShellTab.Home

        navigator.back() shouldBe false
    }

    @Test
    fun `re-selecting a tab restores its stack`() {
        val navigator = navigator()
        navigator.selectTab(ShellTab.Library)
        navigator.open(album)
        navigator.selectTab(ShellTab.Search)
        navigator.selectTab(ShellTab.Library)

        navigator.stack(ShellTab.Library) shouldBe listOf(LibraryRoute, album)
    }

    @Test
    fun `re-selecting the current tab pops it to its root`() {
        val navigator = navigator()
        navigator.open(album)
        navigator.open(SettingsRoute)
        navigator.selectTab(ShellTab.Home)

        navigator.stack(ShellTab.Home) shouldBe listOf(HomeRoute)
        navigator.selectedTab shouldBe ShellTab.Home
    }

    @Test
    fun `the start tab can be Library`() {
        val navigator = navigator(startTab = ShellTab.Library)
        navigator.selectTab(ShellTab.Home)
        navigator.visibleTabs shouldBe listOf(ShellTab.Library, ShellTab.Home)
        navigator.back() shouldBe true
        navigator.selectedTab shouldBe ShellTab.Library
    }

    @Test
    fun `replaceAbove drops everything above the anchor and pushes the route`() {
        val navigator = navigator()
        navigator.open(SettingsRoute)
        navigator.open(sources)
        navigator.open(ThisDeviceRoute)

        navigator.replaceAbove(SettingsRoute, privacy)

        navigator.stack(ShellTab.Home) shouldBe listOf(HomeRoute, SettingsRoute, privacy)
    }

    @Test
    fun `replaceAbove with nothing above the anchor pushes, and the same page again leaves the stack alone`() {
        val navigator = navigator()
        navigator.open(SettingsRoute)
        navigator.replaceAbove(SettingsRoute, sources)
        navigator.replaceAbove(SettingsRoute, sources)

        navigator.stack(ShellTab.Home) shouldBe listOf(HomeRoute, SettingsRoute, sources)
    }

    @Test
    fun `replaceAbove keeps the anchor nearest the top`() {
        val navigator = navigator()
        navigator.open(SettingsRoute)
        navigator.open(sources)
        navigator.open(SettingsRoute)
        navigator.open(privacy)

        navigator.replaceAbove(SettingsRoute, sources)

        navigator.stack(ShellTab.Home) shouldBe listOf(HomeRoute, SettingsRoute, sources, SettingsRoute, sources)
    }

    @Test
    fun `replaceAbove never replaces the tab's root`() {
        val navigator = navigator()
        navigator.selectTab(ShellTab.Library)
        navigator.open(album)

        // Anchored on the root, the root stays and only what is above it goes.
        navigator.replaceAbove(LibraryRoute, sources)
        navigator.stack(ShellTab.Library) shouldBe listOf(LibraryRoute, sources)

        // With no anchor on the stack, the route is pushed over what is there.
        navigator.replaceAbove(SettingsRoute, privacy)
        navigator.stack(ShellTab.Library) shouldBe listOf(LibraryRoute, sources, privacy)
    }

    @Test
    fun `popping the route the player opened asks for the player again`() {
        val navigator = navigator()
        navigator.openFromPlayer(EqualizerRoute)
        navigator.playerReturnPending shouldBe false

        navigator.back()

        navigator.stack(ShellTab.Home) shouldBe listOf(HomeRoute)
        navigator.playerReturnPending shouldBe true
        navigator.consumePlayerReturn()
        navigator.playerReturnPending shouldBe false
    }

    @Test
    fun `popping a screen opened above the player's route does not ask for the player yet`() {
        val navigator = navigator()
        navigator.openFromPlayer(EqualizerRoute)
        navigator.open(album)

        navigator.back()
        navigator.playerReturnPending shouldBe false

        navigator.back()
        navigator.playerReturnPending shouldBe true
    }

    @Test
    fun `leaving for another tab forgets the player's route`() {
        val navigator = navigator()
        navigator.openFromPlayer(EqualizerRoute)
        navigator.selectTab(ShellTab.Library)
        navigator.selectTab(ShellTab.Home)

        navigator.back()

        navigator.playerReturnPending shouldBe false
    }

    @Test
    fun `re-selecting a tab already at its root announces it, and one deeper only pops`() = runTest {
        val navigator = navigator()
        val reselects = mutableListOf<ShellTab>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { navigator.reselects.toList(reselects) }

        navigator.selectTab(ShellTab.Library)
        navigator.open(album)
        navigator.selectTab(ShellTab.Library)
        reselects shouldBe emptyList()
        navigator.stack(ShellTab.Library) shouldBe listOf(LibraryRoute)

        navigator.selectTab(ShellTab.Library)
        reselects shouldBe listOf(ShellTab.Library)
    }
}
