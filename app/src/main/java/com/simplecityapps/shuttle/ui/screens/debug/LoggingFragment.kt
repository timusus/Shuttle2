package com.simplecityapps.shuttle.ui.screens.debug

import android.os.Bundle
import android.text.SpannableString
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.simplecityapps.shuttle.DebugLoggingTree
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import kotlinx.android.synthetic.main.fragment_debug_logging.*
import javax.inject.Inject

class LoggingFragment : Fragment(), Injectable, DebugLoggingTree.Callbacks {

    @Inject lateinit var debugLoggingTree: DebugLoggingTree

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_debug_logging, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        debugLoggingTree.callback = this
    }

    override fun onDestroyView() {
        debugLoggingTree.callback = null

        super.onDestroyView()
    }


    // DebugLoggingTree.Callbacks Implementation

    override fun onTextChanged(text: SpannableString) {
        textView.handler.post {
            textView.text = text
        }
    }
}