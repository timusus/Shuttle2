package com.simplecityapps.shuttle.ui.common

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager

fun View.closeKeyboard() {
    (context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(windowToken, 0)
}