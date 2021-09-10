package com.simplecityapps.shuttle.ui.common.view

import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment

interface ToolbarHost {
    val toolbar: Toolbar?
    val contextualToolbar: Toolbar?
}

fun Fragment.findToolbarHost(): ToolbarHost? {
    return (this as? ToolbarHost) ?: parentFragment?.findToolbarHost()
}