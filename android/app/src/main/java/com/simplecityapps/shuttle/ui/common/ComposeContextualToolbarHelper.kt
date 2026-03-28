package com.simplecityapps.shuttle.ui.common

import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import timber.log.Timber

class ComposeContextualToolbarHelper(
    private val clearSelection: () -> Unit,
) {

    var toolbar: Toolbar? = null
    var contextualToolbar: Toolbar? = null

    fun show() {
        contextualToolbar?.let { contextualToolbar ->
            toolbar?.isVisible = false
            contextualToolbar.isVisible = true
            contextualToolbar.setNavigationOnClickListener {
                hide()
            }
        } ?: Timber.e("Failed to show contextual toolbar: toolbar null")
    }

    fun hide() {
        toolbar?.isVisible = true
        contextualToolbar?.isVisible = false
        contextualToolbar?.setNavigationOnClickListener(null)
        clearSelection()
    }
}
