package com.simplecityapps.shuttle.ui.screens.settings.screens

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.RecyclerView
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.mediaprovider.worker.ImportFrequency
import com.simplecityapps.mediaprovider.worker.MediaImportWorker
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.ui.common.recyclerview.SectionedAdapter
import com.simplecityapps.shuttle.ui.screens.onboarding.OnboardingParentFragmentArgs
import com.simplecityapps.shuttle.ui.screens.onboarding.scanner.MediaScannerDialogFragment
import com.simplecityapps.shuttle.ui.screens.settings.ExcludeBinder
import com.squareup.phrase.Phrase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.DateFormat
import javax.inject.Inject

@AndroidEntryPoint
class MediaPreferenceFragment : PreferenceFragmentCompat() {

    @Inject
    lateinit var songRepository: SongRepository

    @Inject
    lateinit var imageLoader: ArtworkImageLoader

    @Inject
    lateinit var preferenceManager: GeneralPreferenceManager

    @Inject
    lateinit var mediaImporter: MediaImporter

    private var mediaImportListener: MediaImporter.Listener? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_media, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar: Toolbar = view.findViewById(R.id.toolbar)
        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        toolbar.setTitle(R.string.pref_category_title_media)

        preferenceScreen.findPreference<Preference>("pref_media_provider")?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.onboardingFragment, OnboardingParentFragmentArgs(false).toBundle())
            true
        }

        preferenceScreen.findPreference<Preference>("pref_media_rescan")?.setOnPreferenceClickListener {
            MediaScannerDialogFragment.newInstance().show(childFragmentManager)
            true
        }

        preferenceScreen.findPreference<Preference>("pref_excluded")?.setOnPreferenceClickListener {
            val adapter = SectionedAdapter(viewLifecycleOwner.lifecycleScope)
            val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_exclude_list, null)
            val recyclerView = dialogView.findViewById<RecyclerView>(R.id.recyclerView)
            val emptyLabel = dialogView.findViewById<TextView>(R.id.emptyLabel)
            recyclerView.adapter = adapter

            val excludeListener = object : ExcludeBinder.Listener {
                override fun onRemoveClicked(song: com.simplecityapps.shuttle.model.Song) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        songRepository.setExcluded(listOf(song), false)
                    }
                }
            }

            viewLifecycleOwner.lifecycleScope.launch {
                songRepository
                    .getSongs(SongQuery.All(includeExcluded = true))
                    .filterNotNull()
                    .map { songList -> songList.filter { song -> song.blacklisted } }
                    .collect { songs ->
                        adapter.update(songs.map { ExcludeBinder(it, imageLoader, excludeListener) })
                        emptyLabel.isVisible = songs.isEmpty()
                    }
            }

            MaterialAlertDialogBuilder(requireContext()).setTitle(getString(R.string.settings_dialog_title_excluded))
                .setView(dialogView)
                .setPositiveButton(getString(R.string.dialog_button_close), null)
                .setNegativeButton(getString(R.string.settings_dialog_button_clear_artwork)) { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        songRepository.clearExcludeList()
                    }
                }
                .show()

            true
        }


        val rescanFrequencyPreference: ListPreference? = preferenceScreen.findPreference("pref_media_rescan_frequency")
        rescanFrequencyPreference?.setOnPreferenceChangeListener { _, newValue ->
            MediaImportWorker.updateWork(
                context = requireContext(),
                importFrequency = ImportFrequency.values().first { it.value == (newValue as String).toInt() }
            )

            val lastScanDateString = preferenceManager.lastMediaImportDate?.let { Phrase.from(resources, R.string.pref_last_scan_date).put("date", dateFormat.format(it)).format() }

            rescanFrequencyPreference.summary = (rescanFrequencyPreference.entries[rescanFrequencyPreference.entryValues.indexOf(newValue)]
                ?: "").toString() + " " + lastScanDateString

            true
        }

        mediaImportListener = object : MediaImporter.Listener {
            override fun onSongImportProgress(providerType: MediaProviderType, message: String, progress: Progress?) {
            }

            override fun onAllComplete() {
                super.onAllComplete()

                updateLastScannedText()
            }
        }

        mediaImporter.listeners.add(mediaImportListener!!)
    }

    override fun onResume() {
        super.onResume()

        updateLastScannedText()
    }

    override fun onDestroyView() {

        mediaImporter.listeners.remove(mediaImportListener)

        super.onDestroyView()
    }

    fun updateLastScannedText() {
        val lastScanDateString = preferenceManager.lastMediaImportDate?.let { Phrase.from(resources, R.string.pref_last_scan_date).put("date", dateFormat.format(it)).format() }

        val rescanFrequencyPreference: ListPreference? = preferenceScreen.findPreference("pref_media_rescan_frequency")
        rescanFrequencyPreference?.summary =
            (rescanFrequencyPreference?.entry ?: "").toString() + " " + lastScanDateString
    }

    companion object {
        val dateFormat: DateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    }
}
