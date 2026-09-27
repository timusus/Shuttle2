package com.simplecityapps.shuttle.ui.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class SelectionState<T> {

    private val _selectedItems = MutableStateFlow(emptySet<T>())
    val selectedItems = _selectedItems.asStateFlow()
    val selectedCount = _selectedItems.map { it.size }

    fun toggle(item: T) {
        _selectedItems.value = if (_selectedItems.value.contains(item)) {
            _selectedItems.value - item
        } else {
            _selectedItems.value + item
        }
    }

    fun clear() {
        _selectedItems.value = emptySet()
    }

    fun isActive() = _selectedItems.value.isNotEmpty()
}
