package com.simplecityapps.shuttle.shared.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore

/**
 * Ends a shared ViewModel's life from Swift: runs `onCleared`, cancels its `viewModelScope` and closes what it added
 * with `addCloseable`, as a popped back stack entry's `ViewModelStore` does on Android. `ViewModel.clear()` is
 * internal to lifecycle, so the ViewModel goes through a one-off store of its own.
 *
 * `ViewModelCache` (ios/S2/KMP) calls this when a screen's entry is evicted; call it once per ViewModel.
 */
fun ViewModel.clearFromSwift() {
    val store = ViewModelStore()
    @Suppress("RestrictedApi")
    store.put(STORE_KEY, this)
    store.clear()
}

private const val STORE_KEY = "com.simplecityapps.shuttle.shared.di.clearFromSwift"
