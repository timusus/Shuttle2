package com.simplecityapps.shuttle.ui.common.recyclerview

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

fun RecyclerView.clearAdapterOnDetach() {

    addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewDetachedFromWindow(v: View?) {
            adapter = null
        }

        override fun onViewAttachedToWindow(v: View?) {
        }
    })
}

fun ViewPager2.clearAdapterOnDetach() {

    addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewDetachedFromWindow(v: View?) {
            adapter = null
        }

        override fun onViewAttachedToWindow(v: View?) {
        }
    })
}