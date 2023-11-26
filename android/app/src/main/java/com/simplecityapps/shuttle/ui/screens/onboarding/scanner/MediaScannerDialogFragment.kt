package com.simplecityapps.shuttle.ui.screens.onboarding.scanner

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.simplecityapps.shuttle.R

class MediaScannerDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val containerView = FragmentContainerView(requireContext())
        containerView.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        containerView.id = View.generateViewId()
        childFragmentManager.beginTransaction()
            .add(containerView.id, MediaScannerFragment.newInstance(scanAutomatically = true, showRescanButton = false, dismissOnScanComplete = false, showToolbar = false))
            .commit()

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.onboarding_media_scanner_title)
            .setView(containerView)
            .setNegativeButton(R.string.dialog_button_close) { _, _ ->
                dismiss()
            }
            .show()
    }

    fun show(manager: FragmentManager) {
        super.show(manager, "MediaScannerDialogFragment")
    }

    companion object {
        fun newInstance() = MediaScannerDialogFragment()
    }
}
