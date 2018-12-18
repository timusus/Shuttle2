package com.simplecityapps.shuttle.ui.view

import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment

interface ToolbarHost {

    fun getToolbar(): Toolbar?

}

fun Fragment.findToolbarHost(): ToolbarHost? {
    return (this as? ToolbarHost) ?: parentFragment?.findToolbarHost()
}