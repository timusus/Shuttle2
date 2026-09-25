package com.simplecityapps.shuttle.ui.shell

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavKey
import io.kotest.matchers.shouldBe
import org.junit.Test

class AppNavigatorTest {

    private fun navigator(startTab: ShellTab = ShellTab.Home): AppNavigator = AppNavigator(
        startTab = startTab,
        stacks = ShellTab.entries.associateWith { mutableListOf<NavKey>(it.root) },
        selectedTab = mutableStateOf(startTab),
    )

    private val album = AlbumRoute(albumKey = "a", albumArtistKey = "b")

    @Test
    fun `open pushes onto the selected tab only`() {
        val navigator = navigator()
        navigator.selectTab(ShellTab.Library)
        navigator.open(album)

        navigator.stack(ShellTab.Library) shouldBe listOf(LibraryRoute, album)
        navigator.stack(ShellTab.Home) shouldBe listOf(HomeRoute)
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
}
