package com.simplecityapps.shuttle.ui.screens.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import au.com.simplecityapps.shuttle.imageloading.glide.GlideImageLoader
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.screens.changelog.ChangelogDialogFragment
import com.simplecityapps.shuttle.ui.screens.onboarding.OnboardingParentFragmentArgs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

class SettingsFragment : PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener,
    Injectable,
    MediaImporter.Listener {

    @Inject lateinit var preferenceManager: GeneralPreferenceManager
    @Inject lateinit var playbackPreferenceManager: PlaybackPreferenceManager

    @Inject lateinit var mediaImporter: MediaImporter

    private var scanningProgressView: ProgressBar? = null
    private var scanningDialog: AlertDialog? = null

    private var imageLoader: GlideImageLoader by autoCleared()

    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        imageLoader = GlideImageLoader(this)

        val toolbar: Toolbar = view.findViewById(R.id.toolbar)
        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        preferenceScreen.sharedPreferences.registerOnSharedPreferenceChangeListener(this)

        preferenceScreen.findPreference<Preference>("changelog_show")?.setOnPreferenceClickListener {
            ChangelogDialogFragment.newInstance().show(childFragmentManager)
            true
        }

        preferenceScreen.findPreference<Preference>("pref_media_provider")?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.onboardingFragment, OnboardingParentFragmentArgs(false).toBundle())
            true
        }

        preferenceScreen.findPreference<Preference>("pref_crash_reporting")?.setOnPreferenceClickListener {
            if (!preferenceManager.crashReportingEnabled) {
                AlertDialog.Builder(requireContext())
                    .setTitle("Requires Restart")
                    .setMessage("In order to completely opt-out of Crashlytics, please restart Shuttle. Make sure to pause, swipe away the notification, and clear the app from recents.")
                    .setNegativeButton("Close", null)
                    .show()
            }
            true
        }

        preferenceScreen.findPreference<Preference>("pref_media_rescan")?.setOnPreferenceClickListener {
            mediaImporter.rescan()

            val customView = View.inflate(requireContext(), R.layout.progress_dialog_loading_horizontal, null)
            scanningProgressView = customView.findViewById(R.id.progressBar)
            scanningDialog = AlertDialog.Builder(requireContext())
                .setView(customView)
                .setNegativeButton("Close", null)
                .show()

            true
        }

        preferenceScreen.findPreference<Preference>("pref_clear_artwork")?.setOnPreferenceClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Clear Artwork")
                .setMessage("This will permanently remove all cached artwork")
                .setPositiveButton("Clear") { _, _ ->
                    coroutineScope.launch {
                        context?.let { context ->
                            imageLoader.clearCache(context)
                        }
                    }
                }
                .setNegativeButton("Close", null)
                .show()

            true
        }

        mediaImporter.listeners.add(this)
    }

    override fun onDestroyView() {
        preferenceScreen.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        mediaImporter.listeners.remove(this)

        coroutineScope.cancel()

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

    // MediaImporter.Listener Implementation

    override fun onProgress(progress: Float, message: String) {
        scanningProgressView?.progress = (progress * 100).toInt()
    }

    override fun onComplete() {
        super.onComplete()
        scanningDialog?.dismiss()
    }


    // Static

    companion object {
        fun newInstance() = SettingsFragment()
    }
}