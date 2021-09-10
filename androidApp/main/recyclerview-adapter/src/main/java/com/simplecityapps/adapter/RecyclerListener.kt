package com.simplecityapps.adapter

import androidx.recyclerview.widget.RecyclerView

class RecyclerListener : RecyclerView.RecyclerListener {

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        (holder as? RecyclingViewHolder)?.recycle()
    }
}