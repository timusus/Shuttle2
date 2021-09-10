package com.simplecityapps.shuttle.ui.screens.onboarding.scanner

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.mediaprovider.iconResId
import com.simplecityapps.mediaprovider.title
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes

class ScanProgressBinder(
    val mediaProviderType: MediaProviderType,
    val songImportProgressState: ImportProgressState,
    val playlistImportProgressState: ImportProgressState
) : ViewBinder {

    override fun createViewHolder(parent: ViewGroup): ViewBinder.ViewHolder<out ViewBinder> {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_scan_progress, parent, false))
    }

    override fun viewType(): Int {
        return ViewTypes.ScanProgress
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ScanProgressBinder
        if (mediaProviderType != other.mediaProviderType) return false
        return true
    }

    override fun hashCode(): Int {
        return mediaProviderType.hashCode()
    }

    override fun areContentsTheSame(other: Any): Boolean {
        return other is ScanProgressBinder
                && mediaProviderType == other.mediaProviderType
                && songImportProgressState == other.songImportProgressState
                && playlistImportProgressState == other.playlistImportProgressState
    }


    class ViewHolder(itemView: View) : ViewBinder.ViewHolder<ScanProgressBinder>(itemView) {
        val icon: ImageView = itemView.findViewById(R.id.icon)
        val titleTextView: TextView = itemView.findViewById(R.id.title)

        val songImportTitle: TextView = itemView.findViewById(R.id.songImportTitle)
        val songProgressBar: ProgressBar = itemView.findViewById(R.id.songImportProgress)
        val songImportProgressMessage: TextView = itemView.findViewById(R.id.songImportProgressMessage)
        val songImportSuccessImage: ImageView = itemView.findViewById(R.id.songImportSuccessImage)

        val playlistImportTitle: TextView = itemView.findViewById(R.id.playlistImportTitle)
        val playlistProgressBar: ProgressBar = itemView.findViewById(R.id.playlistImportProgress)
        val playlistImportProgressMessage: TextView = itemView.findViewById(R.id.playlistImportProgressMessage)
        val playlistImportSuccessImage: ImageView = itemView.findViewById(R.id.playlistImportSuccessImage)

        override fun bind(viewBinder: ScanProgressBinder, isPartial: Boolean) {
            super.bind(viewBinder, isPartial)

            titleTextView.text = viewBinder.mediaProviderType.title(itemView.context)
            icon.setImageResource(viewBinder.mediaProviderType.iconResId())

            when (viewBinder.songImportProgressState) {
                ImportProgressState.Unknown -> {
                    titleTextView.text = viewBinder.mediaProviderType.title(itemView.context)
                    songImportTitle.text = "Importing songs..."
                    songProgressBar.isIndeterminate = true
                    songProgressBar.isVisible = true

                    songImportProgressMessage.isVisible = false
                    songImportSuccessImage.isVisible = false
                }
                is ImportProgressState.InProgress -> {
                    songImportTitle.isVisible = true
                    songImportTitle.text = "Importing songs..."
                    songProgressBar.progress = ((viewBinder.songImportProgressState.progress?.asFloat() ?: 0f) * 100).toInt()
                    songProgressBar.isVisible = true
                    songProgressBar.isIndeterminate = viewBinder.songImportProgressState.progress == null
                    songImportProgressMessage.isVisible = true
                    songImportProgressMessage.text = viewBinder.songImportProgressState.message
                    songImportProgressMessage.gravity = GravityCompat.END
                    songImportSuccessImage.isVisible = false
                }
                is ImportProgressState.Complete -> {
                    songImportTitle.text = "Song import complete"
                    songImportTitle.isVisible = true
                    songImportSuccessImage.isVisible = true
                    songImportProgressMessage.isVisible = false
                    songProgressBar.isVisible = false
                }
                is ImportProgressState.Failed -> {
                    songImportTitle.text = "Song import failed"
                    songProgressBar.isVisible = false
                    songImportProgressMessage.isVisible = true
                    songImportProgressMessage.text = viewBinder.songImportProgressState.message
                    songImportProgressMessage.gravity = GravityCompat.START
                    songImportSuccessImage.isVisible = false
                }

            }
            when (viewBinder.playlistImportProgressState) {
                ImportProgressState.Unknown -> {
                    titleTextView.text = viewBinder.mediaProviderType.title(itemView.context)
                    playlistImportTitle.text = "Unknown"
                    playlistProgressBar.isIndeterminate = true
                    playlistProgressBar.isVisible = true

                    playlistImportTitle.isVisible = false
                    playlistImportProgressMessage.isVisible = false
                    playlistImportSuccessImage.isVisible = false
                    playlistProgressBar.isVisible = false
                }
                is ImportProgressState.InProgress -> {
                    playlistImportTitle.isVisible = true
                    playlistImportTitle.text = "Importing playlists..."
                    playlistProgressBar.progress = ((viewBinder.playlistImportProgressState.progress?.asFloat() ?: 0f) * 100).toInt()
                    playlistProgressBar.isVisible = true
                    playlistProgressBar.isIndeterminate = viewBinder.playlistImportProgressState.progress == null
                    playlistImportProgressMessage.isVisible = true
                    playlistImportProgressMessage.gravity = GravityCompat.END
                    playlistImportProgressMessage.text = viewBinder.playlistImportProgressState.message
                    playlistImportSuccessImage.isVisible = false
                }
                is ImportProgressState.Complete -> {
                    playlistImportTitle.text = "Playlist import complete"
                    playlistImportTitle.isVisible = true
                    playlistImportSuccessImage.isVisible = true
                    playlistImportProgressMessage.isVisible = false
                    playlistProgressBar.isVisible = false
                }
                is ImportProgressState.Failed -> {
                    playlistImportTitle.text = "Playlist import failed"
                    playlistProgressBar.isVisible = false
                    playlistImportProgressMessage.isVisible = true
                    playlistImportProgressMessage.text = viewBinder.playlistImportProgressState.message
                    playlistImportProgressMessage.gravity = GravityCompat.START
                    playlistImportSuccessImage.isVisible = false
                }
            }
        }
    }
}