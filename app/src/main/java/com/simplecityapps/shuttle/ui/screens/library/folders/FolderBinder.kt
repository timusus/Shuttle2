package com.simplecityapps.shuttle.ui.screens.library.folders

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.simplecityappds.saf.SafDirectoryHelper
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.recyclerview.SectionViewBinder
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes
import com.simplecityapps.shuttle.ui.common.utils.dp

class FolderBinder(
    val fileNode: SafDirectoryHelper.FileNode,
    val imageLoader: ArtworkImageLoader,
    val listener: Listener? = null
) : ViewBinder,
    SectionViewBinder {

    interface Listener {
        fun onNodeSelected(node: SafDirectoryHelper.FileNode)
        fun onOverflowClicked(view: View, song: Song) {}
    }

    override fun createViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_folder, parent, false))
    }

    override fun viewType(): Int {
        return ViewTypes.Folder
    }

    override fun getSectionName(): String? {
        return fileNode.displayName.firstOrNull()?.toString() ?: ""
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FolderBinder

        if (fileNode != other.fileNode) return false

        return true
    }

    override fun hashCode(): Int {
        return fileNode.hashCode()
    }


    class ViewHolder(itemView: View) : ViewBinder.ViewHolder<FolderBinder>(itemView) {

        private val title = itemView.findViewById<TextView>(R.id.title)
        private val subtitle = itemView.findViewById<TextView>(R.id.subtitle)
        private val imageView: ImageView = itemView.findViewById(R.id.imageView)
        private val overflowButton: ImageButton = itemView.findViewById(R.id.overflowButton)

        init {
            itemView.setOnClickListener {
                viewBinder?.listener?.onNodeSelected(viewBinder!!.fileNode)
            }
            overflowButton.setOnClickListener {
                viewBinder?.listener?.onOverflowClicked(it, (viewBinder!!.fileNode as FileNode).song)
            }
        }

        override fun bind(viewBinder: FolderBinder, isPartial: Boolean) {
            super.bind(viewBinder, isPartial)

            subtitle.text = viewBinder.fileNode.displayName

            if (viewBinder.fileNode is FileNodeTree) {
                imageView.setImageResource(R.drawable.ic_folder_open_black_24dp)
                imageView.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(itemView.context, R.color.primary_material_dark))
                imageView.setPadding(4.dp)
                title.isVisible = false
                overflowButton.isVisible = false
            } else {
                imageView.imageTintList = null
                imageView.setPadding(0)
                title.text = (viewBinder.fileNode as FileNode).song.name
                title.isVisible = true
                overflowButton.isVisible = true

                viewBinder.imageLoader.loadArtwork(
                    imageView,
                    viewBinder.fileNode.song,
                    ArtworkImageLoader.Options.RoundedCorners(16),
                    ArtworkImageLoader.Options.Crossfade(200)
                )
            }
        }

        override fun recycle() {
            viewBinder?.imageLoader?.clear(imageView)
        }
    }
}