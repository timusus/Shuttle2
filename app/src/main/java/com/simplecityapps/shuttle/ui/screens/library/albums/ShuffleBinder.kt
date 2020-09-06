package com.simplecityapps.shuttle.ui.screens.library.albums

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes

class ShuffleBinder(val listener: Listener) : ViewBinder {

    interface Listener {
        fun onClicked()
    }

    override fun createViewHolder(parent: ViewGroup): ViewBinder.ViewHolder<out ViewBinder> {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_album_shuffle, parent, false))
    }

    override fun viewType(): Int {
        return ViewTypes.AlbumShuffle
    }

    override fun spanSize(spanCount: Int): Int {
        return spanCount
    }


    class ViewHolder(itemView: View) : ViewBinder.ViewHolder<ShuffleBinder>(itemView) {
        init {
            itemView.setOnClickListener {
                viewBinder?.listener?.onClicked()
            }
        }
    }
}