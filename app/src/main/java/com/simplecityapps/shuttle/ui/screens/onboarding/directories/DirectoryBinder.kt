package com.simplecityapps.shuttle.ui.screens.onboarding.directories

import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import com.simplecityappds.saf.SafDirectoryHelper
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes

class DirectoryBinder(
    val directory: MusicDirectoriesContract.Directory,
    val listener: Listener
) : ViewBinder {

    interface Listener {
        fun onRemoveClicked(directory: MusicDirectoriesContract.Directory)
    }

    override fun createViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(parent.inflateView(R.layout.list_item_onboaring_directory))
    }

    override fun viewType(): Int {
        return ViewTypes.OnboardingDirectory
    }

    override fun areContentsTheSame(other: Any): Boolean {
        return directory.traversalComplete == (other as? DirectoryBinder)?.directory?.traversalComplete
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DirectoryBinder

        if (directory != other.directory) return false

        return true
    }

    override fun hashCode(): Int {
        return directory.hashCode()
    }


    class ViewHolder(itemView: View) : ViewBinder.ViewHolder<DirectoryBinder>(itemView) {

        private val title: TextView = itemView.findViewById(R.id.titleLabel)
        private val subtitle: TextView = itemView.findViewById(R.id.subtitleLabel)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.progressBar)
        private val removeButton: ImageButton = itemView.findViewById(R.id.removeButton)

        init {
            removeButton.setOnClickListener { viewBinder?.listener?.onRemoveClicked(viewBinder!!.directory) }
        }

        override fun bind(viewBinder: DirectoryBinder, isPartial: Boolean) {
            super.bind(viewBinder, isPartial)

            title.text = viewBinder.directory.tree.documentId
            progressBar.isVisible = !viewBinder.directory.traversalComplete
            if (viewBinder.directory.traversalComplete) {
                val leaves = viewBinder.directory.tree.getLeaves()
                subtitle.text = "${leaves.size} audio file${if (leaves.size == 1) "" else "s"}"
                progressBar.isVisible = false
            } else {
                progressBar.isVisible = true
                subtitle.text = "Scanning..."
            }
        }
    }
}