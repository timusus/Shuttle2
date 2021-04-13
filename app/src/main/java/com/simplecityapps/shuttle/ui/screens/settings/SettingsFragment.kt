package com.simplecityapps.shuttle.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.TransactionTooLargeException
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.recyclerview.widget.RecyclerView
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.MediaProvider
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.SongQuery
import com.simplecityapps.mediaprovider.repository.SongRepository
import com.simplecityapps.playback.ActivityIntentProvider
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.exoplayer.EqualizerAudioProcessor
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.widgets.WidgetManager
import com.simplecityapps.provider.emby.EmbyAuthenticationManager
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.debug.DebugLoggingTree
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.ui.ThemeManager
import com.simplecityapps.shuttle.ui.common.recyclerview.SectionedAdapter
import com.simplecityapps.shuttle.ui.screens.changelog.ChangelogDialogFragment
import com.simplecityapps.shuttle.ui.screens.onboarding.OnboardingParentFragmentArgs
import com.simplecityapps.shuttle.ui.screens.opensource.LicensesDialogFragment
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named

@Suppress("NAME_SHADOWING")
class SettingsFragment : PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener,
    Injectable,
    MediaImporter.Listener {

    @Inject lateinit var preferenceManager: GeneralPreferenceManager
    @Inject lateinit var playbackPreferenceManager: PlaybackPreferenceManager

    @Inject lateinit var mediaImporter: MediaImporter

    @Inject lateinit var songRepository: SongRepository

    @Inject lateinit var playbackManager: PlaybackManager
    @Inject lateinit var equalizerAudioProcessor: EqualizerAudioProcessor

    @Inject lateinit var embyAuthenticationManager: EmbyAuthenticationManager

    @Inject lateinit var imageLoader: ArtworkImageLoader

    @Inject lateinit var themeManager: ThemeManager

    @Named("AppCoroutineScope") @Inject lateinit var appCoroutineScope: CoroutineScope

    private var scanningProgressView: ProgressBar? = null
    private var scanningDialog: AlertDialog? = null


    private val coroutineScope = CoroutineScope(Dispatchers.Main + CoroutineExceptionHandler { _, throwable -> Timber.e(throwable) })

    private var theme: GeneralPreferenceManager.Theme? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar: Toolbar = view.findViewById(R.id.toolbar)
        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        theme = preferenceManager.themeBase

        preferenceScreen.sharedPreferences.registerOnSharedPreferenceChangeListener(this)

        preferenceScreen.findPreference<Preference>("changelog_show")?.setOnPreferenceClickListener {
            ChangelogDialogFragment.newInstance().show(childFragmentManager)
            true
        }

        preferenceScreen.findPreference<Preference>("licenses_show")?.setOnPreferenceClickListener {
            LicensesDialogFragment.newInstance().show(childFragmentManager)
            true
        }

        preferenceScreen.findPreference<Preference>("pref_media_provider")?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.onboardingFragment, OnboardingParentFragmentArgs(false).toBundle())
            true
        }

        preferenceScreen.findPreference<Preference>("pref_crash_reporting")?.setOnPreferenceClickListener {
            if (!preferenceManager.crashReportingEnabled) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Requires Restart")
                    .setMessage("In order to completely opt-out of crash reporting, please restart Shuttle. Make sure to pause, swipe away the notification, and clear the app from recents.")
                    .setNegativeButton("Close", null)
                    .show()
            }
            true
        }

        preferenceScreen.findPreference<Preference>("pref_media_rescan")?.setOnPreferenceClickListener {
            appCoroutineScope.launch {
                mediaImporter.import()
            }

            val customView = View.inflate(requireContext(), R.layout.progress_dialog_loading_horizontal, null)
            scanningProgressView = customView.findViewById(R.id.progressBar)
            scanningDialog = MaterialAlertDialogBuilder(requireContext())
                .setView(customView)
                .setNegativeButton("Close", null)
                .show()

            true
        }

        preferenceScreen.findPreference<Preference>("pref_excluded")?.setOnPreferenceClickListener {
            val adapter = SectionedAdapter(viewLifecycleOwner.lifecycleScope)
            val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_exclude_list, null)
            val recyclerView = dialogView.findViewById<RecyclerView>(R.id.recyclerView)
            val emptyLabel = dialogView.findViewById<TextView>(R.id.emptyLabel)
            recyclerView.adapter = adapter

            val excludeListener = object : ExcludeBinder.Listener {
                override fun onRemoveClicked(song: Song) {
                    coroutineScope.launch {
                        songRepository.setExcluded(listOf(song), false)
                    }
                }
            }

            coroutineScope.launch {
                songRepository
                    .getSongs(SongQuery.All(includeExcluded = true))
                    .filterNotNull()
                    .map { songList -> songList.filter { song -> song.blacklisted } }
                    .collect { songs ->
                        adapter.update(songs.map { ExcludeBinder(it, imageLoader, excludeListener) })
                        emptyLabel.isVisible = songs.isEmpty()
                    }
            }

            MaterialAlertDialogBuilder(requireContext()).setTitle("Excluded")
                .setView(dialogView)
                .setPositiveButton("Close", null)
                .setNegativeButton("Clear") { _, _ ->
                    coroutineScope.launch {
                        songRepository.clearExcludeList()
                    }
                }
                .show()

            true
        }

        preferenceScreen.findPreference<Preference>("pref_clear_artwork")?.setOnPreferenceClickListener {
            MaterialAlertDialogBuilder(requireContext())
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

        preferenceScreen.findPreference<Preference>("widget_dark_mode")?.setOnPreferenceClickListener {
            (requireContext().applicationContext as ActivityIntentProvider).updateAppWidgets(WidgetManager.UpdateReason.Unknown)
            true
        }

        preferenceScreen.findPreference<SeekBarPreference>("widget_background_opacity")?.setOnPreferenceChangeListener { _, _ ->
            (requireContext().applicationContext as ActivityIntentProvider).updateAppWidgets(WidgetManager.UpdateReason.Unknown)
            true
        }

        preferenceScreen.findPreference<Preference>("pref_amoled_mode")?.setOnPreferenceChangeListener { _, _ ->
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Requires Restart")
                .setMessage("S2 must be restarted for theme changes to take effect")
                .setNegativeButton("Close", null)
                .show()
            true
        }

        preferenceScreen.findPreference<Preference>("pref_copy_debug_logs")?.setOnPreferenceClickListener {
            val clipboardManager: ClipboardManager = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val file = requireContext().getFileStreamPath(DebugLoggingTree.FILE_NAME)
            if (file.exists()) {
                val clip = ClipData.newPlainText("Shuttle Logs", requireContext().getFileStreamPath(DebugLoggingTree.FILE_NAME).readText(Charsets.UTF_8))
                try {
                    clipboardManager.setPrimaryClip(clip)
                    Toast.makeText(requireContext(), "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
                } catch (e: TransactionTooLargeException) {
                    Toast.makeText(requireContext(), "Log file is too large for clipboard", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "Log file empty", Toast.LENGTH_SHORT).show()
            }

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
            "pref_theme", "pref_theme_accent", "pref_theme_extra_dark" -> {
                setTheme()
            }
        }
    }

    private fun setTheme() {
        themeManager.setDayNightMode()
        activity?.recreate()
    }


    // MediaImporter.Listener Implementation

    override fun onProgress(providerType: MediaProvider.Type, progress: Int, total: Int, song: Song) {
        scanningProgressView?.progress = ((progress / total.toFloat()) * 100).toInt()
    }

    override fun onAllComplete() {
        scanningDialog?.dismiss()
    }


    // Static

    companion object {
        fun newInstance() = SettingsFragment()
    }
}