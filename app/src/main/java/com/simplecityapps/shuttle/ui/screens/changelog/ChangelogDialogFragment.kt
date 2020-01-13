package com.simplecityapps.shuttle.ui.screens.changelog

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.simplecityapps.shuttle.BuildConfig
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.dagger.Injectable
import com.squareup.moshi.Moshi
import javax.inject.Inject

class ChangelogDialogFragment : DialogFragment(), Injectable {

    @Inject lateinit var moshi: Moshi

    @Inject lateinit var preferenceManager: GeneralPreferenceManager

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val changelog = moshi.adapter(Changelog::class.java).fromJson(context!!.assets.open("changelog.json").bufferedReader().use { it.readText() })

        preferenceManager.hasSeenChangelog = true

        return AlertDialog.Builder(context!!)
            .setTitle("Changelog")
            .setMessage("v${BuildConfig.VERSION_NAME}\n\n- ${changelog?.commits?.joinToString("\n\n- ") ?: "Unknown"}")
            .setNegativeButton("Close", null)
            .show()
    }
}