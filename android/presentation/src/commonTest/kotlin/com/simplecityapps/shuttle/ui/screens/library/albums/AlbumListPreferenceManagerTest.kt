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
}
