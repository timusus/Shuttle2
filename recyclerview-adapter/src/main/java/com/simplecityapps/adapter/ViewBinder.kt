package com.simplecityapps.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.CallSuper
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.RecyclerView
import com.simplecityapps.diff.ContentsComparator

interface ViewBinder : ContentsComparator {

    // Todo: This doesn't belong here
    enum class ViewType {
        Song
    }

    fun createViewHolder(parent: ViewGroup): ViewHolder<out ViewBinder>

    fun viewType(): ViewType

    fun spanSize(spanCount: Int): Int {
        return spanCount
    }

    fun bindViewHolder(holder: ViewHolder<ViewBinder>) {
        holder.bind(this)
    }

    fun sectionName(): String? {
        return null
    }

    override fun areContentsTheSame(other: Any): Boolean {
        return this == other
    }

    open class ViewHolder<B : ViewBinder>(itemView: View) : RecyclerView.ViewHolder(itemView),
            RecyclingViewHolder {

        var viewBinder: B? = null

        @CallSuper
        open fun bind(viewBinder: B) {
            this.viewBinder = viewBinder
        }

        override fun recycle() {

        }
    }

    fun ViewGroup.inflateView(@LayoutRes layoutResId: Int): View {
        return LayoutInflater.from(context).inflate(layoutResId, this, false)
    }
}