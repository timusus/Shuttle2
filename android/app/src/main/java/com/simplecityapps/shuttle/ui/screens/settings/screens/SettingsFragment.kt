package com.simplecityapps.shuttle.ui.screens.settings.screens

import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.simplecityapps.shuttle.R
import dagger.hilt.android.AndroidEntryPoint

@Suppress("NAME_SHADOWING")
@AndroidEntryPoint
class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?
    ) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar: Toolbar = view.findViewById(R.id.toolbar)
        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        preferenceScreen.findPreference<Preference>("pref_screen_display")?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.displayPreferenceFragment)
            true
        }
        preferenceScreen.findPreference<Preference>("pref_screen_playback")?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.playbackPreferenceFragment)
            true
        }
        preferenceScreen.findPreference<Preference>("pref_screen_media")?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.mediaPreferenceFragment)
            true
        }
        preferenceScreen.findPreference<Preference>("pref_screen_artwork")?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.artworkPreferenceFragment)
            true
        }
        preferenceScreen.findPreference<Preference>("pref_screen_widget")?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.widgetPreferenceManager)
            true
        }
        preferenceScreen.findPreference<Preference>("pref_screen_playlist")?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.playlistPreferenceFragment)
            true
        }
        preferenceScreen.findPreference<Preference>("pref_screen_app_info")?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.appInfoPreferenceFragment)
            true
        }
        preferenceScreen.findPreference<Preference>("pref_screen_privacy")?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.privacyPreferenceFragment)
            true
        }
        preferenceScreen.findPreference<Preference>("pref_screen_debug")?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.debugPreferenceFragment)
            true
        }
    }

    // Static

    companion object {
        fun newInstance() = SettingsFragment()
    }
}
