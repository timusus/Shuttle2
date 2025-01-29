package com.simplecityapps.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.CallSuper
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.RecyclerView
import com.simplecityapps.diff.ContentsComparator

interface ViewBinder : ContentsComparator {
    fun createViewHolder(parent: ViewGroup): ViewHolder<out ViewBinder>

    fun viewType(): Int

    fun spanSize(spanCount: Int): Int = spanCount

    fun bindViewHolder(
        holder: ViewHolder<ViewBinder>,
        isPartial: Boolean = false
    ) {
        holder.bind(this, isPartial)
    }

    override fun areContentsTheSame(other: Any): Boolean = true

    open class ViewHolder<B : ViewBinder>(itemView: View) :
        RecyclerView.ViewHolder(itemView),
        RecyclingViewHolder,
        AttachAwareViewHolder {
        var viewBinder: B? = null

        @CallSuper
        open fun bind(
            viewBinder: B,
            isPartial: Boolean
        ) {
            this.viewBinder = viewBinder
        }

        override fun recycle() {
        }

        override fun onAttach() {
        }

        override fun onDetach() {
        }
    }

    // Extension

    fun ViewGroup.inflateView(
        @LayoutRes layoutResId: Int
    ): View = LayoutInflater.from(context).inflate(layoutResId, this, false)
}
