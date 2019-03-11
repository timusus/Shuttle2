package com.simplecityapps.shuttle.ui.screens.debug

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.debug.DebugLoggingTree
import com.simplecityapps.shuttle.debug.LogMessage
import com.simplecityapps.shuttle.ui.common.utils.withArgs
import kotlinx.android.synthetic.main.fragment_debug_logging.*
import java.io.Serializable
import javax.inject.Inject

class LoggingFragment : Fragment(), Injectable, DebugLoggingTree.Callback {

    @Inject lateinit var debugLoggingTree: DebugLoggingTree

    private val adapter = RecyclerAdapter()

    private var filter: Filter? = null

    init {
        adapter.loggingEnabled = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        filter = arguments?.getSerializable(ARG_FILTER) as? Filter
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_debug_logging, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        debugLoggingTree.addCallback(this)

        recyclerView.adapter = adapter
    }

    override fun onDestroyView() {
        debugLoggingTree.removeCallback(this)

        super.onDestroyView()
    }


    // DebugLoggingTree.Callback Implementation

    override fun onLog(logMessage: LogMessage) {

        var canLog = true

        filter?.let { filter ->
            filter.excludesPriority?.let { excludesPriority ->
                if (logMessage.priority == excludesPriority) {
                    canLog = false
                }
            }
            filter.includesPriority?.let { includesPriority ->
                if (logMessage.priority != includesPriority) {
                    canLog = false
                }
            }
            filter.excludesTag?.let { excludesTag ->
                if (logMessage.tag?.equals(excludesTag, false) == true) {
                    canLog = false
                }
            }
            filter.includesTag?.let { includesTag ->
                if (logMessage.tag?.equals(includesTag, false) == false) {
                    canLog = false
                }
            }
        }
        if (canLog) {
            adapter.setData(adapter.items.toMutableList() + LogMessageBinder(logMessage))
        }
    }


    companion object {

        private const val ARG_FILTER = "filter"

        fun newInstance(filter: Filter? = null) = LoggingFragment().withArgs {
            putSerializable(ARG_FILTER, filter)
        }
    }


    class Filter(
        val includesPriority: Int? = null,
        val excludesPriority: Int? = null,
        val includesTag: String? = null,
        val excludesTag: String? = null

    ) : Serializable
}