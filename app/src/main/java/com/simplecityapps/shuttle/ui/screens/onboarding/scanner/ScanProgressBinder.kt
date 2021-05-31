package com.simplecityapps.shuttle.ui.screens.onboarding.scanner

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.mediaprovider.MediaProvider
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.recyclerview.ViewTypes
import com.squareup.phrase.Phrase

class ScanProgressBinder(val mediaProviderType: MediaProvider.Type, val progressState: ProgressState) : ViewBinder {

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
                && progressState == other.progressState
    }


    class ViewHolder(itemView: View) : ViewBinder.ViewHolder<ScanProgressBinder>(itemView) {
        val icon: ImageView = itemView.findViewById(R.id.icon)
        val progressBar: ProgressBar = itemView.findViewById(R.id.progressBar)
        val titleTextView: TextView = itemView.findViewById(R.id.title)
        val subtitleTextView: TextView = itemView.findViewById(R.id.subtitle)
        val songCountTextView: TextView = itemView.findViewById(R.id.songCount)
        val checkImage: ImageView = itemView.findViewById(R.id.check)

        override fun bind(viewBinder: ScanProgressBinder, isPartial: Boolean) {
            super.bind(viewBinder, isPartial)

            subtitleTextView.text = viewBinder.mediaProviderType.title(itemView.context)

            when (viewBinder.progressState) {
                ProgressState.Unknown -> {
                    icon.setImageResource(viewBinder.mediaProviderType.iconResId())
                    titleTextView.text = viewBinder.mediaProviderType.title(itemView.context)
                    subtitleTextView.text = itemView.context.getString(R.string.onboarding_media_scanner_scanning)
                    progressBar.isIndeterminate = true
                    progressBar.isVisible = true
                    songCountTextView.isVisible = false
                    checkImage.isVisible = false
                }
                is ProgressState.InProgress -> {
                    icon.setImageResource(viewBinder.mediaProviderType.iconResId())
                    titleTextView.text = viewBinder.mediaProviderType.title(itemView.context)
                    subtitleTextView.text = viewBinder.progressState.message
                    progressBar.progress = ((viewBinder.progressState.progress / viewBinder.progressState.total.toFloat()) * 100).toInt()
                    progressBar.isVisible = true
                    progressBar.isIndeterminate = false
                    songCountTextView.isVisible = true
                    songCountTextView.text = Phrase.from(itemView.context, R.string.media_provider_scan_progress)
                        .put("progress", viewBinder.progressState.progress)
                        .put("total", viewBinder.progressState.total)
                        .format()
                }
                is ProgressState.Complete -> {
                    icon.setImageResource(viewBinder.mediaProviderType.iconResId())
                    titleTextView.text = viewBinder.mediaProviderType.title(itemView.context)
                    subtitleTextView.text = Phrase.from(itemView.context, R.string.media_provider_scan_success_subtitle)
                        .put("inserts", viewBinder.progressState.inserts)
                        .put("updates", viewBinder.progressState.updates)
                        .put("deletes", viewBinder.progressState.deletes)
                        .format()
                    songCountTextView.isVisible = false
                    progressBar.isVisible = false
                    checkImage.isVisible = true
                }
                is ProgressState.Failed -> {
                    icon.setImageResource(viewBinder.mediaProviderType.iconResId())
                    titleTextView.text = viewBinder.mediaProviderType.title(itemView.context)
                    subtitleTextView.text = itemView.context.getString(R.string.media_provider_scan_failure_title)
                    progressBar.isVisible = false
                    songCountTextView.isVisible = false
                    checkImage.isVisible = false
                }
            }
        }
    }
}