package com.simplecityapps.shuttle.ui.screens.library

import com.simplecityapps.shuttle.persistence.LibraryTab
import io.kotest.matchers.shouldBe
import org.junit.Test

class LibrarySelectionCoordinatorTest {

    @Test
    fun `switching tabs clears the outgoing tab's selection`() {
        val cleared = mutableListOf<LibraryTab>()
        val coordinator = LibrarySelectionCoordinator(clearSelection = { cleared += it })

        coordinator.onTabChanged(LibraryTab.Songs)
        coordinator.onTabChanged(LibraryTab.Albums)

        cleared shouldBe listOf(LibraryTab.Songs)
    }

    @Test
    fun `re-selecting the same tab clears nothing`() {
        val cleared = mutableListOf<LibraryTab>()
        val coordinator = LibrarySelectionCoordinator(clearSelection = { cleared += it })

        coordinator.onTabChanged(LibraryTab.Songs)
        coordinator.onTabChanged(LibraryTab.Songs)

        cleared shouldBe emptyList()
    }

    @Test
    fun `the first tab selected has nothing to clear`() {
        val cleared = mutableListOf<LibraryTab>()
        val coordinator = LibrarySelectionCoordinator(clearSelection = { cleared += it })

        coordinator.onTabChanged(LibraryTab.Songs)

        cleared shouldBe emptyList()
    }

    @Test
    fun `leaving the destination clears whichever tab was showing`() {
        val cleared = mutableListOf<LibraryTab>()
        val coordinator = LibrarySelectionCoordinator(clearSelection = { cleared += it })

        coordinator.onTabChanged(LibraryTab.Albums)
        coordinator.onDestinationLeft()

        cleared shouldBe listOf(LibraryTab.Albums)
    }

    @Test
    fun `leaving with no current tab clears nothing`() {
        val cleared = mutableListOf<LibraryTab>()
        val coordinator = LibrarySelectionCoordinator(clearSelection = { cleared += it })

        coordinator.onDestinationLeft()

        cleared shouldBe emptyList()
    }

    @Test
    fun `a tab switch after leaving starts tracking again`() {
        val cleared = mutableListOf<LibraryTab>()
        val coordinator = LibrarySelectionCoordinator(clearSelection = { cleared += it })

        coordinator.onTabChanged(LibraryTab.Albums)
        coordinator.onDestinationLeft()
        coordinator.onTabChanged(LibraryTab.Songs)
        coordinator.onTabChanged(LibraryTab.Artists)

        cleared shouldBe listOf(LibraryTab.Albums, LibraryTab.Songs)
    }
}
