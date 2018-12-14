package com.simplecityapps.shuttle.ui.screens.library.folders

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.shuttle.R

class FolderBinder(val path: String) : ViewBinder {

    override fun createViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_folder, parent, false))
    }

    override fun viewType(): ViewBinder.ViewType {
        return ViewBinder.ViewType.Folder
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FolderBinder) return false

        if (path != other.path) return false

        return true
    }

    override fun hashCode(): Int {
        return path.hashCode()
    }


    class ViewHolder(itemView: View) : ViewBinder.ViewHolder<FolderBinder>(itemView) {

        private val title = itemView.findViewById<TextView>(R.id.title)

        override fun bind(viewBinder: FolderBinder) {
            super.bind(viewBinder)

            title.text = viewBinder.path
        }
    }
}