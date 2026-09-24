package com.simplecityapps.shuttle.ui.common

import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import timber.log.Timber

/**
 * [keySelector] identifies an item across rebinds whose other fields may have changed (e.g. a
 * reordered [T]) so selection isn't lost when list data updates mid-selection. Defaults to the
 * item itself, preserving equals()-based identity for callers that don't need this.
 */
class ContextualToolbarHelper<T>(
    private val keySelector: (T) -> Any? = { it }
) {
    interface Callback<T> {
        fun onCountChanged(count: Int)

        fun onItemUpdated(
            item: T,
            isSelected: Boolean
        )
    }

    var isActive = false

    val selectedItems: List<T>
        get() = selectedItemsByKey.values.toList()

    private val selectedItemsByKey: LinkedHashMap<Any?, T> = LinkedHashMap()

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
        selectedItemsByKey.values.toList().forEach { item -> callback?.onItemUpdated(item = item, isSelected = false) }
        selectedItemsByKey.clear()
        isActive = false
    }

    private fun addOrRemoveItem(item: T) {
        val key = keySelector(item)
        if (selectedItemsByKey.containsKey(key)) {
            selectedItemsByKey.remove(key)
            callback?.onItemUpdated(item = item, isSelected = false)
        } else {
            selectedItemsByKey[key] = item
            callback?.onItemUpdated(item = item, isSelected = true)
        }

        updateCount()

        if (selectedItemsByKey.isEmpty()) {
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
