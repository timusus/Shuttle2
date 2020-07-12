package com.simplecityapps.shuttle.ui.screens.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.RecyclerView
import au.com.simplecityapps.shuttle.imageloading.glide.GlideImageLoader
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.SongRepository
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.recyclerview.SectionedAdapter
import com.simplecityapps.shuttle.ui.screens.changelog.ChangelogDialogFragment
import com.simplecityapps.shuttle.ui.screens.onboarding.OnboardingParentFragmentArgs
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@Suppress("NAME_SHADOWING")
class SettingsFragment : PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener,
    Injectable,
    MediaImporter.Listener {

    @Inject lateinit var preferenceManager: GeneralPreferenceManager
    @Inject lateinit var playbackPreferenceManager: PlaybackPreferenceManager

    @Inject lateinit var mediaImporter: MediaImporter

    @Inject lateinit var songRepository: SongRepository

    private var scanningProgressView: ProgressBar? = null
    private var scanningDialog: AlertDialog? = null

    private var imageLoader: GlideImageLoader by autoCleared()

    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    private var compositeDisposable = CompositeDisposable()

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
                    .setMessage("In order to completely opt-out of crash reporting, please restart Shuttle. Make sure to pause, swipe away the notification, and clear the app from recents.")
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

        preferenceScreen.findPreference<Preference>("pref_blacklist")?.setOnPreferenceClickListener {

            val adapter = SectionedAdapter()
            val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_blacklist, null)
            val recyclerView = dialogView.findViewById<RecyclerView>(R.id.recyclerView)
            val emptyLabel = dialogView.findViewById<TextView>(R.id.emptyLabel)
            recyclerView.adapter = adapter

            val blacklistListener = object : BlacklistBinder.Listener {
                override fun onRemoveClicked(song: Song) {
                    compositeDisposable.add(
                        songRepository.setBlacklisted(listOf(song), false)
                            .subscribeOn(Schedulers.io())
                            .subscribeBy(onError = { throwable -> Timber.e(throwable, "Failed to remove song from blacklist") })
                    )
                }
            }

            compositeDisposable.add(songRepository.getSongs(includeBlacklisted = true)
                .map { songList -> songList.filter { song -> song.blacklisted } }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                    onNext = { songs ->
                        adapter.setData(songs.map { BlacklistBinder(it, imageLoader, blacklistListener) })
                        emptyLabel.isVisible = songs.isEmpty()
                    },
                    onError = { error -> Timber.e(error, "Failed to load blacklisted songs") }
                ))

            AlertDialog.Builder(requireContext()).setTitle("Blacklist")
                .setView(dialogView)
                .setPositiveButton("Close", null)
                .setNegativeButton("Clear") { _, _ ->
                    compositeDisposable.add(
                        songRepository.clearBlacklist()
                            .subscribeOn(Schedulers.io())
                            .subscribeBy(onError = { throwable -> Timber.e(throwable, "Failed to clear blacklist") })
                    )
                }
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

        compositeDisposable.dispose()

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