package com.simplecityapps.shuttle.ui.screens.settings.screens

import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.screens.changelog.ChangelogDialogFragment
import com.simplecityapps.shuttle.ui.screens.opensource.LicensesDialogFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AppInfoPreferenceFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_app_info, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar: Toolbar = view.findViewById(R.id.toolbar)
        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        preferenceScreen.findPreference<Preference>("changelog_show")?.setOnPreferenceClickListener {
            ChangelogDialogFragment.newInstance().show(childFragmentManager)
            true
        }

        preferenceScreen.findPreference<Preference>("licenses_show")?.setOnPreferenceClickListener {
            LicensesDialogFragment.newInstance().show(childFragmentManager)
            true
        }
    }
}