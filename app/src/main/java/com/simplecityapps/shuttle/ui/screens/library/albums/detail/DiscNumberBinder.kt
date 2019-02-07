package com.simplecityapps.shuttle.ui.screens.library.albums.detail

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.shuttle.R

class DiscNumberBinder(val discNumber: Int) : ViewBinder {

    override fun createViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_disc_number, parent, false))
    }

    override fun viewType(): ViewBinder.ViewType {
        return ViewBinder.ViewType.DiscNumber
    }


    class ViewHolder(itemView: View) : ViewBinder.ViewHolder<DiscNumberBinder>(itemView) {

        override fun bind(viewBinder: DiscNumberBinder) {
            super.bind(viewBinder)

            itemView as TextView
            itemView.text = "Disc ${viewBinder.discNumber}"
        }
    }
}