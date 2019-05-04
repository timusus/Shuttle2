package com.simplecityapps.shuttle.ui.common.recyclerview

import android.view.View
import androidx.recyclerview.widget.RecyclerView

fun RecyclerView.clearAdapterOnDetach() {

    addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewDetachedFromWindow(v: View?) {
            adapter = null
        }

        override fun onViewAttachedToWindow(v: View?) {
        }
    })
}