package com.simplecityapps.shuttle.ui.common

import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import timber.log.Timber

class ContextualToolbarHelper<T> {
    interface Callback<T> {
        fun onCountChanged(count: Int)

        fun onItemUpdated(
            item: T,
            isSelected: Boolean
        )
    }

    var isActive = false

    val selectedItems: MutableSet<T> = mutableSetOf()

    var callback: Callback<T>? = null

    var toolbar: Toolbar? = null
    var contextualToolbar: Toolbar? = null

    fun show() {
        contextualToolbar?.let { contextualToolbar ->
            toolbar?.isVisible = false
            contextualToolbar.isVisible = true
            contextualToolbar.setNavigationOnClickListener {
                hide()
            }
            isActive = true
            updateCount()
        } ?: Timber.e("Failed to show contextual toolbar: toolbar null")
    }

    fun hide() {
        toolbar?.isVisible = true
        contextualToolbar?.isVisible = false
        contextualToolbar?.setNavigationOnClickListener(null)
        selectedItems.forEach { item -> callback?.onItemUpdated(item = item, isSelected = false) }
        selectedItems.clear()
        isActive = false
    }

    private fun addOrRemoveItem(item: T) {
        if (selectedItems.contains(item)) {
            selectedItems.remove(item)
            callback?.onItemUpdated(item = item, isSelected = false)
        } else {
            selectedItems.add(item)
            callback?.onItemUpdated(item = item, isSelected = true)
        }

        updateCount()

        if (selectedItems.isEmpty()) {
            hide()
        }
    }

    fun handleClick(item: T): Boolean {
        if (isActive) {
            addOrRemoveItem(item)
            return true
        }
        return false
    }

    fun handleLongClick(item: T): Boolean {
        addOrRemoveItem(item)
        if (!isActive) {
            show()
            return true
        }
        return false
    }

    private fun updateCount() {
        callback?.onCountChanged(selectedItems.size)
    }
}
