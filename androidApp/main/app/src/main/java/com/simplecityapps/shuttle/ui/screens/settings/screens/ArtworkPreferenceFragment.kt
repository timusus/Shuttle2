package com.simplecityapps.shuttle.ui.screens.settings.screens

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import au.com.simplecityapps.shuttle.imageloading.ArtworkDownloadService
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.simplecityapps.shuttle.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ArtworkPreferenceFragment : PreferenceFragmentCompat() {

    @Inject
    lateinit var imageLoader: ArtworkImageLoader

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_artwork, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar: Toolbar = view.findViewById(R.id.toolbar)
        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        toolbar.setTitle(R.string.pref_category_title_artwork)

        preferenceScreen.findPreference<Preference>("pref_clear_artwork")?.setOnPreferenceClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.settings_dialog_title_clear_artwork))
                .setMessage(getString(R.string.settings_dialog_message_clear_artwork))
                .setPositiveButton(getString(R.string.settings_dialog_button_clear_artwork)) { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        context?.let { context ->
                            imageLoader.clearCache(context)
                        }
                    }
                }
                .setNegativeButton(getString(R.string.dialog_button_close), null)
                .show()

            true
        }

        preferenceScreen.findPreference<Preference>("pref_download_artwork")?.setOnPreferenceClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.settings_dialog_title_download_artwork))
                .setMessage(getString(R.string.settings_dialog_message_download_artwork))
                .setPositiveButton(getString(R.string.settings_dialog_button_download_artwork)) { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        context?.let { context ->
                            context.startService(Intent(context, ArtworkDownloadService::class.java))
                        }
                    }
                }
                .setNegativeButton(getString(R.string.dialog_button_close), null)
                .show()

            true
        }

    }
}