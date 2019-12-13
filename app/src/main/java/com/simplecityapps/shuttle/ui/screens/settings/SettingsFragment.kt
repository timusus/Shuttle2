package com.simplecityapps.shuttle.ui.screens.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.simplecityapps.shuttle.GeneralPreferenceManager
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import javax.inject.Inject

class SettingsFragment : PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener,
    Injectable {

    @Inject lateinit var preferenceManager: GeneralPreferenceManager

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar: Toolbar = view.findViewById(R.id.toolbar)
        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        preferenceScreen.sharedPreferences.registerOnSharedPreferenceChangeListener(this)

        preferenceScreen.findPreference<Preference>("changelog_show")?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.action_settingsFragment_to_changelogFragment)
            true
        }
    }

    override fun onDestroyView() {
        preferenceScreen.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroyView()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }


    // OnSharedPreferenceChangeListener Implementation

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        when (key) {
            "pref_night_mode" -> {
                setNightMode(sharedPreferences.getString(key, "0") ?: "0")
            }
        }
    }

    private fun setNightMode(value: String) {
        when (value) {
            "0" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            "1" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "2" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }
    }
}