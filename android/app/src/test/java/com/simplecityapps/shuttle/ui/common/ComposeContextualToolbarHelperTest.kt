package com.simplecityapps.shuttle.ui.common

import android.graphics.drawable.ColorDrawable
import android.view.ContextThemeWrapper
import android.widget.ImageButton
import androidx.appcompat.widget.Toolbar
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Library tabs share one toolbar and one contextual toolbar. These tests stand up two tabs' helpers
 * over the same pair of toolbars, as LibraryFragment's pager does, and check that one tab's idle
 * state can't take down the selection toolbar another tab put up (#344).
 */
@RunWith(RobolectricTestRunner::class)
class ComposeContextualToolbarHelperTest {

    private val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), androidx.appcompat.R.style.Theme_AppCompat)
    private val toolbar = Toolbar(context)
    private val contextualToolbar = Toolbar(context).apply { visibility = Toolbar.GONE }

    private var songsSelectionCleared = 0
    private var albumsSelectionCleared = 0

    private val songsTab = helper { songsSelectionCleared++ }
    private val albumsTab = helper { albumsSelectionCleared++ }

    private fun helper(clearSelection: () -> Unit) = ComposeContextualToolbarHelper(clearSelection).apply {
        toolbar = this@ComposeContextualToolbarHelperTest.toolbar
        contextualToolbar = this@ComposeContextualToolbarHelperTest.contextualToolbar
    }

    private fun assertSelectionToolbarShown(title: String) {
        contextualToolbar.visibility shouldBe Toolbar.VISIBLE
        toolbar.visibility shouldBe Toolbar.GONE
        contextualToolbar.title.toString() shouldBe title
    }

    private fun assertLibraryToolbarShown() {
        contextualToolbar.visibility shouldBe Toolbar.GONE
        toolbar.visibility shouldBe Toolbar.VISIBLE
    }

    @Test
    fun `selecting shows the contextual toolbar with the selected count`() {
        songsTab.render(selectedCount = 2, mediaProviders = emptyList())

        assertSelectionToolbarShown("2 selected")
    }

    @Test
    fun `an idle tab's state emission does not hide another tab's selection toolbar`() {
        songsTab.render(selectedCount = 1, mediaProviders = emptyList())

        // Albums re-emits with no selection, e.g. because play/pause changed an album's play count
        albumsTab.render(selectedCount = 0, mediaProviders = emptyList())
        albumsTab.render(selectedCount = 0, mediaProviders = emptyList())

        assertSelectionToolbarShown("1 selected")
        songsSelectionCleared shouldBe 0
    }

    @Test
    fun `a tab re-emitting its own selection keeps the selection toolbar`() {
        songsTab.render(selectedCount = 1, mediaProviders = emptyList())
        songsTab.render(selectedCount = 1, mediaProviders = emptyList())

        assertSelectionToolbarShown("1 selected")
        songsSelectionCleared shouldBe 0
    }

    @Test
    fun `a tab hides the selection toolbar when its own selection ends`() {
        songsTab.render(selectedCount = 1, mediaProviders = emptyList())

        songsTab.render(selectedCount = 0, mediaProviders = emptyList())

        assertLibraryToolbarShown()
    }

    @Test
    fun `navigating up from the selection toolbar clears the selection`() {
        songsTab.render(selectedCount = 1, mediaProviders = emptyList())

        contextualToolbar.navigationIcon = ColorDrawable()
        (0 until contextualToolbar.childCount)
            .map { contextualToolbar.getChildAt(it) }
            .first { it is ImageButton }
            .performClick()

        assertLibraryToolbarShown()
        songsSelectionCleared shouldBe 1
    }

    @Test
    fun `leaving the page mid-selection clears the selection and resets the toolbar`() {
        songsTab.render(selectedCount = 1, mediaProviders = emptyList())

        songsTab.onPageLeft()

        assertLibraryToolbarShown()
        songsSelectionCleared shouldBe 1
    }

    @Test
    fun `leaving an idle page leaves the toolbar alone`() {
        songsTab.render(selectedCount = 1, mediaProviders = emptyList())

        albumsTab.onPageLeft()

        assertSelectionToolbarShown("1 selected")
        songsSelectionCleared shouldBe 0
        albumsSelectionCleared shouldBe 1
    }
}
