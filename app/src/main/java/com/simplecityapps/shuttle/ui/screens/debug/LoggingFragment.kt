package com.simplecityapps.shuttle.ui.screens.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.TransactionTooLargeException
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.shuttle.BuildConfig
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.debug.DebugLoggingTree
import com.simplecityapps.shuttle.debug.LogMessage
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.recyclerview.clearAdapterOnDetach
import com.simplecityapps.shuttle.ui.common.utils.withArgs
import java.io.Serializable
import javax.inject.Inject


class LoggingFragment : Fragment(), Injectable, DebugLoggingTree.Callback {

    @Inject lateinit var debugLoggingTree: DebugLoggingTree

    private lateinit var adapter: RecyclerAdapter

    private var recyclerView: RecyclerView by autoCleared()

    private var filter: Filter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        filter = arguments?.getSerializable(ARG_FILTER) as? Filter
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_debug_logging, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = RecyclerAdapter()
        adapter.loggingEnabled = false

        debugLoggingTree.addCallback(this)

        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.adapter = adapter
        recyclerView.clearAdapterOnDetach()

        val dumpButton: Button = view.findViewById(R.id.dumpButton)
        dumpButton.setOnClickListener {
            val clipboardManager: ClipboardManager = context!!.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val file = context!!.getFileStreamPath(DebugLoggingTree.FILE_NAME)
            if (file.exists()) {
                val clip = ClipData.newPlainText("Shuttle Logs", context!!.getFileStreamPath(DebugLoggingTree.FILE_NAME).readText(Charsets.UTF_8))
                try {
                    clipboardManager.primaryClip = clip
                } catch (e: TransactionTooLargeException) {
                    Toast.makeText(context!!, "Log file is too big large clipboard", Toast.LENGTH_SHORT).show()
                }
                Toast.makeText(context!!, "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context!!, "Log file is empty", Toast.LENGTH_SHORT).show()
            }
        }

        val versionInfo: TextView = view.findViewById(R.id.versionInfoLabel)
        versionInfo.text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    }

    override fun onDestroyView() {
        adapter.dispose()

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
            recyclerView.post {
                adapter.setData(adapter.items.toMutableList() + LogMessageBinder(logMessage), false)
            }
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