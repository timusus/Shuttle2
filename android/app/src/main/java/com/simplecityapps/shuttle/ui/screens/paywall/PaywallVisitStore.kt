package com.simplecityapps.shuttle.ui.screens.paywall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/**
 * The view model store of the gate paywall's current visit. It is held by the activity so a recreation keeps the
 * visit, but [close] clears it, so closing the dialog ends the paywall's view model and the next open starts a fresh one.
 */
class PaywallVisitStore : ViewModel() {
    private var store: ViewModelStore? = null

    /** The owner for the paywall's view models: the store of the current visit, created on first use. */
    val owner: ViewModelStoreOwner = object : ViewModelStoreOwner {
        override val viewModelStore: ViewModelStore
            get() = store ?: ViewModelStore().also { store = it }
    }

    /** Ends the visit: clears its view models. */
    fun close() {
        store?.clear()
        store = null
    }

    override fun onCleared() = close()
}
