package com.simplecityapps.shuttle.ui.screens.settings.screens

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.ui.ThemeManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class DisplayPreferenceFragment : PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    @Inject
    lateinit var preferenceManager: GeneralPreferenceManager

    @Inject
    lateinit var themeManager: ThemeManager

    private var theme: GeneralPreferenceManager.Theme? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_display, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        theme = preferenceManager.themeBase

        preferenceScreen.sharedPreferences.registerOnSharedPreferenceChangeListener(this)

        preferenceScreen.findPreference<Preference>("pref_amoled_mode")?.setOnPreferenceChangeListener { _, _ ->
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.settings_dialog_title_amoled_required_restart))
                .setMessage(getString(R.string.settings_dialog_message_amoled_requires_restart))
                .setNegativeButton(getString(R.string.dialog_button_close), null)
                .show()
            true
        }

        val toolbar: Toolbar = view.findViewById(R.id.toolbar)
        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
    }

    override fun onDestroyView() {
        preferenceScreen.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroyView()
    }


    // Private

    private fun setTheme() {
        themeManager.setDayNightMode()
        activity?.recreate()
    }

    // OnSharedPreferenceChangeListener Implementation

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        when (key) {
            "pref_theme", "pref_theme_accent", "pref_theme_extra_dark" -> {
                setTheme()
            }
        }
    }
}