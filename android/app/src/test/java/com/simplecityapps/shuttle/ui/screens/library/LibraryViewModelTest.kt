package com.simplecityapps.shuttle.ui.screens.library

import com.simplecityapps.fakes.FakeSharedPreferences
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.persistence.LibraryTab
import io.kotest.matchers.shouldBe
import org.junit.Test

class LibraryViewModelTest {

    private val preferences = GeneralPreferenceManager(FakeSharedPreferences())

    private fun viewModel() = LibraryViewModel(ReadLibraryTabs(preferences), SaveLibraryTabs(preferences), SaveCurrentLibraryTab(preferences))

    @Test
    fun `defaults to every tab but Folders, opening on Artists`() {
        val state = viewModel().uiState.value

        state.tabs shouldBe LibraryTab.entries - LibraryTab.Folders
        state.currentTab shouldBe LibraryTab.Artists
    }

    @Test
    fun `the selected tab persists across view models`() {
        viewModel().onTabSelected(LibraryTab.Songs)

        viewModel().uiState.value.currentTab shouldBe LibraryTab.Songs
    }

    @Test
    fun `editing tabs reorders, hides and shows them, and persists the result`() {
        val viewModel = viewModel()
        val order = listOf(LibraryTab.Folders, LibraryTab.Songs, LibraryTab.Albums, LibraryTab.Artists, LibraryTab.Genres, LibraryTab.Playlists)

        viewModel.onTabsChanged(order, enabled = setOf(LibraryTab.Folders, LibraryTab.Songs, LibraryTab.Artists))

        viewModel.uiState.value.allTabs shouldBe order
        viewModel.uiState.value.tabs shouldBe listOf(LibraryTab.Folders, LibraryTab.Songs, LibraryTab.Artists)
        viewModel().uiState.value.tabs shouldBe listOf(LibraryTab.Folders, LibraryTab.Songs, LibraryTab.Artists)
    }

    @Test
    fun `hiding the current tab falls back to Artists, then to the first shown tab`() {
        val viewModel = viewModel()
        viewModel.onTabSelected(LibraryTab.Songs)

        viewModel.onTabsChanged(LibraryTab.entries, enabled = setOf(LibraryTab.Albums, LibraryTab.Artists))
        viewModel.uiState.value.currentTab shouldBe LibraryTab.Artists

        viewModel.onTabsChanged(LibraryTab.entries, enabled = setOf(LibraryTab.Albums))
        viewModel.uiState.value.currentTab shouldBe LibraryTab.Albums
    }

    @Test
    fun `hiding every tab leaves no current tab`() {
        val viewModel = viewModel()

        viewModel.onTabsChanged(LibraryTab.entries, enabled = emptySet())

        viewModel.uiState.value.tabs shouldBe emptyList()
        viewModel.uiState.value.currentTab shouldBe null
    }
}
