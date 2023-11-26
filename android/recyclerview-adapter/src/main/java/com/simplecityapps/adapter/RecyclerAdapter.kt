package com.simplecityapps.adapter

import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.recyclerview.widget.RecyclerView
import com.simplecityapps.diff.DiffCallbacks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@OptIn(ObsoleteCoroutinesApi::class)
open class RecyclerAdapter(scope: CoroutineScope, val skipIntermediateUpdates: Boolean = true) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var items = mutableListOf<ViewBinder>()
        private set

    var loggingEnabled = false

    var updateJob: Job? = null

    sealed class AdapterOperation {
        class Update(val newItems: MutableList<ViewBinder>, val callback: (() -> Unit)?) : AdapterOperation()
        class Add(val index: Int, val newItem: ViewBinder) : AdapterOperation()
        class Remove(val index: Int) : AdapterOperation()
        object Clear : AdapterOperation()
        class Move(val fromPosition: Int, val toPosition: Int) : AdapterOperation()
    }

    private val loggingListUpdateCallback by lazy {
        LoggingListUpdateCallback()
    }

    private val actor = scope.actor<AdapterOperation>(capacity = Channel.CONFLATED) {
        for (operation in channel) {
            when (operation) {
                is AdapterOperation.Update -> {
                    updateJob?.cancel()
                    updateJob = launch {
                        updateInternal(operation.newItems, operation.callback)
                    }
                }
                is AdapterOperation.Add -> addInternal(operation.index, operation.newItem)
                is AdapterOperation.Remove -> removeInternal(operation.index)
                is AdapterOperation.Clear -> {
                    updateJob?.cancel() // If there's a pending update we may as well cancel it
                    clearInternal()
                }
                is AdapterOperation.Move -> moveInternal(operation.fromPosition, operation.toPosition)
            }
        }
    }

    // Public

    fun update(newList: List<ViewBinder>, completion: (() -> Unit)? = null) {
        actor.trySend(AdapterOperation.Update(newList.toMutableList(), completion))
    }

    fun add(index: Int = items.size, newItem: ViewBinder) {
        actor.trySend(AdapterOperation.Add(index, newItem))
    }

    fun remove(index: Int) {
        actor.trySend(AdapterOperation.Remove(index))
    }

    fun remove(item: ViewBinder) {
        actor.trySend(AdapterOperation.Remove(items.indexOf(item)))
    }

    fun move(fromPosition: Int, toPosition: Int) {
        actor.trySend(AdapterOperation.Move(fromPosition, toPosition))
    }

    fun clear() {
        actor.trySend(AdapterOperation.Clear)
    }

    // Private

    private suspend fun updateInternal(newItems: MutableList<ViewBinder>, callback: (() -> Unit)? = null) {
        val diffResult = withContext(Dispatchers.IO) {
            if (skipIntermediateUpdates) {
                delay(50) // Acts as a debounce, there's a 50ms window for a new job to come in and cancel this one
            }
            DiffUtil.calculateDiff(DiffCallbacks(items.toList(), newItems))
        }

        withContext(Dispatchers.Main) {
            items = newItems.toMutableList()
            diffResult.dispatchUpdatesTo(this@RecyclerAdapter)
            if (loggingEnabled) {
                diffResult.dispatchUpdatesTo(loggingListUpdateCallback)
            }
            callback?.invoke()
        }
    }

    private suspend fun addInternal(index: Int, newItem: ViewBinder) {
        withContext(Dispatchers.Main) {
            items.add(index, newItem)
            notifyItemInserted(index)
        }
    }

    private suspend fun removeInternal(index: Int) {
        withContext(Dispatchers.Main) {
            items.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    private suspend fun clearInternal() {
        withContext(Dispatchers.Main) {
            val count = items.size
            items.clear()
            notifyItemRangeRemoved(0, count)
        }
    }

    private suspend fun moveInternal(fromPosition: Int, toPosition: Int) {
        withContext(Dispatchers.Main) {
            items.add(toPosition, items.removeAt(fromPosition))
            notifyItemMoved(fromPosition, toPosition)
        }
    }

    // RecyclerView.Adapter Implementation

    override fun getItemViewType(position: Int): Int {
        return items[position].viewType()
    }

    override fun getItemCount(): Int {
        return items.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return items.firstOrNull { adapterViewModel -> adapterViewModel.viewType() == viewType }?.createViewHolder(
            parent
        ) ?: throw IllegalStateException("Cannot create ViewHolder for view viewType: $viewType")
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        @Suppress("UNCHECKED_CAST")
        items[position].bindViewHolder(holder as ViewBinder.ViewHolder<ViewBinder>)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        items[position].bindViewHolder(holder as ViewBinder.ViewHolder<ViewBinder>, payloads.isNotEmpty())
    }

    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        (holder as? AttachAwareViewHolder)?.onAttach()
        super.onViewAttachedToWindow(holder)
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        (holder as? AttachAwareViewHolder)?.onDetach()
        super.onViewDetachedFromWindow(holder)
    }

    // Logging Callback

    private class LoggingListUpdateCallback : ListUpdateCallback {
        override fun onChanged(position: Int, count: Int, payload: Any?) {
            Timber.v("onChanged() $count")
        }

        override fun onMoved(fromPosition: Int, toPosition: Int) {
            Timber.v("onMoved() from: $fromPosition to: $toPosition")
        }

        override fun onInserted(position: Int, count: Int) {
            Timber.v("onInserted() $count")
        }

        override fun onRemoved(position: Int, count: Int) {
            Timber.v("onRemoved() $count")
        }
    }
}
