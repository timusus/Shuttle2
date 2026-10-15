package com.simplecityapps.shuttle.ui.screens.library.albums

import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.ui.screens.library.ViewMode
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** iOS test names can't hold `(`, `)` or `#` (Kotlin/Native export), unlike the JVM-only tests elsewhere. */
class AlbumListPreferenceManagerTest {

    private val preferences = AlbumListPreferenceManager(GeneralPreferenceManager(InMemoryKeyValueStore()))

    // Albums start as a grid: they're the most visual thing in the library (#491).
    @Test
    fun `albums start as a grid`() {
        preferences.albumListViewMode shouldBe ViewMode.Grid
    }

    @Test
    fun `a saved list view mode sticks`() {
        preferences.albumListViewMode = ViewMode.List

        preferences.albumListViewMode shouldBe ViewMode.List
    }

    @Test
    fun `a saved compact grid view mode sticks`() {
        preferences.albumListViewMode = ViewMode.CompactGrid

        preferences.albumListViewMode shouldBe ViewMode.CompactGrid
    }

    @Test
    fun `a stored view mode the app no longer knows reads as the grid`() {
        val store = GeneralPreferenceManager(InMemoryKeyValueStore())
        store.albumListViewMode = "Mosaic"

        AlbumListPreferenceManager(store).albumListViewMode shouldBe ViewMode.Grid
    }
}
