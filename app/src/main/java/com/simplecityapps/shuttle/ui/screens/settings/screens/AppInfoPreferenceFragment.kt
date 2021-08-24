package com.simplecityapps.shuttle.ui.screens.settings.screens

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.screens.changelog.ChangelogDialogFragment
import com.simplecityapps.shuttle.ui.screens.opensource.LicensesDialogFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AppInfoPreferenceFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_app_info, rootKey)
    }

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == "changelog_show_on_launch") {
            preferenceScreen.findPreference<SwitchPreference>("changelog_show_on_launch")?.isChecked = prefs.getBoolean(key, true)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar: Toolbar = view.findViewById(R.id.toolbar)
        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        toolbar.setTitle(R.string.pref_category_title_app_info)

        preferenceScreen.findPreference<Preference>("changelog_show")?.setOnPreferenceClickListener {
            ChangelogDialogFragment.newInstance().show(childFragmentManager)
            true
        }

        preferenceScreen.findPreference<Preference>("licenses_show")?.setOnPreferenceClickListener {
            LicensesDialogFragment.newInstance().show(childFragmentManager)
            true
        }


        preferenceManager.sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    override fun onDestroyView() {
        preferenceManager.sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)

        super.onDestroyView()
    }
}