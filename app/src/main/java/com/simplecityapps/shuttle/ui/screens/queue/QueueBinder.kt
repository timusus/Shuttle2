package com.simplecityapps.shuttle.ui.screens.queue

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.shuttle.R

class QueueBinder(
    val queueItem: QueueItem,
    val imageLoader: ArtworkImageLoader,
    val listener: Listener
) : ViewBinder {

    interface Listener {
        fun onQueueItemClicked(queueItem: QueueItem)
    }

    override fun createViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_queue, parent, false))
    }

    override fun viewType(): ViewBinder.ViewType {
        return ViewBinder.ViewType.QueueItem
    }

    override fun sectionName(): String? {
        return queueItem.song.name.firstOrNull().toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is QueueBinder) return false

        if (queueItem != other.queueItem) return false

        return true
    }

    override fun hashCode(): Int {
        return queueItem.hashCode()
    }

    override fun areContentsTheSame(other: Any): Boolean {
        (other as? QueueBinder)?.let {
            return queueItem.isCurrent == other.queueItem.isCurrent
        }

        return true
    }


    class ViewHolder(itemView: View) : ViewBinder.ViewHolder<QueueBinder>(itemView) {

        private val title = itemView.findViewById<TextView>(R.id.title)
        private val subtitle = itemView.findViewById<TextView>(R.id.subtitle)

        init {
            itemView.setOnClickListener {
                viewBinder?.listener?.onQueueItemClicked(viewBinder!!.queueItem)
            }
        }

        override fun bind(viewBinder: QueueBinder) {
            super.bind(viewBinder)

            title.text = "${viewBinder.queueItem.song.track} ${viewBinder.queueItem.song.name}"
            subtitle.text = "${viewBinder.queueItem.song.albumArtistName} • ${viewBinder.queueItem.song.albumName}"

            if (viewBinder.queueItem.isCurrent) {
                (itemView.background as? RippleDrawable)?.let { rippleDrawable ->
                    rippleDrawable.setColor(ColorStateList.valueOf(Color.RED))
                }
                itemView.isHovered = true
            } else {
                (itemView.background as? RippleDrawable)?.let { rippleDrawable ->
                    rippleDrawable.setColor(ColorStateList.valueOf(itemView.context.getThemeColor(R.attr.rippleColor)))
                }
                itemView.isHovered = false
            }
        }
    }
}

@ColorInt
fun Context.getThemeColor(
    @AttrRes attribute: Int
) = TypedValue().let {
    theme.resolveAttribute(attribute, it, true)
    it.data
}