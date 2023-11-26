package com.simplecityapps.shuttle.ui.common.utils

import android.os.Bundle
import androidx.fragment.app.Fragment

fun <T> Fragment.findParent(clazz: Class<T>): T? {
    if (clazz.isInstance(parentFragment)) {
        @Suppress("UNCHECKED_CAST")
        return parentFragment as T
    }
    return parentFragment?.findParent(clazz)
}

inline fun <T : Fragment> T.withArgs(argsBuilder: Bundle.() -> Unit): T = this.apply { arguments = Bundle().apply(argsBuilder) }
