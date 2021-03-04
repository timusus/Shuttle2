package com.simplecityapps.shuttle.ui.screens.opensource

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.mikepenz.aboutlibraries.entity.Library
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes

class LibraryBinder(val library: Library, val listener: Listener) : ViewBinder {

    interface Listener {
        fun onItemClick(library: Library)
    }

    override fun createViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_license, parent, false))
    }

    override fun viewType(): Int {
        return ViewTypes.License
    }


    class ViewHolder(itemView: View) : ViewBinder.ViewHolder<LibraryBinder>(itemView) {
        val title: TextView = itemView.findViewById(R.id.title)
        val author: TextView = itemView.findViewById(R.id.author)
        val version: TextView = itemView.findViewById(R.id.version)
        val license: TextView = itemView.findViewById(R.id.license)

        init {
            itemView.setOnClickListener {
                viewBinder?.listener?.onItemClick(viewBinder!!.library)
            }
        }

        override fun bind(viewBinder: LibraryBinder, isPartial: Boolean) {
            super.bind(viewBinder, isPartial)

            title.text = viewBinder.library.libraryName.ifEmpty { "Unknown" }
            author.text = "Author: ${viewBinder.library.author.ifEmpty { "Unknown" }}"
            version.text = "Version: ${viewBinder.library.libraryVersion.ifEmpty { "Unknown" }}"
            license.text = "License: ${viewBinder.library.licenses?.firstOrNull()?.licenseName?.ifEmpty { "Unknown" } ?: "Unknown"}"
        }
    }
}