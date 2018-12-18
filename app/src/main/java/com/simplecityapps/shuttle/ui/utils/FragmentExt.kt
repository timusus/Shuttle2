package com.simplecityapps.shuttle.ui.utils

import androidx.fragment.app.Fragment

fun <T> Fragment.findParent(clazz: Class<T>): T? {
    if (clazz.isInstance(parentFragment)) {
        @Suppress("UNCHECKED_CAST")
        return parentFragment as T
    }
    return parentFragment?.findParent(clazz)
}