package com.simplecityapps.shuttle.ui.screens.settings.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.TransactionTooLargeException
import android.view.View
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.debug.DebugLoggingTree
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class DebugPreferenceFragment : PreferenceFragmentCompat() {

    @Inject
    lateinit var preferenceManager: GeneralPreferenceManager

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_debug, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar: Toolbar = view.findViewById(R.id.toolbar)
        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        toolbar.setTitle(R.string.pref_category_title_debug)

        preferenceScreen.findPreference<Preference>("pref_crash_reporting")?.setOnPreferenceClickListener {
            if (!preferenceManager.crashReportingEnabled) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.settings_crash_reporting_dialog_requires_restart))
                    .setMessage(getString(R.string.settings_crash_reporting_dialog_message))
                    .setNegativeButton(getString(R.string.dialog_button_close), null)
                    .show()
            }
            true
        }
        preferenceScreen.findPreference<Preference>("pref_copy_debug_logs")?.setOnPreferenceClickListener {
            val clipboardManager: ClipboardManager = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val file = requireContext().getFileStreamPath(DebugLoggingTree.FILE_NAME)
            if (file.exists()) {
                val clip = ClipData.newPlainText(getString(R.string.settings_logging_clipboard_name), requireContext().getFileStreamPath(DebugLoggingTree.FILE_NAME).readText(Charsets.UTF_8))
                try {
                    clipboardManager.setPrimaryClip(clip)
                    Toast.makeText(requireContext(), getString(R.string.settings_logging_clipboard_logs_copied), Toast.LENGTH_SHORT).show()
                } catch (e: TransactionTooLargeException) {
                    Toast.makeText(requireContext(), getString(R.string.settings_logging_clipboard_logs_too_large), Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), getString(R.string.settings_logging_clipboard_logs_empty), Toast.LENGTH_SHORT).show()
            }

            true
        }
    }
}