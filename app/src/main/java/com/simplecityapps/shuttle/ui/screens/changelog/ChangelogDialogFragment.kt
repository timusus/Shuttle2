package com.simplecityapps.shuttle.ui.screens.changelog

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.simplecityapps.shuttle.BuildConfig
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.squareup.moshi.Moshi
import timber.log.Timber
import javax.inject.Inject

class ChangelogDialogFragment : DialogFragment(), Injectable {

    @Inject lateinit var moshi: Moshi

    @Inject lateinit var preferenceManager: GeneralPreferenceManager

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val changelog = try {
            moshi.adapter(Changelog::class.java).fromJson(requireContext().assets.open("changelog.json").bufferedReader().use { it.readText() })
        } catch (e: RuntimeException) {
            Timber.e(e, "Invalid changelog")
            null
        }

        preferenceManager.hasSeenChangelog = true

        return AlertDialog.Builder(requireContext())
            .setTitle("Changelog")
            .setMessage("v${BuildConfig.VERSION_NAME}\n\n- ${changelog?.commits?.joinToString("\n\n- ") ?: "Unknown"}")
            .setNegativeButton("Close", null)
            .show()
    }

    fun show(fragmentManager: FragmentManager) {
        show(fragmentManager, TAG)
    }

    companion object {
        const val TAG = "ChangelogDialog"

        fun newInstance() = ChangelogDialogFragment()
    }
}