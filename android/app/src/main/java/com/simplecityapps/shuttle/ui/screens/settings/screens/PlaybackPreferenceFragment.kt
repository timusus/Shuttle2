package com.simplecityapps.shuttle.ui.screens.settings.screens

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.simplecityapps.playback.settings.PlaybackSettings
import com.simplecityapps.shuttle.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PlaybackPreferenceFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?
    ) {
        setPreferencesFromResource(R.xml.preferences_playback, rootKey)

        // Bit-perfect output needs AudioMixerAttributes, added in Android 14
        findPreference<Preference>(PlaybackSettings.UsbDacDirectOutput.key)?.isVisible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar: Toolbar = view.findViewById(R.id.toolbar)
        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        toolbar.setTitle(R.string.pref_category_title_playback)
    }
}
