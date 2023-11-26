package com.simplecityapps.shuttle.ui.screens.opensource

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.mikepenz.aboutlibraries.entity.Library
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes
import com.squareup.phrase.Phrase

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

    class ViewHolder(val itemView: View) : ViewBinder.ViewHolder<LibraryBinder>(itemView) {
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

            title.text = viewBinder.library.name.ifEmpty { itemView.context.getString(com.simplecityapps.core.R.string.unknown) }

            author.text = Phrase.from(itemView.context, R.string.open_source_library_author)
                .put("author", viewBinder.library.organization?.name.orEmpty().ifEmpty { itemView.context.getString(com.simplecityapps.core.R.string.unknown) })
                .format()

            version.text = Phrase.from(itemView.context, R.string.open_source_library_version)
                .put("version", viewBinder.library.artifactVersion.orEmpty().ifEmpty { itemView.context.getString(com.simplecityapps.core.R.string.unknown) })
                .format()

            license.text = Phrase.from(itemView.context, R.string.open_source_library_license)
                .put(
                    "license",
                    viewBinder.library.licenses.firstOrNull()?.name.orEmpty().ifEmpty { itemView.context.getString(com.simplecityapps.core.R.string.unknown) })
                .format()
        }
    }
}
