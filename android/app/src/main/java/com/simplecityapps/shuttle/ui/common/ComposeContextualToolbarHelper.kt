package com.simplecityapps.shuttle.ui.common

import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.MediaProviderType
import com.squareup.phrase.Phrase
import timber.log.Timber

/**
 * Drives the toolbar and contextual toolbar that every library tab shares (see ToolbarHost) from
 * one tab's multi-select state.
 *
 * Because the toolbars are shared, a tab may only hide the contextual toolbar when it is the one
 * that showed it. Otherwise an idle tab re-emitting its state (e.g. Albums, whose play counts
 * change on play/pause) wipes the selection toolbar of the tab in view (#344).
 */
class ComposeContextualToolbarHelper(
    private val clearSelection: () -> Unit,
) {

    var toolbar: Toolbar? = null
    var contextualToolbar: Toolbar? = null

    /** Whether this tab put up the contextual toolbar and hasn't hidden it since. */
    private var isShowing = false

    /**
     * Applies this tab's selection to the shared toolbars: shows the contextual toolbar with the
     * selected count while [selectedCount] > 0, and hides it only on this tab's own transition
     * back to no selection. Call it only while the tab is the visible page.
     */
    fun render(
        selectedCount: Int,
        mediaProviders: List<MediaProviderType>,
    ) {
        if (selectedCount > 0) {
            show()
            contextualToolbar?.let { contextualToolbar ->
                contextualToolbar.title =
                    Phrase.fromPlural(contextualToolbar.context, R.plurals.multi_select_items_selected, selectedCount)
                        .put("count", selectedCount)
                        .format()
                TagEditorMenuSanitiser.sanitise(contextualToolbar.menu, mediaProviders)
            }
        } else if (isShowing) {
            hide()
        }
    }

    /** Clears this tab's selection when it stops being the visible page. */
    fun onPageLeft() {
        if (isShowing) hide() else clearSelection()
    }

    fun hide() {
        toolbar?.isVisible = true
        contextualToolbar?.isVisible = false
        contextualToolbar?.setNavigationOnClickListener(null)
        isShowing = false
        clearSelection()
    }

    private fun show() {
        contextualToolbar?.let { contextualToolbar ->
            toolbar?.isVisible = false
            contextualToolbar.isVisible = true
            contextualToolbar.setNavigationOnClickListener {
                hide()
            }
            isShowing = true
        } ?: Timber.e("Failed to show contextual toolbar: toolbar null")
    }
}
