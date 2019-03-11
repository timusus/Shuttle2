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
import kotlinx.android.synthetic.main.fragment_debug_logging.*
import javax.inject.Inject

class LoggingFragment : Fragment(), Injectable, DebugLoggingTree.Callbacks {

    @Inject lateinit var debugLoggingTree: DebugLoggingTree

    private val adapter = RecyclerAdapter()

    init {
        adapter.loggingEnabled = false
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_debug_logging, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        debugLoggingTree.callback = this

        recyclerView.adapter = adapter
    }

    override fun onDestroyView() {
        debugLoggingTree.callback = null

        super.onDestroyView()
    }


    // DebugLoggingTree.Callbacks Implementation

    override fun onLog(logMessage: LogMessage) {
        adapter.setData(adapter.items.toMutableList() + LogMessageBinder(logMessage))
    }
}