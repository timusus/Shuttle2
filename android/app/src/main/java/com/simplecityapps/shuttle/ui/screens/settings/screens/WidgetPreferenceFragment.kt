package com.simplecityapps.shuttle.ui.screens.settings.screens

import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.widgets.WidgetManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class WidgetPreferenceFragment : PreferenceFragmentCompat() {
    @Inject
    lateinit var widgetManager: WidgetManager

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?
    ) {
        setPreferencesFromResource(R.xml.preferences_widget, rootKey)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar: Toolbar = view.findViewById(R.id.toolbar)
        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        toolbar.setTitle(R.string.pref_category_title_widgets)

        preferenceScreen.findPreference<Preference>("widget_dark_mode")?.setOnPreferenceClickListener {
            widgetManager.updateAppWidgets(WidgetManager.UpdateReason.Unknown)
            true
        }

        preferenceScreen.findPreference<SeekBarPreference>("widget_background_opacity")?.setOnPreferenceChangeListener { _, _ ->
            widgetManager.updateAppWidgets(WidgetManager.UpdateReason.Unknown)
            true
        }
    }
}
